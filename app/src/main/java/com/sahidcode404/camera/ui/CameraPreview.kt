package com.sahidcode404.camera.ui

import android.view.TextureView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sahidcode404.camera.camera.camera2.Camera2PreviewController
import com.sahidcode404.camera.core.model.LensTarget

@Composable
fun CameraPreview(target: LensTarget?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { Camera2PreviewController(context.applicationContext) }

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
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> TextureView(ctx).also(controller::attach) },
            update = { controller.setTarget(target) },
        )

        // Compose owns interaction coordinates while TextureView remains a zero-copy camera surface.
        // Tap coordinates are normalized inside the displayed preview and mapped to the active sensor crop.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(target) {
                    detectTapGestures { point ->
                        if (size.width > 0 && size.height > 0) {
                            controller.focusAt(
                                normalizedX = point.x / size.width.toFloat(),
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
