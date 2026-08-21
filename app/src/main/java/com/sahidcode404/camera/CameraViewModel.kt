package com.sahidcode404.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sahidcode404.camera.camera.discovery.CameraDiscovery
import com.sahidcode404.camera.core.model.AppCameraState
import com.sahidcode404.camera.core.model.CameraInventory
import com.sahidcode404.camera.core.model.CameraRuntimeSignal
import com.sahidcode404.camera.core.model.CaptureMode
import com.sahidcode404.camera.core.model.HdrMode
import com.sahidcode404.camera.core.model.LensDescriptor
import com.sahidcode404.camera.core.model.LensFacing
import com.sahidcode404.camera.core.model.PreviewPipeline
import com.sahidcode404.camera.core.model.UpscaleMode
import com.sahidcode404.camera.core.settings.CameraSettingsRepository
import kotlinx.coroutines.delay
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

    private var persistedLensKey: String? = null
    private var fullDiscoveryStarted = false

    init {
        CameraRuntimeSignal.resetForProcessStartup()
        viewModelScope.launch {
            settings.preferences.collect { p ->
                persistedLensKey = p.selectedLensKey
                _state.update { current ->
                    current.copy(
                        hdrMode = p.hdrMode,
                        previewPipeline = p.previewPipeline,
                        upscaleMode = p.upscaleMode,
                        selectedLensKey = if (current.inventory.isEmpty()) {
                            p.selectedLensKey ?: current.selectedLensKey
                        } else {
                            current.selectedLensKey
                        },
                    )
                }
            }
        }
        startPrimaryDiscovery()
    }

    private fun startPrimaryDiscovery() = viewModelScope.launch {
        _state.update { it.copy(discoveryRunning = true, error = null) }
        runCatching { discovery.discoverPrimaryRear() }
            .onSuccess { seed ->
                if (seed.isEmpty()) {
                    fullDiscoveryStarted = true
                    runFullDiscovery(showFailure = true)
                    return@onSuccess
                }
                applyInventory(seed, keepCurrent = false)
                _state.update { it.copy(discoveryRunning = false) }

                // The reference implementation starts the hidden-AUX pass only after the actual
                // repeating preview request is streaming. Poll a process-local signal set by the
                // TextureView wrapper; use a conservative fallback so an unusual HAL error cannot
                // permanently suppress the complete inventory.
                viewModelScope.launch {
                    repeat(150) {
                        if (CameraRuntimeSignal.firstPreviewStreaming) {
                            onPreviewStreaming()
                            return@launch
                        }
                        delay(20)
                    }
                    onPreviewStreaming()
                }
            }
            .onFailure {
                fullDiscoveryStarted = true
                runFullDiscovery(showFailure = true)
            }
    }

    fun onPreviewStreaming() {
        if (fullDiscoveryStarted) return
        fullDiscoveryStarted = true
        viewModelScope.launch { runFullDiscovery(showFailure = false) }
    }

    fun refreshCameras() {
        if (fullDiscoveryStarted) return
        fullDiscoveryStarted = true
        viewModelScope.launch { runFullDiscovery(showFailure = true) }
    }

    private suspend fun runFullDiscovery(showFailure: Boolean) {
        runCatching { discovery.discoverFull(deepScan = true) }
            .onSuccess { inventory ->
                applyInventory(inventory, keepCurrent = true)
                _state.update { it.copy(discoveryRunning = false, error = null) }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        discoveryRunning = false,
                        error = if (showFailure) error.message else it.error,
                    )
                }
            }
    }

    private fun applyInventory(discovered: CameraInventory, keepCurrent: Boolean) {
        val inventory = discovered.toUserFacingInventory()
        val all = inventory.rear + inventory.front + inventory.external
        val currentKey = if (keepCurrent) _state.value.selectedLensKey else null
        val current = all.firstOrNull { it.stableKey == currentKey }
        val persisted = all.firstOrNull { it.stableKey == persistedLensKey }
        val selected = current
            ?: persisted
            ?: preferredLens(inventory.rear, 24f)
            ?: preferredLens(inventory.front, 26f)
            ?: inventory.external.firstOrNull()
        _state.update {
            it.copy(
                inventory = inventory,
                selectedLensKey = selected?.stableKey,
            )
        }
    }

    fun selectLens(lens: LensDescriptor) {
        _state.update { it.copy(selectedLensKey = lens.stableKey) }
        persistedLensKey = lens.stableKey
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

private fun CameraInventory.isEmpty(): Boolean = rear.isEmpty() && front.isEmpty() && external.isEmpty()

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
        if (lens.isLogicalAuto && facing == LensFacing.BACK) lens.copy(userLabel = "1x") else lens
    }
}

private fun preferredLens(lenses: List<LensDescriptor>, targetEquivalentMm: Float): LensDescriptor? =
    lenses.minWithOrNull(
        compareBy<LensDescriptor> {
            it.equivalentFocalLengthMm?.let { equivalent -> abs(equivalent - targetEquivalentMm) }
                ?: Float.MAX_VALUE
        }.thenByDescending { it.maxRawSize?.area ?: 0L },
    )
