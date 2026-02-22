package com.recordermetronome.view_models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordermetronome.data.RecordingFile
import com.recordermetronome.util.RecorderEngine
import com.recordermetronome.util.RecordingFileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class PlaybackViewModel : ViewModel() {
    private val engine = RecorderEngine()
    val recordingStateFlow = engine.recordingStateFlow
    val accumulatedWaveformData = engine.waveformDataStateFlow
    val timestamp = engine.timestampStateFlow

    private val _currentRecording = MutableStateFlow<RecordingFile?>(null)
    val currentRecording = _currentRecording.asStateFlow()

    private val _existingRecordings = MutableStateFlow<List<RecordingFile>>(emptyList())
    val existingRecordings = _existingRecordings.asStateFlow()

    fun initialize(
        context: Context,
        recording: RecordingFile,
        preLoadedRecordings: List<RecordingFile>?
    ) {
        _currentRecording.value = recording
        engine.loadAudioFile(recording.filePath)

        if (preLoadedRecordings != null) {
            _existingRecordings.value = preLoadedRecordings
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                _existingRecordings.value = RecordingFileUtil.getRecordingFiles(context)
            }
        }
    }

    fun applyRename(oldRecording: RecordingFile, newName: String) {
        val updated = oldRecording.copy(
            name = newName,
            filePath = buildRenamedPath(oldRecording.filePath, newName)
        )
        _currentRecording.value = updated
        _existingRecordings.value = _existingRecordings.value.map { existing ->
            if (existing.filePath == oldRecording.filePath) {
                existing.copy(name = newName, filePath = updated.filePath)
            } else {
                existing
            }
        }
    }

    private fun buildRenamedPath(oldPath: String, newName: String): String {
        val oldFile = File(oldPath)
        val parentDir = oldFile.parentFile
        return File(parentDir, "$newName.wav").absolutePath
    }

    fun onPlaybackTapped() = engine.playBackCurrentStream()
    fun onPausePlaybackTapped() = engine.pause()

    fun onStopTapped() {
        engine.pause()
        engine.finalize { }
    }

    override fun onCleared() {
        super.onCleared()
        engine.finalize { }
    }
}
