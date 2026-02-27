package com.recorder.view_models

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recorder.util.RecorderEngine
import com.recorder.util.RecordingState
import com.recorder.data.WaveformData
import com.recorder.util.RecorderFileUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecorderViewModel : ViewModel() {
    private val engine = RecorderEngine()
    val recordingStateFlow = engine.recordingStateFlow

    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog = _showSaveDialog.asStateFlow()

    private val _generatedFileName = MutableStateFlow("")
    val generatedFileName = _generatedFileName.asStateFlow()

    private val _showBackDialog = MutableStateFlow(false)
    val showBackDialog = _showBackDialog.asStateFlow()

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


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onRecordTapped() = engine.startOrResumeRecording()
    fun onPauseRecordTapped() = engine.pause()

    fun onPlaybackTapped() = engine.playBackCurrentStream()
    fun onPausePlaybackTapped() = engine.pause()

    fun onStopTapped() {
        engine.pause()

        _generatedFileName.value = RecorderFileUtil.generateDefaultFileName()
        _showSaveDialog.value = true
    }

    fun onStopDialogSave(context: Context, fileName: String, callback: () -> Unit) {
        _showSaveDialog.value = false

        engine.finalize { audioData ->
            RecorderFileUtil.saveRecording(context, fileName, audioData)
            callback()
        }
    }

    fun onStopDialogCancel() {
        _showSaveDialog.value = false
    }

    fun onBackPressed() {
        engine.pause()
        _showBackDialog.value = true
    }

    fun onBackDialogSave(context: Context, callback: () -> Unit) {
        _showBackDialog.value = false
        val fileName = RecorderFileUtil.generateDefaultFileName()
        engine.finalize { audioData ->
            RecorderFileUtil.saveRecording(context, fileName, audioData)
        }
        callback()
    }

    fun onBackDialogDiscard(callback: () -> Unit) {
        _showBackDialog.value = false
        engine.finalize { callback() }
    }

    fun onBackDialogCancel() {
        _showBackDialog.value = false
    }

    override fun onCleared() {
        super.onCleared()
        engine.finalize { }
    }

    fun onPermissionDenied(callback: () -> Unit) {
        engine.finalize { callback() }
    }
}