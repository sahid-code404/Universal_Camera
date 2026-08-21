package com.sahidcode404.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.sahidcode404.camera.ui.CameraScreen
import com.sahidcode404.camera.ui.CameraTheme
import com.sahidcode404.camera.updater.ApkInstaller
import com.sahidcode404.camera.updater.AvailableUpdate
import com.sahidcode404.camera.updater.GitHubUpdateClient
import com.sahidcode404.camera.updater.UpdateCheckResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraTheme {
                CameraPermissionGate()
            }
        }
    }

    @Composable
    private fun CameraPermissionGate() {
        val requiredPermissions = remember {
            buildList {
                add(Manifest.permission.CAMERA)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
        fun allGranted(): Boolean = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        var granted by remember { mutableStateOf(allGranted()) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            granted = allGranted()
        }
        LaunchedEffect(Unit) {
            if (!granted) launcher.launch(requiredPermissions.toTypedArray())
        }

        if (granted) {
            CameraWithOtaPopup()
        } else {
            Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { launcher.launch(requiredPermissions.toTypedArray()) }) {
                        Text("Allow camera")
                    }
                }
            }
        }
    }

    @Composable
    private fun CameraWithOtaPopup() {
        val scope = rememberCoroutineScope()
        val updater = remember {
            GitHubUpdateClient(applicationContext, BuildConfig.UPDATE_OWNER, BuildConfig.UPDATE_REPO)
        }
        var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
        var updateBusy by remember { mutableStateOf(false) }
        var updateStatus by remember { mutableStateOf<String?>(null) }

        // Check every foreground launch. The update manifest already compares versionCode, so this is
        // cheap and makes OTA visible as a real popup instead of hiding it behind the settings sheet.
        LaunchedEffect(Unit) {
            when (val result = updater.check()) {
                is UpdateCheckResult.Available -> availableUpdate = result.update
                else -> Unit
            }
        }

        CameraScreen()

        availableUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = {
                    if (!updateBusy) availableUpdate = null
                },
                title = { Text("Camera ${update.manifest.versionName} available") },
                text = {
                    Text(
                        updateStatus
                            ?: update.manifest.changelog.takeIf { it.isNotBlank() }
                            ?: "A verified Camera update is ready to install.",
                    )
                },
                dismissButton = {
                    TextButton(
                        enabled = !updateBusy,
                        onClick = { availableUpdate = null },
                    ) { Text("Later") }
                },
                confirmButton = {
                    Button(
                        enabled = !updateBusy,
                        onClick = {
                            scope.launch {
                                updateBusy = true
                                updateStatus = "Downloading and verifying update…"
                                updater.downloadAndVerify(update)
                                    .onSuccess { apk ->
                                        if (!ApkInstaller.canRequestInstalls(this@MainActivity)) {
                                            updateStatus = "Allow Camera to install updates, then return here."
                                            ApkInstaller.openUnknownSourcesSettings(this@MainActivity)
                                        } else {
                                            updateStatus = "Verified. Opening Android installer…"
                                            ApkInstaller.install(this@MainActivity, apk)
                                        }
                                    }
                                    .onFailure { failure ->
                                        updateStatus = failure.message ?: "Update failed"
                                    }
                                updateBusy = false
                            }
                        },
                    ) { Text(if (updateBusy) "Updating…" else "Update") }
                },
            )
        }
    }
}
