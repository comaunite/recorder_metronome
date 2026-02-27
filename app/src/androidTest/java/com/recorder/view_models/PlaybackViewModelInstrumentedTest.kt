package com.recorder.view_models

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.recorder.data.RecorderFile
import com.recorder.util.RecordingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val viewModel = PlaybackViewModel()
        val wavFile = createTestWavFile(
            fileName = "test.wav",
            sampleRate = 44100,
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

        assertEquals("Test Recording", viewModel.currentRecording.value?.name)
        assertFalse(viewModel.accumulatedWaveformData.value.amplitudes.isEmpty())
    }

    @Test
    fun playbackViewModel_initialState_isCorrect() {
        val viewModel = PlaybackViewModel()

        assertEquals(RecordingState.IDLE, viewModel.recordingStateFlow.value)
        assertTrue(viewModel.accumulatedWaveformData.value.amplitudes.isEmpty())
        assertEquals(0L, viewModel.timestamp.value)
    }

    @Test
    fun playbackViewModel_withPreLoadedRecordings_usesProvidedList() {
        val viewModel = PlaybackViewModel()
        val preLoadedRecordings = listOf(
            RecorderFile("Recording 1", "/path1", 1000L, System.currentTimeMillis()),
            RecorderFile("Recording 2", "/path2", 2000L, System.currentTimeMillis())
        )

        val wavFile = createTestWavFile(
            fileName = "test.wav",
            sampleRate = 44100,
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
        val viewModel = PlaybackViewModel()
        val oldRecording = RecorderFile(
            name = "Old Name",
            filePath = "/path/old.wav",
            durationMs = 1000L,
            createdTime = System.currentTimeMillis()
        )

        viewModel.updateInMemoryCollections(oldRecording, "New Name")

        assertEquals("New Name", viewModel.currentRecording.value?.name)
    }

    @Test
    fun playbackViewModel_accumulatedWaveformData_updatesFromEngine() {
        val viewModel = PlaybackViewModel()
        val wavFile = createTestWavFile(
            fileName = "test.wav",
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(44100) { 42 } // ~1 second
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
        val viewModel = PlaybackViewModel()
        assertNull(viewModel.currentRecording.value)

        val wavFile = createTestWavFile(
            fileName = "test.wav",
            sampleRate = 44100,
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
        val viewModel = PlaybackViewModel()
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

    // Helper function
    private fun createTestWavFile(
        fileName: String,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
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

