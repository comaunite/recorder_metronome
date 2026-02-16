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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val state by viewModel.recordingStateFlow.collectAsStateWithLifecycle()
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

        // Three-button layout with state-based logic
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                RecordingState.IDLE -> {
                    PlayButton(false, {})

                    RecordButton(true, { handleRecordAction() })

                    StopButton(false, {})
                }

                RecordingState.RECORDING -> {
                    PlayButton(false, {})

                    PauseRecordButton({ viewModel.onPauseRecordTapped() })

                    StopButton(true, { viewModel.onStopTapped() })
                }

                RecordingState.PAUSED -> {
                    PlayButton(true, { viewModel.onPlaybackTapped() })

                    RecordButton(true, { handleRecordAction() })

                    StopButton(true, { viewModel.onStopTapped() })
                }

                RecordingState.PLAYBACK -> {
                    PausePlaybackButton({ viewModel.onPausePlaybackTapped() })

                    RecordButton(false, {})

                    StopButton(true, { viewModel.onStopTapped() })
                }
            }
        }

        val showSaveDialog by viewModel.showSaveDialog.collectAsStateWithLifecycle()

        if (showSaveDialog) {
            SaveRecordingDialog(
                onSave = {
                    viewModel.onSaveData(context, "recording_${System.currentTimeMillis()}")
                },
                onDiscard = { viewModel.onDiscardData() }
            )
        }
    }
}