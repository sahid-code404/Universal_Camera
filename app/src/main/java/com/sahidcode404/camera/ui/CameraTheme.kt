package com.sahidcode404.camera.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF17345E),
    surface = Color(0xFF101114),
    onSurface = Color(0xFFF8F9FA),
    surfaceVariant = Color(0xFF242529),
)

@Composable
fun CameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
