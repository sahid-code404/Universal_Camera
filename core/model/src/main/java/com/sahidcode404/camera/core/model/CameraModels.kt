package com.sahidcode404.camera.core.model

enum class LensFacing { BACK, FRONT, EXTERNAL, UNKNOWN }

data class PixelSize(val width: Int, val height: Int) {
    val area: Long get() = width.toLong() * height.toLong()
    override fun toString(): String = "${width}x${height}"
}

data class LensTarget(
    val logicalCameraId: String,
    val physicalCameraId: String? = null,
) {
    val stableKey: String = physicalCameraId?.let { "$logicalCameraId::$it" } ?: logicalCameraId
}

data class LensDescriptor(
    val target: LensTarget,
    val facing: LensFacing,
    val userLabel: String,
    val focalLengthMm: Float?,
    val equivalentFocalLengthMm: Float?,
    val aperture: Float?,
    val sensorWidthMm: Float?,
    val sensorHeightMm: Float?,
    val supportsRaw: Boolean,
    val supportsManualSensor: Boolean,
    val supportsOis: Boolean,
    val isLogicalAuto: Boolean,
    val hardwareLevel: Int,
    val maxRawSize: PixelSize?,
    val previewSizes: List<PixelSize>,
    val fpsRanges: List<IntRange>,
) {
    val stableKey: String get() = target.stableKey
}

data class CameraInventory(
    val rear: List<LensDescriptor> = emptyList(),
    val front: List<LensDescriptor> = emptyList(),
    val external: List<LensDescriptor> = emptyList(),
    val rejectedCameraIds: List<String> = emptyList(),
)

enum class ModeFamily { PHOTO, VIDEO }

enum class CaptureMode(val label: String, val family: ModeFamily) {
    PORTRAIT("Portrait", ModeFamily.PHOTO),
    PHOTO("Photo", ModeFamily.PHOTO),
    NIGHT("Night", ModeFamily.PHOTO),
    PRO("Pro", ModeFamily.PHOTO),
    PANORAMA("Panorama", ModeFamily.PHOTO),
    ACTION_PAN("Action Pan", ModeFamily.PHOTO),
    LONG_EXPOSURE("Long Exposure", ModeFamily.PHOTO),
    VIDEO("Video", ModeFamily.VIDEO),
    SLOW_MOTION("Slow Motion", ModeFamily.VIDEO),
    TIME_LAPSE("Time Lapse", ModeFamily.VIDEO),
    CINEMATIC_PAN("Cinematic Pan", ModeFamily.VIDEO),
}

enum class HdrMode(val label: String) {
    NORMAL("Normal"), HDR("HDR"), HDR_PLUS_AUTO("HDR+ Auto");
    fun next(): HdrMode = entries[(ordinal + 1) % entries.size]
}

enum class PreviewPipeline(val label: String) { PROCESSED("Processed"), RAW("RAW") }

enum class UpscaleMode(val label: String) {
    NATIVE("Native"), COMPUTATIONAL_SR("Computational SR"), X1_5("1.5x"), X2("2x")
}

enum class CameraSessionState { CLOSED, OPENING, CONFIGURING, PREVIEW, CLOSING, ERROR }

enum class ProcessingStage {
    WAITING, INGESTING, SCORING, ALIGNING, FUSING, HDR, SUPER_RES, FINALIZING_RAW,
    WRITING_DNG, VALIDATING, COMPLETE, FAILED
}

data class AppCameraState(
    val inventory: CameraInventory = CameraInventory(),
    val selectedLensKey: String? = null,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val hdrMode: HdrMode = HdrMode.HDR_PLUS_AUTO,
    val previewPipeline: PreviewPipeline = PreviewPipeline.PROCESSED,
    val upscaleMode: UpscaleMode = UpscaleMode.NATIVE,
    val discoveryRunning: Boolean = false,
    val error: String? = null,
)
