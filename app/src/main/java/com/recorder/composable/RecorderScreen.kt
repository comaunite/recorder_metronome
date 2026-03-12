package com.recorder.composable

import android.annotation.SuppressLint
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recorder.services.RecordingState
import com.recorder.composable.components.PauseButtonSmall
import com.recorder.composable.components.PauseButtonBig
import com.recorder.composable.dialogs.ExitRecordingDialog
import com.recorder.composable.dialogs.StopRecordingDialog
import com.recorder.services.FileService
import com.recorder.composable.components.PlayButtonSmall
import com.recorder.composable.components.RecordButton
import com.recorder.composable.components.StopButton
import com.recorder.composable.components.WaveformVisualizer
import com.recorder.helpers.ensureRecordingAudioPermissions
import com.recorder.view_models.RecorderViewModel
import com.recorder.composable.components.TimestampDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderScreen(
    modifier: Modifier = Modifier,
    viewModel: RecorderViewModel,
    onNavigateBack: () -> Unit = {},
    preLoadedRecordings: List<com.recorder.data.RecorderFile>? = null
) {
    val context = LocalContext.current
    val state by viewModel.recordingStateFlow.collectAsStateWithLifecycle()
    val waveformData by viewModel.accumulatedWaveformData.collectAsStateWithLifecycle()
    val timestamp by viewModel.timestamp.collectAsStateWithLifecycle()
    val canScrub = state == RecordingState.PAUSED || state == RecordingState.IDLE || state == RecordingState.PLAYBACK

    var existingRecordings by remember { mutableStateOf(preLoadedRecordings ?: emptyList()) }
    var shouldLoadRecordings by remember { mutableStateOf(preLoadedRecordings == null) }

    // ── Bluetooth mic indicator ──────────────────────────────────────────────────
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    fun hasBtMic(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).any { d ->
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                d.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }

    // produceState's scope is tied to the composition. We write `value` directly
    // from the callback (same mechanism as disconnect, which works), and launch
    // retry coroutines from this scope for slow HFP SCO negotiation.
    val bluetoothMicConnected by produceState(initialValue = hasBtMic()) {
        var retryJob: kotlinx.coroutines.Job? = null

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<AudioDeviceInfo>) {
                value = hasBtMic()
                // SCO input can take seconds to appear after the initial callback.
                retryJob?.cancel()
                retryJob = launch {
                    listOf(500L, 1500L, 3000L, 5000L).forEach { delayMs ->
                        kotlinx.coroutines.delay(delayMs)
                        value = hasBtMic()
                    }
                }
            }
            override fun onAudioDevicesRemoved(removed: Array<AudioDeviceInfo>) {
                retryJob?.cancel()
                value = hasBtMic()
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)

        awaitDispose {
            retryJob?.cancel()
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    BackHandler { viewModel.onBackPressed() }

    val handleRecordAction = ensureRecordingAudioPermissions(context, {
        @SuppressLint("MissingPermission")
        viewModel.onRecordTapped(context)
    }, {
        viewModel.onPermissionDenied(onNavigateBack)
    })

    LaunchedEffect(Unit) {
        if (shouldLoadRecordings) {
            existingRecordings = FileService.getRecorderFiles(context)
            shouldLoadRecordings = false
        }

        if (state == RecordingState.IDLE) {
            handleRecordAction()
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar with file explorer button
        TopAppBar(
            title = { Text("Recorder") },
            navigationIcon = {
                IconButton(onClick = { viewModel.onBackPressed() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to recordings"
                    )
                }
            },
            actions = {
                if (bluetoothMicConnected) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = "Bluetooth microphone active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── flexible top breathing room ──────────────────────────────
            Spacer(modifier = Modifier.weight(0.5f))

            TimestampDisplay(
                timestampMs = timestamp
            )

            // ── larger gap: timestamp → waveform ─────────────────────────
            Spacer(modifier = Modifier.weight(0.5f))

            // Waveform
            WaveformVisualizer(
                waveformData = waveformData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                enableScrubbing = canScrub,
                onScrubPosition = if (canScrub) ({ viewModel.onWaveformScrubbed(it) }) else null,
                onScrubStart = if (canScrub) ({ viewModel.onScrubStart() }) else null,
                onScrubEnd = if (canScrub) ({ viewModel.onScrubEnd() }) else null
            )

            // ── larger gap: waveform → buttons ───────────────────────────
            Spacer(modifier = Modifier.weight(1f))

            // Three-button layout with state-based logic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state) {
                    RecordingState.IDLE -> {
                        PlayButtonSmall(false, {})

                        RecordButton(true, { handleRecordAction() })

                        StopButton(false, {})
                    }

                    RecordingState.RECORDING -> {
                        PlayButtonSmall(false, {})

                        PauseButtonBig({ viewModel.onPauseRecordTapped() })

                        StopButton(true, { viewModel.onStopTapped() })
                    }

                    RecordingState.PAUSED -> {
                        PlayButtonSmall(true, { viewModel.onPlaybackTapped() })

                        RecordButton(true, { handleRecordAction() })

                        StopButton(true, { viewModel.onStopTapped() })
                    }

                    RecordingState.PLAYBACK -> {
                        PauseButtonSmall({ viewModel.onPausePlaybackTapped() })

                        RecordButton(false, {})

                        StopButton(true, { viewModel.onStopTapped() })
                    }
                }
            }

            // ── flexible bottom breathing room ───────────────────────────
            Spacer(modifier = Modifier.weight(0.5f))

            val showSaveDialog by viewModel.showSaveDialog.collectAsStateWithLifecycle()
            val generatedFileName by viewModel.generatedFileName.collectAsStateWithLifecycle()

            if (showSaveDialog) {
                StopRecordingDialog(
                    onSave = { fileName ->
                        viewModel.onStopDialogSave(context, fileName, onNavigateBack)
                    },
                    onCancel = { viewModel.onStopDialogCancel() },
                    preGeneratedName = generatedFileName,
                    existingRecordings = existingRecordings
                )
            }

            val showBackDialog by viewModel.showBackDialog.collectAsStateWithLifecycle()

            if (showBackDialog) {
                ExitRecordingDialog(
                    recordingName = generatedFileName,
                    onSave = {
                        viewModel.onBackDialogSave(context) {
                            onNavigateBack()
                        }
                    },
                    onDiscard = {
                        viewModel.onBackDialogDiscard {
                            onNavigateBack()
                        }
                    },
                    onCancel = { viewModel.onBackDialogCancel() }
                )
            }
        }
    }
}