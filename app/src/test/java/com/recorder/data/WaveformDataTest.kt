package com.recorder.data

import org.junit.Test
import org.junit.Assert.*

class WaveformDataTest {

    @Test
    fun waveformData_defaultConstructor_hasDefaultValues() {
        val waveformData = WaveformData()

        assertTrue(waveformData.amplitudes.isEmpty())
        assertEquals(1f, waveformData.maxAmplitude)
        assertEquals(0, waveformData.currentPosition)
    }

    @Test
    fun waveformData_withAmplitudes_storesValues() {
        val amplitudes = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
        val waveformData = WaveformData(
            amplitudes = amplitudes,
            maxAmplitude = 0.5f,
            currentPosition = 3
        )

        assertEquals(amplitudes, waveformData.amplitudes)
        assertEquals(0.5f, waveformData.maxAmplitude)
        assertEquals(3, waveformData.currentPosition)
    }

    @Test
    fun waveformData_copy_createsNewInstanceWithChangedFields() {
        val original = WaveformData(
            amplitudes = listOf(0.1f, 0.2f),
            maxAmplitude = 0.2f,
            currentPosition = 1
        )
        val copied = original.copy(currentPosition = 2)

        assertEquals(listOf(0.1f, 0.2f), copied.amplitudes)
        assertEquals(0.2f, copied.maxAmplitude)
        assertEquals(2, copied.currentPosition)
    }

    @Test
    fun waveformData_equality_worksCorrectly() {
        val waveform1 = WaveformData(listOf(0.1f, 0.2f), 0.2f, 1)
        val waveform2 = WaveformData(listOf(0.1f, 0.2f), 0.2f, 1)

        assertEquals(waveform1, waveform2)
    }

    @Test
    fun waveformData_inequality_withDifferentAmplitudes() {
        val waveform1 = WaveformData(listOf(0.1f, 0.2f), 0.2f, 1)
        val waveform2 = WaveformData(listOf(0.1f, 0.3f), 0.2f, 1)

        assertNotEquals(waveform1, waveform2)
    }

    @Test
    fun waveformData_inequality_withDifferentMaxAmplitude() {
        val waveform1 = WaveformData(listOf(0.1f, 0.2f), 0.2f, 1)
        val waveform2 = WaveformData(listOf(0.1f, 0.2f), 0.3f, 1)

        assertNotEquals(waveform1, waveform2)
    }

    @Test
    fun waveformData_inequality_withDifferentPosition() {
        val waveform1 = WaveformData(listOf(0.1f, 0.2f), 0.2f, 1)
        val waveform2 = WaveformData(listOf(0.1f, 0.2f), 0.2f, 2)

        assertNotEquals(waveform1, waveform2)
    }

    @Test
    fun waveformUpdate_defaultConstructor_hasDefaultValues() {
        val update = WaveformUpdate()

        assertTrue(update.newAmplitudes.isEmpty())
        assertEquals(10000f, update.maxAmplitude)
    }

    @Test
    fun waveformUpdate_withValues_storesValues() {
        val amplitudes = listOf(0.1f, 0.2f, 0.3f)
        val update = WaveformUpdate(
            newAmplitudes = amplitudes,
            maxAmplitude = 0.3f
        )

        assertEquals(amplitudes, update.newAmplitudes)
        assertEquals(0.3f, update.maxAmplitude)
    }

    @Test
    fun waveformUpdate_copy_createsNewInstanceWithChangedFields() {
        val original = WaveformUpdate(listOf(0.1f, 0.2f), 0.2f)
        val copied = original.copy(maxAmplitude = 0.5f)

        assertEquals(listOf(0.1f, 0.2f), copied.newAmplitudes)
        assertEquals(0.5f, copied.maxAmplitude)
    }

    @Test
    fun playbackPosition_defaultConstructor_hasDefaultValue() {
        val position = PlaybackPosition()

        assertEquals(0, position.currentIndex)
    }

    @Test
    fun playbackPosition_withValue_storesValue() {
        val position = PlaybackPosition(currentIndex = 42)

        assertEquals(42, position.currentIndex)
    }

    @Test
    fun playbackPosition_copy_createsNewInstanceWithChangedField() {
        val original = PlaybackPosition(currentIndex = 10)
        val copied = original.copy(currentIndex = 20)

        assertEquals(20, copied.currentIndex)
    }

    @Test
    fun playbackPosition_equality_worksCorrectly() {
        val position1 = PlaybackPosition(currentIndex = 10)
        val position2 = PlaybackPosition(currentIndex = 10)

        assertEquals(position1, position2)
    }

    @Test
    fun playbackPosition_inequality_withDifferentIndex() {
        val position1 = PlaybackPosition(currentIndex = 10)
        val position2 = PlaybackPosition(currentIndex = 20)

        assertNotEquals(position1, position2)
    }
}

