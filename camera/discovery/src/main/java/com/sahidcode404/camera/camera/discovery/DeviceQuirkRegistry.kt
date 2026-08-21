package com.sahidcode404.camera.camera.discovery

import android.content.Context
import android.os.Build
import com.sahidcode404.camera.core.model.LensDescriptor

enum class CameraQuirk {
    SKIP_RAW_STILL_PROBE,
    FORCE_SMALL_PREVIEW_PROBE,
}

data class DeviceQuirkRule(
    val manufacturer: String? = null,
    val brand: String? = null,
    val device: String? = null,
    val model: String? = null,
    val logicalCameraId: String? = null,
    val physicalCameraId: String? = null,
    val minSdk: Int? = null,
    val maxSdk: Int? = null,
    val quirks: Set<CameraQuirk>,
) {
    fun matches(lens: LensDescriptor): Boolean {
        fun String?.matches(actual: String): Boolean = this == null || equals(actual, ignoreCase = true)
        return manufacturer.matches(Build.MANUFACTURER) &&
            brand.matches(Build.BRAND) &&
            device.matches(Build.DEVICE) &&
            model.matches(Build.MODEL) &&
            (logicalCameraId == null || logicalCameraId == lens.target.logicalCameraId) &&
            (physicalCameraId == null || physicalCameraId == lens.target.physicalCameraId) &&
            (minSdk == null || Build.VERSION.SDK_INT >= minSdk) &&
            (maxSdk == null || Build.VERSION.SDK_INT <= maxSdk)
    }
}

/**
 * Central device/camera quirk registry.
 *
 * Static rules stay intentionally sparse. Runtime probe failures are recorded by exact Android
 * build fingerprint + lens key so a repeatedly crashing RAW probe can be suppressed without
 * turning the whole codebase into model-name conditionals.
 */
class DeviceQuirkRegistry(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Add a rule only after it is reproduced on hardware and documented in the device matrix.
    private val staticRules: List<DeviceQuirkRule> = emptyList()

    fun quirksFor(lens: LensDescriptor): Set<CameraQuirk> {
        val result = staticRules.asSequence()
            .filter { it.matches(lens) }
            .flatMap { it.quirks.asSequence() }
            .toMutableSet()

        if (failureCount(lens, STAGE_RAW) >= RAW_FAILURE_SUPPRESSION_THRESHOLD) {
            result += CameraQuirk.SKIP_RAW_STILL_PROBE
        }
        return result
    }

    fun recordFailure(lens: LensDescriptor, stage: String) {
        val key = failureKey(lens, stage)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun recordSuccess(lens: LensDescriptor, stage: String) {
        prefs.edit().remove(failureKey(lens, stage)).apply()
    }

    private fun failureCount(lens: LensDescriptor, stage: String): Int =
        prefs.getInt(failureKey(lens, stage), 0)

    private fun failureKey(lens: LensDescriptor, stage: String): String =
        "${Build.FINGERPRINT}|${lens.stableKey}|$stage"

    companion object {
        const val STAGE_PREVIEW = "preview"
        const val STAGE_RAW = "raw"
        private const val RAW_FAILURE_SUPPRESSION_THRESHOLD = 2
        private const val PREFS = "camera_device_quirks_v1"
    }
}
