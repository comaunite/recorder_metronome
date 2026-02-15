package com.recordermetronome.composable

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordermetronome.RecorderViewModel
import com.recordermetronome.RecordingState

@Composable
fun RecorderScreen(
    modifier: Modifier = Modifier,
    viewModel: RecorderViewModel
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingData = viewModel.pendingAudioData
    val waveformData by viewModel.waveformData.collectAsStateWithLifecycle()
    val timestamp by viewModel.timestamp.collectAsStateWithLifecycle()
    val formattedTimestamp = remember(timestamp) { viewModel.formatMillisToTimestamp(timestamp) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                viewModel.onRecordTapped()
            }
        }

    fun handleRecordAction() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onRecordTapped()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            permissionLauncher.launch(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Timestamp Tracker
        Text(
            text = formattedTimestamp,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Waveform
        WaveformVisualizer(
            waveformData = waveformData,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Status Text
        Text(
            text = when (state) {
                RecordingState.RECORDING -> "🔴 Recording..."
                RecordingState.PAUSED -> "⏸️ Paused"
                RecordingState.PLAYBACK -> "🔊 Playing Back..."
                RecordingState.IDLE -> "Ready to record"
            },
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                RecordingState.IDLE -> {
                    Button(onClick = { handleRecordAction() }) {
                        Text("Start Recording")
                    }
                }

                RecordingState.RECORDING -> {
                    Button(onClick = { viewModel.onPlaybackTapped() }) {
                        Text("Play")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { viewModel.onPauseRecordTapped() }) {
                        Text("Pause Recording")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.onStopTapped() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Stop")
                    }
                }

                RecordingState.PAUSED -> {
                    Button(onClick = { viewModel.onPlaybackTapped() }) {
                        Text("Play")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { viewModel.onRecordTapped() }) {
                        Text("Resume Recording")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.onStopTapped() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Stop")
                    }
                }

                RecordingState.PLAYBACK -> {
                    Button(onClick = { viewModel.onPausePlaybackTapped() }) {
                        Text("Pause Playback")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { viewModel.onRecordTapped() }) {
                        Text("Resume Recording")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.onStopTapped() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Stop")
                    }
                }
            }
        }

        if (pendingData != null) {
            AlertDialog(
                onDismissRequest = { viewModel.onDiscardData() },
                title = { Text("Save Recording?") },
                text = { Text("Do you want to save this recording or discard it?") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.onSaveData(context, "recording_${System.currentTimeMillis()}")
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Button(onClick = { viewModel.onDiscardData() }) {
                        Text("Discard")
                    }
                }
            )
        }
    }
}