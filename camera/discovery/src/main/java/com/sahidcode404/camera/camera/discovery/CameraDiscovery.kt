package com.sahidcode404.camera.camera.discovery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import androidx.core.content.ContextCompat
import com.sahidcode404.camera.core.model.CameraInventory
import com.sahidcode404.camera.core.model.LensDescriptor
import com.sahidcode404.camera.core.model.LensFacing
import com.sahidcode404.camera.core.model.LensTarget
import com.sahidcode404.camera.core.model.LensValidation
import com.sahidcode404.camera.core.model.LensValidationStatus
import com.sahidcode404.camera.core.model.PixelSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

class CameraDiscovery(
    context: Context,
    private val probe: CameraProbeContract = Camera2ActiveProbe(context),
    private val cache: CameraProbeCache = CameraProbeCache(context),
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(CameraManager::class.java)

    suspend fun discover(): CameraInventory = withContext(Dispatchers.Default) {
        val candidates = mutableListOf<LensDescriptor>()
        val rejected = mutableListOf<String>()
        val topLevelIds = manager.cameraIdList.toList()

        // Two-pass collection prevents a physical child that also appears as a top-level ID
        // from being shown twice simply because enumeration order changed.
        val physicalChildren = topLevelIds.flatMap { id ->
            runCatching { manager.getCameraCharacteristics(id).physicalCameraIds.toList() }
                .getOrDefault(emptyList())
        }.toSet()

        topLevelIds.forEach { id ->
            runCatching {
                val chars = manager.getCameraCharacteristics(id)
                val capabilities = chars
                    .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.toSet()
                    .orEmpty()
                val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val previewSizes = streamMap?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
                val backwardsCompatible = capabilities.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
                )
                val depthOnly = capabilities.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT,
                ) && !backwardsCompatible
                if (depthOnly || previewSizes.isEmpty()) {
                    rejected += id
                    return@runCatching
                }

                val physicalIds = chars.physicalCameraIds
                val isLogical = capabilities.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA,
                ) && physicalIds.isNotEmpty()
                if (isLogical) {
                    candidates += descriptor(
                        logicalId = id,
                        physicalId = null,
                        chars = chars,
                        isLogicalAuto = true,
                        fallbackLabel = "Auto",
                    )
                    physicalIds.forEach { physicalId ->
                        runCatching {
                            val physicalChars = manager.getCameraCharacteristics(physicalId)
                            val lens = descriptor(
                                logicalId = id,
                                physicalId = physicalId,
                                chars = physicalChars,
                                isLogicalAuto = false,
                                fallbackLabel = "Lens",
                            )
                            if (lens.previewSizes.isNotEmpty()) {
                                candidates += lens
                            } else {
                                rejected += "$id::$physicalId"
                            }
                        }.onFailure { rejected += "$id::$physicalId" }
                    }
                } else if (id !in physicalChildren) {
                    candidates += descriptor(
                        logicalId = id,
                        physicalId = null,
                        chars = chars,
                        isLogicalAuto = false,
                        fallbackLabel = "Lens",
                    )
                }
            }.onFailure { rejected += id }
        }

        val unique = candidates.distinctBy { lens ->
            lens.target.physicalCameraId?.let { "physical:$it" }
                ?: "logical:${lens.target.logicalCameraId}"
        }

        val activeProbeAllowed = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        val validated = if (activeProbeAllowed) {
            unique.mapNotNull { lens ->
                val cached = cache.read(lens)
                val result = cached ?: runCatching { probe.validate(lens) }
                    .getOrElse { failure ->
                        ProbeResult(
                            previewUsable = false,
                            rawStillUsable = false,
                            continuousRawUsable = false,
                            videoUsable = false,
                            failureStage = "probe",
                            notes = listOf(
                                "Active probe threw ${failure.javaClass.simpleName}: ${failure.message.orEmpty()}",
                            ),
                        )
                    }.also { cache.write(lens, it) }

                if (!result.previewUsable) {
                    rejected += lens.stableKey
                    null
                } else {
                    lens.copy(
                        supportsRaw = lens.supportsRaw && result.rawStillUsable,
                        validation = LensValidation(
                            status = if (cached != null) {
                                LensValidationStatus.CACHED
                            } else {
                                LensValidationStatus.VALIDATED
                            },
                            previewUsable = result.previewUsable,
                            rawStillUsable = result.rawStillUsable,
                            continuousRawUsable = result.continuousRawUsable,
                            videoUsable = result.videoUsable,
                            deliveredPreviewFrames = result.deliveredPreviewFrames,
                            firstPreviewTimestampNs = result.firstPreviewTimestampNs,
                            lastPreviewTimestampNs = result.lastPreviewTimestampNs,
                            notes = result.notes,
                        ),
                    )
                }
            }
        } else {
            unique
        }

        val named = labelLenses(validated)
        CameraInventory(
            rear = named.filter { it.facing == LensFacing.BACK }.sortedWith(lensOrder()),
            front = named.filter { it.facing == LensFacing.FRONT }.sortedWith(lensOrder()),
            external = named.filter { it.facing == LensFacing.EXTERNAL }.sortedWith(lensOrder()),
            rejectedCameraIds = rejected.distinct(),
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
        val focal = chars
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()
        val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val equivalent = if (
            focal != null &&
            physicalSize != null &&
            physicalSize.width > 0f &&
            physicalSize.height > 0f
        ) {
            val sensorDiagonal = hypot(
                physicalSize.width.toDouble(),
                physicalSize.height.toDouble(),
            )
            (focal * (43.266615 / sensorDiagonal)).toFloat()
        } else {
            null
        }
        val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
            CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
            CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
            else -> LensFacing.UNKNOWN
        }
        val ois = chars
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.any { it == CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON } == true
        val fps = (chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray())
            .map { it.lower..it.upper }
        return LensDescriptor(
            target = LensTarget(logicalId, physicalId),
            facing = facing,
            userLabel = fallbackLabel,
            focalLengthMm = focal,
            equivalentFocalLengthMm = equivalent,
            aperture = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull(),
            sensorWidthMm = physicalSize?.width,
            sensorHeightMm = physicalSize?.height,
            supportsRaw = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW,
            ) && rawSizes.isNotEmpty(),
            supportsManualSensor = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
            ),
            supportsOis = ois,
            isLogicalAuto = isLogicalAuto,
            hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1,
            maxRawSize = rawSizes.maxByOrNull { it.area },
            previewSizes = preview.sortedByDescending { it.area },
            fpsRanges = fps,
        )
    }

    private fun labelLenses(input: List<LensDescriptor>): List<LensDescriptor> {
        return input.groupBy { it.facing }.values.flatMap { group ->
            val optics = group.filterNot { it.isLogicalAuto }
            val mainEq = optics
                .mapNotNull { it.equivalentFocalLengthMm }
                .minByOrNull { kotlin.math.abs(it - 24f) }
            group.map { lens ->
                if (lens.isLogicalAuto) {
                    lens.copy(userLabel = "Auto")
                } else {
                    val lensEq = lens.equivalentFocalLengthMm
                    val baseEq = mainEq
                    val ratio = if (lensEq != null && baseEq != null && baseEq > 0f) {
                        lensEq / baseEq
                    } else {
                        null
                    }
                    val label = ratio?.let { formatZoom(it) }
                        ?: lens.focalLengthMm?.let { "${"%.1f".format(it)} mm" }
                        ?: "Lens"
                    lens.copy(userLabel = label)
                }
            }
        }
    }

    private fun formatZoom(value: Float): String {
        val rounded = when {
            value < 1f -> (value * 10f).toInt() / 10f
            value < 3f -> (value * 10f).toInt() / 10f
            else -> value.toInt().toFloat()
        }
        return if (rounded % 1f == 0f) {
            "${rounded.toInt()}x"
        } else {
            "${"%.1f".format(rounded)}x"
        }
    }

    private fun lensOrder() = compareBy<LensDescriptor>(
        { !it.isLogicalAuto },
        { it.equivalentFocalLengthMm ?: Float.MAX_VALUE },
    )

    private fun Size.toPixelSize() = PixelSize(width, height)
}
