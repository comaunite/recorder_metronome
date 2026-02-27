package com.recorder.composable

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recorder.composable.components.MoreOptionsMenu
import com.recorder.composable.components.PauseButtonBig
import com.recorder.composable.components.PlayButtonBig
import com.recorder.composable.components.PlaybackSpeedButton
import com.recorder.composable.components.RepeatToggleButtonSmall
import com.recorder.composable.components.WaveformVisualizer
import com.recorder.data.RecorderFile
import com.recorder.util.FormattingHelper
import com.recorder.util.RecordingState
import com.recorder.view_models.FileExplorerViewModel
import com.recorder.view_models.PlaybackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel,
    fileExplorerViewModel: FileExplorerViewModel,
    recorderFile: RecorderFile,
    onNavigateBack: () -> Unit = {},
    preLoadedRecordings: List<RecorderFile>? = null
) {
    val context = LocalContext.current

    // Load the recording when screen is first displayed or when the selected file changes.
    LaunchedEffect(recorderFile) {
        viewModel.initialize(context, recorderFile, preLoadedRecordings)
    }

    // Refresh the recordings list without resetting the current playback state.
    LaunchedEffect(preLoadedRecordings) {
        preLoadedRecordings?.let { viewModel.setExistingRecordings(it) }
    }

    BackHandler { viewModel.onReturnToFileExplorer(onNavigateBack) }

    val state by viewModel.recordingStateFlow.collectAsStateWithLifecycle()
    val waveformData by viewModel.accumulatedWaveformData.collectAsStateWithLifecycle()
    val repeatEnabled by viewModel.repeatPlaybackEnabled.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()

    val currentRecording by viewModel.currentRecording.collectAsStateWithLifecycle()
    val existingRecordings by viewModel.existingRecordings.collectAsStateWithLifecycle()

    val timestamp by viewModel.timestamp.collectAsStateWithLifecycle()
    val formattedTimestamp = remember(timestamp) { FormattingHelper.formatDurationWithMs(timestamp) }
    val formattedDuration = remember(currentRecording.durationMs) { FormattingHelper.formatDuration(currentRecording.durationMs) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar with back button and menu
        TopAppBar(
            title = { Text("Playback") },
            navigationIcon = {
                IconButton(onClick = { viewModel.onReturnToFileExplorer(onNavigateBack) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to recordings"
                    )
                }
            },
            actions = {
                MoreOptionsMenu(
                    context = context,
                    recording = currentRecording,
                    existingRecordings = existingRecordings,
                    onRenameSuccess = { newName ->
                        fileExplorerViewModel.loadRecordings(context)
                        viewModel.updateInMemoryCollections(currentRecording, newName)
                    },
                    onDeleteSuccess = {
                        viewModel.onReturnToFileExplorer(onNavigateBack)
                    }
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentRecording.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamp and duration
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedTimestamp,
                    style = MaterialTheme.typography.displayLarge,
                )
            }

            Row(
                modifier = Modifier
                .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDuration,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Waveform
            WaveformVisualizer(
                waveformData = waveformData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Playback control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state) {
                    RecordingState.IDLE,
                    RecordingState.RECORDING,
                    RecordingState.PAUSED -> {
                        PlaybackSpeedButton(playbackSpeed) { viewModel.onPlaybackSpeedTapped(it) }

                        PlayButtonBig(true, { viewModel.onPlaybackTapped() })

                        RepeatToggleButtonSmall(repeatEnabled) { viewModel.onRepeatToggleTapped() }
                    }

                    RecordingState.PLAYBACK -> {
                        PlaybackSpeedButton(playbackSpeed) { viewModel.onPlaybackSpeedTapped(it) }

                        PauseButtonBig({ viewModel.onPausePlaybackTapped() })

                        RepeatToggleButtonSmall(repeatEnabled) { viewModel.onRepeatToggleTapped() }
                    }
                }
            }
        }
    }
}
