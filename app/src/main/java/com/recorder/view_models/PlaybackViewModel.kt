package com.recorder.view_models

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recorder.data.RecorderFile
import com.recorder.data.WaveformData
import com.recorder.services.PlaybackService
import com.recorder.services.RecorderEngine
import com.recorder.services.FileService
import com.recorder.services.RecordingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = RecorderEngine()
    val recordingStateFlow = engine.recordingStateFlow

    private val _accumulatedWaveformData = MutableStateFlow(WaveformData())
    val accumulatedWaveformData = _accumulatedWaveformData.asStateFlow()

    val timestamp = engine.timestampStateFlow
    val repeatPlaybackEnabled = engine.repeatPlaybackEnabledFlow
    val playbackSpeed = engine.playbackSpeedFlow

    private val _currentRecording = MutableStateFlow(RecorderFile("", "", 0, 0))
    val currentRecording = _currentRecording.asStateFlow()

    private val _existingRecordings = MutableStateFlow<List<RecorderFile>>(emptyList())
    val existingRecordings = _existingRecordings.asStateFlow()

    private var wasPlayingBeforeScrub = false

    init {
        // Listen to full waveform updates (at load or playback start)
        viewModelScope.launch {
            engine.waveformDataStateFlow.collect { waveformData ->
                if (waveformData.amplitudes.isNotEmpty()) {
                    _accumulatedWaveformData.value = waveformData
                }
            }
        }

        // Listen to playback position updates to track the red bar position
        viewModelScope.launch {
            engine.playbackPositionStateFlow.collect { position ->
                // Only PLAYBACK — never PAUSED. A dying/paused thread can emit stale position=0.
                if (recordingStateFlow.value == RecordingState.PLAYBACK) {
                    val current = _accumulatedWaveformData.value
                    _accumulatedWaveformData.value = current.copy(
                        currentPosition = position.currentIndex
                    )
                }
            }
        }

        // Drive the foreground-service notification from state + timestamp changes.
        // The timestamp emits ~every 50 ms during playback; we only push a notification
        // update when the displayed second changes, or when isPlaying/recording changes.
        viewModelScope.launch {
            var lastNotifSec = -1L
            var lastIsPlaying = false

            combine(recordingStateFlow, timestamp, _currentRecording) { state, ts, rec ->
                Triple(state, ts, rec)
            }.collect { (state, ts, rec) ->
                when (state) {
                    RecordingState.PLAYBACK, RecordingState.PAUSED -> {
                        val isPlaying = state == RecordingState.PLAYBACK
                        val sec = ts / 1000
                        if (isPlaying != lastIsPlaying || sec != lastNotifSec) {
                            lastIsPlaying = isPlaying
                            lastNotifSec = sec
                            viewModelScope.launch(Dispatchers.IO) {
                                pushServiceUpdate(rec.name, isPlaying, ts, rec.durationMs)
                            }
                        }
                    }
                    RecordingState.IDLE -> {
                        if (lastIsPlaying || lastNotifSec >= 0) {
                            lastIsPlaying = false
                            lastNotifSec = -1L
                            viewModelScope.launch(Dispatchers.IO) { stopNotificationService() }
                        }
                    }
                    else -> {}
                }
            }
        }

        // React to play/pause tapped directly on the notification
        viewModelScope.launch {
            PlaybackService.playPauseRequested.collect {
                when (recordingStateFlow.value) {
                    RecordingState.PLAYBACK -> onPausePlaybackTapped()
                    RecordingState.PAUSED,
                    RecordingState.IDLE    -> onPlaybackTapped()
                    else                   -> {}
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Service helpers
    // -------------------------------------------------------------------------

    private fun pushServiceUpdate(
        title: String, isPlaying: Boolean, positionMs: Long, durationMs: Long
    ) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, PlaybackService::class.java).apply {
            putExtra(PlaybackService.EXTRA_TITLE,       title)
            putExtra(PlaybackService.EXTRA_IS_PLAYING,  isPlaying)
            putExtra(PlaybackService.EXTRA_POSITION_MS, positionMs)
            putExtra(PlaybackService.EXTRA_DURATION_MS, durationMs)
        }
        ctx.startForegroundService(intent)
    }

    private fun stopNotificationService() {
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, PlaybackService::class.java))
    }

    // -------------------------------------------------------------------------
    // Existing public API (unchanged)
    // -------------------------------------------------------------------------

    fun initialize(
        context: Context,
        recording: RecorderFile,
        preLoadedRecordings: List<RecorderFile>?
    ) {
        _currentRecording.value = recording

        println("PLAYBACK_VM: Initializing with recording: ${recording.name} at ${recording.filePath}")
        try {
            val parsedAudio = FileService.readRecorderFile(recording.filePath)
            engine.loadRecordingForPlayback(parsedAudio)
        } catch (e: Exception) {
            println("PLAYBACK_VM: Error loading audio file: ${e.message}")
            e.printStackTrace()
        }
        println("PLAYBACK_VM: Audio file loading completed")

        if (preLoadedRecordings != null) {
            setExistingRecordings(preLoadedRecordings)
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                _existingRecordings.value = FileService.getRecorderFiles(context)
            }
        }
    }

    fun setExistingRecordings(recordings: List<RecorderFile>) {
        _existingRecordings.value = recordings
    }

    fun updateInMemoryCollections(oldRecording: RecorderFile, newName: String) {
        val updated = oldRecording.copy(
            name = newName,
            filePath = buildRenamedPath(oldRecording.filePath, newName)
        )
        _currentRecording.value = updated
        _existingRecordings.value = _existingRecordings.value.map { existing ->
            if (existing.filePath == oldRecording.filePath) {
                existing.copy(name = newName, filePath = updated.filePath)
            } else {
                existing
            }
        }
    }

    private fun buildRenamedPath(oldPath: String, newName: String): String {
        val oldFile = File(oldPath)
        val parentDir = oldFile.parentFile
        return File(parentDir, "$newName.wav").absolutePath
    }

    fun onPlaybackTapped() {
        viewModelScope.launch(Dispatchers.IO) { engine.playBackCurrentStream() }
    }

    fun onPausePlaybackTapped() = engine.pause()
    fun onRepeatToggleTapped()  = engine.toggleRepeatPlayback()
    fun onPlaybackSpeedTapped(speed: Float) = engine.setPlaybackSpeed(speed)
    fun onWaveformScrubbed(targetIndex: Int) = engine.seekToWaveformIndex(targetIndex)

    fun onScrubStart() {
        wasPlayingBeforeScrub = recordingStateFlow.value == RecordingState.PLAYBACK
        engine.onScrubStart()
    }

    fun onScrubEnd() {
        engine.onScrubEnd()
        if (wasPlayingBeforeScrub) {
            wasPlayingBeforeScrub = false
            viewModelScope.launch(Dispatchers.IO) { engine.playBackCurrentStream() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopNotificationService()
        engine.finalize { }
    }

    fun onReturnToFileExplorer(callback: () -> Unit) {
        stopNotificationService()
        engine.finalize { }
        callback()
    }
}
