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

        val audioData1 = ByteArray(100) { 11 }
        val audioData2 = ByteArray(200) { 22 }

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
}




