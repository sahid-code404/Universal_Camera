package com.sahidcode404.camera.updater

data class ReleaseManifest(
    val schema: Int,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkAssetName: String,
    val sha256: String,
    val signingCertSha256: String?,
    val changelog: String,
    val mandatory: Boolean,
)

data class AvailableUpdate(
    val manifest: ReleaseManifest,
    val apkUrl: String,
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val update: AvailableUpdate) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}
