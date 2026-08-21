package com.sahidcode404.camera.camera.discovery

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import com.sahidcode404.camera.core.model.CameraInventory
import com.sahidcode404.camera.core.model.LensDescriptor
import com.sahidcode404.camera.core.model.LensFacing
import com.sahidcode404.camera.core.model.LensTarget
import com.sahidcode404.camera.core.model.LensValidation
import com.sahidcode404.camera.core.model.PixelSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Progressive universal camera discovery.
 *
 * This follows the startup architecture used by the reference Camera repository:
 * 1. Resolve one normal rear Camera2 route with the absolute minimum metadata and return it.
 * 2. Let the app open the viewfinder immediately.
 * 3. Only after first preview, run the complete Java + NDK hidden-AUX metadata scan.
 *
 * Discovery never serially opens every candidate camera. A lens is validated naturally when the
 * user selects it and the real preview session is configured, which avoids multi-second startup
 * probes and avoids rejecting valid vendor AUX routes merely because the HAL was busy during boot.
 */
class CameraDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(CameraManager::class.java)
    private val metadataSlots = Semaphore(4)

    /** Absolute critical path used before the first viewfinder frame. */
    suspend fun discoverPrimaryRear(): CameraInventory = withContext(Dispatchers.Default) {
        val ids = runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())
        var fallback: LensDescriptor? = null

        for (id in ids) {
            val chars = runCatching { manager.getCameraCharacteristics(id) }.getOrNull() ?: continue
            val capabilities = chars
                .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.toSet()
                .orEmpty()
            if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE !in capabilities) {
                continue
            }
            val seed = startupDescriptor(id, chars)
            if (fallback == null) fallback = seed
            if (seed.facing == LensFacing.BACK) {
                return@withContext CameraInventory(rear = listOf(seed))
            }
        }

        when (fallback?.facing) {
            LensFacing.BACK -> CameraInventory(rear = listOfNotNull(fallback))
            LensFacing.FRONT -> CameraInventory(front = listOfNotNull(fallback))
            LensFacing.EXTERNAL -> CameraInventory(external = listOfNotNull(fallback))
            else -> CameraInventory()
        }
    }

    /** Compatibility entry point for diagnostics/manual refresh. */
    suspend fun discover(): CameraInventory = discoverFull(deepScan = true)

    /**
     * Complete metadata pass. Java Camera2 metadata and the NDK hidden-ID scan run concurrently.
     * No camera device is opened here.
     */
    suspend fun discoverFull(deepScan: Boolean = true): CameraInventory = withContext(Dispatchers.Default) {
        val rejected = mutableListOf<String>()
        val javaIds = runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())

        val bundles = coroutineScope {
            val ndkDeferred = async(Dispatchers.Default) {
                NativeAuxCameraScanner.enumerateCandidateIds(deepScan = deepScan)
            }
            val advertisedDeferred = javaIds.map { id ->
                async(Dispatchers.IO) {
                    metadataSlots.withPermit { buildCandidateGroup(id) }
                }
            }
            val advertised = advertisedDeferred.awaitAll()
            val ndkIds = ndkDeferred.await()
            val hiddenIds = ndkIds.filterNot { it in javaIds }
            val hidden = hiddenIds.map { id ->
                async(Dispatchers.IO) {
                    metadataSlots.withPermit { buildCandidateGroup(id) }
                }
            }.awaitAll()
            advertised + hidden
        }

        val candidates = mutableListOf<LensDescriptor>()
        bundles.forEach { bundle ->
            candidates += bundle.lenses
            rejected += bundle.rejected
        }

        // Collapse Java top-level aliases, logical/physical aliases and hidden numeric aliases by
        // optical fingerprint. Prefer physical members because sibling switching can then reuse the
        // same logical CameraDevice instead of closing/reopening the device.
        val unique = candidates
            .groupBy(::opticalFingerprint)
            .values
            .map { group ->
                group.maxWithOrNull(
                    compareBy<LensDescriptor>({ routePreference(it) }, { it.maxRawSize?.area ?: 0L }),
                ) ?: group.first()
            }
            .distinctBy { it.stableKey }

        val named = labelLenses(unique)
        CameraInventory(
            rear = named.filter { it.facing == LensFacing.BACK }.sortedWith(lensOrder()),
            front = named.filter { it.facing == LensFacing.FRONT }.sortedWith(lensOrder()),
            external = named.filter { it.facing == LensFacing.EXTERNAL }.sortedWith(lensOrder()),
            rejectedCameraIds = rejected.distinct(),
        )
    }

    private fun buildCandidateGroup(cameraId: String): CandidateBundle {
        val rejected = mutableListOf<String>()
        val result = mutableListOf<LensDescriptor>()
        val chars = runCatching { manager.getCameraCharacteristics(cameraId) }
            .getOrElse {
                return CandidateBundle(emptyList(), listOf(cameraId))
            }

        val capabilities = chars
            .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.toSet()
            .orEmpty()
        val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val previewSizes = streamMap?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
        val backwardsCompatible = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE in capabilities
        val depthOnly = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT in capabilities && !backwardsCompatible
        if (depthOnly || previewSizes.isEmpty()) {
            return CandidateBundle(emptyList(), listOf(cameraId))
        }

        val physicalIds = chars.physicalCameraIds
        val logical = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in capabilities &&
            physicalIds.isNotEmpty()

        if (logical) {
            result += descriptor(
                logicalId = cameraId,
                physicalId = null,
                chars = chars,
                isLogicalAuto = true,
                fallbackLabel = "1x",
            )
            physicalIds.forEach { physicalId ->
                runCatching {
                    val physicalChars = manager.getCameraCharacteristics(physicalId)
                    val child = descriptor(
                        logicalId = cameraId,
                        physicalId = physicalId,
                        chars = physicalChars,
                        isLogicalAuto = false,
                        fallbackLabel = "Lens",
                    )
                    // Some physical metadata blocks omit standalone preview-size tables. The logical
                    // parent remains the transport, so inherit the parent's preview candidates.
                    val usableChild = if (child.previewSizes.isEmpty()) {
                        child.copy(previewSizes = previewSizes.map { it.toPixelSize() })
                    } else child
                    result += usableChild
                }.onFailure { rejected += "$cameraId::$physicalId" }
            }
        } else {
            result += descriptor(
                logicalId = cameraId,
                physicalId = null,
                chars = chars,
                isLogicalAuto = false,
                fallbackLabel = "Lens",
            )
        }
        return CandidateBundle(result, rejected)
    }

    private fun startupDescriptor(id: String, chars: CameraCharacteristics): LensDescriptor {
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull { it > 0f }
        val physical = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val equivalent = equivalent35mm(focal, physical?.width, physical?.height)
        val facing = facingOf(chars)
        return LensDescriptor(
            target = LensTarget(id, null, facing),
            facing = facing,
            userLabel = "1x",
            focalLengthMm = focal,
            equivalentFocalLengthMm = equivalent,
            aperture = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull(),
            sensorWidthMm = physical?.width,
            sensorHeightMm = physical?.height,
            supportsRaw = false,
            supportsManualSensor = false,
            supportsOis = false,
            isLogicalAuto = false,
            hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1,
            maxRawSize = null,
            previewSizes = emptyList(),
            fpsRanges = emptyList(),
            validation = LensValidation(),
        )
    }

    private fun descriptor(
        logicalId: String,
        physicalId: String?,
        chars: CameraCharacteristics,
        isLogicalAuto: Boolean,
        fallbackLabel: String,
    ): LensDescriptor {
        val capabilities = chars
            .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.toSet()
            .orEmpty()
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val preview = (map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray())
            .map { it.toPixelSize() }
        val rawSizes = (map?.getOutputSizes(ImageFormat.RAW_SENSOR) ?: emptyArray())
            .map { it.toPixelSize() }
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull { it > 0f }
        val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val equivalent = equivalent35mm(focal, physicalSize?.width, physicalSize?.height)
        val facing = facingOf(chars)
        val ois = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.any { it == CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON } == true
        val fps = (chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray())
            .map { it.lower..it.upper }

        return LensDescriptor(
            target = LensTarget(logicalId, physicalId, facing),
            facing = facing,
            userLabel = fallbackLabel,
            focalLengthMm = focal,
            equivalentFocalLengthMm = equivalent,
            aperture = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull(),
            sensorWidthMm = physicalSize?.width,
            sensorHeightMm = physicalSize?.height,
            // Like the reference resolver, a concrete RAW_SENSOR stream is stronger evidence than
            // a vendor metadata block forgetting to repeat the RAW capability flag.
            supportsRaw = rawSizes.isNotEmpty(),
            supportsManualSensor = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities,
            supportsOis = ois,
            isLogicalAuto = isLogicalAuto,
            hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1,
            maxRawSize = rawSizes.maxByOrNull { it.area },
            previewSizes = preview.sortedByDescending { it.area },
            fpsRanges = fps,
            validation = LensValidation(),
        )
    }

    private fun labelLenses(input: List<LensDescriptor>): List<LensDescriptor> =
        input.groupBy { it.facing }.values.flatMap { group ->
            val concrete = group.filterNot { it.isLogicalAuto }
            val baseEq = concrete.mapNotNull { it.equivalentFocalLengthMm }
                .minByOrNull { abs(it - if (group.firstOrNull()?.facing == LensFacing.FRONT) 26f else 25f) }
            group.map { lens ->
                if (lens.isLogicalAuto) lens.copy(userLabel = "1x")
                else {
                    val eq = lens.equivalentFocalLengthMm
                    val ratio = if (eq != null && baseEq != null && baseEq > 0f) eq / baseEq else null
                    lens.copy(
                        userLabel = ratio?.let(::formatZoom)
                            ?: lens.focalLengthMm?.let { "${"%.1f".format(it)} mm" }
                            ?: "Lens",
                    )
                }
            }
        }

    private fun routePreference(lens: LensDescriptor): Int =
        (if (!lens.isLogicalAuto) 100 else 0) +
            (if (lens.target.physicalCameraId != null) 64 else 0) +
            (if (lens.supportsRaw) 32 else 0) +
            (if (lens.supportsManualSensor) 8 else 0)

    private fun opticalFingerprint(lens: LensDescriptor): String {
        val focal = lens.focalLengthMm?.let { (it * 100f).roundToInt() } ?: -1
        val eq = lens.equivalentFocalLengthMm?.roundToInt() ?: -1
        val sw = lens.sensorWidthMm?.let { (it * 100f).roundToInt() } ?: -1
        val sh = lens.sensorHeightMm?.let { (it * 100f).roundToInt() } ?: -1
        // If optics are unavailable, keep routes separate instead of accidentally merging cameras.
        return if (focal < 0 && sw < 0) "route:${lens.stableKey}" else "${lens.facing}:$focal:$eq:$sw:$sh"
    }

    private fun equivalent35mm(focal: Float?, width: Float?, height: Float?): Float? {
        if (focal == null || width == null || height == null || width <= 0f || height <= 0f) return null
        val diagonal = hypot(width.toDouble(), height.toDouble())
        return (focal * (43.266615 / diagonal)).toFloat()
    }

    private fun facingOf(chars: CameraCharacteristics): LensFacing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
        CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
        CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
        CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
        else -> LensFacing.UNKNOWN
    }

    private fun formatZoom(value: Float): String {
        val rounded = when {
            value < 3f -> (value * 10f).roundToInt() / 10f
            else -> value.roundToInt().toFloat()
        }
        return if (rounded % 1f == 0f) "${rounded.toInt()}x" else "${"%.1f".format(rounded)}x"
    }

    private fun lensOrder() = compareBy<LensDescriptor>(
        { it.isLogicalAuto },
        { it.equivalentFocalLengthMm ?: Float.MAX_VALUE },
    )

    private fun Size.toPixelSize() = PixelSize(width, height)

    private data class CandidateBundle(
        val lenses: List<LensDescriptor>,
        val rejected: List<String>,
    )
}
