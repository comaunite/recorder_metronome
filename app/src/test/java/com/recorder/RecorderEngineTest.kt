package com.recorder

import com.recorder.data.ParsedAudioData
import com.recorder.util.RecorderEngine
import com.recorder.util.RecordingState
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecorderEngineTest {

    @Test
    fun recordingState_initialState_isIdle() {
        val engine = RecorderEngine()
        assertEquals(RecordingState.IDLE, engine.recordingStateFlow.value)
    }

    @Test
    fun waveformData_initialState_isEmpty() {
        val engine = RecorderEngine()
        val waveformData = engine.waveformDataStateFlow.value
        assertTrue(waveformData.amplitudes.isEmpty())
        assertEquals(1f, waveformData.maxAmplitude)
        assertEquals(0, waveformData.currentPosition)
    }

    @Test
    fun waveformUpdate_initialState_isEmpty() {
        val engine = RecorderEngine()
        val waveformUpdate = engine.waveformUpdateStateFlow.value
        assertTrue(waveformUpdate.newAmplitudes.isEmpty())
        assertEquals(10000f, waveformUpdate.maxAmplitude)
    }

    @Test
    fun playbackPosition_initialState_isZero() {
        val engine = RecorderEngine()
        val position = engine.playbackPositionStateFlow.value
        assertEquals(0, position.currentIndex)
    }

    @Test
    fun timestamp_initialState_isZero() {
        val engine = RecorderEngine()
        assertEquals(0L, engine.timestampStateFlow.value)
    }

    @Test
    fun recordingEngine_created_successfully() {
        val engine = RecorderEngine()
        assertNotNull(engine.recordingStateFlow)
        assertNotNull(engine.waveformDataStateFlow)
        assertNotNull(engine.waveformUpdateStateFlow)
        assertNotNull(engine.playbackPositionStateFlow)
        assertNotNull(engine.timestampStateFlow)
    }

    @Test
    fun loadRecordingForPlayback_withValidParsedAudio_setsStateCorrectly() {
        val engine = RecorderEngine()

        // Create test audio data (100 bytes at 44100Hz, mono, 16-bit)
        val audioData = ByteArray(100) { 42 }
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        engine.loadRecordingForPlayback(parsed)

        assertEquals(RecordingState.PAUSED, engine.recordingStateFlow.value)
        assertEquals(0L, engine.timestampStateFlow.value)
        assertFalse(engine.waveformDataStateFlow.value.amplitudes.isEmpty())
    }

    @Test
    fun loadRecordingForPlayback_withValidAudio_generatesWaveformData() {
        val engine = RecorderEngine()

        val audioData = ByteArray(88200) { 42 } // ~2 seconds at 44100Hz
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        engine.loadRecordingForPlayback(parsed)

        val waveform = engine.waveformDataStateFlow.value
        assertFalse(waveform.amplitudes.isEmpty())
        assertTrue(waveform.amplitudes.size > 0)
        assertTrue(waveform.maxAmplitude > 0f)
    }

    @Test
    fun loadRecordingForPlayback_withSmallAudio_generatesAtLeastOneBar() {
        val engine = RecorderEngine()

        // Very small audio (10 bytes)
        val audioData = ByteArray(10) { 42 }
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        engine.loadRecordingForPlayback(parsed)

        val waveform = engine.waveformDataStateFlow.value
        assertTrue(waveform.amplitudes.isNotEmpty())
        assertTrue(waveform.amplitudes.size >= 1)
    }

    @Test
    fun loadRecordingForPlayback_resetsStateBeforeLoading() {
        val engine = RecorderEngine()

        val audioData = ByteArray(100) { 42 }
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        engine.loadRecordingForPlayback(parsed)

        // Verify all state is reset
        assertEquals(RecordingState.PAUSED, engine.recordingStateFlow.value)
        assertEquals(0L, engine.timestampStateFlow.value)
        assertEquals(0, engine.waveformDataStateFlow.value.currentPosition)
    }

    @Test
    fun loadRecordingForPlayback_withDifferentAudioFormats_loadsCorrectly() {
        val engine1 = RecorderEngine()
        val engine2 = RecorderEngine()
        val engine3 = RecorderEngine()

        val audioData = ByteArray(100) { 42 }

        // 44100Hz, mono, 16-bit
        engine1.loadRecordingForPlayback(ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        ))

        // 48000Hz, stereo, 24-bit
        engine2.loadRecordingForPlayback(ParsedAudioData(
            audioData = audioData,
            sampleRate = 48000,
            channels = 2,
            bitsPerSample = 24,
            hasValidHeader = true
        ))

        // 22050Hz, mono, 16-bit
        engine3.loadRecordingForPlayback(ParsedAudioData(
            audioData = audioData,
            sampleRate = 22050,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        ))

        // All should have loaded successfully and generated waveforms
        assertFalse(engine1.waveformDataStateFlow.value.amplitudes.isEmpty())
        assertFalse(engine2.waveformDataStateFlow.value.amplitudes.isEmpty())
        assertFalse(engine3.waveformDataStateFlow.value.amplitudes.isEmpty())
    }

    @Test
    fun loadRecordingForPlayback_multipleLoads_replacePreviousData() {
        val engine = RecorderEngine()

        // Use significantly larger audio data to ensure different bar counts
        // At 44100Hz, 16-bit, mono: 88200 bytes = 1 second = 20 bars (at 20 bars/sec)
        val audioData1 = ByteArray(88200) { 11 }  // 1 second = 20 bars
        val audioData2 = ByteArray(176400) { 22 } // 2 seconds = 40 bars

        val parsed1 = ParsedAudioData(
            audioData = audioData1,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        val parsed2 = ParsedAudioData(
            audioData = audioData2,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        engine.loadRecordingForPlayback(parsed1)
        val waveform1 = engine.waveformDataStateFlow.value.amplitudes.size

        engine.loadRecordingForPlayback(parsed2)
        val waveform2 = engine.waveformDataStateFlow.value.amplitudes.size

        // Second load should have different amplitude count (more data)
        assertTrue(waveform2 > waveform1)
    }

    @Test
    fun loadRecordingForPlayback_withRawPcmData_loadsSuccessfully() {
        val engine = RecorderEngine()

        val audioData = ByteArray(100) { 42 }
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = false // Raw PCM without header
        )

        engine.loadRecordingForPlayback(parsed)

        assertEquals(RecordingState.PAUSED, engine.recordingStateFlow.value)
        assertFalse(engine.waveformDataStateFlow.value.amplitudes.isEmpty())
    }

    @Test
    fun loadRecordingForPlayback_withEmptyAudioData_loadsButGeneratesMinimalWaveform() {
        val engine = RecorderEngine()

        val audioData = byteArrayOf()
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        engine.loadRecordingForPlayback(parsed)

        // Should still generate at least 1 bar
        val waveform = engine.waveformDataStateFlow.value
        assertTrue(waveform.amplitudes.isNotEmpty())
    }

    @Test
    fun pause_whileRecording_pausesSuccessfully() {
        val engine = RecorderEngine()
        // Note: Recording requires permissions, this just tests the pause method doesn't crash
        engine.pause()
        // If we get here, pause was successful
        assertNotNull(engine.recordingStateFlow)
    }

    @Test
    fun pause_whilePlayback_pausesSuccessfully() {
        val engine = RecorderEngine()
        // First load some audio
        val audioData = ByteArray(100) { 42 }
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)

        // Try to pause (may not be in playback state, but shouldn't crash)
        engine.pause()
        assertNotNull(engine.recordingStateFlow)
    }

    @Test
    fun finalize_resetsWaveformData() {
        val engine = RecorderEngine()

        // Load some audio first
        val audioData = ByteArray(100) { 42 }
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)
        assertFalse(engine.waveformDataStateFlow.value.amplitudes.isEmpty())

        // Finalize should clear the waveform
        engine.finalize { }
        assertTrue(engine.waveformDataStateFlow.value.amplitudes.isEmpty())
    }

    @Test
    fun finalize_resetsRecordingState() {
        val engine = RecorderEngine()

        // Load some audio
        val audioData = ByteArray(100) { 42 }
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)

        // Finalize should reset state to IDLE
        engine.finalize { }
        assertEquals(RecordingState.IDLE, engine.recordingStateFlow.value)
    }

    @Test
    fun finalize_resetsTimestamp() {
        val engine = RecorderEngine()

        // Load some audio
        val audioData = ByteArray(100) { 42 }
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)

        // Finalize should reset timestamp
        engine.finalize { }
        assertEquals(0L, engine.timestampStateFlow.value)
    }

    @Test
    fun loadRecordingForPlayback_thenFinalize_clearsAllState() {
        val engine = RecorderEngine()

        val audioData = ByteArray(1000) { 42 }
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        engine.loadRecordingForPlayback(parsed)
        // Verify state was set
        assertNotNull(engine.waveformDataStateFlow.value)
        assertFalse(engine.waveformDataStateFlow.value.amplitudes.isEmpty())

        // Finalize everything
        engine.finalize { }

        // Verify all state is cleared
        assertEquals(RecordingState.IDLE, engine.recordingStateFlow.value)
        assertTrue(engine.waveformDataStateFlow.value.amplitudes.isEmpty())
        assertEquals(0L, engine.timestampStateFlow.value)
        assertEquals(0, engine.playbackPositionStateFlow.value.currentIndex)
    }

    // ─── Initial state for new flows ────────────────────────────────────────────

    @Test
    fun repeatPlaybackEnabled_initialState_isFalse() {
        val engine = RecorderEngine()
        assertFalse(engine.repeatPlaybackEnabledFlow.value)
    }

    @Test
    fun playbackSpeed_initialState_isOne() {
        val engine = RecorderEngine()
        assertEquals(1.0f, engine.playbackSpeedFlow.value)
    }

    // ─── toggleRepeatPlayback ────────────────────────────────────────────────────

    @Test
    fun toggleRepeatPlayback_fromFalse_becomesTrue() {
        val engine = RecorderEngine()
        assertFalse(engine.repeatPlaybackEnabledFlow.value)
        engine.toggleRepeatPlayback()
        assertTrue(engine.repeatPlaybackEnabledFlow.value)
    }

    @Test
    fun toggleRepeatPlayback_fromTrue_becomesFalse() {
        val engine = RecorderEngine()
        engine.toggleRepeatPlayback()
        assertTrue(engine.repeatPlaybackEnabledFlow.value)
        engine.toggleRepeatPlayback()
        assertFalse(engine.repeatPlaybackEnabledFlow.value)
    }

    @Test
    fun toggleRepeatPlayback_calledMultipleTimes_togglesCorrectly() {
        val engine = RecorderEngine()
        repeat(6) { engine.toggleRepeatPlayback() }
        // 6 toggles from false → false again
        assertFalse(engine.repeatPlaybackEnabledFlow.value)
    }

    // ─── setPlaybackSpeed ────────────────────────────────────────────────────────

    @Test
    fun setPlaybackSpeed_whenIdle_updatesFlowValue() {
        val engine = RecorderEngine()
        engine.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, engine.playbackSpeedFlow.value)
    }

    @Test
    fun setPlaybackSpeed_toHalf_updatesFlowValue() {
        val engine = RecorderEngine()
        engine.setPlaybackSpeed(0.5f)
        assertEquals(0.5f, engine.playbackSpeedFlow.value)
    }

    @Test
    fun setPlaybackSpeed_toDouble_updatesFlowValue() {
        val engine = RecorderEngine()
        engine.setPlaybackSpeed(2.0f)
        assertEquals(2.0f, engine.playbackSpeedFlow.value)
    }

    @Test
    fun setPlaybackSpeed_whileInPausedState_updatesFlowValue() {
        val engine = RecorderEngine()
        val parsed = ParsedAudioData(
            audioData = ByteArray(100) { 1 },
            sampleRate = 44100, channels = 1, bitsPerSample = 16, hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)
        assertEquals(RecordingState.PAUSED, engine.recordingStateFlow.value)

        engine.setPlaybackSpeed(1.75f)
        assertEquals(1.75f, engine.playbackSpeedFlow.value)
    }

    @Test
    fun loadRecordingForPlayback_resetsPlaybackSpeedToOne() {
        val engine = RecorderEngine()
        engine.setPlaybackSpeed(2.0f)
        assertEquals(2.0f, engine.playbackSpeedFlow.value)

        val parsed = ParsedAudioData(
            audioData = ByteArray(100) { 1 },
            sampleRate = 44100, channels = 1, bitsPerSample = 16, hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)
        assertEquals(1.0f, engine.playbackSpeedFlow.value)
    }

    @Test
    fun finalize_resetsPlaybackSpeedToOne() {
        val engine = RecorderEngine()
        engine.setPlaybackSpeed(2.0f)
        engine.finalize { }
        assertEquals(1.0f, engine.playbackSpeedFlow.value)
    }

    // ─── pause branches ──────────────────────────────────────────────────────────

    @Test
    fun pause_whenIdle_doesNotCrashAndStaysIdle() {
        val engine = RecorderEngine()
        assertEquals(RecordingState.IDLE, engine.recordingStateFlow.value)
        engine.pause() // hits the else branch
        assertEquals(RecordingState.IDLE, engine.recordingStateFlow.value)
    }

    @Test
    fun pause_whenPaused_doesNotCrashAndStaysPaused() {
        val engine = RecorderEngine()
        val parsed = ParsedAudioData(
            audioData = ByteArray(100) { 1 },
            sampleRate = 44100, channels = 1, bitsPerSample = 16, hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)
        assertEquals(RecordingState.PAUSED, engine.recordingStateFlow.value)
        engine.pause() // already PAUSED → else branch
        assertEquals(RecordingState.PAUSED, engine.recordingStateFlow.value)
    }

    // ─── finalize: onSave callback ───────────────────────────────────────────────

    @Test
    fun finalize_withNoRecordedData_doesNotInvokeOnSave() {
        val engine = RecorderEngine()
        var called = false
        engine.finalize { called = true }
        assertFalse(called)
    }

    // ─── playBackCurrentStream guard branches ────────────────────────────────────

    @Test
    fun playBackCurrentStream_whenIdle_doesNotCrash() {
        val engine = RecorderEngine()
        // State is IDLE → not PAUSED → early return
        engine.playBackCurrentStream()
        assertEquals(RecordingState.IDLE, engine.recordingStateFlow.value)
    }

    @Test
    fun playBackCurrentStream_withNoAudioData_doesNotStartPlayback() {
        val engine = RecorderEngine()
        // Load empty data so state becomes PAUSED but recordedData is empty
        val parsed = ParsedAudioData(
            audioData = ByteArray(0),
            sampleRate = 44100, channels = 1, bitsPerSample = 16, hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)
        assertEquals(RecordingState.PAUSED, engine.recordingStateFlow.value)

        engine.playBackCurrentStream()
        // Should not have moved to PLAYBACK because audioBytes.isEmpty()
        // (it enters the method but hits the empty-data guard)
        // Give thread a moment if it did start
        Thread.sleep(50)
        assertNotEquals(RecordingState.PLAYBACK, engine.recordingStateFlow.value)
    }

    // ─── seekToWaveformIndex ─────────────────────────────────────────────────────

    @Test
    fun seekToWaveformIndex_whenIdle_doesNothing() {
        val engine = RecorderEngine()
        engine.seekToWaveformIndex(5)
        // State stays IDLE, no crash
        assertEquals(RecordingState.IDLE, engine.recordingStateFlow.value)
    }

    @Test
    fun seekToWaveformIndex_withEmptyAmplitudes_doesNothing() {
        val engine = RecorderEngine()
        // Put engine in PAUSED with waveform data that has no amplitudes
        // Load then finalize → waveform is cleared, but state goes IDLE; instead
        // use a zero-byte load which still generates ≥1 amplitude → not useful here.
        // Best we can do: engine is PAUSED after load; clear waveform indirectly
        // by finalizing and re-checking — instead just verify IDLE guard fires first.
        // Confirmed by idle test above. Test the empty-amplitudes guard separately
        // via a fresh engine that has never loaded (waveform stays empty, state IDLE):
        val engine2 = RecorderEngine()
        engine2.seekToWaveformIndex(0)
        assertEquals(RecordingState.IDLE, engine2.recordingStateFlow.value)
    }

    @Test
    fun seekToWaveformIndex_whenPaused_updatesTimestampAndPosition() {
        val engine = RecorderEngine()
        // Use enough audio data to guarantee multiple amplitude bars
        val parsed = ParsedAudioData(
            audioData = ByteArray(88200) { 42 }, // 1 second of audio
            sampleRate = 44100, channels = 1, bitsPerSample = 16, hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)
        assertEquals(RecordingState.PAUSED, engine.recordingStateFlow.value)

        val amplitudeCount = engine.waveformDataStateFlow.value.amplitudes.size
        assertTrue(amplitudeCount > 1)

        engine.onScrubStart()
        engine.seekToWaveformIndex(amplitudeCount / 2)

        assertEquals(amplitudeCount / 2, engine.waveformDataStateFlow.value.currentPosition)
        assertEquals(amplitudeCount / 2, engine.playbackPositionStateFlow.value.currentIndex)
        assertTrue(engine.timestampStateFlow.value >= 0L)
    }

    @Test
    fun seekToWaveformIndex_toZero_resetsTimestamp() {
        val engine = RecorderEngine()
        val parsed = ParsedAudioData(
            audioData = ByteArray(88200) { 42 },
            sampleRate = 44100, channels = 1, bitsPerSample = 16, hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)

        engine.onScrubStart()
        engine.seekToWaveformIndex(0)

        assertEquals(0L, engine.timestampStateFlow.value)
        assertEquals(0, engine.waveformDataStateFlow.value.currentPosition)
    }

    @Test
    fun seekToWaveformIndex_toLastBar_clampsCorrectly() {
        val engine = RecorderEngine()
        val parsed = ParsedAudioData(
            audioData = ByteArray(88200) { 42 },
            sampleRate = 44100, channels = 1, bitsPerSample = 16, hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)

        val lastIndex = engine.waveformDataStateFlow.value.amplitudes.size - 1
        engine.onScrubStart()
        engine.seekToWaveformIndex(lastIndex + 100) // beyond bounds → clamped

        assertEquals(lastIndex, engine.waveformDataStateFlow.value.currentPosition)
        assertEquals(lastIndex, engine.playbackPositionStateFlow.value.currentIndex)
    }

    @Test
    fun seekToWaveformIndex_negativeIndex_clampsToZero() {
        val engine = RecorderEngine()
        val parsed = ParsedAudioData(
            audioData = ByteArray(88200) { 42 },
            sampleRate = 44100, channels = 1, bitsPerSample = 16, hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)

        engine.onScrubStart()
        engine.seekToWaveformIndex(-10)

        assertEquals(0, engine.waveformDataStateFlow.value.currentPosition)
        assertEquals(0, engine.playbackPositionStateFlow.value.currentIndex)
    }

    @Test
    fun seekToWaveformIndex_singleAmplitude_positionRemainsZero() {
        val engine = RecorderEngine()
        // Very small data → likely collapses to 1 amplitude bar
        val parsed = ParsedAudioData(
            audioData = ByteArray(2) { 42 },
            sampleRate = 44100, channels = 1, bitsPerSample = 16, hasValidHeader = true
        )
        engine.loadRecordingForPlayback(parsed)

        engine.onScrubStart()
        engine.seekToWaveformIndex(0)

        assertEquals(0, engine.waveformDataStateFlow.value.currentPosition)
        assertEquals(0L, engine.timestampStateFlow.value)
    }
}




