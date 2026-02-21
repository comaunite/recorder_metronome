package com.recordermetronome.composable

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.recordermetronome.composable.dialogs.ExitRecordingDialog
import com.recordermetronome.composable.dialogs.StopRecordingDialog

@Composable
fun RecorderScreen(
    modifier: Modifier = Modifier,
    viewModel: RecorderViewModel
) {
    val context = LocalContext.current
    val state by viewModel.recordingStateFlow.collectAsStateWithLifecycle()
    val waveformData by viewModel.accumulatedWaveformData.collectAsStateWithLifecycle()
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

    // Register back button dispatcher event handler
    val activity = LocalContext.current as? ComponentActivity
    if (activity != null) {
        DisposableEffect(Unit) {
            val callback = object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBackPressed()
                    // Event was handled, stop propagation
                    return
                }
            }
            activity.onBackPressedDispatcher.addCallback(callback)
            onDispose {
                callback.remove()
            }
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
        val generatedFileName by viewModel.generatedFileName.collectAsStateWithLifecycle()

        if (showSaveDialog) {
            StopRecordingDialog(
                onSave = { fileName ->
                    viewModel.onStopDialogSave(context, fileName)
                },
                onCancel = { viewModel.onStopDialogCancel() },
                preGeneratedName = generatedFileName
            )
        }

        val showBackDialog by viewModel.showBackDialog.collectAsStateWithLifecycle()

        if (showBackDialog && activity != null) {
            ExitRecordingDialog(
                onSave = {
                    viewModel.onBackDialogSave(context) {
                        activity.finish()
                    }
                },
                onDiscard = {
                    viewModel.onBackDialogDiscard {
                        activity.finish()
                    }
                },
                onCancel = { viewModel.onBackDialogCancel() }
            )
        }
    }
}