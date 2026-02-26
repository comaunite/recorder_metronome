package com.recorder.composable.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.recorder.data.RecordingFile
import com.recorder.util.FilenameValidator

@Composable
fun StopRecordingDialog(
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    preGeneratedName: String = "",
    existingRecordings: List<RecordingFile> = emptyList()
) {
    var fileName by remember { mutableStateOf(preGeneratedName) }
    val validationResult = FilenameValidator.validateNewFilename(fileName, existingRecordings)
    val isValid = validationResult.isValid

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Save Recording") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Enter a name for your recording:")
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Recording Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isValid) onSave(fileName) }
                    )
                )

                // Display error message
                if (!isValid && fileName.isNotEmpty()) {
                    Text(
                        text = validationResult.errorMessage,
                        color = Color.Red,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isValid) onSave(fileName) },
                enabled = isValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}