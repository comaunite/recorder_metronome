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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun PauseIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier) {
        val barWidth = size.width * 0.25f
        val barHeight = size.height
        val spacing = size.width * 0.2f

        // Left bar
        drawRoundRect(
            color = tint,
            topLeft = Offset((size.width - 2 * barWidth - spacing) / 2f, 0f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(4.dp.toPx())
        )

        // Right bar
        drawRoundRect(
            color = tint,
            topLeft = Offset((size.width - 2 * barWidth - spacing) / 2f + barWidth + spacing, 0f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
    }
}

@Composable
fun StopIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = size,
            cornerRadius = CornerRadius(4.dp.toPx())
        )
    }
}

@Composable
fun RecordIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = tint,
            radius = size.minDimension / 2f
        )
    }
}

@Composable
fun RecorderScreen(
    modifier: Modifier = Modifier,
    viewModel: RecorderViewModel
) {
    val context = LocalContext.current
    val state by viewModel.recordingStateFlow.collectAsStateWithLifecycle()
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

        // Three-button layout with state-based logic
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                RecordingState.IDLE -> {
                    // Play button (disabled)
                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(56.dp),
                        enabled = false
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Record button (big, enabled)
                    FilledTonalIconButton(
                        onClick = { handleRecordAction() },
                        modifier = Modifier.size(80.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Red,
                            contentColor = Color.White
                        )
                    ) {
                        RecordIcon(
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }

                    // Stop button (disabled)
                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(56.dp),
                        enabled = false
                    ) {
                        StopIcon(
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                RecordingState.RECORDING -> {
                    // Play button (disabled)
                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(56.dp),
                        enabled = false
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Pause recording button (big, enabled)
                    FilledTonalIconButton(
                        onClick = { viewModel.onPauseRecordTapped() },
                        modifier = Modifier.size(80.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        PauseIcon(
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Stop button (enabled)
                    IconButton(
                        onClick = { viewModel.onStopTapped() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        StopIcon(
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                RecordingState.PAUSED -> {
                    // Play button (enabled)
                    IconButton(
                        onClick = { viewModel.onPlaybackTapped() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Record button (big, enabled)
                    FilledTonalIconButton(
                        onClick = { viewModel.onRecordTapped() },
                        modifier = Modifier.size(80.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Red,
                            contentColor = Color.White
                        )
                    ) {
                        RecordIcon(
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }

                    // Stop button (enabled)
                    IconButton(
                        onClick = { viewModel.onStopTapped() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        StopIcon(
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                RecordingState.PLAYBACK -> {
                    // Pause playback button (enabled)
                    IconButton(
                        onClick = { viewModel.onPausePlaybackTapped() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        PauseIcon(
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Record button (big, disabled)
                    FilledTonalIconButton(
                        onClick = {},
                        modifier = Modifier.size(80.dp),
                        enabled = false,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        RecordIcon(
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Stop button (enabled)
                    IconButton(
                        onClick = { viewModel.onStopTapped() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        StopIcon(
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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