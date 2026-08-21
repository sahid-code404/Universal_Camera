package com.sahidcode404.camera.updater

import android.content.Context

class UpdateAutoChecker(
    context: Context,
    private val client: GitHubUpdateClient,
) {
    private val prefs = context.getSharedPreferences("camera_update_state", Context.MODE_PRIVATE)

    suspend fun checkIfDue(
        nowMs: Long = System.currentTimeMillis(),
        intervalMs: Long = 12L * 60L * 60L * 1000L,
    ): UpdateCheckResult? {
        val last = prefs.getLong("last_check_ms", 0L)
        if (nowMs - last < intervalMs) return null
        val result = client.check()
        prefs.edit().putLong("last_check_ms", nowMs).apply()
        return result
    }
}
