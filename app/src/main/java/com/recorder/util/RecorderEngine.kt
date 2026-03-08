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
import kotlin.compareTo
import kotlin.math.abs
import kotlin.times

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
    @Volatile private var isScrubbing = false // Prevents playback thread from stomping seek position
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
            // May want to also provide option for user to use MediaRecorder.AudioSource.UNPROCESSED
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

        // If position is at or past the end, reset to beginning
        val totalDurationMs = (audioBytes.size / (sampleRate * 2f) * 1000).toLong()
        if (pausedPlaybackPosition >= totalDurationMs - 500) {
            pausedPlaybackPosition = 0L
            timestamp.value = 0L
            playbackPosition.value = PlaybackPosition(currentIndex = 0)
        }

        val amplitudes = if (amplitudeList.isNotEmpty()) {
            amplitudeList.toList()
        } else {
            extractAmplitudesFromAudio(audioBytes, sampleRate, 1, 16)
        }

        val startPositionIndex =
            ((pausedPlaybackPosition.toFloat() / totalDurationMs) * amplitudes.size).toInt()
                .coerceIn(0, amplitudes.size - 1)

        waveformData.value = WaveformData(amplitudes, maxAmplitude, currentPosition = startPositionIndex)

        // Wait for any previous playback thread to fully exit and release the AudioTrack.
        // The old thread must see PAUSED (set by onScrubStart) so its loop condition exits.
        // Only then flip to PLAYBACK and start the new thread.
        playbackThread?.join(500)

        recordingState.value = RecordingState.PLAYBACK

        playbackThread = Thread {
            try {
                println("PLAYBACK: Starting from ${pausedPlaybackPosition}ms (${audioBytes.size} bytes)")

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                // MODE_STATIC: write all audio once, then seek freely with setPlaybackHeadPosition
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(audioBytes.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                // Write entire file upfront — non-blocking with MODE_STATIC
                audioTrack?.write(audioBytes, 0, audioBytes.size)

                audioTrack?.playbackParams = PlaybackParams().apply {
                    this.speed = playbackSpeed.value
                }

                // Seek to the correct start frame
                val startFrame = ((pausedPlaybackPosition / 1000f) * sampleRate).toInt()
                    .coerceIn(0, audioBytes.size / 2)
                audioTrack?.setPlaybackHeadPosition(startFrame)

                audioTrack?.play()
                println("PLAYBACK: play() called from frame $startFrame (${pausedPlaybackPosition}ms)")

                val totalFrames = audioBytes.size / 2
                var startPositionMs = pausedPlaybackPosition
                var lastTick = System.currentTimeMillis()
                var elapsedMs = 0f

                while (recordingState.value == RecordingState.PLAYBACK) {
                    val now = System.currentTimeMillis()
                    elapsedMs += (now - lastTick) * playbackSpeed.value
                    lastTick = now

                    val posMs = startPositionMs + elapsedMs.toLong()
                    val posFrame = ((posMs / 1000f) * sampleRate).toInt()

                    // Detect natural end
                    if (posFrame >= totalFrames) {
                        println("PLAYBACK: Reached end of audio")
                        audioTrack?.stop()

                        if (repeatPlaybackEnabled.value) {
                            pausedPlaybackPosition = 0L
                            timestamp.value = 0L
                            playbackPosition.value = PlaybackPosition(currentIndex = 0)
                            audioTrack?.setPlaybackHeadPosition(0)
                            audioTrack?.play()
                            startPositionMs = 0L
                            elapsedMs = 0f
                            lastTick = System.currentTimeMillis()
                            continue
                        } else {
                            recordingState.value = RecordingState.PAUSED
                            // Keep position at the end — don't reset to 0
                            // playBackCurrentStream() will reset if user presses play from the end
                            println("PLAYBACK: Finished naturally")
                            break
                        }
                    }

                    if (!isScrubbing) {
                        pausedPlaybackPosition = posMs
                        timestamp.value = posMs

                        val currentPosIndex = ((posMs.toFloat() / totalDurationMs) * amplitudes.size)
                            .toInt().coerceIn(0, amplitudes.size - 1)
                        playbackPosition.value = PlaybackPosition(currentIndex = currentPosIndex)
                    }

                    Thread.sleep(10)
                }

                if (recordingState.value != RecordingState.PLAYBACK) {
                    println("PLAYBACK: Interrupted at ${pausedPlaybackPosition}ms")
                }

            } catch (e: Exception) {
                println("PLAYBACK ERROR: ${e.message}")
                e.printStackTrace()
                recordingState.value = RecordingState.PAUSED
            } finally {
                audioTrack?.release()
                audioTrack = null
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
        // Only valid while scrubbing (already paused by onScrubStart)
        if (!isScrubbing) return

        val amplitudes = waveformData.value.amplitudes.ifEmpty {
            // During recording-pause, waveformData is never populated — amplitudeList has everything
            amplitudeList.toList()
        }
        if (amplitudes.isEmpty()) return

        val clampedIndex = targetIndex.coerceIn(0, amplitudes.size - 1)
        val totalDurationMs = ((recordedData.size() / (sampleRate * 2f)) * 1000).toLong()
        val newPositionMs = if (amplitudes.size <= 1 || totalDurationMs <= 0L) {
            0L
        } else {
            ((clampedIndex.toFloat() / (amplitudes.size - 1)) * totalDurationMs).toLong()
        }

        // AudioTrack is already paused by onScrubStart — just reposition the head
        val targetFrame = ((newPositionMs / 1000f) * sampleRate).toInt()
            .coerceIn(0, recordedData.size() / 2)
        audioTrack?.setPlaybackHeadPosition(targetFrame)

        pausedPlaybackPosition = newPositionMs
        timestamp.value = newPositionMs
        // Always emit the full waveform with the new position so ViewModel collectors
        // that gate on isNotEmpty() will accept the update
        waveformData.value = WaveformData(
            amplitudes = amplitudes,
            maxAmplitude = maxAmplitude,
            currentPosition = clampedIndex
        )
        playbackPosition.value = PlaybackPosition(currentIndex = clampedIndex)
    }

    fun onScrubStart() {
        val wasPlaying = recordingState.value == RecordingState.PLAYBACK
        if (wasPlaying) {
            recordingState.value = RecordingState.PAUSED
            audioTrack?.pause()
        }
        isScrubbing = true
    }

    fun onScrubEnd() {
        isScrubbing = false

        println("SCRUB: onScrubEnd() at position ${pausedPlaybackPosition}ms")
    }
}