package com.recordermetronome

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordermetronome.data.WaveformData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecorderViewModel : ViewModel() {
    private val engine = RecorderEngine()
    val recordingStateFlow = engine.recordingStateFlow

    // State to control the "Save or Discard" dialog
    var pendingAudioData by mutableStateOf<ByteArray?>(null)
        private set

    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog = _showSaveDialog.asStateFlow()

    // Accumulated waveform data for the UI
    private val _accumulatedWaveformData = MutableStateFlow(WaveformData())
    val accumulatedWaveformData = _accumulatedWaveformData.asStateFlow()

    val timestamp = engine.timestampStateFlow

    init {
        // Listen to incremental waveform updates during recording
        viewModelScope.launch {
            engine.waveformUpdateStateFlow.collect { update ->
                if (update.newAmplitudes.isNotEmpty()) {
                    val current = _accumulatedWaveformData.value
                    val updatedAmplitudes = current.amplitudes + update.newAmplitudes
                    _accumulatedWaveformData.value = WaveformData(
                        amplitudes = updatedAmplitudes,
                        maxAmplitude = update.maxAmplitude,
                        currentPosition = updatedAmplitudes.size - 1
                    )
                }
            }
        }

        // Listen to full waveform updates (at playback start)
        viewModelScope.launch {
            engine.waveformDataStateFlow.collect { waveformData ->
                // Full waveform update (e.g., at playback start or reset)
                if (waveformData.amplitudes.isNotEmpty()) {
                    _accumulatedWaveformData.value = waveformData
                }
            }
        }

        // Listen to playback position updates
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

        // Clear accumulated waveform when returning to IDLE
        viewModelScope.launch {
            recordingStateFlow.collect { state ->
                if (state == RecordingState.IDLE) {
                    _accumulatedWaveformData.value = WaveformData()
                }
            }
        }
    }

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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onRecordTapped() = engine.startOrResumeRecording()
    fun onPauseRecordTapped() = engine.pauseRecording()

    fun onPlaybackTapped() = engine.playBackCurrentStream()
    fun onPausePlaybackTapped() = engine.pausePlayback()

    fun onStopTapped() {
        engine.stopAndFinalize { data ->
            // Extract the data out of the engine on stop
            pendingAudioData = data
        }

        _showSaveDialog.value = true
    }

    fun onDiscardData() {
        _showSaveDialog.value = false
        pendingAudioData = null
    }

    fun onSaveData(context: Context, string: String) {
        _showSaveDialog.value = false
        TODO("Not yet implemented")
    }
}