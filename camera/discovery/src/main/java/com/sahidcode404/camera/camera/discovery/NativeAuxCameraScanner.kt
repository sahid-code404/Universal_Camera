package com.sahidcode404.camera.camera.discovery

import org.json.JSONArray

/**
 * Metadata-only auxiliary-camera discovery using Android's public NDK ACameraManager.
 *
 * Some vendor frameworks expose a smaller Java CameraManager ID list than the camera service can
 * describe through the NDK API. This scanner mirrors the discovery-only part of the proven
 * MotionCam-style path from sahid-code404/Camera: it enumerates advertised native IDs and performs
 * a bounded numeric metadata scan. It never opens a camera and never changes preview/capture logic.
 *
 * CameraDiscovery still requires every candidate to be describable/openable by the existing Java
 * Camera2 path before it reaches the UI, so all displayed lenses keep the same preview pipeline.
 */
object NativeAuxCameraScanner {
    private val loaded = runCatching {
        System.loadLibrary("camera_aux_discovery")
        true
    }.getOrDefault(false)

    val available: Boolean get() = loaded

    fun enumerateCandidateIds(deepScan: Boolean): List<String> {
        if (!loaded) return emptyList()
        val json = runCatching { nativeEnumerateIdsJson(deepScan) }.getOrNull().orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index)
                        .takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    private external fun nativeEnumerateIdsJson(deepScan: Boolean): String
}
