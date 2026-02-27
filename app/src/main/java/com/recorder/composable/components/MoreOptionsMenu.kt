package com.recorder.composable.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.recorder.composable.dialogs.DeleteRecordingDialog
import com.recorder.composable.dialogs.RenameRecordingDialog
import com.recorder.data.RecorderFile
import com.recorder.util.RecorderFileUtil

@Composable
fun MoreOptionsMenu(
    context: Context,
    recording: RecorderFile,
    existingRecordings: List<RecorderFile>,
    onRenameSuccess: (String) -> Unit,
    onDeleteSuccess: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    // Use remember(recording) to ensure the state resets when the recording changes
    var renameText by remember(recording) { mutableStateOf(recording.name) }

    if (showDeleteDialog) {
        DeleteRecordingDialog(
            recordingName = recording.name,
            onDelete = {
                showDeleteDialog = false
                RecorderFileUtil.deleteRecording(recording)
                onDeleteSuccess()
            },
            onCancel = { showDeleteDialog = false }
        )
    }

    if (showRenameDialog) {
        RenameRecordingDialog(
            currentRecording = recording,
            existingRecordings = existingRecordings,
            renameText = renameText,
            onRename = { newName ->
                showRenameDialog = false
                RecorderFileUtil.renameRecording(recording, newName)
                onRenameSuccess(newName)
            },
            onRenameTextChange = { renameText = it },
            onCancel = {
                showRenameDialog = false
            }
        )
    }

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
                    // Reset renameText to current recording name before showing dialog
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
                    RecorderFileUtil.shareRecording(context, recording)
                }
            )
        }
    }
}
