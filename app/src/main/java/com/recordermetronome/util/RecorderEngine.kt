package com.recordermetronome.util

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.recordermetronome.data.PlaybackPosition
import com.recordermetronome.data.WaveformData
import com.recordermetronome.data.WaveformUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs

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
    private val waveformUpdate = MutableStateFlow(WaveformUpdate())
    val waveformUpdateStateFlow: StateFlow<WaveformUpdate> = waveformUpdate.asStateFlow()
    private val playbackPosition = MutableStateFlow(PlaybackPosition())
    val playbackPositionStateFlow: StateFlow<PlaybackPosition> = playbackPosition.asStateFlow()

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
            waveformUpdate.value = WaveformUpdate()
            waveformData.value = WaveformData()
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

    fun pause() {
        when (recordingState.value) {
            RecordingState.RECORDING -> {
                println("RECORDING: Pausing recording...")
                recordingState.value = RecordingState.PAUSED
            }
            RecordingState.PLAYBACK -> {
                recordingState.value = RecordingState.PAUSED
                audioTrack?.pause()
                audioTrack?.flush()
                println("PLAYBACK: Paused at ${pausedPlaybackPosition}ms")
            }
            else -> {
                println("ENGINE: Not recording, ignoring pause request")
            }
        }
    }

    fun playBackCurrentStream() {
        if (recordingState.value == RecordingState.RECORDING) {
            println("PLAYBACK: Recording is active, pausing recording...")
            pause()
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

        // Emit complete waveform data ONCE at the start of playback
        waveformData.value =
            WaveformData(amplitudes, maxAmplitude, currentPosition = startPositionIndex)

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

                        // Emit ONLY the current position index, not the entire waveform
                        val currentPosIndex =
                            ((pausedPlaybackPosition.toFloat() / totalDurationMs) * amplitudes.size).toInt()
                                .coerceIn(0, amplitudes.size - 1)

                        playbackPosition.value = PlaybackPosition(currentIndex = currentPosIndex)

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


    // Emit only newly added amplitude values during recording
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

            // Emit only the NEW amplitudes to minimize data transfer
            waveformUpdate.value = WaveformUpdate(
                newAmplitudes = newAmplitudes,
                maxAmplitude = maxAmplitude
            )
        }
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
                    sum += abs(sample)
                }
            }

            amplitudes.add(sum / ((endByte - startByte) / bytesPerSample).coerceAtLeast(1))
        }

        return amplitudes
    }

    fun finalize(onSave: (ByteArray) -> Unit) {
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

    /**
     * Load audio data from a WAV file for playback
     */
    fun loadAudioFile(filePath: String) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                println("LOAD ERROR: File does not exist: $filePath")
                return
            }

            val audioBytes = file.readBytes()
            if (audioBytes.isEmpty()) {
                println("LOAD ERROR: File is empty")
                return
            }

            var audioData: ByteArray = ByteArray(0)
            var parsedSampleRate = sampleRate
            var parsedChannels = 1
            var parsedBitsPerSample = 16
            var hasValidHeader = false

            // Try to parse WAV header
            if (audioBytes.size > 44) {
                val buffer = ByteBuffer.wrap(audioBytes)
                buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)

                val sampleRate = buffer.getInt(24)
                val channels = buffer.getShort(22).toInt()
                val bitsPerSample = buffer.getShort(34).toInt()

                if (sampleRate > 0 && channels > 0 && bitsPerSample > 0) {
                    // Valid header found
                    audioData = audioBytes.copyOfRange(44, audioBytes.size)
                    parsedSampleRate = sampleRate
                    parsedChannels = channels
                    parsedBitsPerSample = bitsPerSample
                    hasValidHeader = true
                    println("LOAD: Found valid WAV header - SR:$sampleRate CH:$channels BPS:$bitsPerSample")
                }
            }

            // If no valid header found, treat entire file as raw PCM data
            if (!hasValidHeader) {
                println("LOAD: No valid WAV header found, treating entire file as raw PCM data")
                audioData = audioBytes
            }

            // Extract amplitudes
            val durationSeconds = (audioData.size / (parsedSampleRate * parsedChannels * (parsedBitsPerSample / 8))).toFloat()
            val targetBarCount = (durationSeconds * 3).toInt().coerceAtLeast(1)
            val amplitudes = extractAmplitudes(audioData, sampleCount = targetBarCount)

            // Reset state
            recordedData.reset()
            recordedData.write(audioData)
            amplitudeList.clear()
            amplitudeList.addAll(amplitudes)
            pausedPlaybackPosition = 0L
            totalProcessedBytes = audioData.size.toLong()
            lastWaveformProcessedBytes = audioData.size.toLong()
            recordingState.value = RecordingState.PAUSED
            timestamp.value = 0L

            // Emit waveform data
            waveformData.value = WaveformData(
                amplitudes = amplitudes,
                maxAmplitude = maxAmplitude,
                currentPosition = 0
            )

            println("LOAD: Successfully loaded ${audioData.size} bytes from $filePath")
        } catch (e: Exception) {
            println("LOAD ERROR: ${e.message}")
            e.printStackTrace()
        }
    }
}