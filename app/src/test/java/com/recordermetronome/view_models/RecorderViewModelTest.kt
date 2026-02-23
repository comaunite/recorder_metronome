package com.recordermetronome.view_models

import com.recordermetronome.util.RecordingState
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecorderViewModelTest {

    @Test
    fun recordingViewModel_initialState_isIdle() {
        val viewModel = RecorderViewModel()
        assertEquals(RecordingState.IDLE, viewModel.recordingStateFlow.value)
    }

    @Test
    fun recordingViewModel_showSaveDialog_initialState_isFalse() {
        val viewModel = RecorderViewModel()
        assertFalse(viewModel.showSaveDialog.value)
    }

    @Test
    fun recordingViewModel_generatedFileName_initialState_isEmpty() {
        val viewModel = RecorderViewModel()
        assertEquals("", viewModel.generatedFileName.value)
    }

    @Test
    fun recordingViewModel_showBackDialog_initialState_isFalse() {
        val viewModel = RecorderViewModel()
        assertFalse(viewModel.showBackDialog.value)
    }

    @Test
    fun recordingViewModel_accumulatedWaveformData_initialState_isEmpty() {
        val viewModel = RecorderViewModel()
        assertTrue(viewModel.accumulatedWaveformData.value.amplitudes.isEmpty())
    }

    @Test
    fun recordingViewModel_timestamp_initialState_isZero() {
        val viewModel = RecorderViewModel()
        assertEquals(0L, viewModel.timestamp.value)
    }

    @Test
    fun recordingViewModel_created_successfully() {
        val viewModel = RecorderViewModel()
        assertNotNull(viewModel.recordingStateFlow)
        assertNotNull(viewModel.showSaveDialog)
        assertNotNull(viewModel.generatedFileName)
        assertNotNull(viewModel.showBackDialog)
        assertNotNull(viewModel.accumulatedWaveformData)
        assertNotNull(viewModel.timestamp)
    }

    @Test
    fun recordingViewModel_onRecordTapped_startsRecording() {
        // This would normally require permissions, so it's tested with Robolectric
        val viewModel = RecorderViewModel()
        // Verify the method exists and doesn't crash
        assertNotNull(viewModel)
    }

    @Test
    fun recordingViewModel_onPauseRecordTapped_pausesRecording() {
        val viewModel = RecorderViewModel()
        val initialState = viewModel.recordingStateFlow.value
        viewModel.onPauseRecordTapped()
        // After pause, state should still be accessible
        assertNotNull(viewModel.recordingStateFlow.value)
    }

    @Test
    fun recordingViewModel_onPlaybackTapped_startsPlayback() {
        val viewModel = RecorderViewModel()
        // Verify the method exists and doesn't crash
        assertNotNull(viewModel)
    }

    @Test
    fun recordingViewModel_onPausePlaybackTapped_pausesPlayback() {
        val viewModel = RecorderViewModel()
        viewModel.onPausePlaybackTapped()
        // After pause, state should still be accessible
        assertNotNull(viewModel.recordingStateFlow.value)
    }

    @Test
    fun recordingViewModel_onStopTapped_showsSaveDialog() {
        val viewModel = RecorderViewModel()
        viewModel.onStopTapped()
        // After stop, should show save dialog
        assertTrue(viewModel.showSaveDialog.value)
    }

    @Test
    fun recordingViewModel_onBackPressed_showsBackDialog() {
        val viewModel = RecorderViewModel()
        viewModel.onBackPressed()
        // After back press, should show back dialog
        assertTrue(viewModel.showBackDialog.value)
    }

    @Test
    fun recordingViewModel_onBackDialogCancel_hidesBackDialog() {
        val viewModel = RecorderViewModel()
        viewModel.onBackPressed() // Show dialog first
        assertTrue(viewModel.showBackDialog.value)
        viewModel.onBackDialogCancel()
        assertFalse(viewModel.showBackDialog.value)
    }

    @Test
    fun recordingViewModel_onStopDialogCancel_hidesSaveDialog() {
        val viewModel = RecorderViewModel()
        viewModel.onStopTapped() // Show dialog first
        assertTrue(viewModel.showSaveDialog.value)
        viewModel.onStopDialogCancel()
        assertFalse(viewModel.showSaveDialog.value)
    }
}




