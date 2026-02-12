package com.recordermetronome

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecorderViewModel : ViewModel() {
    private val engine = RecorderEngine()
    val state = engine.state

    // State to control the "Save or Discard" dialog
    var pendingAudioData by mutableStateOf<ByteArray?>(null)
        private set

    private val _waveformData = MutableStateFlow(WaveformData())
    val waveformData = _waveformData.asStateFlow()

    fun updateWaveform(amplitudes: List<Float>, maxAmplitude: Float) {
        _waveformData.value = WaveformData(amplitudes, maxAmplitude)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onRecordTapped() = engine.startOrResumeRecording()
    fun onPauseRecordTapped() = engine.pauseRecording()

    fun onPlaybackTapped() = engine.playBackCurrentStream()
    fun onPausePlaybackTapped() = engine.pausePlayback()

    fun onStopTapped() {
        engine.stopAndFinalize { data ->
            pendingAudioData = data // This triggers the UI dialog
        }
    }

    fun onDiscardData() {
        TODO("Not yet implemented")
    }

    fun onSaveData(context: Context, string: String) {
        TODO("Not yet implemented")
    }
}