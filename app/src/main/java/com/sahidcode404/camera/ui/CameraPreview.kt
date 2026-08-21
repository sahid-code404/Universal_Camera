package com.sahidcode404.camera.ui

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

    AndroidView(
        modifier = modifier,
        factory = { ctx -> TextureView(ctx).also(controller::attach) },
        update = { controller.setTarget(target) },
    )
}
