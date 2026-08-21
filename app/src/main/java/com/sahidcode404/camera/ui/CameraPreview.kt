package com.sahidcode404.camera.ui

import android.view.TextureView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahidcode404.camera.camera.camera2.Camera2PreviewController
import com.sahidcode404.camera.core.model.CameraRuntimeSignal
import com.sahidcode404.camera.core.model.CameraSessionState
import com.sahidcode404.camera.core.model.LensFacing
import com.sahidcode404.camera.core.model.LensTarget

@Composable
fun CameraPreview(
    target: LensTarget?,
    controller: Camera2PreviewController,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val undoFrontMirror = target?.facing == LensFacing.FRONT
    val previewState by controller.state.collectAsStateWithLifecycle()

    LaunchedEffect(previewState) {
        if (previewState == CameraSessionState.PREVIEW) {
            CameraRuntimeSignal.markPreviewStreaming()
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.resume()
                Lifecycle.Event.ON_STOP -> controller.closeCamera()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.close()
        }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                // The controller's Camera2 transform mirrors front display space. Undo only that
                // display mirror so the preview matches the real scene without touching sensor data.
                .graphicsLayer { scaleX = if (undoFrontMirror) -1f else 1f },
            factory = { ctx -> TextureView(ctx).also(controller::attach) },
            update = { controller.setTarget(target) },
        )

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(target) {
                    detectTapGestures { point ->
                        if (size.width > 0 && size.height > 0) {
                            val displayX = point.x / size.width.toFloat()
                            controller.focusAt(
                                normalizedX = if (undoFrontMirror) 1f - displayX else displayX,
                                normalizedY = point.y / size.height.toFloat(),
                            )
                        }
                    }
                }
                .pointerInput(target) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom.isFinite() && zoom > 0f) controller.multiplyZoom(zoom)
                    }
                },
        )
    }
}
