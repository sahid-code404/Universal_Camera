package com.sahidcode404.camera.processing.api

import com.sahidcode404.camera.core.model.ProcessingStage

data class RawFrameHandle(
    val nativeHandle: Long,
    val sensorTimestampNs: Long,
    val exposureTimeNs: Long,
    val sensitivityIso: Int,
)

data class CapturePlan(
    val requestedFrames: Int,
    val hdrEnabled: Boolean,
    val superResolutionEnabled: Boolean,
)

data class ProcessingProgress(val stage: ProcessingStage, val fraction: Float)

data class ComputationalDngResult(val tempPath: String, val acceptedFrames: Int)

/** Interface only. No fake HDR/fusion implementation is shipped in the foundation. */
interface ComputationalPipeline {
    suspend fun process(
        frames: List<RawFrameHandle>,
        plan: CapturePlan,
        onProgress: (ProcessingProgress) -> Unit,
    ): ComputationalDngResult
}
