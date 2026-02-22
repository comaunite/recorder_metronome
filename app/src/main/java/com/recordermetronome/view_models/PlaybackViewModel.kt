package com.recordermetronome.view_models

import androidx.lifecycle.ViewModel
import com.recordermetronome.data.RecordingFile
import com.recordermetronome.util.RecorderEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackViewModel : ViewModel() {
    private val engine = RecorderEngine()
    val recordingStateFlow = engine.recordingStateFlow
    val accumulatedWaveformData = engine.waveformDataStateFlow
    val timestamp = engine.timestampStateFlow

    private val _currentRecording = MutableStateFlow<RecordingFile?>(null)
    val currentRecording = _currentRecording.asStateFlow()

    fun loadRecording(recording: RecordingFile) {
        _currentRecording.value = recording
        engine.loadAudioFile(recording.filePath)
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

