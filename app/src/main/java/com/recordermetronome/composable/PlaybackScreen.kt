package com.recordermetronome.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.recordermetronome.composable.components.PauseButtonBig
import com.recordermetronome.composable.components.PlayButtonBig
import com.recordermetronome.composable.components.RepeatToggleButtonSmall
import com.recordermetronome.composable.components.WaveformVisualizer
import com.recordermetronome.composable.dialogs.DeleteRecordingDialog
import com.recordermetronome.composable.dialogs.RenameRecordingDialog
import com.recordermetronome.data.RecordingFile
import com.recordermetronome.util.FormattingHelper
import com.recordermetronome.util.RecordingFileUtil
import com.recordermetronome.util.RecordingState
import com.recordermetronome.view_models.FileExplorerViewModel
import com.recordermetronome.view_models.PlaybackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel,
    fileExplorerViewModel: FileExplorerViewModel,
    recordingFile: RecordingFile,
    onNavigateBack: () -> Unit = {},
    preLoadedRecordings: List<RecordingFile>? = null
) {
    val context = LocalContext.current
    val state by viewModel.recordingStateFlow.collectAsStateWithLifecycle()
    val waveformData by viewModel.accumulatedWaveformData.collectAsStateWithLifecycle()
    val timestamp by viewModel.timestamp.collectAsStateWithLifecycle()
    val repeatEnabled by viewModel.repeatPlaybackEnabled.collectAsStateWithLifecycle()
    val currentRecording by viewModel.currentRecording.collectAsStateWithLifecycle()
    val existingRecordings by viewModel.existingRecordings.collectAsStateWithLifecycle()

    val activeRecording = currentRecording ?: recordingFile
    val formattedTimestamp = remember(timestamp) { FormattingHelper.formatDurationWithMs(timestamp) }
    val formattedDuration = remember(activeRecording.durationMs) { FormattingHelper.formatDuration(activeRecording.durationMs) }

    BackHandler { viewModel.onReturnToFileExplorer(onNavigateBack()) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(activeRecording.name) }

    // Load the recording when screen is first displayed
    LaunchedEffect(recordingFile, preLoadedRecordings) {
        viewModel.initialize(context, recordingFile, preLoadedRecordings)
    }

    if (showDeleteDialog) {
        DeleteRecordingDialog(
            recordingName = activeRecording.name,
            onDelete = {
                fileExplorerViewModel.deleteRecording(context, activeRecording)
                showDeleteDialog = false
                viewModel.onReturnToFileExplorer(onNavigateBack())
            },
            onCancel = { showDeleteDialog = false }
        )
    }

    if (showRenameDialog) {
        RenameRecordingDialog(
            currentRecording = activeRecording,
            existingRecordings = existingRecordings,
            renameText = renameText,
            onRename = { newName ->
                fileExplorerViewModel.renameRecording(context, activeRecording, newName)
                viewModel.applyRename(activeRecording, newName)
                renameText = newName
                showRenameDialog = false
            },
            onRenameTextChange = { renameText = it },
            onCancel = {
                showRenameDialog = false
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar with back button and menu
        TopAppBar(
            title = { Text("Playback") },
            navigationIcon = {
                IconButton(onClick = { viewModel.onReturnToFileExplorer(onNavigateBack()) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to recordings"
                    )
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuExpanded = false
                                renameText = activeRecording.name
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuExpanded = false
                                showDeleteDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                menuExpanded = false
                                RecordingFileUtil.shareRecording(context, activeRecording)
                            }
                        )
                    }
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
            // File name - now updates when renamed
            Text(
                text = activeRecording.name,
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
                    style = MaterialTheme.typography.displayMedium,
                )

                Text(
                    text = " / $formattedDuration",
                    style = MaterialTheme.typography.bodyMedium,
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
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state) {
                    RecordingState.IDLE,
                    RecordingState.RECORDING,
                    RecordingState.PAUSED -> {
                        Spacer(modifier = Modifier)

                        PlayButtonBig(true, { viewModel.onPlaybackTapped() })

                        RepeatToggleButtonSmall(repeatEnabled) { viewModel.onRepeatToggleTapped() }

                        Spacer(modifier = Modifier)
                    }

                    RecordingState.PLAYBACK -> {
                        Spacer(modifier = Modifier)

                        PauseButtonBig({ viewModel.onPausePlaybackTapped() })

                        RepeatToggleButtonSmall(repeatEnabled) { viewModel.onRepeatToggleTapped() }

                        Spacer(modifier = Modifier)
                    }
                }
            }
        }
    }
}
