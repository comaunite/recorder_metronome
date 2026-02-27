package com.recorder.composable

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recorder.util.RecordingState
import com.recorder.composable.components.PauseButtonSmall
import com.recorder.composable.components.PauseButtonBig
import com.recorder.composable.dialogs.ExitRecordingDialog
import com.recorder.composable.dialogs.StopRecordingDialog
import com.recorder.util.RecorderFileUtil
import com.recorder.composable.components.PlayButtonSmall
import com.recorder.composable.components.RecordButton
import com.recorder.composable.components.StopButton
import com.recorder.composable.components.WaveformVisualizer
import com.recorder.util.FormattingHelper
import com.recorder.util.ensureRecordingAudioPermissions
import com.recorder.view_models.RecorderViewModel

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
    val formattedTimestamp = remember(timestamp) { FormattingHelper.formatDurationWithMs(timestamp) }

    var existingRecordings by remember { mutableStateOf(preLoadedRecordings ?: emptyList()) }
    var shouldLoadRecordings by remember { mutableStateOf(preLoadedRecordings == null) }

    BackHandler { viewModel.onBackPressed() }

    val handleRecordAction = ensureRecordingAudioPermissions(context, {
        @SuppressLint("MissingPermission")
        viewModel.onRecordTapped()
    }, {
        viewModel.onPermissionDenied(onNavigateBack)
    })

    LaunchedEffect(Unit) {
        if (shouldLoadRecordings) {
            existingRecordings = RecorderFileUtil.getRecorderFiles(context)
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
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Timestamp Tracker
            Text(
                text = formattedTimestamp,
                style = MaterialTheme.typography.bodyLarge,
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