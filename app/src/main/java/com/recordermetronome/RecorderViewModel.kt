package com.recordermetronome

import android.Manifest
import android.annotation.SuppressLint
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

    val waveformData = engine.waveformData

    private val _timestamp = MutableStateFlow(0L)
    val timestamp = _timestamp.asStateFlow()

    val formattedTimestamp: String
        get() = formatMillisToTimestamp(_timestamp.value)

    fun updateTimestamp(millis: Long) {
        _timestamp.value = millis
    }

    @SuppressLint("DefaultLocale")
    fun formatMillisToTimestamp(millis: Long): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000
        val ms = millis % 1000
        return String.format("%02d:%02d:%02d.%01d", hours, minutes, seconds, ms / 100)
    }


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onRecordTapped() = engine.startOrResumeRecording()
    fun onPauseRecordTapped() = engine.pauseRecording()

    fun onPlaybackTapped() = engine.playBackCurrentStream()
    fun onPausePlaybackTapped() = engine.pausePlayback()

    fun onStopTapped() {
        engine.stopAndFinalize { data ->
            pendingAudioData = data
        }
    }

    fun onDiscardData() {
        TODO("Not yet implemented")
    }

    fun onSaveData(context: Context, string: String) {
        TODO("Not yet implemented")
    }

    init {
        engine.setTimestampListener { millis ->
            updateTimestamp(millis)
        }
    }
}