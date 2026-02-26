package com.recorder.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat

@Composable
fun ensureRecordingAudioPermissions(
    context: Context,
    onPermissionGranted: () -> Unit
): () -> Unit {
    val currentOnGranted by rememberUpdatedState(onPermissionGranted)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            currentOnGranted()
        }
    }

    return remember(context, permissionLauncher) {
        {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                currentOnGranted()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                permissionLauncher.launch(Manifest.permission.MODIFY_AUDIO_SETTINGS)
            }
        }
    }
}

