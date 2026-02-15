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
    private var playbackThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private val recordedData = ByteArrayOutputStream()
    private var pausedPlaybackPosition = 0L // Remember position when paused
    private var globalMaxAmplitude = 1f // Track max amplitude across entire recording
    private val amplitudeList = mutableListOf<Float>() // Persistent amplitude list across pause/resume
    private var lastProcessedBytes = 0L // Track how much data we've already processed

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

        // If IDLE (new recording), clear the buffer and reset max amplitude
        if (_state.value == RecordingState.IDLE) {
            recordedData.reset()
            globalMaxAmplitude = 1f
            amplitudeList.clear()
            lastProcessedBytes = 0L
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
            val waveformUpdateInterval = 50L // Update every ~50ms

            while (_state.value == RecordingState.RECORDING) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    recordedData.write(buffer, 0, read)
                    totalBytes += read

                    // Update timestamp on every read
                    val millis = (totalBytes / bytesPerMs).toLong()
                    timestampListener?.invoke(millis)

                    // Update waveform only ~3 times per second (adds 1 bar each time)
                    val now = System.currentTimeMillis()
                    if (now - lastWaveformUpdate >= waveformUpdateInterval) {
                        // Extract amplitude from NEW data only (since last update)
                        val allData = recordedData.toByteArray()
                        val newDataStartByte = lastProcessedBytes.toInt().coerceAtLeast(0)

                        if (newDataStartByte < allData.size) {
                            val newDataBytes = allData.copyOfRange(newDataStartByte, allData.size)
                            // Extract 1 amplitude value from this chunk (333ms of audio)
                            val newAmplitudes = extractAmplitudes(newDataBytes, sampleCount = 1)

                            // Add new amplitude to the growing list
                            amplitudeList.addAll(newAmplitudes)

                            // Update global max amplitude if we found a higher value
                            val currentMaxAmp = newAmplitudes.maxOrNull() ?: 1f
                            if (currentMaxAmp > globalMaxAmplitude) {
                                globalMaxAmplitude = currentMaxAmp
                            }

                            lastProcessedBytes = allData.size.toLong()
                        }

                        // Use global max for consistent scaling
                        _waveformData.value = WaveformData(amplitudeList.toList(), globalMaxAmplitude, currentPosition = amplitudeList.size - 1)
                        println("WAVEFORM: Updated with ${amplitudeList.size} amplitudes (added at 3/sec), globalMax=$globalMaxAmplitude, pos=${amplitudeList.size - 1}")
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

            // Keep the existing amplitude list - don't regenerate
            // The amplitude list was built progressively during recording at 3 bars/sec
            // Just ensure we have the latest data processed
            val allData = recordedData.toByteArray()
            if (allData.isNotEmpty() && lastProcessedBytes < allData.size) {
                // Process any remaining data that wasn't caught in the last update
                val newDataStartByte = lastProcessedBytes.toInt().coerceAtLeast(0)
                val newDataBytes = allData.copyOfRange(newDataStartByte, allData.size)

                if (newDataBytes.isNotEmpty()) {
                    val newAmplitudes = extractAmplitudes(newDataBytes, sampleCount = 1)
                    amplitudeList.addAll(newAmplitudes)

                    val currentMaxAmp = newAmplitudes.maxOrNull() ?: 1f
                    if (currentMaxAmp > globalMaxAmplitude) {
                        globalMaxAmplitude = currentMaxAmp
                    }

                    lastProcessedBytes = allData.size.toLong()
                }
            }

            // Keep position at the end when paused
            if (amplitudeList.isNotEmpty()) {
                _waveformData.value = WaveformData(amplitudeList.toList(), globalMaxAmplitude, currentPosition = amplitudeList.size - 1)
                println("WAVEFORM PAUSED: ${amplitudeList.size} amplitudes, globalMax=$globalMaxAmplitude, pos=${amplitudeList.size - 1}")
            }
        }
    }

    fun pausePlayback() {
        if (_state.value == RecordingState.PLAYBACK) {
            _state.value = RecordingState.PAUSED
            // Stop the audio track
            audioTrack?.pause()
            audioTrack?.flush()
            // Timestamp is already preserved via pausedPlaybackPosition
            println("PLAYBACK: Paused at ${pausedPlaybackPosition}ms")
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

        // Use the amplitude list that was built during recording
        // This ensures consistency between recording and playback views
        val amplitudes = if (amplitudeList.isNotEmpty()) {
            amplitudeList.toList()
        } else {
            // Fallback: generate amplitudes if list is empty (shouldn't happen normally)
            val durationSeconds = (audioBytes.size / (sampleRate * 2f))
            val targetBarCount = (durationSeconds * 3).toInt().coerceAtLeast(1)
            extractAmplitudes(audioBytes, sampleCount = targetBarCount)
        }

        // Use the global max amplitude from recording for consistent scaling
        val maxAmp = globalMaxAmplitude

        // Calculate starting position based on pausedPlaybackPosition
        val totalDurationMs = (audioBytes.size / (sampleRate * 2f) * 1000).toLong()
        val startPositionIndex = ((pausedPlaybackPosition.toFloat() / totalDurationMs) * amplitudes.size).toInt()
            .coerceIn(0, amplitudes.size - 1)

        _waveformData.value = WaveformData(amplitudes, maxAmp, currentPosition = startPositionIndex)
        println("WAVEFORM PLAYBACK: Updated with ${amplitudes.size} amplitudes, globalMax=$maxAmp, startPos=$startPositionIndex")

        _state.value = RecordingState.PLAYBACK

        playbackThread = Thread {
            try {
                println("PLAYBACK: Starting playback of ${audioBytes.size} bytes from position ${pausedPlaybackPosition}ms")

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(audioBytes.size)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                println("PLAYBACK: AudioTrack state=${audioTrack?.state}, playState=${audioTrack?.playState}")
                println("PLAYBACK: Buffer size=${audioBytes.size}, Sample rate=$sampleRate")

                // Calculate start position in bytes
                val startPositionBytes = ((pausedPlaybackPosition / 1000f) * sampleRate * 2).toInt()
                    .coerceIn(0, audioBytes.size)

                // Write audio data
                val bytesToWrite = audioBytes.size - startPositionBytes
                if (bytesToWrite > 0) {
                    println("PLAYBACK: Writing $bytesToWrite bytes from position $startPositionBytes...")
                    val written = audioTrack?.write(audioBytes, startPositionBytes, bytesToWrite) ?: 0
                    println("PLAYBACK: Written $written bytes")

                    audioTrack?.setVolume(AudioTrack.getMaxVolume())
                    audioTrack?.play()
                    println("PLAYBACK: Play called, playState=${audioTrack?.playState}")

                    val durationMs = (bytesToWrite.toFloat() / (sampleRate * 2)) * 1000
                    val startTime = System.currentTimeMillis()

                    // Non-blocking update loop
                    while (_state.value == RecordingState.PLAYBACK) {
                        val elapsed = System.currentTimeMillis() - startTime
                        pausedPlaybackPosition = (startPositionBytes / (sampleRate * 2f) * 1000).toLong() + elapsed
                        timestampListener?.invoke(pausedPlaybackPosition)

                        // Update waveform position based on current playback position
                        val currentPosIndex = ((pausedPlaybackPosition.toFloat() / totalDurationMs) * amplitudes.size).toInt()
                            .coerceIn(0, amplitudes.size - 1)
                        _waveformData.value = WaveformData(amplitudes, globalMaxAmplitude, currentPosition = currentPosIndex)

                        if (elapsed >= durationMs) {
                            println("PLAYBACK: Reached end of audio")
                            break
                        }

                        Thread.sleep(16) // ~60Hz update
                    }

                    audioTrack?.stop()
                    println("PLAYBACK: Stopped")
                } else {
                    println("PLAYBACK: No bytes to write from position $startPositionBytes")
                }

                audioTrack?.release()
                audioTrack = null

                // If we finished naturally (not paused), reset position
                if (_state.value == RecordingState.PLAYBACK) {
                    _state.value = RecordingState.PAUSED
                    pausedPlaybackPosition = 0L
                    timestampListener?.invoke(0L)
                    println("PLAYBACK: Finished, reset position")
                } else {
                    println("PLAYBACK: Interrupted at ${pausedPlaybackPosition}ms")
                }
            } catch (e: Exception) {
                println("PLAYBACK ERROR: ${e.message}")
                e.printStackTrace()
                _state.value = RecordingState.PAUSED
            }
        }
        playbackThread?.start()
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

        // Cleanup playback if running
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        playbackThread?.join(500)
        pausedPlaybackPosition = 0L

        // Cleanup recording if running
        recordingThread?.join(500)

        val data = recordedData.toByteArray()
        recordedData.reset()
        globalMaxAmplitude = 1f // Reset for next recording
        amplitudeList.clear()
        lastProcessedBytes = 0L
        _waveformData.value = WaveformData() // Clear waveform
        timestampListener?.invoke(0L)

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