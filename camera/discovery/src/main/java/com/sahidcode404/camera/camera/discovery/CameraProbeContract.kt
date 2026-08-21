package com.sahidcode404.camera.camera.discovery

import com.sahidcode404.camera.core.model.LensDescriptor

/**
 * Phase-1 active probe contract.
 *
 * CameraCharacteristics are descriptive metadata, not proof that a lens can be safely opened
 * and used by this application. A production inventory is allowed to expose a lens only after
 * the probe has demonstrated a useful preview path on the current device build.
 */
interface CameraProbeContract {
    suspend fun validate(lens: LensDescriptor): ProbeResult
}

data class ProbeResult(
    val previewUsable: Boolean,
    val rawStillUsable: Boolean,
    val continuousRawUsable: Boolean,
    val videoUsable: Boolean,
    val deliveredPreviewFrames: Int = 0,
    val firstPreviewTimestampNs: Long? = null,
    val lastPreviewTimestampNs: Long? = null,
    val failureStage: String? = null,
    val notes: List<String> = emptyList(),
)
