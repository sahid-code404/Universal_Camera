package com.sahidcode404.camera.camera.camera2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
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

class Camera2PreviewController(private val context: Context) : AutoCloseable {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraThread = HandlerThread("CameraControl").apply { start() }
    private val handler = Handler(cameraThread.looper)
    private val executor = Executor { command -> handler.post(command) }

    private val _state = MutableStateFlow(CameraSessionState.CLOSED)
    val state: StateFlow<CameraSessionState> = _state

    private var target: LensTarget? = null
    private var textureView: TextureView? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var previewSize: android.util.Size? = null
    private var generation = 0L

    fun attach(view: TextureView) {
        textureView = view
        view.surfaceTextureListener = listener
        if (view.isAvailable) reopen()
    }

    fun setTarget(newTarget: LensTarget?) {
        if (target == newTarget) return
        target = newTarget
        reopen()
    }

    fun resume() = reopen()

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
            val charsForStream = runCatching {
                manager.getCameraCharacteristics(selected.physicalCameraId ?: selected.logicalCameraId)
            }.getOrElse { manager.getCameraCharacteristics(selected.logicalCameraId) }
            val map = charsForStream.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
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
                    device.close(); if (myGeneration == generation) _state.value = CameraSessionState.ERROR
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close(); if (myGeneration == generation) _state.value = CameraSessionState.ERROR
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
                        captureSession.close(); surface.release(); return
                    }
                    session = captureSession
                    runCatching {
                        val requestChars = runCatching {
                            manager.getCameraCharacteristics(selected.physicalCameraId ?: selected.logicalCameraId)
                        }.getOrElse { manager.getCameraCharacteristics(selected.logicalCameraId) }
                        val afModes = requestChars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES).orEmpty()
                        val afMode = when {
                            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) -> CaptureRequest.CONTROL_AF_MODE_AUTO
                            else -> CaptureRequest.CONTROL_AF_MODE_OFF
                        }
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AF_MODE, afMode)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        }.build()
                        captureSession.setSingleRepeatingRequest(request, executor, object : CameraCaptureSession.CaptureCallback() {})
                        _state.value = CameraSessionState.PREVIEW
                        view.post { configureTransform(view.width, view.height) }
                    }.onFailure { _state.value = CameraSessionState.ERROR }
                }
                override fun onConfigureFailed(captureSession: CameraCaptureSession) {
                    captureSession.close(); surface.release(); if (myGeneration == generation) _state.value = CameraSessionState.ERROR
                }
            }
        )
        try {
            device.createCaptureSession(config)
        } catch (_: CameraAccessException) {
            surface.release(); _state.value = CameraSessionState.ERROR
        }
    }

    private fun choosePreviewSize(sizes: Array<android.util.Size>, width: Int, height: Int): android.util.Size? {
        if (sizes.isEmpty()) return null
        val targetRatio = if (height == 0) 4f / 3f else width.toFloat() / height.toFloat()
        return sizes
            .filter { it.width <= 2560 && it.height <= 1440 }
            .minWithOrNull(compareBy<android.util.Size>({ abs((it.width.toFloat() / it.height) - targetRatio) }, { -it.width * it.height }))
            ?: sizes.maxByOrNull { it.width.toLong() * it.height.toLong() }
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val view = textureView ?: return
        val size = previewSize ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return
        val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
        val buffer = RectF(0f, 0f, size.height.toFloat(), size.width.toFloat())
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        val matrix = Matrix()
        if (displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270) {
            buffer.offset(centerX - buffer.centerX(), centerY - buffer.centerY())
            matrix.setRectToRect(viewRect, buffer, Matrix.ScaleToFit.FILL)
            val scale = maxOf(viewHeight.toFloat() / size.height, viewWidth.toFloat() / size.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(if (displayRotation == Surface.ROTATION_90) -90f else 90f, centerX, centerY)
        }
        view.setTransform(matrix)
    }

    fun closeCamera() {
        handler.post { generation++; closeCameraInternal() }
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
