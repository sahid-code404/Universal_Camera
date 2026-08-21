package com.sahidcode404.camera.camera.camera2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import com.sahidcode404.camera.core.model.CameraSessionState
import com.sahidcode404.camera.core.model.LensTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class Camera2PreviewController(private val context: Context) : AutoCloseable {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraThread = HandlerThread("CameraControl").apply { start() }
    private val handler = Handler(cameraThread.looper)
    private val executor = Executor { command -> handler.post(command) }

    private val _state = MutableStateFlow(CameraSessionState.CLOSED)
    val state: StateFlow<CameraSessionState> = _state

    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio: StateFlow<Float> = _zoomRatio

    private val _maxZoomRatio = MutableStateFlow(1f)
    val maxZoomRatio: StateFlow<Float> = _maxZoomRatio

    private var target: LensTarget? = null
    private var textureView: TextureView? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var previewSize: android.util.Size? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var requestCharacteristics: CameraCharacteristics? = null
    private var activeArray: Rect? = null
    private var currentCropRegion: Rect? = null
    private var currentZoomRatio = 1f
    private var generation = 0L

    fun attach(view: TextureView) {
        textureView = view
        view.surfaceTextureListener = listener
        if (view.isAvailable) reopen()
    }

    fun setTarget(newTarget: LensTarget?) {
        if (target == newTarget) return
        target = newTarget
        currentZoomRatio = 1f
        _zoomRatio.value = 1f
        reopen()
    }

    fun resume() = reopen()

    fun multiplyZoom(scaleFactor: Float) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
        handler.post {
            setZoomRatioInternal(currentZoomRatio * scaleFactor)
        }
    }

    fun setZoomRatio(ratio: Float) {
        if (!ratio.isFinite()) return
        handler.post { setZoomRatioInternal(ratio) }
    }

    fun focusAt(normalizedX: Float, normalizedY: Float) {
        handler.post {
            val captureSession = session ?: return@post
            val builder = previewRequestBuilder ?: return@post
            val chars = requestCharacteristics ?: return@post
            val crop = currentCropRegion ?: activeArray ?: return@post
            val (sensorX, sensorY) = mapPreviewPointToSensor(
                normalizedX.coerceIn(0f, 1f),
                normalizedY.coerceIn(0f, 1f),
                chars,
            )
            val centerX = crop.left + (sensorX * crop.width()).roundToInt()
            val centerY = crop.top + (sensorY * crop.height()).roundToInt()
            val halfSide = (min(crop.width(), crop.height()) * 0.07f).roundToInt().coerceAtLeast(48)
            val meteringRect = Rect(
                (centerX - halfSide).coerceIn(crop.left, crop.right - 1),
                (centerY - halfSide).coerceIn(crop.top, crop.bottom - 1),
                (centerX + halfSide).coerceIn(crop.left + 1, crop.right),
                (centerY + halfSide).coerceIn(crop.top + 1, crop.bottom),
            )
            if (meteringRect.width() <= 0 || meteringRect.height() <= 0) return@post
            val metering = MeteringRectangle(meteringRect, MeteringRectangle.METERING_WEIGHT_MAX)

            runCatching {
                if ((chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0) > 0) {
                    builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(metering))
                }
                if ((chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0) > 0) {
                    builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(metering))
                }

                val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                if (afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                    captureSession.capture(
                        builder.build(),
                        object : CameraCaptureSession.CaptureCallback() {},
                        handler,
                    )
                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                }
                captureSession.setRepeatingRequest(
                    builder.build(),
                    object : CameraCaptureSession.CaptureCallback() {},
                    handler,
                )
            }
        }
    }

    private val listener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = reopen()
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeCamera()
            return true
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    private fun reopen() {
        val view = textureView ?: return
        val selected = target ?: return
        if (!view.isAvailable) return
        handler.post {
            generation++
            val myGeneration = generation
            closeCameraInternal()
            openCameraInternal(selected, myGeneration)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCameraInternal(selected: LensTarget, myGeneration: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            _state.value = CameraSessionState.ERROR
            return
        }
        try {
            _state.value = CameraSessionState.OPENING
            val charsForStream = characteristicsForTarget(selected)
            requestCharacteristics = charsForStream
            activeArray = charsForStream.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val maxZoom = max(1f, charsForStream.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
            _maxZoomRatio.value = maxZoom
            currentZoomRatio = currentZoomRatio.coerceIn(1f, maxZoom)
            _zoomRatio.value = currentZoomRatio

            val map = charsForStream.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
            previewSize = choosePreviewSize(sizes, textureView?.width ?: 1080, textureView?.height ?: 1440)
            manager.openCamera(selected.logicalCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (myGeneration != generation) {
                        device.close()
                        return
                    }
                    camera = device
                    createSession(device, selected, myGeneration)
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    if (myGeneration == generation) _state.value = CameraSessionState.ERROR
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    if (myGeneration == generation) _state.value = CameraSessionState.ERROR
                }
            }, handler)
        } catch (_: Throwable) {
            _state.value = CameraSessionState.ERROR
        }
    }

    private fun createSession(device: CameraDevice, selected: LensTarget, myGeneration: Long) {
        val view = textureView ?: return
        val st = view.surfaceTexture ?: return
        val size = previewSize ?: return
        st.setDefaultBufferSize(size.width, size.height)
        val surface = Surface(st)
        previewSurface = surface
        val output = OutputConfiguration(surface)
        if (selected.physicalCameraId != null) {
            runCatching { output.setPhysicalCameraId(selected.physicalCameraId) }
        }
        _state.value = CameraSessionState.CONFIGURING
        val config = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            listOf(output),
            executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(captureSession: CameraCaptureSession) {
                    if (myGeneration != generation) {
                        captureSession.close()
                        surface.release()
                        return
                    }
                    session = captureSession
                    runCatching {
                        val requestChars = characteristicsForTarget(selected)
                        requestCharacteristics = requestChars
                        activeArray = requestChars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                        val afModes = requestChars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                        val afMode = when {
                            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) -> CaptureRequest.CONTROL_AF_MODE_AUTO
                            else -> CaptureRequest.CONTROL_AF_MODE_OFF
                        }
                        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AF_MODE, afMode)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        }
                        previewRequestBuilder = builder
                        applyZoom(builder, requestChars, currentZoomRatio)
                        captureSession.setRepeatingRequest(
                            builder.build(),
                            object : CameraCaptureSession.CaptureCallback() {},
                            handler,
                        )
                        _state.value = CameraSessionState.PREVIEW
                        view.post { configureTransform(view.width, view.height) }
                    }.onFailure { _state.value = CameraSessionState.ERROR }
                }
                override fun onConfigureFailed(captureSession: CameraCaptureSession) {
                    captureSession.close()
                    surface.release()
                    if (myGeneration == generation) _state.value = CameraSessionState.ERROR
                }
            }
        )
        try {
            device.createCaptureSession(config)
        } catch (_: CameraAccessException) {
            surface.release()
            _state.value = CameraSessionState.ERROR
        }
    }

    private fun setZoomRatioInternal(requested: Float) {
        val builder = previewRequestBuilder ?: return
        val chars = requestCharacteristics ?: return
        val maxZoom = max(1f, chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
        val clamped = requested.coerceIn(1f, maxZoom)
        if (abs(clamped - currentZoomRatio) < 0.002f) return
        currentZoomRatio = clamped
        _zoomRatio.value = clamped
        applyZoom(builder, chars, clamped)
        val captureSession = session ?: return
        runCatching {
            captureSession.setRepeatingRequest(
                builder.build(),
                object : CameraCaptureSession.CaptureCallback() {},
                handler,
            )
        }
    }

    private fun applyZoom(builder: CaptureRequest.Builder, chars: CameraCharacteristics, ratio: Float) {
        val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val maxZoom = max(1f, chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
        val safeRatio = ratio.coerceIn(1f, maxZoom)
        val cropWidth = (sensor.width() / safeRatio).roundToInt().coerceAtLeast(2)
        val cropHeight = (sensor.height() / safeRatio).roundToInt().coerceAtLeast(2)
        val left = sensor.centerX() - cropWidth / 2
        val top = sensor.centerY() - cropHeight / 2
        val crop = Rect(left, top, left + cropWidth, top + cropHeight)
        currentCropRegion = crop
        builder.set(CaptureRequest.SCALER_CROP_REGION, crop)
    }

    private fun choosePreviewSize(
        sizes: Array<out android.util.Size>,
        width: Int,
        height: Int,
    ): android.util.Size? {
        if (sizes.isEmpty()) return null
        val viewLong = max(width, height).coerceAtLeast(1)
        val viewShort = min(width, height).coerceAtLeast(1)
        val targetRatio = viewLong.toFloat() / viewShort.toFloat()
        val bounded = sizes.filter {
            max(it.width, it.height) <= 2560 && min(it.width, it.height) <= 1440
        }
        val source = bounded.ifEmpty { sizes.toList() }
        return source.minWithOrNull(
            compareBy<android.util.Size>(
                {
                    val longSide = max(it.width, it.height).toFloat()
                    val shortSide = min(it.width, it.height).coerceAtLeast(1).toFloat()
                    abs((longSide / shortSide) - targetRatio)
                },
                { -(it.width.toLong() * it.height.toLong()) },
            ),
        )
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val view = textureView ?: return
        val size = previewSize ?: return
        val chars = requestCharacteristics ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return

        val rotation = relativeRotationDegrees(chars, view.display?.rotation ?: Surface.ROTATION_0)
        val rotatedWidth = if (rotation == 90 || rotation == 270) size.height.toFloat() else size.width.toFloat()
        val rotatedHeight = if (rotation == 90 || rotation == 270) size.width.toFloat() else size.height.toFloat()
        val scale = max(viewWidth / rotatedWidth, viewHeight / rotatedHeight)
        val front = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT

        val matrix = Matrix().apply {
            setTranslate(-size.width / 2f, -size.height / 2f)
            postRotate(rotation.toFloat())
            if (front) postScale(-1f, 1f)
            postScale(scale, scale)
            postTranslate(viewWidth / 2f, viewHeight / 2f)
        }
        view.setTransform(matrix)
    }

    private fun mapPreviewPointToSensor(
        normalizedX: Float,
        normalizedY: Float,
        chars: CameraCharacteristics,
    ): Pair<Float, Float> {
        var x = normalizedX
        var y = normalizedY
        val front = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        if (front) x = 1f - x
        val rotation = relativeRotationDegrees(chars, textureView?.display?.rotation ?: Surface.ROTATION_0)
        return when (rotation) {
            90 -> y to (1f - x)
            180 -> (1f - x) to (1f - y)
            270 -> (1f - y) to x
            else -> x to y
        }
    }

    private fun relativeRotationDegrees(chars: CameraCharacteristics, displayRotation: Int): Int {
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val displayDegrees = when (displayRotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val front = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        return if (front) {
            (sensorOrientation + displayDegrees) % 360
        } else {
            (sensorOrientation - displayDegrees + 360) % 360
        }
    }

    private fun characteristicsForTarget(selected: LensTarget): CameraCharacteristics =
        runCatching {
            manager.getCameraCharacteristics(selected.physicalCameraId ?: selected.logicalCameraId)
        }.getOrElse {
            manager.getCameraCharacteristics(selected.logicalCameraId)
        }

    fun closeCamera() {
        handler.post {
            generation++
            closeCameraInternal()
        }
    }

    private fun closeCameraInternal() {
        _state.value = CameraSessionState.CLOSING
        runCatching { session?.stopRepeating() }
        runCatching { session?.abortCaptures() }
        runCatching { session?.close() }
        runCatching { camera?.close() }
        runCatching { previewSurface?.release() }
        session = null
        camera = null
        previewSurface = null
        previewRequestBuilder = null
        requestCharacteristics = null
        activeArray = null
        currentCropRegion = null
        _state.value = CameraSessionState.CLOSED
    }

    override fun close() {
        handler.post {
            generation++
            closeCameraInternal()
            cameraThread.quitSafely()
        }
    }
}
