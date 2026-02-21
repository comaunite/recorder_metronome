package com.recordermetronome.composable.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.recordermetronome.data.RecordingFile
import com.recordermetronome.util.FilenameValidator

@Composable
fun RenameRecordingDialog(
    currentRecording: RecordingFile,
    existingRecordings: List<RecordingFile>,
    onRename: (String) -> Unit,
    onCancel: () -> Unit,
    renameText: String,
    onRenameTextChange: (String) -> Unit
) {
    val validationResult = FilenameValidator.validateRenameFilename(
        renameText,
        currentRecording.name,
        existingRecordings
    )
    val isValid = validationResult.isValid

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
                .fillMaxWidth(0.9f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Rename Recording",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = renameText,
                onValueChange = onRenameTextChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Recording name") }
            )

            // Display error message
            if (!isValid && renameText.isNotEmpty()) {
                Text(
                    text = validationResult.errorMessage,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        if (isValid) {
                            onRename(renameText)
                        }
                    },
                    enabled = isValid
                ) {
                    Text("Rename")
                }
            }
        }
    }
}

