package com.recorder.view_models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recorder.data.RecordingFile
import com.recorder.util.RecordingFileUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileExplorerViewModel : ViewModel() {
    private val _recordings = MutableStateFlow<List<RecordingFile>>(emptyList())
    val recordings = _recordings.asStateFlow()

    fun loadRecordings(context: Context) {
        viewModelScope.launch {
            val recordingFiles = RecordingFileUtil.getRecordingFiles(context)
            _recordings.value = recordingFiles
        }
    }

    fun deleteRecording(context: Context, recording: RecordingFile) {
        viewModelScope.launch {
            RecordingFileUtil.deleteRecording(recording)
            // Reload recordings after deletion
            loadRecordings(context)
        }
    }

    fun renameRecording(context: Context, recording: RecordingFile, newName: String) {
        viewModelScope.launch {
            RecordingFileUtil.renameRecording(recording, newName)
            // Reload recordings after rename
            loadRecordings(context)
        }
    }
}

