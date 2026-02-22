package com.recordermetronome.view_models

import android.annotation.SuppressLint
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

    @SuppressLint("DefaultLocale")
    fun formatMillisToTimestamp(millis: Long): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000
        val ms = millis % 1000

        if (hours > 0) {
            return String.format("%02d:%02d:%02d.%01d", hours, minutes, seconds, ms / 100)
        }

        return String.format("%02d:%02d.%01d", minutes, seconds, ms / 100)
    }

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

