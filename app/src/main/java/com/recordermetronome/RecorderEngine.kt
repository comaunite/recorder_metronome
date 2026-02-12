package com.recordermetronome

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

enum class RecordingState {
    IDLE, RECORDING, PAUSED, PLAYBACK
}

class RecorderEngine {
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val recordedData = ByteArrayOutputStream()

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startOrResumeRecording() {
        if (_state.value == RecordingState.RECORDING) return

        // If IDLE (new recording), clear the buffer
        if (_state.value == RecordingState.IDLE) {
            recordedData.reset()
        }

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        recorder?.startRecording()
        _state.value = RecordingState.RECORDING

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (_state.value == RecordingState.RECORDING) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    recordedData.write(buffer, 0, read)
                } else if (read < 0) {
                    println("RECORDING ERROR: $read")
                }
            }
            cleanupRecorder()
        }.also { it.start() }
    }

    fun pauseRecording() {
        if (_state.value == RecordingState.RECORDING) {
            _state.value = RecordingState.PAUSED
        }
    }

    fun pausePlayback() {
        if (_state.value == RecordingState.PLAYBACK) {
            _state.value = RecordingState.PAUSED
        }
    }

    fun playBackCurrentStream() {
        // Only allow playback if we are paused
        if (_state.value != RecordingState.PAUSED) return

        val audioBytes = recordedData.toByteArray()

        if (audioBytes.isEmpty()) {
            println("PLAYBACK: No data to play")
            return
        }

        _state.value = RecordingState.PLAYBACK

        Thread {
            try {
                println("PLAYBACK: Starting playback of ${audioBytes.size} bytes")

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(audioBytes.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(audioBytes, 0, audioBytes.size)

                // Remove this later
                audioTrack.setVolume(AudioTrack.getMaxVolume())

                audioTrack.play()

                val durationMs = (audioBytes.size.toFloat() / (sampleRate * 2)) * 1000

                println("PLAYBACK: Sleeping for ${durationMs.toLong() + 500}ms")
                Thread.sleep(durationMs.toLong() + 500)

                audioTrack.stop()
                audioTrack.release()
                println("PLAYBACK: Finished")
            } catch (e: Exception) {
                println("PLAYBACK ERROR: ${e.message}")
            } finally {
                _state.value = RecordingState.PAUSED
            }
        }.start()
    }

    fun stopAndFinalize(onSave: (ByteArray) -> Unit) {
        val wasRecording = _state.value == RecordingState.RECORDING
        _state.value = RecordingState.IDLE

        // Give the thread a moment to exit and cleanup
        recordingThread?.join(500)

        val data = recordedData.toByteArray()
        recordedData.reset()

        if (data.isNotEmpty()) {
            onSave(data)
        }
    }

    private fun cleanupRecorder() {
        recorder?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        recorder = null
    }
}