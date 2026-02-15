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

    private val _waveformData = MutableStateFlow(WaveformData())
    val waveformData: StateFlow<WaveformData> = _waveformData.asStateFlow()

    private var timestampListener: ((Long) -> Unit)? = null

    fun setTimestampListener(listener: ((Long) -> Unit)?) {
        timestampListener = listener
    }

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
            var totalBytes = 0L
            val bytesPerMs = (sampleRate * 2) / 1000f // 2 bytes per sample (16-bit mono)
            var lastWaveformUpdate = System.currentTimeMillis()
            val waveformUpdateInterval = 333L // Update every ~333ms (3 times per second)

            while (_state.value == RecordingState.RECORDING) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    recordedData.write(buffer, 0, read)
                    totalBytes += read

                    // Update timestamp on every read
                    val millis = (totalBytes / bytesPerMs).toLong()
                    timestampListener?.invoke(millis)

                    // Update waveform only ~3 times per second
                    val now = System.currentTimeMillis()
                    if (now - lastWaveformUpdate >= waveformUpdateInterval) {
                        val amplitudes = extractAmplitudes(buffer.copyOf(read), sampleCount = 200)
                        val maxAmp = amplitudes.maxOrNull() ?: 1f
                        _waveformData.value = WaveformData(amplitudes, maxAmp)
                        println("WAVEFORM: Updated with ${amplitudes.size} amplitudes, max=$maxAmp")
                        lastWaveformUpdate = now
                    }
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
        if (_state.value == RecordingState.RECORDING) pauseRecording()

        if (_state.value != RecordingState.PAUSED) return

        val audioBytes = recordedData.toByteArray()

        if (audioBytes.isEmpty()) {
            println("PLAYBACK: No data to play")
            return
        }

        // Update waveform for playback - show entire recording with more bars
        val amplitudes = extractAmplitudes(audioBytes, sampleCount = 200)
        val maxAmp = amplitudes.maxOrNull() ?: 1f
        _waveformData.value = WaveformData(amplitudes, maxAmp)
        println("WAVEFORM PLAYBACK: Updated with ${amplitudes.size} amplitudes, max=$maxAmp")

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
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                println("PLAYBACK: AudioTrack state=${audioTrack.state}, playState=${audioTrack.playState}")
                println("PLAYBACK: Buffer size=${audioBytes.size}, Sample rate=$sampleRate")
                println("PLAYBACK: Writing bytes...")

                val written = audioTrack.write(audioBytes, 0, audioBytes.size)
                println("PLAYBACK: Written $written bytes (expected ${audioBytes.size})")

                audioTrack.setVolume(AudioTrack.getMaxVolume())
                println("PLAYBACK: Volume set to ${AudioTrack.getMaxVolume()}")

                audioTrack.play()
                println("PLAYBACK: Play called, playState=${audioTrack.playState}")

                val durationMs = (audioBytes.size.toFloat() / (sampleRate * 2)) * 1000
                val startTime = System.currentTimeMillis()
                var elapsed: Long
                do {
                    elapsed = System.currentTimeMillis() - startTime
                    timestampListener?.invoke(elapsed)
                    Thread.sleep(16) // ~60Hz update
                } while (elapsed < durationMs)

                audioTrack.stop()
                audioTrack.release()
                println("PLAYBACK: Finished")
            } catch (e: Exception) {
                println("PLAYBACK ERROR: ${e.message}")
            } finally {
                _state.value = RecordingState.PAUSED
                timestampListener?.invoke(0L)
            }
        }.start()
    }

    fun extractAmplitudes(audioBytes: ByteArray, sampleCount: Int = 128): List<Float> {
        val amplitudes = mutableListOf<Float>()
        val bytesPerSample = 2 // 16-bit audio
        val samplesPerBucket = (audioBytes.size / bytesPerSample) / sampleCount.coerceAtLeast(1)

        for (i in 0 until sampleCount) {
            val startByte = i * samplesPerBucket * bytesPerSample
            val endByte = ((i + 1) * samplesPerBucket * bytesPerSample).coerceAtMost(audioBytes.size)

            var sum = 0f
            for (j in startByte until endByte step bytesPerSample) {
                if (j + 1 < audioBytes.size) {
                    val sample = ((audioBytes[j + 1].toInt() shl 8) or (audioBytes[j].toInt() and 0xFF))
                    sum += kotlin.math.abs(sample)
                }
            }

            amplitudes.add(sum / ((endByte - startByte) / bytesPerSample).coerceAtLeast(1))
        }

        return amplitudes
    }

    fun stopAndFinalize(onSave: (ByteArray) -> Unit) {
        _state.value = RecordingState.IDLE

        // Give the thread a moment to exit and cleanup
        recordingThread?.join(500)

        val data = recordedData.toByteArray()
        recordedData.reset()
        _waveformData.value = WaveformData() // Clear waveform

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