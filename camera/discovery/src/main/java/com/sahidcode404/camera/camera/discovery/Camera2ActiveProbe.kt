package com.sahidcode404.camera.camera.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import androidx.core.content.ContextCompat
import com.sahidcode404.camera.core.model.LensDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * Conservative Phase-1 hardware validation.
 *
 * The probe deliberately uses a small YUV stream to prove open/session/frame delivery and a
 * single RAW still to prove the RAW path. Sustained full-resolution RAW is not tested here;
 * that belongs to the Phase-4 ring-buffer/thermal safety gate.
 */
class Camera2ActiveProbe(
    context: Context,
    private val quirks: DeviceQuirkRegistry = DeviceQuirkRegistry(context),
) : CameraProbeContract {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(CameraManager::class.java)

    override suspend fun validate(lens: LensDescriptor): ProbeResult {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return ProbeResult(
                previewUsable = false,
                rawStillUsable = false,
                continuousRawUsable = false,
                videoUsable = false,
                failureStage = DeviceQuirkRegistry.STAGE_PREVIEW,
                notes = listOf("Camera permission is not granted; active validation was not run."),
            )
        }

        val thread = HandlerThread("CameraProbe-${lens.stableKey.hashCode()}").apply { start() }
        val handler = Handler(thread.looper)
        val executor = Executor { command -> handler.post(command) }
        val notes = mutableListOf<String>()

        try {
            val preview = runCatching {
                withTimeout(PREVIEW_TIMEOUT_MS) { probePreview(lens, handler, executor) }
            }
            if (preview.isFailure) {
                quirks.recordFailure(lens, DeviceQuirkRegistry.STAGE_PREVIEW)
                val reason = preview.exceptionOrNull()?.message
                    ?: preview.exceptionOrNull()?.javaClass?.simpleName
                    ?: "unknown preview probe failure"
                return ProbeResult(
                    previewUsable = false,
                    rawStillUsable = false,
                    continuousRawUsable = false,
                    videoUsable = false,
                    failureStage = DeviceQuirkRegistry.STAGE_PREVIEW,
                    notes = listOf("Preview probe failed: $reason"),
                )
            }
            quirks.recordSuccess(lens, DeviceQuirkRegistry.STAGE_PREVIEW)
            val evidence = preview.getOrThrow()

            val activeQuirks = quirks.quirksFor(lens)
            var rawUsable = false
            if (lens.supportsRaw) {
                if (CameraQuirk.SKIP_RAW_STILL_PROBE in activeQuirks) {
                    notes += "RAW still probe suppressed after repeated failures on this exact device build/lens."
                } else {
                    val raw = runCatching {
                        withTimeout(RAW_TIMEOUT_MS) { probeRawStill(lens, handler, executor) }
                    }
                    rawUsable = raw.getOrDefault(false)
                    if (rawUsable) {
                        quirks.recordSuccess(lens, DeviceQuirkRegistry.STAGE_RAW)
                    } else {
                        quirks.recordFailure(lens, DeviceQuirkRegistry.STAGE_RAW)
                        val reason = raw.exceptionOrNull()?.message ?: raw.exceptionOrNull()?.javaClass?.simpleName
                        notes += if (reason != null) {
                            "RAW still probe failed: $reason"
                        } else {
                            "RAW still probe did not deliver a valid frame."
                        }
                    }
                }
            }

            notes += "Continuous RAW is intentionally deferred to the Phase-4 sustained-throughput safety probe."
            return ProbeResult(
                previewUsable = true,
                rawStillUsable = rawUsable,
                continuousRawUsable = false,
                videoUsable = hasVideoOutput(lens),
                deliveredPreviewFrames = evidence.timestamps.size,
                firstPreviewTimestampNs = evidence.timestamps.firstOrNull(),
                lastPreviewTimestampNs = evidence.timestamps.lastOrNull(),
                notes = notes,
            )
        } finally {
            thread.quitSafely()
        }
    }

    private suspend fun probePreview(
        lens: LensDescriptor,
        handler: Handler,
        executor: Executor,
    ): PreviewEvidence {
        val chars = characteristicsForTarget(lens)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: error("No stream configuration map")
        val yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
        if (yuvSizes.isEmpty()) error("No YUV_420_888 output")
        val size = choosePreviewProbeSize(yuvSizes, quirks.quirksFor(lens))

        val reader = ImageReader.newInstance(
            size.width,
            size.height,
            ImageFormat.YUV_420_888,
            PREVIEW_MAX_IMAGES,
        )
        val frames = Channel<FrameSample>(capacity = PREVIEW_MAX_IMAGES)
        reader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull()
                ?: return@setOnImageAvailableListener
            try {
                val hasBytes = image.planes.any { it.buffer.remaining() > 0 }
                frames.trySend(FrameSample(image.timestamp, hasBytes))
            } finally {
                image.close()
            }
        }, handler)

        val device = openCamera(lens.target.logicalCameraId, handler)
        try {
            val output = outputFor(reader, lens)
            val session = createSession(device, listOf(output), executor)
            try {
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(reader.surface)
                    applySafeAutoControls(this, chars)
                }.build()
                session.setRepeatingRequest(
                    request,
                    object : CameraCaptureSession.CaptureCallback() {},
                    handler,
                )

                val timestamps = mutableListOf<Long>()
                while (timestamps.size < REQUIRED_PREVIEW_FRAMES) {
                    val sample = frames.receive()
                    if (!sample.hasBytes || sample.timestampNs <= 0L) continue
                    if (timestamps.isEmpty() || sample.timestampNs > timestamps.last()) {
                        timestamps += sample.timestampNs
                    }
                }
                return PreviewEvidence(size, timestamps)
            } finally {
                runCatching { session.stopRepeating() }
                runCatching { session.abortCaptures() }
                runCatching { session.close() }
            }
        } finally {
            runCatching { device.close() }
            reader.setOnImageAvailableListener(null, null)
            reader.close()
            frames.close()
        }
    }

    private suspend fun probeRawStill(
        lens: LensDescriptor,
        handler: Handler,
        executor: Executor,
    ): Boolean {
        val chars = characteristicsForTarget(lens)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: error("No stream configuration map for RAW")
        val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR) ?: emptyArray()
        if (rawSizes.isEmpty()) return false
        val size = rawSizes.minByOrNull { it.width.toLong() * it.height.toLong() } ?: return false

        val reader = ImageReader.newInstance(
            size.width,
            size.height,
            ImageFormat.RAW_SENSOR,
            RAW_MAX_IMAGES,
        )
        val frames = Channel<FrameSample>(capacity = RAW_MAX_IMAGES)
        reader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireNextImage() }.getOrNull()
                ?: return@setOnImageAvailableListener
            try {
                val hasBytes = image.planes.any { it.buffer.remaining() > 0 }
                frames.trySend(FrameSample(image.timestamp, hasBytes))
            } finally {
                image.close()
            }
        }, handler)

        val device = openCamera(lens.target.logicalCameraId, handler)
        try {
            val output = outputFor(reader, lens)
            val session = createSession(device, listOf(output), executor)
            try {
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                }.build()
                session.capture(
                    request,
                    object : CameraCaptureSession.CaptureCallback() {},
                    handler,
                )
                while (true) {
                    val sample = frames.receive()
                    if (sample.hasBytes && sample.timestampNs > 0L) return true
                }
            } finally {
                runCatching { session.abortCaptures() }
                runCatching { session.close() }
            }
        } finally {
            runCatching { device.close() }
            reader.setOnImageAvailableListener(null, null)
            reader.close()
            frames.close()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(cameraId: String, handler: Handler): CameraDevice =
        suspendCancellableCoroutine { continuation ->
            try {
                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (continuation.isActive) continuation.resume(camera) else camera.close()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Camera disconnected during probe: $cameraId"),
                            )
                        }
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Camera open error $error for $cameraId"),
                            )
                        }
                    }
                }, handler)
            } catch (t: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(t)
            }
        }

    private suspend fun createSession(
        device: CameraDevice,
        outputs: List<OutputConfiguration>,
        executor: Executor,
    ): CameraCaptureSession = suspendCancellableCoroutine { continuation ->
        try {
            val configuration = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (continuation.isActive) continuation.resume(session) else session.close()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Capture-session configuration failed"),
                            )
                        }
                    }
                },
            )
            device.createCaptureSession(configuration)
        } catch (t: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(t)
        }
    }

    private fun outputFor(reader: ImageReader, lens: LensDescriptor): OutputConfiguration =
        OutputConfiguration(reader.surface).apply {
            lens.target.physicalCameraId?.let { setPhysicalCameraId(it) }
        }

    private fun characteristicsForTarget(lens: LensDescriptor): CameraCharacteristics =
        runCatching {
            manager.getCameraCharacteristics(lens.target.physicalCameraId ?: lens.target.logicalCameraId)
        }.getOrElse {
            manager.getCameraCharacteristics(lens.target.logicalCameraId)
        }

    private fun hasVideoOutput(lens: LensDescriptor): Boolean {
        val map = characteristicsForTarget(lens)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return false
        return (map.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()).isNotEmpty()
    }

    private fun choosePreviewProbeSize(sizes: Array<out Size>, quirks: Set<CameraQuirk>): Size {
        if (CameraQuirk.FORCE_SMALL_PREVIEW_PROBE in quirks) {
            return sizes.minBy { it.width.toLong() * it.height.toLong() }
        }
        val targetArea = 640L * 480L
        val bounded = sizes.filter { it.width <= 1280 && it.height <= 720 }
        return (bounded.ifEmpty { sizes.toList() }).minBy {
            abs((it.width.toLong() * it.height.toLong()) - targetArea)
        }
    }

    private fun applySafeAutoControls(
        builder: CaptureRequest.Builder,
        chars: CameraCharacteristics,
    ) {
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        when {
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) ->
                builder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ->
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        }
    }

    private data class FrameSample(val timestampNs: Long, val hasBytes: Boolean)
    private data class PreviewEvidence(val size: Size, val timestamps: List<Long>)

    companion object {
        private const val REQUIRED_PREVIEW_FRAMES = 3
        private const val PREVIEW_MAX_IMAGES = 4
        private const val RAW_MAX_IMAGES = 2
        private const val PREVIEW_TIMEOUT_MS = 3_500L
        private const val RAW_TIMEOUT_MS = 5_000L
    }
}
