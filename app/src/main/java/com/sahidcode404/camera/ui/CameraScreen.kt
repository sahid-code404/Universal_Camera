package com.sahidcode404.camera.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sahidcode404.camera.BuildConfig
import com.sahidcode404.camera.CameraViewModel
import com.sahidcode404.camera.camera.camera2.Camera2PreviewController
import com.sahidcode404.camera.camera.camera2.RawDngCaptureInfo
import com.sahidcode404.camera.core.model.CaptureMode
import com.sahidcode404.camera.core.model.LensDescriptor
import com.sahidcode404.camera.core.model.HdrMode
import com.sahidcode404.camera.core.model.PreviewPipeline
import com.sahidcode404.camera.core.model.UpscaleMode
import com.sahidcode404.camera.core.model.ModeFamily
import com.sahidcode404.camera.core.settings.CameraPreferences
import com.sahidcode404.camera.core.settings.CameraSettingsRepository
import com.sahidcode404.camera.storage.DngMediaStore
import com.sahidcode404.camera.updater.ApkInstaller
import com.sahidcode404.camera.updater.AvailableUpdate
import com.sahidcode404.camera.updater.GitHubUpdateClient
import com.sahidcode404.camera.updater.UpdateCheckResult
import com.sahidcode404.camera.updater.UpdateAutoChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Deck = Color.Black
private val SurfaceLow = Color(0xFF242529)
private val SurfaceHi = Color(0xFF303136)
private val Selected = Color(0xFFD3DDFF)
private val SelectedText = Color(0xFF17345E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(vm: CameraViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val captureScope = rememberCoroutineScope()
    val previewController = remember { Camera2PreviewController(context.applicationContext) }
    val dngStore = remember { DngMediaStore(context.applicationContext) }
    val captureSettings = remember { CameraSettingsRepository(context.applicationContext) }
    val capturePrefs by captureSettings.preferences.collectAsStateWithLifecycle(initialValue = CameraPreferences())
    var showSettings by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var captureBusy by remember { mutableStateOf(false) }
    var captureStatus by remember { mutableStateOf<String?>(null) }
    val selected = vm.selectedLens()
    val updaterClient = remember { GitHubUpdateClient(context.applicationContext, BuildConfig.UPDATE_OWNER, BuildConfig.UPDATE_REPO) }
    var backgroundUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }

    LaunchedEffect(Unit) {
        when (val result = UpdateAutoChecker(context.applicationContext, updaterClient).checkIfDue()) {
            is UpdateCheckResult.Available -> backgroundUpdate = result.update
            else -> Unit
        }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(850)
            focusPoint = null
        }
    }

    LaunchedEffect(captureStatus, captureBusy) {
        val text = captureStatus ?: return@LaunchedEffect
        if (!captureBusy && (text.startsWith("Saved") || text.startsWith("RAW capture") || text.startsWith("Video"))) {
            delay(2_200)
            captureStatus = null
        }
    }

    val onShutter: () -> Unit = {
        if (!captureBusy) {
            val lens = selected
            when {
                state.mode.family == ModeFamily.VIDEO -> captureStatus = "Video capture is phase-gated"
                lens == null -> captureStatus = "RAW capture unavailable: no usable lens"
                !lens.supportsRaw || lens.validation.rawStillUsable != true ->
                    captureStatus = "RAW capture unavailable on this lens"
                else -> captureScope.launch {
                    captureBusy = true
                    try {
                        val timerSeconds = capturePrefs.timerSeconds.coerceAtLeast(0)
                        if (timerSeconds > 0) {
                            captureStatus = "Capturing in ${timerSeconds}s"
                            delay(timerSeconds * 1_000L)
                        }
                        captureStatus = "Capturing sensor RAW…"
                        var info: RawDngCaptureInfo? = null
                        dngStore.writeDng("Camera_${System.currentTimeMillis()}.dng") { output ->
                            info = previewController.captureSingleRawDng(output)
                        }
                        val captured = info
                        captureStatus = if (captured != null) {
                            "Saved ${captured.width}×${captured.height} DNG"
                        } else {
                            "Saved DNG"
                        }
                    } catch (t: Throwable) {
                        captureStatus = "RAW capture failed: ${t.message ?: t.javaClass.simpleName}"
                    } finally {
                        captureBusy = false
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
        ) {
            Box(
                Modifier
                    .weight(0.65f)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF151619))
                    .pointerInput(Unit) {
                        detectTapGestures { p ->
                            focusPoint = p.x / size.width to p.y / size.height
                        }
                    }
            ) {
                CameraPreview(
                    target = selected?.target,
                    controller = previewController,
                    modifier = Modifier.fillMaxSize(),
                )
                TopControls(
                    hdr = state.hdrMode.label,
                    updateAvailable = backgroundUpdate != null,
                    onHdr = vm::cycleHdr,
                    onSettings = { showSettings = true },
                )
                LensRail(
                    lenses = visibleLensesForSelectedFacing(state.inventory.rear, state.inventory.front, selected),
                    selected = selected,
                    onLens = vm::selectLens,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
                focusPoint?.let { (x, y) -> FocusRing(x, y, Modifier.fillMaxSize()) }
            }

            BottomDeck(
                mode = state.mode,
                captureBusy = captureBusy,
                onShutter = onShutter,
                onMode = vm::setMode,
                onSwitch = vm::switchFacing,
                onSettings = { showSettings = true },
                onControls = { showControls = true },
                modifier = Modifier.weight(0.35f),
            )
        }

        if (state.discoveryRunning) {
            Text("Discovering cameras…", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
        }
        captureStatus?.let { status ->
            Surface(
                color = Color(0xDD18191C),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 74.dp),
            ) {
                Text(status, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            context = context,
            selected = selected,
            hdrMode = state.hdrMode,
            previewPipeline = state.previewPipeline,
            upscaleMode = state.upscaleMode,
            initialUpdate = backgroundUpdate,
            onHdrMode = vm::setHdrMode,
            onPreviewPipeline = vm::setPreviewPipeline,
            onUpscaleMode = vm::setUpscaleMode,
            onDismiss = { showSettings = false },
        )
    }
    if (showControls) {
        ControlsSheet(onDismiss = { showControls = false })
    }
}

@Composable
private fun TopControls(hdr: String, updateAvailable: Boolean, onHdr: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Pill("DNG", selected = true, onClick = {})
            Pill(hdr, selected = false, onClick = onHdr)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RoundTop { Icon(Icons.Rounded.FlashAuto, null, tint = Color.White) }
            Box {
                RoundTop(onSettings) { Icon(Icons.Rounded.Settings, null, tint = Color.White) }
                if (updateAvailable) {
                    Box(Modifier.align(Alignment.TopEnd).size(9.dp).background(Color(0xFFA8C7FA), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Selected else Color(0x990D0E10),
        contentColor = if (selected) SelectedText else Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.height(36.dp).clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoundTop(onClick: () -> Unit = {}, content: @Composable () -> Unit) {
    Surface(
        color = Color(0x990D0E10), shape = CircleShape,
        modifier = Modifier.size(40.dp).clickable(onClick = onClick),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

@Composable
private fun LensRail(
    lenses: List<LensDescriptor>,
    selected: LensDescriptor?,
    onLens: (LensDescriptor) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lenses.isEmpty()) return
    Row(
        modifier.clip(RoundedCornerShape(25.dp)).background(Color(0xCC08090B)).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        lenses.take(6).forEach { lens ->
            val active = lens.stableKey == selected?.stableKey
            Surface(
                color = if (active) Selected else Color.Transparent,
                contentColor = if (active) SelectedText else Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(38.dp).clickable { onLens(lens) },
            ) {
                Box(Modifier.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
                    Text(lens.userLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BottomDeck(
    mode: CaptureMode,
    captureBusy: Boolean,
    onShutter: () -> Unit,
    onMode: (CaptureMode) -> Unit,
    onSwitch: () -> Unit,
    onSettings: () -> Unit,
    onControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Deck)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CaptureMode.entries.filter { it.family == mode.family }.forEach { item ->
                ModePill(item, item == mode) { onMode(item) }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(94.dp).padding(horizontal = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(color = SurfaceHi, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Image, null, tint = Color.White) }
            }
            Shutter(video = mode.family == ModeFamily.VIDEO, busy = captureBusy, onClick = onShutter)
            Surface(color = SurfaceHi, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(52.dp).clickable(onClick = onSwitch)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Cameraswitch, null, tint = Color.White) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = SurfaceHi, shape = CircleShape, modifier = Modifier.size(50.dp).clickable(onClick = onSettings)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Settings, null, tint = Color.White) }
            }
            Surface(color = SurfaceHi, shape = RoundedCornerShape(25.dp), modifier = Modifier.height(50.dp)) {
                Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PHOTO",
                        color = if (mode.family == ModeFamily.PHOTO) SelectedText else Color.White,
                        modifier = Modifier
                            .background(if (mode.family == ModeFamily.PHOTO) Color(0xFF93B8FF) else Color.Transparent, RoundedCornerShape(21.dp))
                            .clickable { onMode(CaptureMode.PHOTO) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "VIDEO",
                        color = if (mode.family == ModeFamily.VIDEO) SelectedText else Color.White,
                        modifier = Modifier
                            .background(if (mode.family == ModeFamily.VIDEO) Color(0xFF93B8FF) else Color.Transparent, RoundedCornerShape(21.dp))
                            .clickable { onMode(CaptureMode.VIDEO) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Surface(color = SurfaceHi, shape = CircleShape, modifier = Modifier.size(50.dp).clickable(onClick = onControls)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Tune, null, tint = Color.White) }
            }
        }
    }
}

@Composable
private fun ModePill(mode: CaptureMode, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) Color(0xFFC9D7FF) else Color.Transparent,
        contentColor = if (active) SelectedText else Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.height(36.dp).clickable(onClick = onClick),
    ) { Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(mode.label, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium) } }
}

@Composable
private fun Shutter(video: Boolean, busy: Boolean, onClick: () -> Unit) {
    val outer = if (video) Color(0xFF3C4043) else Color(0xFF55585E)
    val inner = if (video) Color(0xFFEF5361) else Color.White
    Box(
        Modifier
            .size(80.dp)
            .background(Color.Black, CircleShape)
            .border(5.dp, outer, CircleShape)
            .padding(7.dp)
            .clip(if (video) RoundedCornerShape(18.dp) else CircleShape)
            .background(inner)
            .clickable(enabled = !busy, onClick = onClick)
    )
}

@Composable
private fun FocusRing(x: Float, y: Float, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        Box(
            Modifier
                .offset(x = maxWidth * x - 33.dp, y = maxHeight * y - 33.dp)
                .size(66.dp)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

private fun visibleLensesForSelectedFacing(rear: List<LensDescriptor>, front: List<LensDescriptor>, selected: LensDescriptor?): List<LensDescriptor> =
    if (selected?.facing == com.sahidcode404.camera.core.model.LensFacing.FRONT) front else rear

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    context: Context,
    selected: LensDescriptor?,
    hdrMode: HdrMode,
    previewPipeline: PreviewPipeline,
    upscaleMode: UpscaleMode,
    initialUpdate: AvailableUpdate?,
    onHdrMode: (HdrMode) -> Unit,
    onPreviewPipeline: (PreviewPipeline) -> Unit,
    onUpscaleMode: (UpscaleMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings = remember { CameraSettingsRepository(context.applicationContext) }
    val prefs by settings.preferences.collectAsStateWithLifecycle(initialValue = CameraPreferences())
    val updater = remember { GitHubUpdateClient(context.applicationContext, BuildConfig.UPDATE_OWNER, BuildConfig.UPDATE_REPO) }
    var status by remember { mutableStateOf(if (initialUpdate != null) "Version ${initialUpdate.manifest.versionName} available" else "Check for updates") }
    var available by remember { mutableStateOf<AvailableUpdate?>(initialUpdate) }
    var tab by remember { mutableStateOf("General") }
    var advanced by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf(0f) }
    var shutter by remember { mutableStateOf(0f) }
    var iso by remember { mutableStateOf(0f) }
    var ev by remember { mutableStateOf(0.5f) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF16171A), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 32.dp)) {
            if (advanced) {
                Text("‹ Camera settings", color = Color(0xFFA8C7FA), modifier = Modifier.clickable { advanced = false }.padding(vertical = 8.dp))
                Text("More settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))

                SectionTitle("General")
                ToggleSetting("Save location", "Add GPS only when permission is granted", prefs.saveLocation) {
                    scope.launch { settings.setSaveLocation(it) }
                }
                ToggleSetting("Camera sounds", "Shutter and recording sounds", prefs.cameraSounds) {
                    scope.launch { settings.setCameraSounds(it) }
                }
                ToggleSetting("Save selfie as previewed", "Mirror front-camera output when enabled", prefs.mirrorSelfie) {
                    scope.launch { settings.setMirrorSelfie(it) }
                }

                SectionTitle("Camera behavior")
                ChoiceSetting("Launch mode", "Mode used when Camera opens", listOf("Photo", "Video"), prefs.launchMode) {
                    scope.launch { settings.setLaunchMode(it) }
                }
                ChoiceSetting("Volume key action", "Action while Camera is open", listOf("Shutter", "Zoom", "Volume"), prefs.volumeKeyAction) {
                    scope.launch { settings.setVolumeKeyAction(it) }
                }
                ToggleSetting("Framing hints", "Horizon and composition helpers", prefs.framingHints) {
                    scope.launch { settings.setFramingHints(it) }
                }
                ChoiceSetting("Grid type", "Composition overlay", listOf("Off", "3x3", "4x4", "Golden"), prefs.grid) {
                    scope.launch { settings.setGrid(it) }
                }
                ToggleSetting("Dirty lens warning", "Show a reminder when image analysis suggests haze", prefs.dirtyLensWarning) {
                    scope.launch { settings.setDirtyLensWarning(it) }
                }
                ToggleSetting("Manual lens selection", "Keep the chosen physical lens where the HAL allows it", prefs.manualLensSelection) {
                    scope.launch { settings.setManualLensSelection(it) }
                }
                ToggleSetting("Remember camera settings", "Persist capture preferences between launches", prefs.rememberSettings) {
                    scope.launch { settings.setRememberSettings(it) }
                }

                SectionTitle("Device")
                SettingLine(
                    "Selected lens capabilities",
                    selected?.let(::capabilitySummary) ?: "No usable lens selected",
                    selected?.userLabel ?: "—",
                )

                SectionTitle("Updates")
                Button(
                    onClick = {
                        scope.launch {
                            status = "Checking…"
                            when (val result = updater.check()) {
                                UpdateCheckResult.UpToDate -> status = "You're up to date"
                                is UpdateCheckResult.Failed -> status = result.message
                                is UpdateCheckResult.Available -> {
                                    available = result.update
                                    status = "Version ${result.update.manifest.versionName} available"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHi),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(status) }

                AnimatedVisibility(available != null) {
                    Column {
                        Text(
                            available?.manifest?.changelog.orEmpty(),
                            color = Color(0xFFB8BBC1),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        Button(
                            onClick = {
                                val update = available ?: return@Button
                                scope.launch {
                                    status = "Downloading and verifying…"
                                    updater.downloadAndVerify(update).onSuccess { apk ->
                                        if (!ApkInstaller.canRequestInstalls(context)) {
                                            status = "Allow Camera to install updates, then try again"
                                            ApkInstaller.openUnknownSourcesSettings(context)
                                        } else {
                                            status = "Verified — opening Android installer"
                                            ApkInstaller.install(context, apk)
                                        }
                                    }.onFailure { status = it.message ?: "Update failed" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Download verified update") }
                    }
                }
            } else {
                Text("Camera settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabPill("General", tab == "General") { tab = "General" }
                    TabPill("Pro", tab == "Pro") { tab = "Pro" }
                }
                Spacer(Modifier.height(10.dp))

                if (tab == "General") {
                    SectionTitle("Photo")
                    SettingLine("Output", "Sensor/computational RAW path only; no JPEG/HEIF intermediate", "DNG")
                    EnumChoiceSetting("HDR", "Per-lens computational policy", HdrMode.entries.toList(), hdrMode, { it.label }, onHdrMode)
                    ChoiceSetting(
                        "Timer",
                        "Delay before shutter",
                        listOf("Off", "3s", "10s"),
                        when (prefs.timerSeconds) { 3 -> "3s"; 10 -> "10s"; else -> "Off" },
                    ) { value -> scope.launch { settings.setTimerSeconds(when (value) { "3s" -> 3; "10s" -> 10; else -> 0 }) } }
                    ChoiceSetting("Aspect ratio", "Preview/capture crop; never stretch", listOf("4:3", "3:2", "16:9", "1:1", "Full"), prefs.aspectRatio) {
                        scope.launch { settings.setAspectRatio(it) }
                    }
                    SettingLine("RAW resolution", "Highest mode reported for this lens; active probe must validate it", maxRawLabel(selected), if (selected?.supportsRaw == true) "RAW" else "—")
                    ToggleSetting("Manual lens selection", "Use explicit physical lens buttons instead of auto switching", prefs.manualLensSelection) {
                        scope.launch { settings.setManualLensSelection(it) }
                    }

                    SectionTitle("Processing")
                    SettingLine(
                        "Preview pipeline",
                        if (selected?.supportsRaw == true) "Processed preview is active. RAW preview remains phase-gated until sustained throughput is validated." else "This lens does not report RAW preview capability.",
                        previewPipeline.label,
                    )
                    EnumChoiceSetting(
                        "Upscaling policy",
                        "Saved per app now; production pipeline applies only after the reconstruction confidence gate exists",
                        UpscaleMode.entries.toList(),
                        upscaleMode,
                        { it.label },
                        onUpscaleMode,
                    )
                    SettingLine("Computational provenance", "Final DNG will carry frame-count/HDR/SR pipeline metadata", "On")

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { advanced = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceLow),
                    ) { Text("More settings") }
                } else {
                    SectionTitle("Pro controls")
                    SettingLine("Lens", selected?.let(::lensSummary) ?: "No usable camera", selected?.userLabel ?: "—")
                    ProSlider("Focus", focus, { focus = it }, if (focus == 0f) "Auto" else "${(focus * 100).toInt()}%")
                    ProSlider("Shutter speed", shutter, { shutter = it }, if (shutter == 0f) "Auto" else "Manual")
                    ProSlider("ISO", iso, { iso = it }, if (iso == 0f) "Auto" else "Manual")
                    ProSlider("Exposure", ev, { ev = it }, String.format("%+.1f EV", (ev - 0.5f) * 6f))
                    SettingLine("Manual sensor", "Controls are applied only when Camera2 MANUAL_SENSOR is exposed", if (selected?.supportsManualSensor == true) "Available" else "Unavailable")
                    SettingLine("Output contract", "Computational still output uses the project DNG pipeline", "DNG")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF16171A), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 32.dp)) {
            Text("Quick controls", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            SettingLine("Exposure", "EV compensation", "0.0")
            SettingLine("White balance", "Sensor/manual presets", "Auto")
            SettingLine("Focus", "Manual control where exposed", "Auto")
            SettingLine("Zoom", "Smooth ratio control; physical switching is capability-driven", "1x")
            SettingLine("Histogram", "Preview analysis overlay", "Phase 2")
            SettingLine("Focus peaking", "Processed preview overlay", "Phase 2")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Color(0xFFAEB4BC),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun TabPill(text: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) Selected else SurfaceLow,
        contentColor = if (active) SelectedText else Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.height(36.dp).clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingLine(title: String, subtitle: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Color(0xFFAEB3BA), fontSize = 11.sp)
        }
        Text(value, color = Color(0xFFBDC2C8), fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ToggleSetting(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Color(0xFFAEB3BA), fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceSetting(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(subtitle, color = Color(0xFFAEB3BA), fontSize = 11.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option -> SmallChoice(option, option == selected) { onSelect(option) } }
        }
    }
}

@Composable
private fun <T> EnumChoiceSetting(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(subtitle, color = Color(0xFFAEB3BA), fontSize = 11.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option -> SmallChoice(label(option), option == selected) { onSelect(option) } }
        }
    }
}

@Composable
private fun SmallChoice(text: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) Selected else Color(0xFF2A2C30),
        contentColor = if (active) SelectedText else Color.White,
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.height(34.dp).clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ProSlider(title: String, value: Float, onValueChange: (Float) -> Unit, valueLabel: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 12.sp)
            Text(valueLabel, color = Color(0xFFBDC2C8), fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onValueChange)
    }
}

private fun maxRawLabel(lens: LensDescriptor?): String {
    val size = lens?.maxRawSize ?: return "Not reported"
    val mp = size.area / 1_000_000.0
    return "${"%.1f".format(mp)} MP • ${size.width}x${size.height}"
}

private fun lensSummary(lens: LensDescriptor): String = buildString {
    append(lens.equivalentFocalLengthMm?.let { "~${it.toInt()} mm eq" } ?: lens.focalLengthMm?.let { "${it} mm" } ?: "Lens")
    lens.maxRawSize?.let { append(" • RAW ${it.width}x${it.height}") }
}

private fun capabilitySummary(lens: LensDescriptor): String = buildString {
    append(if (lens.supportsRaw) "RAW" else "No RAW")
    append(if (lens.supportsManualSensor) " • Manual sensor" else " • Auto sensor")
    if (lens.supportsOis) append(" • OIS")
    lens.equivalentFocalLengthMm?.let { append(" • ~${it.toInt()} mm eq") }
}
