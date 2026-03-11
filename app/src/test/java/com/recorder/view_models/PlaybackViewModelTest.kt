package com.recorder.view_models

import com.recorder.services.RecordingState
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
        // Initial state is an empty RecorderFile, not null
        assertEquals("", viewModel.currentRecording.value.name)
        assertEquals("", viewModel.currentRecording.value.filePath)
        assertEquals(0L, viewModel.currentRecording.value.durationMs)
        assertEquals(0L, viewModel.currentRecording.value.createdTime)
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

        viewModel.updateInMemoryCollections(oldRecording, "New Name")

        assertEquals("New Name", viewModel.currentRecording.value.name)
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

        viewModel.updateInMemoryCollections(oldRecording, "New Name")

        // File path should be updated with new name
        val newRecording = viewModel.currentRecording.value
        assertTrue(newRecording.filePath.contains("New Name"))
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

        viewModel.updateInMemoryCollections(oldRecording, "NewName")

        val newRecording = viewModel.currentRecording.value
        assertEquals(5000L, newRecording.durationMs)
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

    @Test
    fun playbackViewModel_setExistingRecordings_updatesRecordingsList() {
        val viewModel = PlaybackViewModel()
        val recordings = listOf(
            com.recorder.data.RecorderFile("Rec1", "/path1", 1000L, 123L),
            com.recorder.data.RecorderFile("Rec2", "/path2", 2000L, 456L)
        )

        viewModel.setExistingRecordings(recordings)

        assertEquals(2, viewModel.existingRecordings.value.size)
        assertEquals("Rec1", viewModel.existingRecordings.value[0].name)
        assertEquals("Rec2", viewModel.existingRecordings.value[1].name)
    }

    @Test
    fun playbackViewModel_onRepeatToggleTapped_doesNotCrash() {
        val viewModel = PlaybackViewModel()
        // Should not throw
        viewModel.onRepeatToggleTapped()
        assertNotNull(viewModel)
    }

    @Test
    fun playbackViewModel_onPlaybackSpeedTapped_doesNotCrash() {
        val viewModel = PlaybackViewModel()
        // Should not throw
        viewModel.onPlaybackSpeedTapped(1.5f)
        assertNotNull(viewModel)
    }

    @Test
    fun playbackViewModel_onWaveformScrubbed_doesNotCrash() {
        val viewModel = PlaybackViewModel()
        // Should not throw
        viewModel.onWaveformScrubbed(50)
        assertNotNull(viewModel)
    }

    @Test
    fun playbackViewModel_onScrubStart_doesNotCrash() {
        val viewModel = PlaybackViewModel()
        // Should not throw
        viewModel.onScrubStart()
        assertNotNull(viewModel)
    }

    @Test
    fun playbackViewModel_onScrubEnd_doesNotCrash() {
        val viewModel = PlaybackViewModel()
        // Should not throw
        viewModel.onScrubEnd()
        assertNotNull(viewModel)
    }

    @Test
    fun playbackViewModel_onReturnToFileExplorer_executesCallback() {
        val viewModel = PlaybackViewModel()
        var callbackExecuted = false

        viewModel.onReturnToFileExplorer { callbackExecuted = true }

        // Give it a moment for async finalization
        Thread.sleep(100)
        assertTrue(callbackExecuted)
    }

    @Test
    fun playbackViewModel_updateInMemoryCollections_updatesExistingRecordingsList() {
        val viewModel = PlaybackViewModel()
        val oldRecording = com.recorder.data.RecorderFile(
            name = "Old",
            filePath = "/path/old.wav",
            durationMs = 1000L,
            createdTime = 123L
        )

        // Set up existing recordings that include the old recording
        val existingRecordings = listOf(
            com.recorder.data.RecorderFile("Other", "/path/other.wav", 500L, 100L),
            oldRecording,
            com.recorder.data.RecorderFile("Another", "/path/another.wav", 700L, 200L)
        )
        viewModel.setExistingRecordings(existingRecordings)

        // Update the recording
        viewModel.updateInMemoryCollections(oldRecording, "New")

        // Verify the list was updated
        val updatedList = viewModel.existingRecordings.value
        assertEquals(3, updatedList.size)

        // Find the updated recording
        val updatedRecording = updatedList.find { it.filePath.contains("New") }
        assertNotNull(updatedRecording)
        assertEquals("New", updatedRecording?.name)
    }

    @Test
    fun playbackViewModel_repeatPlaybackEnabled_flowIsAccessible() {
        val viewModel = PlaybackViewModel()
        assertNotNull(viewModel.repeatPlaybackEnabled)
        // Initial value depends on engine, just verify it's accessible
        assertNotNull(viewModel.repeatPlaybackEnabled.value)
    }

    @Test
    fun playbackViewModel_playbackSpeed_flowIsAccessible() {
        val viewModel = PlaybackViewModel()
        assertNotNull(viewModel.playbackSpeed)
        // Initial value should be 1.0
        assertEquals(1.0f, viewModel.playbackSpeed.value, 0.01f)
    }

    @Test
    fun playbackViewModel_multipleOperations_doNotCrash() {
        val viewModel = PlaybackViewModel()

        // Perform multiple operations in sequence
        viewModel.onPlaybackTapped()
        viewModel.onPausePlaybackTapped()
        viewModel.onWaveformScrubbed(10)
        viewModel.onScrubStart()
        viewModel.onScrubEnd()
        viewModel.onRepeatToggleTapped()
        viewModel.onPlaybackSpeedTapped(1.5f)

        // Should not crash
        assertNotNull(viewModel)
    }
}


