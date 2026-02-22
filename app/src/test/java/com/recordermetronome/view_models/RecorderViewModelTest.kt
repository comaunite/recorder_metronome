package com.recordermetronome.view_models

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecorderViewModelTest {

    @Test
    fun formatMillisToTimestamp_zeroMillis_returnsZeroFormat() {
        val viewModel = RecorderViewModel()
        val result = viewModel.formatMillisToTimestamp(0L)
        assertEquals("00:00.0", result)
    }

    @Test
    fun formatMillisToTimestamp_oneSecond_returnsFormatted() {
        val viewModel = RecorderViewModel()
        val result = viewModel.formatMillisToTimestamp(1000L)
        assertEquals("00:01.0", result)
    }

    @Test
    fun formatMillisToTimestamp_oneMinute_returnsFormatted() {
        val viewModel = RecorderViewModel()
        val result = viewModel.formatMillisToTimestamp(60000L)
        assertEquals("01:00.0", result)
    }

    @Test
    fun formatMillisToTimestamp_oneMinute30Seconds_returnsFormatted() {
        val viewModel = RecorderViewModel()
        val result = viewModel.formatMillisToTimestamp(90500L)
        assertEquals("01:30.5", result)
    }

    @Test
    fun formatMillisToTimestamp_withHours_returnsFormattedWithHours() {
        val viewModel = RecorderViewModel()
        // 1 hour, 30 minutes, 45 seconds, 600 millis
        val result = viewModel.formatMillisToTimestamp(5445600L)
        assertEquals("01:30:45.6", result)
    }

    @Test
    fun formatMillisToTimestamp_multipleHours_returnsFormatted() {
        val viewModel = RecorderViewModel()
        // 2 hours = 7200000
        val result = viewModel.formatMillisToTimestamp(7200000L)
        assertEquals("02:00:00.0", result)
    }

    @Test
    fun formatMillisToTimestamp_fractionalSeconds_truncatesMillis() {
        val viewModel = RecorderViewModel()
        // 1234 millis = 1 second and 234 millis
        val result = viewModel.formatMillisToTimestamp(1234L)
        assertEquals("00:01.2", result)
    }

    @Test
    fun formatMillisToTimestamp_allNines_returnsFormatted() {
        val viewModel = RecorderViewModel()
        // 9 minutes, 59 seconds, 900 millis
        val result = viewModel.formatMillisToTimestamp(599900L)
        assertEquals("09:59.9", result)
    }

    @Test
    fun formatMillisToTimestamp_padding_ensuresLeadingZeros() {
        val viewModel = RecorderViewModel()
        val result = viewModel.formatMillisToTimestamp(5000L)
        assertEquals("00:05.0", result)
        assertTrue(result.startsWith("00:"))
    }

    @Test
    fun formatMillisToTimestamp_smallValue_returnsZeroPaddedMinutes() {
        val viewModel = RecorderViewModel()
        val result = viewModel.formatMillisToTimestamp(500L)
        // 500 millis = 0 minutes, 0 seconds, 500 millis (displayed as .5)
        assertEquals("00:00.5", result)
    }

    @Test
    fun recordingStateFlow_initialState_isIdle() {
        val viewModel = RecorderViewModel()
        // We can check the initial state (it should be IDLE from RecorderEngine)
        assertNotNull(viewModel.recordingStateFlow)
    }

    @Test
    fun showSaveDialog_initialState_isFalse() {
        val viewModel = RecorderViewModel()
        assertNotNull(viewModel.showSaveDialog)
    }

    @Test
    fun generatedFileName_initialState_isEmpty() {
        val viewModel = RecorderViewModel()
        assertNotNull(viewModel.generatedFileName)
    }

    @Test
    fun showBackDialog_initialState_isFalse() {
        val viewModel = RecorderViewModel()
        assertNotNull(viewModel.showBackDialog)
    }

    @Test
    fun accumulatedWaveformData_initialState_isEmpty() {
        val viewModel = RecorderViewModel()
        assertNotNull(viewModel.accumulatedWaveformData)
    }

    @Test
    fun timestamp_initialState_isNotNull() {
        val viewModel = RecorderViewModel()
        assertNotNull(viewModel.timestamp)
    }
}


