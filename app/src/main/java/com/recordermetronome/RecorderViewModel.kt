package com.recordermetronome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class RecorderViewModel : ViewModel() {
    private val recorderEngine = RecorderEngine()

    var isRecording by mutableStateOf(false)
        private set

    fun onStartRecording() {
        // Logic to start recording
        // In a real app, you'd handle permissions before calling this,
        // or have this call a service.
        try {
            recorderEngine.start()
            isRecording = true
        } catch (e: SecurityException) {
            // Handle permission not granted
        }
    }

    fun onStopRecording() {
        recorderEngine.stop()
        isRecording = false
    }

    override fun onCleared() {
        super.onCleared()
        if (isRecording) {
            recorderEngine.stop()
        }
    }
}
