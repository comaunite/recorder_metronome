package com.recorder.view_models

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.recorder.data.RecorderFile
import com.recorder.services.RecordingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class PlaybackViewModelInstrumentedTest {
    private lateinit var context: Context
    private lateinit var testDir: File

    private val app get() = context.applicationContext as Application

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_recordings")
        if (!testDir.exists()) {
            testDir.mkdirs()
        } else {
            testDir.listFiles()?.forEach { it.delete() }
        }
    }

    @Test
    fun playbackViewModel_initialize_loadsRecordingSuccessfully() {
        val viewModel = PlaybackViewModel(app)
        val wavFile = createTestWavFile(
            fileName = "test.wav",
            sampleRate = 48000,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(1000) { 42 }
        )

        val recording = RecorderFile(
            name = "Test Recording",
            filePath = wavFile.absolutePath,
            durationMs = 1000L,
            createdTime = System.currentTimeMillis()
        )

        viewModel.initialize(context, recording, null)

        // Give it a moment for async operations
        Thread.sleep(500)

        assertEquals("Test Recording", viewModel.currentRecording.value.name)
        assertFalse(viewModel.accumulatedWaveformData.value.amplitudes.isEmpty())
    }

    @Test
    fun playbackViewModel_initialState_isCorrect() {
        val viewModel = PlaybackViewModel(app)

        assertEquals(RecordingState.IDLE, viewModel.recordingStateFlow.value)
        assertTrue(viewModel.accumulatedWaveformData.value.amplitudes.isEmpty())
        assertEquals(0L, viewModel.timestamp.value)
    }

    @Test
    fun playbackViewModel_withPreLoadedRecordings_usesProvidedList() {
        val viewModel = PlaybackViewModel(app)
        val preLoadedRecordings = listOf(
            RecorderFile("Recording 1", "/path1", 1000L, System.currentTimeMillis()),
            RecorderFile("Recording 2", "/path2", 2000L, System.currentTimeMillis())
        )

        val wavFile = createTestWavFile(
            fileName = "test.wav",
            sampleRate = 48000,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(100) { 42 }
        )

        val recording = RecorderFile(
            name = "Current",
            filePath = wavFile.absolutePath,
            durationMs = 1000L,
            createdTime = System.currentTimeMillis()
        )

        viewModel.initialize(context, recording, preLoadedRecordings)
        Thread.sleep(100)

        assertEquals(preLoadedRecordings, viewModel.existingRecordings.value)
    }

    @Test
    fun playbackViewModel_applyRename_updatesRecordingName() {
        val viewModel = PlaybackViewModel(app)
        val oldRecording = RecorderFile(
            name = "Old Name",
            filePath = "/path/old.wav",
            durationMs = 1000L,
            createdTime = System.currentTimeMillis()
        )

        viewModel.updateInMemoryCollections(oldRecording, "New Name")

        assertEquals("New Name", viewModel.currentRecording.value.name)
    }

    @Test
    fun playbackViewModel_accumulatedWaveformData_updatesFromEngine() {
        val viewModel = PlaybackViewModel(app)
        val wavFile = createTestWavFile(
            fileName = "test.wav",
            sampleRate = 48000,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(48000) { 42 } // ~1 second
        )

        val recording = RecorderFile(
            name = "Test",
            filePath = wavFile.absolutePath,
            durationMs = 1000L,
            createdTime = System.currentTimeMillis()
        )

        viewModel.initialize(context, recording, null)
        Thread.sleep(500)

        // Verify waveform data is populated
        val waveformData = viewModel.accumulatedWaveformData.value
        assertFalse(waveformData.amplitudes.isEmpty())
        assertTrue(waveformData.maxAmplitude > 0f)
    }

    @Test
    fun playbackViewModel_currentRecording_updatesOnInitialize() {
        val viewModel = PlaybackViewModel(app)
        // Initial state should be an empty RecorderFile
        assertEquals("", viewModel.currentRecording.value.name)
        assertEquals("", viewModel.currentRecording.value.filePath)

        val wavFile = createTestWavFile(
            fileName = "test.wav",
            sampleRate = 48000,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(100) { 42 }
        )

        val recording = RecorderFile(
            name = "Test Recording",
            filePath = wavFile.absolutePath,
            durationMs = 1000L,
            createdTime = System.currentTimeMillis()
        )

        viewModel.initialize(context, recording, null)

        assertEquals(recording, viewModel.currentRecording.value)
    }

    @Test
    fun playbackViewModel_errorHandling_doesNotCrashOnInvalidFile() {
        val viewModel = PlaybackViewModel(app)
        val recording = RecorderFile(
            name = "Invalid",
            filePath = "/non/existent/file.wav",
            durationMs = 1000L,
            createdTime = System.currentTimeMillis()
        )

        // Should not throw, error should be caught and logged
        viewModel.initialize(context, recording, null)
        Thread.sleep(500)

        // View model should still be in a valid state
        assertNotNull(viewModel.recordingStateFlow)
    }

    // ── State transitions ────────────────────────────────────────────────────────

    @Test
    fun playbackViewModel_afterInitialize_stateIsPaused() {
        val viewModel = PlaybackViewModel(app)
        val wavFile = createTestWavFile(audioData = ByteArray(9600) { 42 })

        viewModel.initialize(context, wavFile.toRecorderFile(), null)
        Thread.sleep(300)

        assertEquals(RecordingState.PAUSED, viewModel.recordingStateFlow.value)
    }

    @Test
    fun playbackViewModel_onPlaybackTapped_stateBecomesPlayback() {
        val viewModel = PlaybackViewModel(app)
        val wavFile = createTestWavFile(audioData = ByteArray(96000) { 42 }) // 1 second

        viewModel.initialize(context, wavFile.toRecorderFile(), null)
        Thread.sleep(300)
        assertEquals(RecordingState.PAUSED, viewModel.recordingStateFlow.value)

        viewModel.onPlaybackTapped()
        Thread.sleep(300)

        assertEquals(RecordingState.PLAYBACK, viewModel.recordingStateFlow.value)
    }

    @Test
    fun playbackViewModel_pauseFromPlayback_stateReturnsToPaused() {
        val viewModel = PlaybackViewModel(app)
        val wavFile = createTestWavFile(audioData = ByteArray(96000) { 42 })

        viewModel.initialize(context, wavFile.toRecorderFile(), null)
        Thread.sleep(300)

        viewModel.onPlaybackTapped()
        Thread.sleep(300)
        assertEquals(RecordingState.PLAYBACK, viewModel.recordingStateFlow.value)

        viewModel.onPausePlaybackTapped()
        Thread.sleep(100)

        assertEquals(RecordingState.PAUSED, viewModel.recordingStateFlow.value)
    }

    // ── Repeat toggle ────────────────────────────────────────────────────────────

    @Test
    fun playbackViewModel_repeatToggle_initiallyFalse() {
        val viewModel = PlaybackViewModel(app)
        assertFalse(viewModel.repeatPlaybackEnabled.value)
    }

    @Test
    fun playbackViewModel_repeatToggle_becomesTrue() {
        val viewModel = PlaybackViewModel(app)
        viewModel.onRepeatToggleTapped()
        assertTrue(viewModel.repeatPlaybackEnabled.value)
    }

    @Test
    fun playbackViewModel_repeatToggle_togglesBackToFalse() {
        val viewModel = PlaybackViewModel(app)
        viewModel.onRepeatToggleTapped()
        viewModel.onRepeatToggleTapped()
        assertFalse(viewModel.repeatPlaybackEnabled.value)
    }

    // ── Playback speed ───────────────────────────────────────────────────────────

    @Test
    fun playbackViewModel_playbackSpeed_initiallyOne() {
        val viewModel = PlaybackViewModel(app)
        assertEquals(1.0f, viewModel.playbackSpeed.value, 0.001f)
    }

    @Test
    fun playbackViewModel_setPlaybackSpeed_updatesValue() {
        val viewModel = PlaybackViewModel(app)
        viewModel.onPlaybackSpeedTapped(1.5f)
        assertEquals(1.5f, viewModel.playbackSpeed.value, 0.001f)
    }

    @Test
    fun playbackViewModel_setPlaybackSpeed_halfSpeed_updatesValue() {
        val viewModel = PlaybackViewModel(app)
        viewModel.onPlaybackSpeedTapped(0.5f)
        assertEquals(0.5f, viewModel.playbackSpeed.value, 0.001f)
    }

    // ── Scrubbing ────────────────────────────────────────────────────────────────

    @Test
    fun playbackViewModel_scrub_updatesWaveformPosition() {
        val viewModel = PlaybackViewModel(app)
        val wavFile = createTestWavFile(audioData = ByteArray(96000) { 42 })

        viewModel.initialize(context, wavFile.toRecorderFile(), null)
        Thread.sleep(300)

        val totalBars = viewModel.accumulatedWaveformData.value.amplitudes.size
        assertTrue(totalBars > 1)

        viewModel.onScrubStart()
        viewModel.onWaveformScrubbed(totalBars / 2)
        Thread.sleep(100) // wait for waveformDataStateFlow coroutine to propagate

        assertEquals(totalBars / 2, viewModel.accumulatedWaveformData.value.currentPosition)
    }

    @Test
    fun playbackViewModel_scrubToZero_resetsPosition() {
        val viewModel = PlaybackViewModel(app)
        val wavFile = createTestWavFile(audioData = ByteArray(96000) { 42 })

        viewModel.initialize(context, wavFile.toRecorderFile(), null)
        Thread.sleep(300)

        viewModel.onScrubStart()
        viewModel.onWaveformScrubbed(5)
        viewModel.onScrubEnd()

        viewModel.onScrubStart()
        viewModel.onWaveformScrubbed(0)

        assertEquals(0, viewModel.accumulatedWaveformData.value.currentPosition)
    }

    // ── Return to file explorer ──────────────────────────────────────────────────

    @Test
    fun playbackViewModel_onReturnToFileExplorer_invokesCallback() {
        val viewModel = PlaybackViewModel(app)
        var callbackInvoked = false

        viewModel.onReturnToFileExplorer { callbackInvoked = true }
        Thread.sleep(200)

        assertTrue(callbackInvoked)
    }

    // ── Rename of a non-current recording ────────────────────────────────────────

    @Test
    fun playbackViewModel_updateInMemoryCollections_updatesFilePathAlongsideName() {
        // updateInMemoryCollections always updates _currentRecording (it is only ever called
        // from PlaybackScreen with the current recording). This test verifies that both the
        // name AND the derived file path are rebuilt correctly.
        val viewModel = PlaybackViewModel(app)
        val wavFile = createTestWavFile(audioData = ByteArray(100) { 1 })
        val current = wavFile.toRecorderFile()

        viewModel.initialize(context, current, null)
        Thread.sleep(100)

        viewModel.updateInMemoryCollections(current, "RenamedRecording")

        val updated = viewModel.currentRecording.value
        assertEquals("RenamedRecording", updated.name)
        assertTrue(
            "Expected file path to contain new name",
            updated.filePath.contains("RenamedRecording")
        )
    }

    @Test
    fun playbackViewModel_updateInMemoryCollections_updatesMatchingEntryInExistingList() {
        val viewModel = PlaybackViewModel(app)
        val wavFile = createTestWavFile(audioData = ByteArray(100) { 1 })
        val current = wavFile.toRecorderFile()
        val sibling = RecorderFile("Sibling", "/some/Sibling.wav", 500L, 100L)
        val other   = RecorderFile("Other",   "/some/Other.wav",   600L, 200L)

        viewModel.initialize(context, current, listOf(current, sibling, other))
        Thread.sleep(100)

        viewModel.updateInMemoryCollections(sibling, "RenamedSibling")

        val renamedEntry = viewModel.existingRecordings.value.find { it.name == "RenamedSibling" }
        assertNotNull(renamedEntry)

        // "Other" must be untouched
        val otherEntry = viewModel.existingRecordings.value.find { it.filePath == other.filePath }
        assertEquals("Other", otherEntry?.name)
    }

    // ── Duration accuracy ────────────────────────────────────────────────────────

    @Test
    fun playbackViewModel_initialize_waveformBarCountMatchesDuration() {
        val viewModel = PlaybackViewModel(app)
        // 3 seconds of audio at 48 kHz / 16-bit / mono = 288 000 bytes
        val wavFile = createTestWavFile(audioData = ByteArray(288_000) { 42 })

        viewModel.initialize(context, wavFile.toRecorderFile(), null)
        Thread.sleep(400)

        val bars = viewModel.accumulatedWaveformData.value.amplitudes.size
        // At 50 ms resolution: 3 s → 60 bars (allow ±2 for rounding)
        assertTrue("Expected ~60 bars, got $bars", bars in 58..62)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun File.toRecorderFile() = RecorderFile(
        name = nameWithoutExtension,
        filePath = absolutePath,
        durationMs = 1000L,
        createdTime = System.currentTimeMillis()
    )

    // Helper function
    private fun createTestWavFile(
        fileName: String = "test.wav",
        sampleRate: Int = 48000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
        audioData: ByteArray
    ): File {
        val file = File(testDir, fileName)
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + audioData.size)
        buffer.put("WAVE".toByteArray())

        // fmt sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * (bitsPerSample / 8))
        buffer.putShort((channels * (bitsPerSample / 8)).toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(audioData.size)

        file.writeBytes(header + audioData)
        return file
    }
}
