package com.recordermetronome

import com.recordermetronome.util.RecorderEngine
import com.recordermetronome.util.RecordingState
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
}



