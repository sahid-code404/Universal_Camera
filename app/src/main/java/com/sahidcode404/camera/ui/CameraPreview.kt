package com.sahidcode404.camera.ui

import android.view.TextureView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sahidcode404.camera.camera.camera2.Camera2PreviewController
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
                // Camera2PreviewController mirrors front cameras in display space. A second display-only
                // horizontal flip makes the viewfinder match the real scene while leaving the Camera2
                // stream topology, crop, zoom and orientation math unchanged for every lens.
                .graphicsLayer { scaleX = if (undoFrontMirror) -1f else 1f },
            factory = { ctx -> TextureView(ctx).also(controller::attach) },
            update = { controller.setTarget(target) },
        )

        // Compose owns interaction coordinates while TextureView remains a zero-copy camera surface.
        // Undo the extra front-camera display flip before handing the point to the controller, which
        // already performs its own front-camera sensor-space mapping.
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
