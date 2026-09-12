package com.vernai.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Manages the runtime RECORD_AUDIO permission flow for Jetpack Compose screens.
 * Explains VernAI's offline-first privacy model with bilingual Telugu/English rationale.
 */
class AudioPermissionState(
    val hasPermission: Boolean,
    val requestPermission: () -> Unit
)

@Composable
fun rememberAudioPermissionState(
    onPermissionGranted: () -> Unit = {}
): AudioPermissionState {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            onPermissionGranted()
        } else {
            showSettingsDialog = true
        }
    }

    val requestAction = {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            hasPermission = true
            onPermissionGranted()
        } else {
            showRationaleDialog = true
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("మైక్రోఫోన్ అనుమతి (Microphone Access)")
            },
            text = {
                Text(
                    "వాయిస్ రికార్డింగ్ మరియు అక్షరీకరణ కోసం మైక్రోఫోన్ అనుమతి అవసరం.\n\n" +
                            "🔒 100% గోప్యత: మీ ఆడియో పూర్తిగా మీ ఫోన్‌లోనే ప్రాసెస్ చేయబడుతుంది. ఏ సర్వర్‌కూ పంపబడదు.\n\n" +
                            "(VernAI processes your voice 100% locally on-device with zero internet telemetry.)"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRationaleDialog = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Text("అనుమతించు (Continue)")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRationaleDialog = false }) {
                    Text("రద్దు (Cancel)")
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("అనుమతి నిరాకరించబడింది (Permission Denied)")
            },
            text = {
                Text(
                    "మైక్రోఫోన్ అనుమతి లేకుండా వాయిస్ ఇన్‌పుట్ పనిచేయదు. దయచేసి యాప్ సెట్టింగ్స్‌లో అనుమతించండి.\n\n" +
                            "(Please enable microphone access in Android App Settings to use voice input.)"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("సెట్టింగ్స్ తెరవండి (Open Settings)")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSettingsDialog = false }) {
                    Text("రద్దు (Cancel)")
                }
            }
        )
    }

    return remember(hasPermission) {
        AudioPermissionState(
            hasPermission = hasPermission,
            requestPermission = requestAction
        )
    }
}
