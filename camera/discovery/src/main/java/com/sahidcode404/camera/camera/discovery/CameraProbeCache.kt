package com.sahidcode404.camera.camera.discovery

import android.content.Context
import android.os.Build
import com.sahidcode404.camera.core.model.LensDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class CameraProbeCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(lens: LensDescriptor, nowMs: Long = System.currentTimeMillis()): ProbeResult? {
        val raw = prefs.getString(key(lens), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema") != SCHEMA) return null
            if (json.optString("build") != Build.FINGERPRINT) return null
            if (json.optString("descriptor") != descriptorFingerprint(lens)) return null
            val checkedAt = json.optLong("checkedAtMs", 0L)
            val previewUsable = json.optBoolean("previewUsable")
            val maxAgeMs = if (previewUsable) SUCCESS_MAX_AGE_MS else FAILURE_MAX_AGE_MS
            if (checkedAt <= 0L || nowMs - checkedAt > maxAgeMs) return null

            val notesJson = json.optJSONArray("notes") ?: JSONArray()
            val notes = buildList {
                for (i in 0 until notesJson.length()) add(notesJson.optString(i))
            }
            ProbeResult(
                previewUsable = previewUsable,
                rawStillUsable = json.optBoolean("rawStillUsable"),
                continuousRawUsable = json.optBoolean("continuousRawUsable"),
                videoUsable = json.optBoolean("videoUsable"),
                deliveredPreviewFrames = json.optInt("deliveredPreviewFrames"),
                firstPreviewTimestampNs = json.optLong("firstPreviewTimestampNs", 0L).takeIf { it > 0L },
                lastPreviewTimestampNs = json.optLong("lastPreviewTimestampNs", 0L).takeIf { it > 0L },
                failureStage = json.optString("failureStage").takeIf { it.isNotBlank() },
                notes = notes,
            )
        }.getOrNull()
    }

    fun write(lens: LensDescriptor, result: ProbeResult, nowMs: Long = System.currentTimeMillis()) {
        val json = JSONObject()
            .put("schema", SCHEMA)
            .put("build", Build.FINGERPRINT)
            .put("descriptor", descriptorFingerprint(lens))
            .put("checkedAtMs", nowMs)
            .put("previewUsable", result.previewUsable)
            .put("rawStillUsable", result.rawStillUsable)
            .put("continuousRawUsable", result.continuousRawUsable)
            .put("videoUsable", result.videoUsable)
            .put("deliveredPreviewFrames", result.deliveredPreviewFrames)
            .put("firstPreviewTimestampNs", result.firstPreviewTimestampNs ?: 0L)
            .put("lastPreviewTimestampNs", result.lastPreviewTimestampNs ?: 0L)
            .put("failureStage", result.failureStage ?: "")
            .put("notes", JSONArray(result.notes))
        prefs.edit().putString(key(lens), json.toString()).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private fun key(lens: LensDescriptor): String = "probe:${lens.stableKey}"

    private fun descriptorFingerprint(lens: LensDescriptor): String {
        val payload = buildString {
            append(lens.stableKey).append('|')
            append(lens.facing).append('|')
            append(lens.hardwareLevel).append('|')
            append(lens.focalLengthMm).append('|')
            append(lens.sensorWidthMm).append('x').append(lens.sensorHeightMm).append('|')
            append(lens.maxRawSize).append('|')
            append(lens.previewSizes.joinToString(","))
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS = "camera_probe_cache_v1"
        private const val SCHEMA = 1
        private const val SUCCESS_MAX_AGE_MS = 14L * 24L * 60L * 60L * 1000L
        private const val FAILURE_MAX_AGE_MS = 10L * 60L * 1000L
    }
}
