package com.recordermetronome.view_models

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordermetronome.RecorderEngine
import com.recordermetronome.RecordingState
import com.recordermetronome.data.WaveformData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    fun generateDefaultFileName(): String {
        val dateFormat = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.US)
        return "Recording ${dateFormat.format(Date())}"
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onRecordTapped() = engine.startOrResumeRecording()
    fun onPauseRecordTapped() = engine.pause()

    fun onPlaybackTapped() = engine.playBackCurrentStream()
    fun onPausePlaybackTapped() = engine.pause()

    fun onStopTapped() {
        engine.pause()

        _generatedFileName.value = generateDefaultFileName()
        _showSaveDialog.value = true
    }

    fun onStopDialogCancel() {
        _showSaveDialog.value = false
    }

    fun onStopDialogSave(context: Context, fileName: String, callback: () -> Unit) {
        _showSaveDialog.value = false

        engine.finalize { audioData ->
            saveToDisk(context, fileName, audioData)
            callback()
        }
    }

    private fun saveToDisk(context: Context, fileName: String, audioData: ByteArray) {
        if (audioData.isNotEmpty()) {
            try {
                // Create recordings directory
                val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
                if (!recordingsDir.exists()) {
                    recordingsDir.mkdirs()
                }

                // Create file with .wav extension
                val outputFile = File(recordingsDir, "$fileName.wav")

                // Write audio data to file
                outputFile.writeBytes(audioData)

                println("Recording saved: ${outputFile.absolutePath}")
            } catch (e: Exception) {
                println("Error saving recording: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun onBackPressed() {
        engine.pause()
        _showBackDialog.value = true
    }

    fun onBackDialogSave(context: Context, callback: () -> Unit) {
        _showBackDialog.value = false
        val fileName = generateDefaultFileName()
        engine.finalize { audioData ->
            saveToDisk(context, fileName, audioData)
        }
        callback()
    }

    fun onBackDialogDiscard(callback: () -> Unit) {
        _showBackDialog.value = false
        engine.finalize { }
        callback()
    }

    fun onBackDialogCancel() {
        _showBackDialog.value = false
    }
}