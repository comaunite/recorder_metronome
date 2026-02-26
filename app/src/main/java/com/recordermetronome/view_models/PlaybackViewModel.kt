package com.recordermetronome.view_models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordermetronome.data.RecordingFile
import com.recordermetronome.data.WaveformData
import com.recordermetronome.util.RecorderEngine
import com.recordermetronome.util.RecordingFileUtil
import com.recordermetronome.util.RecordingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class PlaybackViewModel : ViewModel() {
    private val engine = RecorderEngine()
    val recordingStateFlow = engine.recordingStateFlow

    private val _accumulatedWaveformData = MutableStateFlow(WaveformData())
    val accumulatedWaveformData = _accumulatedWaveformData.asStateFlow()

    val timestamp = engine.timestampStateFlow
    val repeatPlaybackEnabled = engine.repeatPlaybackEnabledFlow
    val playbackSpeed = engine.playbackSpeedFlow

    private val _currentRecording = MutableStateFlow<RecordingFile?>(null)
    val currentRecording = _currentRecording.asStateFlow()

    private val _existingRecordings = MutableStateFlow<List<RecordingFile>>(emptyList())
    val existingRecordings = _existingRecordings.asStateFlow()

    init {
        // Listen to full waveform updates (at load or playback start)
        viewModelScope.launch {
            engine.waveformDataStateFlow.collect { waveformData ->
                if (waveformData.amplitudes.isNotEmpty()) {
                    _accumulatedWaveformData.value = waveformData
                }
            }
        }

        // Listen to playback position updates to track the red bar position
        viewModelScope.launch {
            engine.playbackPositionStateFlow.collect { position ->
                if (recordingStateFlow.value == RecordingState.PLAYBACK) {
                    // Update only the position in the accumulated waveform
                    val current = _accumulatedWaveformData.value
                    _accumulatedWaveformData.value = current.copy(
                        currentPosition = position.currentIndex
                    )
                }
            }
        }
    }

    fun initialize(
        context: Context,
        recording: RecordingFile,
        preLoadedRecordings: List<RecordingFile>?
    ) {
        _currentRecording.value = recording

        println("PLAYBACK_VM: Initializing with recording: ${recording.name} at ${recording.filePath}")
        try {
            val parsedAudio = RecordingFileUtil.readRecordingFile(recording.filePath)
            engine.loadRecordingForPlayback(parsedAudio)
        } catch (e: Exception) {
            println("PLAYBACK_VM: Error loading audio file: ${e.message}")
            e.printStackTrace()
        }
        println("PLAYBACK_VM: Audio file loading completed")

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
    fun onRepeatToggleTapped() = engine.toggleRepeatPlayback()
    fun onPlaybackSpeedTapped(speed: Float) = engine.setPlaybackSpeed(speed)

    override fun onCleared() {
        super.onCleared()
        engine.finalize { }
    }

    fun onReturnToFileExplorer(callback: () -> Unit) {
        this.engine.finalize {
            callback()
        }
    }
}
