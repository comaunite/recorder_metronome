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
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private val recordedData = ByteArrayOutputStream()

    private var pausedPlaybackPosition = 0L // Remember the position when paused
    private val maxAmplitude = 10000f
    private val amplitudeList = mutableListOf<Float>() // Persistent amplitude list across pause/resume
    private var totalProcessedBytes = 0L

    private val recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingStateFlow: StateFlow<RecordingState> = recordingState.asStateFlow()

    private val waveformResolutionInMs = 50L // Update every ~50ms
    private var lastWaveformProcessedBytes = 0L // Track the last processed point
    private val waveformData = MutableStateFlow(WaveformData())
    val waveformDataStateFlow: StateFlow<WaveformData> = waveformData.asStateFlow()


    private val timestamp = MutableStateFlow(0L)
    val timestampStateFlow = timestamp.asStateFlow()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startOrResumeRecording() {
        if (recordingState.value == RecordingState.RECORDING) {
            println("RECORDING: Already recording, ignoring request")
            return
        }

        if (recordingState.value == RecordingState.IDLE) {
            println("RECORDING: Initializing new recording...")
            recordedData.reset()
            amplitudeList.clear()
            lastWaveformProcessedBytes = 0L
            totalProcessedBytes = 0L
        }

        pausedPlaybackPosition = 0L

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        recorder?.startRecording()
        recordingState.value = RecordingState.RECORDING

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            val bytesPerMs = (sampleRate * 2) / 1000f // 2 bytes per sample (16-bit mono)
            var lastWaveformUpdate = System.currentTimeMillis()

            println("RECORDING: Started recording")

            while (recordingState.value == RecordingState.RECORDING) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0

                if (read > 0) {
                    recordedData.write(buffer, 0, read)
                    totalProcessedBytes += read

                    timestamp.value = (totalProcessedBytes / bytesPerMs).toLong()

                    val now = System.currentTimeMillis()
                    if (now - lastWaveformUpdate >= waveformResolutionInMs) {
                        updateWaveform()
                        lastWaveformUpdate = now
                    }
                } else if (read < 0) {
                    println("RECORDING ERROR: $read")
                }
            }

            cleanupRecorder()

            println("RECORDING: Stopped recording")
        }.also { it.start() }
    }

    fun pauseRecording() {
        if (recordingState.value == RecordingState.RECORDING) {
            println("RECORDING: Pausing recording...")
            recordingState.value = RecordingState.PAUSED
        } else {
            println("RECORDING: Not recording, ignoring pause request")
        }
    }

    fun pausePlayback() {
        if (recordingState.value == RecordingState.PLAYBACK) {
            recordingState.value = RecordingState.PAUSED
            audioTrack?.pause()
            audioTrack?.flush()
            println("PLAYBACK: Paused at ${pausedPlaybackPosition}ms")
        } else {
            println("PLAYBACK: Not playing, ignoring pause request")
        }
    }

    fun playBackCurrentStream() {
        if (recordingState.value == RecordingState.RECORDING) {
            println("PLAYBACK: Recording is active, pausing recording...")
            pauseRecording()
        }

        if (recordingState.value != RecordingState.PAUSED) {
            println("PLAYBACK: Cannot playback from a non-paused state, ignoring request")
            return
        }

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
            // Fallback: generate amplitudes if the list is empty (shouldn't happen normally)
            val durationSeconds = (audioBytes.size / (sampleRate * 2f))
            val targetBarCount = (durationSeconds * 3).toInt().coerceAtLeast(1)
            extractAmplitudes(audioBytes, sampleCount = targetBarCount)
        }

        // Calculate a starting position based on pausedPlaybackPosition
        val totalDurationMs = (audioBytes.size / (sampleRate * 2f) * 1000).toLong()
        val startPositionIndex =
            ((pausedPlaybackPosition.toFloat() / totalDurationMs) * amplitudes.size).toInt()
                .coerceIn(0, amplitudes.size - 1)

        waveformData.value = WaveformData(amplitudes, maxAmplitude, currentPosition = startPositionIndex)

        recordingState.value = RecordingState.PLAYBACK

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
                    val written =
                        audioTrack?.write(audioBytes, startPositionBytes, bytesToWrite) ?: 0
                    println("PLAYBACK: Written $written bytes")

                    audioTrack?.play()
                    println("PLAYBACK: Play called, playState=${audioTrack?.playState}")

                    val durationMs = (bytesToWrite.toFloat() / (sampleRate * 2)) * 1000
                    val startTime = System.currentTimeMillis()

                    while (recordingState.value == RecordingState.PLAYBACK) {
                        val elapsed = System.currentTimeMillis() - startTime
                        pausedPlaybackPosition =
                            (startPositionBytes / (sampleRate * 2f) * 1000).toLong() + elapsed

                        timestamp.value = pausedPlaybackPosition

                        // Update waveform position based on the current playback position
                        val currentPosIndex =
                            ((pausedPlaybackPosition.toFloat() / totalDurationMs) * amplitudes.size).toInt()
                                .coerceIn(0, amplitudes.size - 1)

                        // TODO: Same as below, try to emit only the current position,
                        //  instead of re-emitting the whole list just to update position
                        waveformData.value = WaveformData(
                            amplitudes,
                            maxAmplitude,
                            currentPosition = currentPosIndex
                        )

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
                if (recordingState.value == RecordingState.PLAYBACK) {
                    recordingState.value = RecordingState.PAUSED
                    pausedPlaybackPosition = 0L
                    timestamp.value = 0L
                    println("PLAYBACK: Finished, reset position")
                } else {
                    println("PLAYBACK: Interrupted at ${pausedPlaybackPosition}ms")
                }
            } catch (e: Exception) {
                println("PLAYBACK ERROR: ${e.message}")
                e.printStackTrace()
                recordingState.value = RecordingState.PAUSED
            }
        }
        playbackThread?.start()
    }


    // TODO: Look into emitting only newly added values to amplitudes,
    //  and let ViewData accumulate those into complete waveform
    private fun updateWaveform() {
        // Extract amplitude from NEW data only (since last update)
        val allData = recordedData.toByteArray()
        val newDataStartByte = lastWaveformProcessedBytes.toInt().coerceAtLeast(0)

        if (newDataStartByte < allData.size) {
            val newDataBytes = allData.copyOfRange(newDataStartByte, allData.size)
            // Extract 1 amplitude value from this chunk
            val newAmplitudes = extractAmplitudes(newDataBytes, sampleCount = 1)

            // Add new amplitude to the growing list
            amplitudeList.addAll(newAmplitudes)

            lastWaveformProcessedBytes = allData.size.toLong()
        }

        waveformData.value = WaveformData(
            amplitudeList.toList(),
            maxAmplitude,
            currentPosition = amplitudeList.size - 1
        )
    }

    private fun extractAmplitudes(audioBytes: ByteArray, sampleCount: Int = 128): List<Float> {
        val amplitudes = mutableListOf<Float>()
        val bytesPerSample = 2 // 16-bit audio
        val samplesPerBucket = (audioBytes.size / bytesPerSample) / sampleCount.coerceAtLeast(1)

        for (i in 0 until sampleCount) {
            val startByte = i * samplesPerBucket * bytesPerSample
            val endByte =
                ((i + 1) * samplesPerBucket * bytesPerSample).coerceAtMost(audioBytes.size)

            var sum = 0f
            for (j in startByte until endByte step bytesPerSample) {
                if (j + 1 < audioBytes.size) {
                    val sample =
                        ((audioBytes[j + 1].toInt() shl 8) or (audioBytes[j].toInt() and 0xFF))
                    sum += kotlin.math.abs(sample)
                }
            }

            amplitudes.add(sum / ((endByte - startByte) / bytesPerSample).coerceAtLeast(1))
        }

        return amplitudes
    }

    fun stopAndFinalize(onSave: (ByteArray) -> Unit) {
        recordingState.value = RecordingState.IDLE

        // Cleanup playback if running
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        playbackThread?.join(500)
        pausedPlaybackPosition = 0L

        recordingThread?.join(500)
        val data = recordedData.toByteArray()

        // Cleanup recording if running
        recordedData.reset()
        amplitudeList.clear()
        lastWaveformProcessedBytes = 0L
        totalProcessedBytes = 0L
        waveformData.value = WaveformData() // Clear waveform
        timestamp.value = 0L

        cleanupRecorder()

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