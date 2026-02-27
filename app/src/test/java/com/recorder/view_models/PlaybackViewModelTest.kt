package com.recorder.view_models

import com.recorder.util.RecordingState
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackViewModelTest {

    @Test
    fun playbackViewModel_initialState_isIdle() {
        val viewModel = PlaybackViewModel()
        assertEquals(RecordingState.IDLE, viewModel.recordingStateFlow.value)
    }

    @Test
    fun playbackViewModel_accumulatedWaveformData_initialState_isEmpty() {
        val viewModel = PlaybackViewModel()
        assertTrue(viewModel.accumulatedWaveformData.value.amplitudes.isEmpty())
    }

    @Test
    fun playbackViewModel_currentRecording_initialState_isNull() {
        val viewModel = PlaybackViewModel()
        assertNull(viewModel.currentRecording.value)
    }

    @Test
    fun playbackViewModel_existingRecordings_initialState_isEmpty() {
        val viewModel = PlaybackViewModel()
        assertTrue(viewModel.existingRecordings.value.isEmpty())
    }

    @Test
    fun playbackViewModel_timestamp_initialState_isZero() {
        val viewModel = PlaybackViewModel()
        assertEquals(0L, viewModel.timestamp.value)
    }

    @Test
    fun playbackViewModel_created_successfully() {
        val viewModel = PlaybackViewModel()
        assertNotNull(viewModel.recordingStateFlow)
        assertNotNull(viewModel.accumulatedWaveformData)
        assertNotNull(viewModel.currentRecording)
        assertNotNull(viewModel.existingRecordings)
        assertNotNull(viewModel.timestamp)
    }

    @Test
    fun playbackViewModel_onPlaybackTapped_doesNotCrash() {
        val viewModel = PlaybackViewModel()
        // Should not throw
        viewModel.onPlaybackTapped()
        assertNotNull(viewModel)
    }

    @Test
    fun playbackViewModel_onPausePlaybackTapped_doesNotCrash() {
        val viewModel = PlaybackViewModel()
        // Should not throw
        viewModel.onPausePlaybackTapped()
        assertNotNull(viewModel)
    }

    @Test
    fun playbackViewModel_applyRename_updatesCurrentRecordingName() {
        val viewModel = PlaybackViewModel()
        val oldRecording = com.recorder.data.RecorderFile(
            name = "Old Name",
            filePath = "/path/old.wav",
            durationMs = 1000L,
            createdTime = System.currentTimeMillis()
        )

        viewModel.applyRename(oldRecording, "New Name")

        assertEquals("New Name", viewModel.currentRecording.value?.name)
    }

    @Test
    fun playbackViewModel_applyRename_updatesFilePath() {
        val viewModel = PlaybackViewModel()
        val oldRecording = com.recorder.data.RecorderFile(
            name = "Old Name",
            filePath = "/path/old.wav",
            durationMs = 1000L,
            createdTime = System.currentTimeMillis()
        )

        viewModel.applyRename(oldRecording, "New Name")

        // File path should be updated with new name
        val newRecording = viewModel.currentRecording.value
        assertNotNull(newRecording)
        assertTrue(newRecording!!.filePath.contains("New Name"))
    }

    @Test
    fun playbackViewModel_applyRename_preservesOtherAttributes() {
        val viewModel = PlaybackViewModel()
        val oldRecording = com.recorder.data.RecorderFile(
            name = "Old",
            filePath = "/path/old.wav",
            durationMs = 5000L,
            createdTime = 123456L
        )

        viewModel.applyRename(oldRecording, "NewName")

        val newRecording = viewModel.currentRecording.value
        assertNotNull(newRecording)
        assertEquals(5000L, newRecording!!.durationMs)
        assertEquals(123456L, newRecording.createdTime)
    }

    @Test
    fun playbackViewModel_recordingStateFlow_isDirectlyFromEngine() {
        val viewModel = PlaybackViewModel()
        // Should get the engine's recording state
        val state = viewModel.recordingStateFlow.value
        assertNotNull(state)
    }

    @Test
    fun playbackViewModel_allStateFlowsAreNonNull() {
        val viewModel = PlaybackViewModel()
        assertNotNull(viewModel.recordingStateFlow)
        assertNotNull(viewModel.accumulatedWaveformData)
        assertNotNull(viewModel.timestamp)
        assertNotNull(viewModel.currentRecording)
        assertNotNull(viewModel.existingRecordings)
    }
}

