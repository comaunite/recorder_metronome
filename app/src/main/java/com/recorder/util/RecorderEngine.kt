package com.recorder.util

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.PlaybackParams
import androidx.annotation.RequiresPermission
import com.recorder.data.ParsedAudioData
import com.recorder.data.PlaybackPosition
import com.recorder.data.WaveformData
import com.recorder.data.WaveformUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
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
    private val repeatPlaybackEnabled = MutableStateFlow(false)
    val repeatPlaybackEnabledFlow: StateFlow<Boolean> = repeatPlaybackEnabled.asStateFlow()

    private val playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeedFlow: StateFlow<Float> = playbackSpeed.asStateFlow()

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

    fun toggleRepeatPlayback() {
        repeatPlaybackEnabled.value = !repeatPlaybackEnabled.value
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed.value = speed
        if (recordingState.value == RecordingState.PLAYBACK) {
            try {
                audioTrack?.playbackParams = PlaybackParams().apply {
                    this.speed = speed
                }
            } catch (e: Exception) {
                println("PLAYBACK: Failed to update speed: ${e.message}")
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
            // Use consistent waveform resolution (20 bars per second)
            extractAmplitudesFromAudio(audioBytes, sampleRate, 1, 16)
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

                while (recordingState.value == RecordingState.PLAYBACK) {
                    val startPositionBytes = ((pausedPlaybackPosition / 1000f) * sampleRate * 2).toInt()
                        .coerceIn(0, audioBytes.size)

                    audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(attributes)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(audioBytes.size)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    println("PLAYBACK: AudioTrack state=${audioTrack?.state}, playState=${audioTrack?.playState}")
                    println("PLAYBACK: Buffer size=${audioBytes.size}, Sample rate=$sampleRate")

                    audioTrack?.playbackParams = PlaybackParams().apply {
                        this.speed = playbackSpeed.value
                    }

                    val bytesToWrite = audioBytes.size - startPositionBytes
                    var finishedNaturally = false

                    if (bytesToWrite > 0) {
                        println("PLAYBACK: Writing $bytesToWrite bytes from position $startPositionBytes...")
                        val written =
                            audioTrack?.write(audioBytes, startPositionBytes, bytesToWrite) ?: 0
                        println("PLAYBACK: Written $written bytes")

                        audioTrack?.play()
                        println("PLAYBACK: Play called, playState=${audioTrack?.playState}")

                        val durationMs = (bytesToWrite.toFloat() / (sampleRate * 2)) * 1000
                        var lastProgressUpdate = System.currentTimeMillis()
                        var playTimeMs = 0f

                        while (recordingState.value == RecordingState.PLAYBACK) {
                            val now = System.currentTimeMillis()
                            val delta = now - lastProgressUpdate
                            lastProgressUpdate = now
                            
                            // Track how much audio (at 1x speed) has been played
                            playTimeMs += delta * playbackSpeed.value
                            
                            pausedPlaybackPosition = ((startPositionBytes / (sampleRate * 2f) * 1000).toLong() + playTimeMs).toLong()
                            timestamp.value = pausedPlaybackPosition

                            // Emit ONLY the current position index, not the entire waveform
                            val currentPosIndex =
                                ((pausedPlaybackPosition.toFloat() / totalDurationMs) * amplitudes.size).toInt()
                                    .coerceIn(0, amplitudes.size - 1)

                            playbackPosition.value = PlaybackPosition(currentIndex = currentPosIndex)

                            if (playTimeMs >= durationMs) {
                                println("PLAYBACK: Reached end of audio")
                                finishedNaturally = true
                                break
                            }

                            Thread.sleep(10) // ~100Hz update for smoother bar tracking
                        }

                        audioTrack?.stop()
                        println("PLAYBACK: Stopped")
                    } else {
                        println("PLAYBACK: No bytes to write from position $startPositionBytes")
                        finishedNaturally = true
                    }

                    audioTrack?.release()
                    audioTrack = null

                    if (recordingState.value != RecordingState.PLAYBACK) {
                        println("PLAYBACK: Interrupted at ${pausedPlaybackPosition}ms")
                        break
                    }

                    if (finishedNaturally && repeatPlaybackEnabled.value) {
                        pausedPlaybackPosition = 0L
                        timestamp.value = 0L
                        playbackPosition.value = PlaybackPosition(currentIndex = 0)
                        continue
                    }

                    if (finishedNaturally) {
                        recordingState.value = RecordingState.PAUSED
                        pausedPlaybackPosition = 0L
                        timestamp.value = 0L
                        println("PLAYBACK: Finished, reset position")
                    }

                    break
                }
            } catch (e: Exception) {
                println("PLAYBACK ERROR: ${e.message}")
                e.printStackTrace()
                recordingState.value = RecordingState.PAUSED
            }
        }
        playbackThread?.start()
    }

    /**
     * Update waveform data and emit newly added values
     */
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

    /**
     * Extract amplitudes from audio data with standard waveform resolution
     * Calculates duration based on audio parameters and generates amplitude bars
     * at a consistent rate of 20 bars per second (1 bar every 50ms)
     */
    private fun extractAmplitudesFromAudio(
        audioData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): List<Float> {
        val durationSeconds = (audioData.size / (sampleRate * channels * (bitsPerSample / 8))).toFloat()
        val targetBarCount = (durationSeconds * (1000 / waveformResolutionInMs)).toInt().coerceAtLeast(1)
        return extractAmplitudes(audioData, sampleCount = targetBarCount)
    }

    /**
     * Extract amplitudes from audio data with standard waveform resolution
     */
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
        playbackSpeed.value = 1.0f

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
     * Load audio data from parsed audio data
     */
    fun loadRecordingForPlayback(data: ParsedAudioData) {
        try {
            // Extract amplitudes using standard waveform resolution
            val amplitudes = extractAmplitudesFromAudio(
                data.audioData,
                data.sampleRate,
                data.channels,
                data.bitsPerSample
            )

            // Reset state
            recordedData.reset()
            recordedData.write(data.audioData)
            amplitudeList.clear()
            amplitudeList.addAll(amplitudes)
            pausedPlaybackPosition = 0L
            totalProcessedBytes = data.audioData.size.toLong()
            lastWaveformProcessedBytes = data.audioData.size.toLong()
            recordingState.value = RecordingState.PAUSED
            timestamp.value = 0L
            playbackSpeed.value = 1.0f

            // Emit waveform data
            waveformData.value = WaveformData(
                amplitudes = amplitudes,
                maxAmplitude = maxAmplitude,
                currentPosition = 0
            )

            println("LOAD: Successfully loaded ${data.audioData.size} bytes - SR:${data.sampleRate}Hz CH:${data.channels} BPS:${data.bitsPerSample}bit")
        } catch (e: Exception) {
            println("LOAD ERROR: ${e.message}")
            e.printStackTrace()
        }
    }

    fun seekToWaveformIndex(targetIndex: Int) {
        val state = recordingState.value
        if (state != RecordingState.PAUSED && state != RecordingState.PLAYBACK) {
            return
        }

        val amplitudes = waveformData.value.amplitudes
        if (amplitudes.isEmpty()) {
            return
        }

        val clampedIndex = targetIndex.coerceIn(0, amplitudes.size - 1)
        val totalDurationMs = ((recordedData.size() / (sampleRate * 2f)) * 1000).toLong()
        val newPositionMs = if (amplitudes.size <= 1 || totalDurationMs <= 0L) {
            0L
        } else {
            ((clampedIndex.toFloat() / (amplitudes.size - 1)) * totalDurationMs).toLong()
        }

        val wasPlaying = state == RecordingState.PLAYBACK
        if (wasPlaying) {
            pause()
        }

        pausedPlaybackPosition = newPositionMs
        timestamp.value = newPositionMs
        waveformData.value = waveformData.value.copy(currentPosition = clampedIndex)
        playbackPosition.value = PlaybackPosition(currentIndex = clampedIndex)

        if (wasPlaying) {
            playBackCurrentStream()
        }
    }
}