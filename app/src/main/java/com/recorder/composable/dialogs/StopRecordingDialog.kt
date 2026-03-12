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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.recorder.data.RecorderFile
import com.recorder.helpers.FilenameValidator
import kotlinx.coroutines.delay

@Composable
fun StopRecordingDialog(
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    preGeneratedName: String = "",
    existingRecordings: List<RecorderFile> = emptyList()
) {
    var fileNameValue by remember {
        mutableStateOf(TextFieldValue(preGeneratedName, TextRange(0, preGeneratedName.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(50)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val validationResult = FilenameValidator.validateNewFilename(fileNameValue.text, existingRecordings)
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
                    value = fileNameValue,
                    onValueChange = { fileNameValue = it },
                    label = { Text("Recording Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isValid) onSave(fileNameValue.text) }
                    )
                )

                // Display error message
                if (!isValid && fileNameValue.text.isNotEmpty()) {
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
                onClick = { if (isValid) onSave(fileNameValue.text) },
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