package com.recordermetronome.view_models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordermetronome.data.RecordingFile
import com.recordermetronome.util.RecordingFileUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class FileExplorerViewModel : ViewModel() {
    // Recordings list
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
            try {
                val file = java.io.File(recording.filePath)
                if (file.exists()) {
                    file.delete()
                }
                // Reload recordings after deletion
                loadRecordings(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

