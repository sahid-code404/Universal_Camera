package com.sahidcode404.camera.camera.discovery

import com.sahidcode404.camera.core.model.LensDescriptor

/**
 * Phase-1 active probe contract. Static CameraCharacteristics are not enough to ship a lens.
 * Implementations must eventually test open/session/frame delivery and HAL stability.
 */
interface CameraProbeContract {
    suspend fun validate(lens: LensDescriptor): ProbeResult
}

data class ProbeResult(
    val previewUsable: Boolean,
    val rawStillUsable: Boolean,
    val continuousRawUsable: Boolean,
    val videoUsable: Boolean,
    val notes: List<String> = emptyList(),
)
