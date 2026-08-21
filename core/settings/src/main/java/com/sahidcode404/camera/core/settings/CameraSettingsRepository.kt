package com.sahidcode404.camera.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sahidcode404.camera.core.model.HdrMode
import com.sahidcode404.camera.core.model.PreviewPipeline
import com.sahidcode404.camera.core.model.UpscaleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cameraDataStore by preferencesDataStore(name = "camera_settings")

data class CameraPreferences(
    val hdrMode: HdrMode = HdrMode.HDR_PLUS_AUTO,
    val previewPipeline: PreviewPipeline = PreviewPipeline.PROCESSED,
    val upscaleMode: UpscaleMode = UpscaleMode.NATIVE,
    val selectedLensKey: String? = null,
    val timerSeconds: Int = 0,
    val aspectRatio: String = "4:3",
    val grid: String = "3x3",
    val cameraSounds: Boolean = true,
    val saveLocation: Boolean = false,
    val mirrorSelfie: Boolean = true,
    val framingHints: Boolean = true,
    val dirtyLensWarning: Boolean = true,
    val manualLensSelection: Boolean = false,
    val rememberSettings: Boolean = true,
    val launchMode: String = "Photo",
    val volumeKeyAction: String = "Shutter",
)

class CameraSettingsRepository(private val context: Context) {
    private object Keys {
        val hdr = stringPreferencesKey("hdr_mode")
        val preview = stringPreferencesKey("preview_pipeline")
        val upscale = stringPreferencesKey("upscale_mode")
        val lens = stringPreferencesKey("selected_lens_key")
        val timer = intPreferencesKey("timer_seconds")
        val ratio = stringPreferencesKey("aspect_ratio")
        val grid = stringPreferencesKey("grid")
        val sounds = booleanPreferencesKey("camera_sounds")
        val location = booleanPreferencesKey("save_location")
        val mirror = booleanPreferencesKey("mirror_selfie")
        val framing = booleanPreferencesKey("framing_hints")
        val dirtyLens = booleanPreferencesKey("dirty_lens_warning")
        val manualLens = booleanPreferencesKey("manual_lens_selection")
        val remember = booleanPreferencesKey("remember_settings")
        val launch = stringPreferencesKey("launch_mode")
        val volume = stringPreferencesKey("volume_key_action")
    }

    val preferences: Flow<CameraPreferences> = context.cameraDataStore.data.map { p ->
        CameraPreferences(
            hdrMode = p[Keys.hdr]?.let { runCatching { HdrMode.valueOf(it) }.getOrNull() } ?: HdrMode.HDR_PLUS_AUTO,
            previewPipeline = p[Keys.preview]?.let { runCatching { PreviewPipeline.valueOf(it) }.getOrNull() } ?: PreviewPipeline.PROCESSED,
            upscaleMode = p[Keys.upscale]?.let { runCatching { UpscaleMode.valueOf(it) }.getOrNull() } ?: UpscaleMode.NATIVE,
            selectedLensKey = p[Keys.lens],
            timerSeconds = p[Keys.timer] ?: 0,
            aspectRatio = p[Keys.ratio] ?: "4:3",
            grid = p[Keys.grid] ?: "3x3",
            cameraSounds = p[Keys.sounds] ?: true,
            saveLocation = p[Keys.location] ?: false,
            mirrorSelfie = p[Keys.mirror] ?: true,
            framingHints = p[Keys.framing] ?: true,
            dirtyLensWarning = p[Keys.dirtyLens] ?: true,
            manualLensSelection = p[Keys.manualLens] ?: false,
            rememberSettings = p[Keys.remember] ?: true,
            launchMode = p[Keys.launch] ?: "Photo",
            volumeKeyAction = p[Keys.volume] ?: "Shutter",
        )
    }

    suspend fun setHdrMode(value: HdrMode): Unit {
        context.cameraDataStore.edit { it[Keys.hdr] = value.name }
    }

    suspend fun setPreviewPipeline(value: PreviewPipeline): Unit {
        context.cameraDataStore.edit { it[Keys.preview] = value.name }
    }

    suspend fun setUpscaleMode(value: UpscaleMode): Unit {
        context.cameraDataStore.edit { it[Keys.upscale] = value.name }
    }

    suspend fun setSelectedLens(key: String): Unit {
        context.cameraDataStore.edit { it[Keys.lens] = key }
    }

    suspend fun setTimerSeconds(value: Int): Unit {
        context.cameraDataStore.edit { it[Keys.timer] = value }
    }

    suspend fun setAspectRatio(value: String): Unit {
        context.cameraDataStore.edit { it[Keys.ratio] = value }
    }

    suspend fun setGrid(value: String): Unit {
        context.cameraDataStore.edit { it[Keys.grid] = value }
    }

    suspend fun setCameraSounds(value: Boolean): Unit {
        context.cameraDataStore.edit { it[Keys.sounds] = value }
    }

    suspend fun setSaveLocation(value: Boolean): Unit {
        context.cameraDataStore.edit { it[Keys.location] = value }
    }

    suspend fun setMirrorSelfie(value: Boolean): Unit {
        context.cameraDataStore.edit { it[Keys.mirror] = value }
    }

    suspend fun setFramingHints(value: Boolean): Unit {
        context.cameraDataStore.edit { it[Keys.framing] = value }
    }

    suspend fun setDirtyLensWarning(value: Boolean): Unit {
        context.cameraDataStore.edit { it[Keys.dirtyLens] = value }
    }

    suspend fun setManualLensSelection(value: Boolean): Unit {
        context.cameraDataStore.edit { it[Keys.manualLens] = value }
    }

    suspend fun setRememberSettings(value: Boolean): Unit {
        context.cameraDataStore.edit { it[Keys.remember] = value }
    }

    suspend fun setLaunchMode(value: String): Unit {
        context.cameraDataStore.edit { it[Keys.launch] = value }
    }

    suspend fun setVolumeKeyAction(value: String): Unit {
        context.cameraDataStore.edit { it[Keys.volume] = value }
    }
}
