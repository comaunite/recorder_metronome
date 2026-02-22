package com.recordermetronome.composable

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
import com.recordermetronome.composable.components.PausePlaybackButton
import com.recordermetronome.composable.components.PlayButton
import com.recordermetronome.composable.components.StopButton
import com.recordermetronome.composable.components.WaveformVisualizer
import com.recordermetronome.composable.dialogs.DeleteRecordingDialog
import com.recordermetronome.composable.dialogs.RenameRecordingDialog
import com.recordermetronome.data.RecordingFile
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

    // Track current recording file locally to handle renames
    var currentRecording by remember { mutableStateOf(recordingFile) }
    val formattedTimestamp = remember(timestamp) { viewModel.formatMillisToTimestamp(timestamp) }
    val formattedDuration = remember(currentRecording.durationMs) { RecordingFileUtil.formatDuration(currentRecording.durationMs) }

    // Use pre-loaded recordings if available, otherwise load only when needed
    var existingRecordings by remember { mutableStateOf(preLoadedRecordings ?: emptyList()) }

    // Only load from disk if recordings weren't pre-loaded
    var shouldLoadRecordings by remember { mutableStateOf(preLoadedRecordings == null) }

    LaunchedEffect(Unit) {
        if (shouldLoadRecordings) {
            existingRecordings = RecordingFileUtil.getRecordingFiles(context)
            shouldLoadRecordings = false
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(currentRecording.name) }

    // Load the recording when screen is first displayed
    LaunchedEffect(recordingFile) {
        viewModel.loadRecording(recordingFile)
        currentRecording = recordingFile
    }

    if (showDeleteDialog) {
        DeleteRecordingDialog(
            recordingName = currentRecording.name,
            onDelete = {
                fileExplorerViewModel.deleteRecording(context, currentRecording)
                showDeleteDialog = false
                onNavigateBack()
            },
            onCancel = { showDeleteDialog = false }
        )
    }

    if (showRenameDialog) {
        RenameRecordingDialog(
            currentRecording = currentRecording,
            existingRecordings = existingRecordings,
            renameText = renameText,
            onRename = { newName ->
                fileExplorerViewModel.renameRecording(context, currentRecording, newName)
                // Update current recording with new name
                currentRecording = currentRecording.copy(name = newName)
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
                IconButton(onClick = { onNavigateBack() }) {
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
                                renameText = currentRecording.name
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
                                RecordingFileUtil.shareRecording(context, currentRecording)
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
                    RecordingState.IDLE -> {
                        PlayButton(true, { viewModel.onPlaybackTapped() })

                        Spacer(modifier = Modifier)

                        StopButton(true, { viewModel.onStopTapped() })
                    }

                    RecordingState.RECORDING -> {
                        // Error state - should never be reached
                    }

                    RecordingState.PAUSED -> {
                        PlayButton(true, { viewModel.onPlaybackTapped() })

                        Spacer(modifier = Modifier)

                        StopButton(true, { viewModel.onStopTapped() })
                    }

                    RecordingState.PLAYBACK -> {
                        PausePlaybackButton({ viewModel.onPausePlaybackTapped() })

                        Spacer(modifier = Modifier)

                        StopButton(true, { viewModel.onStopTapped() })
                    }
                }
            }
        }
    }
}
