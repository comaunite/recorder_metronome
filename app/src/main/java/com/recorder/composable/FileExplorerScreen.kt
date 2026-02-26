package com.recorder.composable

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
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
import com.recorder.composable.components.RecordButton
import com.recorder.composable.dialogs.DeleteRecordingDialog
import com.recorder.composable.dialogs.RenameRecordingDialog
import com.recorder.data.RecordingFile
import com.recorder.util.FormattingHelper
import com.recorder.util.RecordingFileUtil
import com.recorder.util.ensureRecordingAudioPermissions
import com.recorder.view_models.FileExplorerViewModel
import com.recorder.view_models.RecorderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    modifier: Modifier = Modifier,
    recorderViewModel: RecorderViewModel,
    fileExplorerViewModel: FileExplorerViewModel,
    onStartRecording: () -> Unit,
    onPlayRecording: (RecordingFile) -> Unit = {}
) {
    val context = LocalContext.current
    val recordings by fileExplorerViewModel.recordings.collectAsStateWithLifecycle()

    val handleRecordAction = ensureRecordingAudioPermissions(context) {
        @SuppressLint("MissingPermission")
        recorderViewModel.onRecordTapped()
        onStartRecording() // This should navigate us to the RecordingScreen
    }

    // Load recordings when screen is displayed
    LaunchedEffect(Unit) {
        fileExplorerViewModel.loadRecordings(context)
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Recordings") },
        )

        // Recordings List
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (recordings.isEmpty()) {
                Text(
                    text = "No recordings yet",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(recordings) { recording ->
                        RecordingFileItem(
                            recording,
                            recordings,
                            fileExplorerViewModel,
                            onPlayRecording = { onPlayRecording(recording) }
                        )
                    }
                }
            }
        }

        // Record Button at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            RecordButton(enabled = true, onClick = { handleRecordAction() })
        }
    }
}

@Composable
fun RecordingFileItem(
    recording: RecordingFile,
    recordings: List<RecordingFile>,
    fileExplorerViewModel: FileExplorerViewModel = remember { FileExplorerViewModel() },
    onPlayRecording: () -> Unit = {}
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(recording.name) }

    if (showDeleteDialog) {
        DeleteRecordingDialog(
            recordingName = recording.name,
            onDelete = {
                fileExplorerViewModel.deleteRecording(context, recording)
                showDeleteDialog = false
            },
            onCancel = { showDeleteDialog = false }
        )
    }

    if (showRenameDialog) {
        RenameRecordingDialog(
            currentRecording = recording,
            existingRecordings = recordings,
            renameText = renameText,
            onRename = { newName ->
                fileExplorerViewModel.renameRecording(context, recording, newName)
                showRenameDialog = false
            },
            onRenameTextChange = { renameText = it },
            onCancel = {
                showRenameDialog = false
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = { onPlayRecording() }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File info column (takes available space)
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name on the left
                Text(
                    text = recording.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )

                // Duration in the middle
                Text(
                    text = FormattingHelper.formatDuration(recording.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Date and time on left, File size on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FormattingHelper.formatTimestamp(recording.createdTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Text(
                    text = FormattingHelper.formatFileSize(recording.sizeKb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Three-dot menu on the right with 2-row row span
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
                        renameText = recording.name
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
                        RecordingFileUtil.shareRecording(context, recording)
                    }
                )
            }
        }
    }
}
