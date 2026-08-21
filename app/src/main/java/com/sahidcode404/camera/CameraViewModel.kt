package com.sahidcode404.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sahidcode404.camera.camera.discovery.CameraDiscovery
import com.sahidcode404.camera.core.model.AppCameraState
import com.sahidcode404.camera.core.model.CameraInventory
import com.sahidcode404.camera.core.model.CaptureMode
import com.sahidcode404.camera.core.model.HdrMode
import com.sahidcode404.camera.core.model.LensDescriptor
import com.sahidcode404.camera.core.model.LensFacing
import com.sahidcode404.camera.core.model.PreviewPipeline
import com.sahidcode404.camera.core.model.UpscaleMode
import com.sahidcode404.camera.core.settings.CameraSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class CameraViewModel(app: Application) : AndroidViewModel(app) {
    private val discovery = CameraDiscovery(app)
    private val settings = CameraSettingsRepository(app)
    private val _state = MutableStateFlow(AppCameraState(discoveryRunning = true))
    val state: StateFlow<AppCameraState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.preferences.collect { p ->
                _state.update {
                    it.copy(
                        hdrMode = p.hdrMode,
                        previewPipeline = p.previewPipeline,
                        upscaleMode = p.upscaleMode,
                        selectedLensKey = p.selectedLensKey ?: it.selectedLensKey,
                    )
                }
            }
        }
        refreshCameras()
    }

    fun refreshCameras() = viewModelScope.launch {
        _state.update { it.copy(discoveryRunning = true, error = null) }
        runCatching { discovery.discover() }
            .onSuccess { discovered ->
                // Logical multi-camera parents are transport/auto-switch routes, not separate pieces
                // of glass. Keep them out of the user lens rail whenever concrete validated lenses
                // exist. If a device exposes only the logical parent, retain it as the sole usable
                // rear route but present it as the normal 1x camera rather than an "Auto" lens.
                val inventory = discovered.toUserFacingInventory()
                val persisted = _state.value.selectedLensKey
                val all = inventory.rear + inventory.front + inventory.external
                val selected = all.firstOrNull { it.stableKey == persisted }
                    ?: preferredLens(inventory.rear, 24f)
                    ?: preferredLens(inventory.front, 26f)
                    ?: inventory.external.firstOrNull()
                _state.update {
                    it.copy(
                        inventory = inventory,
                        selectedLensKey = selected?.stableKey,
                        discoveryRunning = false,
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(discoveryRunning = false, error = e.message) }
            }
    }

    fun selectLens(lens: LensDescriptor) {
        _state.update { it.copy(selectedLensKey = lens.stableKey) }
        viewModelScope.launch { settings.setSelectedLens(lens.stableKey) }
    }

    fun switchFacing() {
        val current = selectedLens()?.facing
        val targetGroup = when (current) {
            LensFacing.FRONT -> _state.value.inventory.rear
            else -> _state.value.inventory.front
        }
        val target = when (current) {
            LensFacing.FRONT -> preferredLens(targetGroup, 24f)
            else -> preferredLens(targetGroup, 26f)
        } ?: targetGroup.firstOrNull() ?: return
        selectLens(target)
    }

    fun setMode(mode: CaptureMode) = _state.update { it.copy(mode = mode) }
    fun cycleHdr() = setHdrMode(_state.value.hdrMode.next())

    fun setHdrMode(value: HdrMode) {
        _state.update { it.copy(hdrMode = value) }
        viewModelScope.launch { settings.setHdrMode(value) }
    }

    fun setPreviewPipeline(value: PreviewPipeline) {
        _state.update { it.copy(previewPipeline = value) }
        viewModelScope.launch { settings.setPreviewPipeline(value) }
    }

    fun setUpscaleMode(value: UpscaleMode) {
        _state.update { it.copy(upscaleMode = value) }
        viewModelScope.launch { settings.setUpscaleMode(value) }
    }

    fun selectedLens(): LensDescriptor? {
        val key = _state.value.selectedLensKey
        return (_state.value.inventory.rear + _state.value.inventory.front + _state.value.inventory.external)
            .firstOrNull { it.stableKey == key }
    }
}

private fun CameraInventory.toUserFacingInventory(): CameraInventory = copy(
    rear = userFacingGroup(rear, LensFacing.BACK),
    front = userFacingGroup(front, LensFacing.FRONT),
    external = userFacingGroup(external, LensFacing.EXTERNAL),
)

private fun userFacingGroup(
    lenses: List<LensDescriptor>,
    facing: LensFacing,
): List<LensDescriptor> {
    val concrete = lenses.filterNot { it.isLogicalAuto }
    if (concrete.isNotEmpty()) return concrete

    return lenses.map { lens ->
        if (lens.isLogicalAuto && facing == LensFacing.BACK) {
            lens.copy(userLabel = "1x")
        } else {
            lens
        }
    }
}

private fun preferredLens(lenses: List<LensDescriptor>, targetEquivalentMm: Float): LensDescriptor? =
    lenses.minWithOrNull(
        compareBy<LensDescriptor> {
            it.equivalentFocalLengthMm?.let { equivalent -> abs(equivalent - targetEquivalentMm) }
                ?: Float.MAX_VALUE
        }.thenByDescending { it.maxRawSize?.area ?: 0L },
    )
