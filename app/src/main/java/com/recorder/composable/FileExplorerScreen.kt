package com.recorder.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwa.clientktx.clientlib.ActivityResultSender
import com.recorder.composable.components.MoreOptionsMenu
import com.recorder.composable.components.RecordButton
import com.recorder.composable.dialogs.DeleteRecordingDialog
import com.recorder.composable.dialogs.DonateDialog
import com.recorder.data.RecorderFile
import com.recorder.helpers.FormattingHelper
import com.recorder.view_models.DonationViewModel
import com.recorder.view_models.FileExplorerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    modifier: Modifier = Modifier,
    viewModel: FileExplorerViewModel,
    donationViewModel: DonationViewModel,
    activityResultSender: ActivityResultSender,
    onStartRecording: () -> Unit,
    onPlayRecording: (RecorderFile) -> Unit = {}
) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    var showDonateDialog by remember { mutableStateOf(false) }

    // ---- Search state ----
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    val displayedRecordings = remember(recordings, searchQuery, searchMode) {
        if (searchMode && searchQuery.isNotEmpty()) {
            recordings.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else {
            recordings
        }
    }

    // ---- Selection state ----
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        DeleteRecordingDialog(
            recordings = recordings.filter { it.filePath in selectedPaths },
            onDelete = {
                val toDelete = recordings.filter { it.filePath in selectedPaths }
                viewModel.deleteRecordings(context, toDelete)
                selectedPaths = emptySet()
                selectionMode = false
                showDeleteConfirmDialog = false
            },
            onCancel = { showDeleteConfirmDialog = false }
        )
    }

    // Exit selection mode on back press; exit search mode if not in selection mode
    BackHandler(enabled = selectionMode || searchMode) {
        if (selectionMode) {
            selectionMode = false
            selectedPaths = emptySet()
        } else if (searchMode) {
            searchMode = false
            searchQuery = ""
        }
    }

    // Load recordings when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.loadRecordings(context)
    }

    if (showDonateDialog) {
        DonateDialog(
            viewModel = donationViewModel,
            activityResultSender = activityResultSender,
            onDismiss = { showDonateDialog = false }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Recordings (${recordings.size})") },
            actions = {
                // Search toggle button
                IconButton(onClick = {
                    searchMode = !searchMode
                    if (!searchMode) searchQuery = ""
                }) {
                    Icon(
                        imageVector = if (searchMode) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = if (searchMode) "Close search" else "Search recordings",
                        tint = if (searchMode) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { showDonateDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.VolunteerActivism,
                        contentDescription = "Donate SOL",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        // ---- Search bar (visible only in search mode) ----
        if (searchMode) {
            LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .focusRequester(searchFocusRequester),
                placeholder = {
                    Text(
                        "Search recordings…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider()
        }

        // ---- Selection toolbar (visible only in selection mode) ----
        if (selectionMode) {
            val selectAllState = when {
                selectedPaths.isEmpty() -> ToggleableState.Off
                selectedPaths.size == recordings.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Select-all tri-state checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false)
                    ) {
                        if (selectAllState == ToggleableState.On) {
                            selectedPaths = emptySet()
                            selectionMode = false
                        } else {
                            selectedPaths = recordings.map { it.filePath }.toSet()
                        }
                    }
                ) {
                    CircularTriStateCheckbox(
                        state = selectAllState,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = if (selectedPaths.isEmpty()) "Select all"
                               else "${selectedPaths.size} selected",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Delete button — only visible when at least one item is selected
                if (selectedPaths.isNotEmpty()) {
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete ${selectedPaths.size} selected",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        // ---- Recordings list ----
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
            } else if (displayedRecordings.isEmpty()) {
                Text(
                    text = "No recordings match \"$searchQuery\"",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 0.dp, end = 0.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(displayedRecordings, key = { it.filePath }) { recording ->
                        RecorderFileItem(
                            recording = recording,
                            recordings = recordings,
                            viewModel = viewModel,
                            selectionMode = selectionMode,
                            isSelected = recording.filePath in selectedPaths,
                            onTap = {
                                if (selectionMode) {
                                    selectedPaths = if (recording.filePath in selectedPaths) {
                                        selectedPaths - recording.filePath
                                    } else {
                                        selectedPaths + recording.filePath
                                    }
                                    // Auto-exit selection mode when last item deselected
                                    if (selectedPaths.isEmpty()) selectionMode = false
                                } else {
                                    onPlayRecording(recording)
                                }
                            },
                            onLongPress = {
                                selectionMode = true
                                selectedPaths = selectedPaths + recording.filePath
                            }
                        )
                    }
                }
            }
        }

        // Record button at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            RecordButton(enabled = true, onClick = onStartRecording)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecorderFileItem(
    recording: RecorderFile,
    recordings: List<RecorderFile>,
    viewModel: FileExplorerViewModel = remember { FileExplorerViewModel() },
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .combinedClickable(
                onClick = { onTap() },
                onLongClick = { onLongPress() }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox — only visible in selection mode
        if (selectionMode) {
            CircularCheckbox(
                checked = isSelected,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        // File info column (takes available space)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recording.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = FormattingHelper.formatDuration(recording.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

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

        // Three-dot menu — hidden in selection mode
        if (!selectionMode) {
            MoreOptionsMenu(
                context = context,
                recording = recording,
                existingRecordings = recordings,
                onRenameSuccess = { viewModel.loadRecordings(context) },
                onDeleteSuccess = { viewModel.loadRecordings(context) }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Circular checkbox components
// ---------------------------------------------------------------------------

/**
 * A single-state circular checkbox drawn with Material icons.
 * Checked → filled circle with tick  |  Unchecked → outlined empty circle
 */
@Composable
private fun CircularCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier
) {
    val primary  = MaterialTheme.colorScheme.primary
    val outline  = MaterialTheme.colorScheme.outline
    Icon(
        imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (checked) primary else outline,
        modifier = modifier.size(24.dp)
    )
}

/**
 * A tri-state circular checkbox for the "select all" row.
 * On → filled circle with tick  |  Indeterminate → filled circle with dash  |  Off → outlined circle
 */
@Composable
private fun CircularTriStateCheckbox(
    state: ToggleableState,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val (icon, tint) = when (state) {
        ToggleableState.On            -> Icons.Filled.CheckCircle      to primary
        ToggleableState.Indeterminate -> Icons.Filled.RemoveCircle     to primary
        ToggleableState.Off           -> Icons.Outlined.RadioButtonUnchecked to outline
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(24.dp)
    )
}
