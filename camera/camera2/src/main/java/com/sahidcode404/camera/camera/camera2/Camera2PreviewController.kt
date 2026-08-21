package com.sahidcode404.camera.camera.camera2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import com.sahidcode404.camera.core.model.CameraSessionState
import com.sahidcode404.camera.core.model.LensTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.OutputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Metadata about a Phase-3 single sensor RAW capture that was encoded as DNG. */
data class RawDngCaptureInfo(
    val logicalCameraId: String,
    val physicalCameraId: String?,
    val width: Int,
    val height: Int,
    val sensorTimestampNs: Long,
)

class Camera2PreviewController(private val context: Context) : AutoCloseable {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val cameraThread = HandlerThread("CameraControl").apply { start() }
    private val handler = Handler(cameraThread.looper)
    private val uiHandler = Handler(context.mainLooper)
    private val executor = Executor { command -> handler.post(command) }
    private val rawCaptureMutex = Mutex()

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
    private var displayListenerRegistered = false
    private var lastDisplayRotation: Int? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            val view = textureView ?: return
            val display = view.display ?: return
            if (display.displayId != displayId) return
            val rotation = display.rotation
            if (lastDisplayRotation == rotation) return
            lastDisplayRotation = rotation
            view.post { configureTransform(view.width, view.height) }
        }
    }

    fun attach(view: TextureView) {
        textureView = view
        view.surfaceTextureListener = listener
        lastDisplayRotation = view.display?.rotation
        if (!displayListenerRegistered) {
            displayManager.registerDisplayListener(displayListener, uiHandler)
            displayListenerRegistered = true
        }
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

    /**
     * Captures exactly one maximum-size RAW_SENSOR frame, pairs it with the matching capture result,
     * and writes a standards-based DNG directly to [destination]. No JPEG/HEIF intermediate exists.
     *
     * Phase 3 deliberately uses an exclusive RAW session. This is slower than a combined preview/RAW
     * session but is more conservative across vendor HALs. Phase 4 will validate sustained combined RAW
     * throughput before introducing ZSL/ring-buffer capture.
     */
    suspend fun captureSingleRawDng(destination: OutputStream): RawDngCaptureInfo = rawCaptureMutex.withLock {
        val selected = target ?: error("No camera lens selected")
        val device = camera ?: error("Camera is not open")
        val myGeneration = generation
        if (_state.value != CameraSessionState.PREVIEW) error("Camera preview is not ready")

        val chars = characteristicsForTarget(selected)
        val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: error("Selected lens has no stream configuration map")
        val rawSize = (streamMap.getOutputSizes(ImageFormat.RAW_SENSOR) ?: emptyArray())
            .maxByOrNull { it.width.toLong() * it.height.toLong() }
            ?: error("Selected lens does not expose RAW_SENSOR output")

        val reader = ImageReader.newInstance(
            rawSize.width,
            rawSize.height,
            ImageFormat.RAW_SENSOR,
            RAW_MAX_IMAGES,
        )
        val imageDeferred = CompletableDeferred<Image>()
        val resultDeferred = CompletableDeferred<TotalCaptureResult>()
        val imageRef = AtomicReference<Image?>(null)
        var rawSession: CameraCaptureSession? = null

        reader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireNextImage() }.getOrNull()
                ?: return@setOnImageAvailableListener
            imageRef.set(image)
            if (!imageDeferred.complete(image)) {
                imageRef.compareAndSet(image, null)
                image.close()
            }
        }, handler)

        try {
            _state.value = CameraSessionState.CONFIGURING
            withContext(Dispatchers.Main.immediate) {
                // TextureView remains alive; only its CameraCaptureSession is replaced temporarily.
            }
            closeCurrentSessionForReconfiguration()

            rawSession = withTimeout(RAW_SESSION_TIMEOUT_MS) {
                createRawCaptureSession(device, reader, selected, myGeneration)
            }
            session = rawSession

            val stillBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                applySafeAutoControls(this, chars)
                currentCropRegion?.let { set(CaptureRequest.SCALER_CROP_REGION, it) }
            }

            rawSession.capture(
                stillBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) {
                        resultDeferred.complete(result)
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure,
                    ) {
                        resultDeferred.completeExceptionally(
                            IllegalStateException("RAW capture failed: reason=${failure.reason}"),
                        )
                    }
                },
                handler,
            )

            val image = withTimeout(RAW_CAPTURE_TIMEOUT_MS) { imageDeferred.await() }
            val totalResult = withTimeout(RAW_CAPTURE_TIMEOUT_MS) { resultDeferred.await() }
            if (generation != myGeneration || target != selected) {
                error("Camera changed while RAW capture was in flight")
            }

            val resultForDng: CaptureResult = selected.physicalCameraId
                ?.let { physicalId -> totalResult.physicalCameraResults[physicalId] }
                ?: totalResult
            val sensorTimestamp = resultForDng.get(CaptureResult.SENSOR_TIMESTAMP)
                ?: totalResult.get(CaptureResult.SENSOR_TIMESTAMP)
                ?: error("Capture result did not include SENSOR_TIMESTAMP")
            require(sensorTimestamp == image.timestamp) {
                "RAW image/result timestamp mismatch: image=${image.timestamp}, result=$sensorTimestamp"
            }

            withContext(Dispatchers.IO) {
                DngCreator(chars, resultForDng).use { creator ->
                    creator.setDescription("Camera sensor RAW")
                    creator.setOrientation(exifOrientationForCurrentDisplay(chars))
                    creator.writeImage(destination, image)
                    destination.flush()
                }
            }

            RawDngCaptureInfo(
                logicalCameraId = selected.logicalCameraId,
                physicalCameraId = selected.physicalCameraId,
                width = rawSize.width,
                height = rawSize.height,
                sensorTimestampNs = sensorTimestamp,
            )
        } finally {
            reader.setOnImageAvailableListener(null, null)
            imageRef.getAndSet(null)?.close()
            runCatching { rawSession?.abortCaptures() }
            runCatching { rawSession?.close() }
            if (session === rawSession) session = null
            runCatching { reader.close() }

            handler.post {
                if (generation == myGeneration && camera === device && target == selected && textureView?.isAvailable == true) {
                    createSession(device, selected, myGeneration)
                }
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
                        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            applySafeAutoControls(this, requestChars)
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
            },
        )
        try {
            device.createCaptureSession(config)
        } catch (_: CameraAccessException) {
            surface.release()
            _state.value = CameraSessionState.ERROR
        }
    }

    private suspend fun createRawCaptureSession(
        device: CameraDevice,
        reader: ImageReader,
        selected: LensTarget,
        myGeneration: Long,
    ): CameraCaptureSession {
        val deferred = CompletableDeferred<CameraCaptureSession>()
        handler.post {
            if (generation != myGeneration || camera !== device || target != selected) {
                deferred.completeExceptionally(IllegalStateException("Camera changed before RAW session setup"))
                return@post
            }
            val output = OutputConfiguration(reader.surface).apply {
                selected.physicalCameraId?.let { setPhysicalCameraId(it) }
            }
            val config = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(output),
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(captureSession: CameraCaptureSession) {
                        if (generation == myGeneration && camera === device && target == selected) {
                            deferred.complete(captureSession)
                        } else {
                            captureSession.close()
                            deferred.completeExceptionally(IllegalStateException("Camera changed during RAW session setup"))
                        }
                    }

                    override fun onConfigureFailed(captureSession: CameraCaptureSession) {
                        captureSession.close()
                        deferred.completeExceptionally(IllegalStateException("RAW capture-session configuration failed"))
                    }
                },
            )
            runCatching { device.createCaptureSession(config) }
                .onFailure { deferred.completeExceptionally(it) }
        }
        return deferred.await()
    }

    private fun closeCurrentSessionForReconfiguration() {
        runCatching { session?.stopRepeating() }
        runCatching { session?.abortCaptures() }
        runCatching { session?.close() }
        session = null
        previewRequestBuilder = null
        runCatching { previewSurface?.release() }
        previewSurface = null
    }

    private fun applySafeAutoControls(builder: CaptureRequest.Builder, chars: CameraCharacteristics) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        val afMode = when {
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) -> CaptureRequest.CONTROL_AF_MODE_AUTO
            else -> CaptureRequest.CONTROL_AF_MODE_OFF
        }
        builder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
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

    /**
     * TextureView already compensates the camera sensor's mounting orientation. The remaining app-side
     * work is to compensate the current display rotation and undo TextureView's implicit non-uniform
     * scaling before applying a uniform center-crop scale. This follows Android's Camera2 preview
     * guidance and works for portrait, landscape, reverse portrait, reverse landscape and front cameras.
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val view = textureView ?: return
        val size = previewSize ?: return
        val chars = requestCharacteristics ?: return
        if (viewWidth <= 0 || viewHeight <= 0 || size.width <= 0 || size.height <= 0) return

        val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
        lastDisplayRotation = displayRotation
        val displayDegrees = displayRotationDegrees(displayRotation)
        val sensorOrientation = ((chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0) % 360 + 360) % 360
        val rotationRequired = relativeRotationDegrees(chars, displayRotation) % 180 != 0

        val windowWidth = viewWidth.toFloat()
        val windowHeight = viewHeight.toFloat()
        val previewWidth = size.width.toFloat()
        val previewHeight = size.height.toFloat()

        val scaleX: Float
        val scaleY: Float
        if (sensorOrientation % 180 == 0) {
            scaleX = if (!rotationRequired) windowWidth / previewHeight else windowWidth / previewWidth
            scaleY = if (!rotationRequired) windowHeight / previewWidth else windowHeight / previewHeight
        } else {
            scaleX = if (rotationRequired) windowWidth / previewHeight else windowWidth / previewWidth
            scaleY = if (rotationRequired) windowHeight / previewWidth else windowHeight / previewHeight
        }

        if (scaleX <= 0f || scaleY <= 0f || !scaleX.isFinite() || !scaleY.isFinite()) return
        val finalScale = max(scaleX, scaleY)
        val centerX = windowWidth / 2f
        val centerY = windowHeight / 2f

        val matrix = Matrix()
        if (rotationRequired) {
            matrix.setScale(
                finalScale / scaleX,
                finalScale / scaleY,
                centerX,
                centerY,
            )
        } else {
            matrix.setScale(
                (windowHeight / windowWidth) / scaleY * finalScale,
                (windowWidth / windowHeight) / scaleX * finalScale,
                centerX,
                centerY,
            )
        }

        matrix.postRotate(-displayDegrees.toFloat(), centerX, centerY)

        if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            matrix.postScale(-1f, 1f, centerX, centerY)
        }

        view.setTransform(matrix)
    }

    private fun mapPreviewPointToSensor(
        normalizedX: Float,
        normalizedY: Float,
        chars: CameraCharacteristics,
    ): Pair<Float, Float> {
        var x = normalizedX.coerceIn(0f, 1f)
        var y = normalizedY.coerceIn(0f, 1f)
        val front = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT

        // The front preview is mirrored in display space; undo that before converting to sensor space.
        if (front) x = 1f - x

        // Account for the uniform center crop used by configureTransform so taps near an edge still map
        // to the sensor region that is actually visible rather than the hidden part of the stream.
        val view = textureView
        val size = previewSize
        val rotation = relativeRotationDegrees(chars, view?.display?.rotation ?: Surface.ROTATION_0)
        if (view != null && size != null && view.width > 0 && view.height > 0) {
            val orientedWidth = if (rotation % 180 != 0) size.height.toFloat() else size.width.toFloat()
            val orientedHeight = if (rotation % 180 != 0) size.width.toFloat() else size.height.toFloat()
            val scale = max(view.width / orientedWidth, view.height / orientedHeight)
            if (scale > 0f && scale.isFinite()) {
                val visibleWidth = view.width / scale
                val visibleHeight = view.height / scale
                val left = (orientedWidth - visibleWidth) / 2f
                val top = (orientedHeight - visibleHeight) / 2f
                x = ((left + x * visibleWidth) / orientedWidth).coerceIn(0f, 1f)
                y = ((top + y * visibleHeight) / orientedHeight).coerceIn(0f, 1f)
            }
        }

        return when (rotation) {
            90 -> y to (1f - x)
            180 -> (1f - x) to (1f - y)
            270 -> (1f - y) to x
            else -> x to y
        }
    }

    private fun displayRotationDegrees(displayRotation: Int): Int = when (displayRotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    private fun relativeRotationDegrees(chars: CameraCharacteristics, displayRotation: Int): Int {
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val displayDegrees = displayRotationDegrees(displayRotation)
        val sign = if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) 1 else -1
        return (sensorOrientation - displayDegrees * sign + 360) % 360
    }

    private fun exifOrientationForCurrentDisplay(chars: CameraCharacteristics): Int =
        when (relativeRotationDegrees(chars, textureView?.display?.rotation ?: Surface.ROTATION_0)) {
            90 -> 6 // ExifInterface.ORIENTATION_ROTATE_90
            180 -> 3 // ExifInterface.ORIENTATION_ROTATE_180
            270 -> 8 // ExifInterface.ORIENTATION_ROTATE_270
            else -> 1 // ExifInterface.ORIENTATION_NORMAL
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
        closeCurrentSessionForReconfiguration()
        runCatching { camera?.close() }
        camera = null
        requestCharacteristics = null
        activeArray = null
        currentCropRegion = null
        _state.value = CameraSessionState.CLOSED
    }

    override fun close() {
        if (displayListenerRegistered) {
            runCatching { displayManager.unregisterDisplayListener(displayListener) }
            displayListenerRegistered = false
        }
        handler.post {
            generation++
            closeCameraInternal()
            textureView = null
            cameraThread.quitSafely()
        }
    }

    companion object {
        private const val RAW_MAX_IMAGES = 2
        private const val RAW_SESSION_TIMEOUT_MS = 5_000L
        private const val RAW_CAPTURE_TIMEOUT_MS = 8_000L
    }
}
