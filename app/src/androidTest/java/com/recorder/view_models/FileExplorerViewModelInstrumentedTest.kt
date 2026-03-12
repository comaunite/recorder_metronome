package com.recorder.view_models

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class FileExplorerViewModelInstrumentedTest {
    private lateinit var context: Context
    private lateinit var viewModel: FileExplorerViewModel
    private lateinit var recordingsDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        viewModel = FileExplorerViewModel()
        // Use the same directory as the app uses (getExternalFilesDir, not filesDir)
        recordingsDir = File(context.getExternalFilesDir(null), "recordings")

        // Ensure clean state - delete all files first
        if (recordingsDir.exists()) {
            recordingsDir.listFiles()?.forEach { it.delete() }
        }
        recordingsDir.mkdirs()

        // Verify directory is empty
        assertEquals(0, recordingsDir.listFiles()?.size ?: 0)
    }

    @After
    fun tearDown() {
        // Clean up test files
        recordingsDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun useAppContext() {
        assertEquals("com.recorder", context.packageName)
    }

    @Test
    fun fileExplorerViewModel_initialState_recordingsIsEmpty() {
        val recordings = viewModel.recordings
        assertNotNull(recordings)
        assertTrue(recordings.value.isEmpty())
    }

    @Test
    fun fileExplorerViewModel_created_successfully() {
        assertNotNull(viewModel)
        assertNotNull(viewModel.recordings)
    }

    @Test
    fun fileExplorerViewModel_loadRecordings_withNoFiles_returnsEmptyList() = runBlocking {
        viewModel.loadRecordings(context)

        // Give it a moment for async operations
        Thread.sleep(100)

        val recordings = viewModel.recordings.value
        assertTrue(recordings.isEmpty())
    }

    @Test
    fun fileExplorerViewModel_loadRecordings_withOneFile_returnsOneRecording() = runBlocking {
        // Ensure clean state
        recordingsDir.listFiles()?.forEach { it.delete() }

        // Create a test WAV file
        createTestWavFile("test_recording.wav", ByteArray(1000) { 42 })

        viewModel.loadRecordings(context)

        // Give it a moment for async operations
        Thread.sleep(200)

        val recordings = viewModel.recordings.value
        assertEquals("Expected exactly 1 recording, but found ${recordings.size}: ${recordings.map { it.name }}",
            1, recordings.size)
        assertEquals("test_recording", recordings[0].name)
        assertTrue(recordings[0].filePath.contains("test_recording.wav"))
        assertTrue(recordings[0].durationMs >= 0)
        assertTrue(recordings[0].createdTime > 0)
        assertTrue(recordings[0].sizeKb >= 0)
    }

    @Test
    fun fileExplorerViewModel_loadRecordings_withMultipleFiles_returnsAllRecordings() = runBlocking {
        // Ensure clean state
        recordingsDir.listFiles()?.forEach { it.delete() }

        // Create multiple test WAV files
        createTestWavFile("recording1.wav", ByteArray(1000) { 42 })
        Thread.sleep(50)
        createTestWavFile("recording2.wav", ByteArray(2000) { 43 })
        Thread.sleep(50)
        createTestWavFile("recording3.wav", ByteArray(3000) { 44 })

        viewModel.loadRecordings(context)

        // Give it a moment for async operations
        Thread.sleep(200)

        val recordings = viewModel.recordings.value
        assertEquals("Expected exactly 3 recordings, but found ${recordings.size}: ${recordings.map { it.name }}",
            3, recordings.size)

        // Check that names are correct
        val names = recordings.map { it.name }.toSet()
        assertTrue(names.contains("recording1"))
        assertTrue(names.contains("recording2"))
        assertTrue(names.contains("recording3"))
    }

    @Test
    fun fileExplorerViewModel_loadRecordings_sortsByCreationTimeDescending() = runBlocking {
        // Ensure clean state
        recordingsDir.listFiles()?.forEach { it.delete() }

        // Create files with different timestamps
        createTestWavFile("old_recording.wav", ByteArray(1000) { 42 })
        Thread.sleep(100)
        createTestWavFile("new_recording.wav", ByteArray(1000) { 42 })

        viewModel.loadRecordings(context)
        Thread.sleep(200)

        val recordings = viewModel.recordings.value
        assertEquals("Expected exactly 2 recordings, but found ${recordings.size}: ${recordings.map { it.name }}",
            2, recordings.size)

        // Newer file should be first
        assertEquals("new_recording", recordings[0].name)
        assertEquals("old_recording", recordings[1].name)

        // Verify timestamps are sorted descending
        assertTrue(recordings[0].createdTime >= recordings[1].createdTime)
    }

    @Test
    fun fileExplorerViewModel_loadRecordings_multipleCalls_updatesRecordings() = runBlocking {
        // First load - no files
        viewModel.loadRecordings(context)
        Thread.sleep(100)
        assertTrue(viewModel.recordings.value.isEmpty())

        // Add a file
        createTestWavFile("new_file.wav", ByteArray(1000) { 42 })

        // Second load - should have one file
        viewModel.loadRecordings(context)
        Thread.sleep(100)
        assertEquals(1, viewModel.recordings.value.size)

        // Add another file
        createTestWavFile("another_file.wav", ByteArray(1000) { 42 })

        // Third load - should have two files
        viewModel.loadRecordings(context)
        Thread.sleep(100)
        assertEquals(2, viewModel.recordings.value.size)
    }

    @Test
    fun fileExplorerViewModel_loadRecordings_withDeletedFile_updatesRecordings() = runBlocking {
        // Ensure clean state
        recordingsDir.listFiles()?.forEach { it.delete() }

        // Create files
        val file1 = createTestWavFile("file1.wav", ByteArray(1000) { 42 })
        createTestWavFile("file2.wav", ByteArray(1000) { 42 })

        viewModel.loadRecordings(context)
        Thread.sleep(100)
        assertEquals(2, viewModel.recordings.value.size)

        // Delete one file
        file1.delete()

        // Reload
        viewModel.loadRecordings(context)
        Thread.sleep(100)
        assertEquals(1, viewModel.recordings.value.size)
        assertEquals("file2", viewModel.recordings.value[0].name)
    }

    @Test
    fun fileExplorerViewModel_loadRecordings_calculatesFileSizeCorrectly() = runBlocking {
        // Ensure clean state
        recordingsDir.listFiles()?.forEach { it.delete() }

        // Create file with known size
        val audioData = ByteArray(88200) { 42 } // ~86 KB
        createTestWavFile("sized_file.wav", audioData)

        viewModel.loadRecordings(context)
        Thread.sleep(100)

        val recordings = viewModel.recordings.value
        assertEquals("Expected exactly 1 recording, but found ${recordings.size}: ${recordings.map { it.name }}",
            1, recordings.size)

        val recording = recordings[0]
        // File size should be > 0 (includes WAV header + audio data)
        assertTrue(recording.sizeKb > 0)
        // Should be approximately the audio data size plus header (44 bytes)
        assertTrue(recording.sizeKb >= 86) // At least 86 KB
    }

    @Test
    fun fileExplorerViewModel_loadRecordings_ignoresNonWavFiles() = runBlocking {
        // Ensure clean state
        recordingsDir.listFiles()?.forEach { it.delete() }

        // Create a WAV file
        createTestWavFile("valid.wav", ByteArray(1000) { 42 })

        // Create non-WAV files
        File(recordingsDir, "invalid.txt").writeText("test")
        File(recordingsDir, "invalid.mp3").writeBytes(ByteArray(100))

        viewModel.loadRecordings(context)
        Thread.sleep(100)

        val recordings = viewModel.recordings.value
        // Should only find the WAV file
        assertEquals("Expected exactly 1 recording, but found ${recordings.size}: ${recordings.map { it.name }}",
            1, recordings.size)
        assertEquals("valid", recordings[0].name)
    }

    @Test
    fun fileExplorerViewModel_recordingsStateFlow_emitsUpdates() = runBlocking {
        // Ensure clean state
        recordingsDir.listFiles()?.forEach { it.delete() }

        // Initial state should be empty
        assertEquals(0, viewModel.recordings.value.size)

        // Create a file
        createTestWavFile("test.wav", ByteArray(1000) { 42 })

        // Load recordings
        viewModel.loadRecordings(context)
        Thread.sleep(200)

        // State should update
        val recordings = viewModel.recordings.value
        assertEquals(1, recordings.size)
    }

    @Test
    fun fileExplorerViewModel_loadRecordings_withLargeNumberOfFiles_loadsAll() = runBlocking {
        // Ensure clean state
        recordingsDir.listFiles()?.forEach { it.delete() }

        // Create multiple files
        repeat(10) { index ->
            createTestWavFile("recording_$index.wav", ByteArray(1000) { 42 })
            Thread.sleep(10)
        }

        viewModel.loadRecordings(context)
        Thread.sleep(300)

        val recordings = viewModel.recordings.value
        assertEquals("Expected exactly 10 recordings, but found ${recordings.size}: ${recordings.map { it.name }}",
            10, recordings.size)
    }

    /**
     * Helper function to create a test WAV file with valid header
     */
    private fun createTestWavFile(fileName: String, audioData: ByteArray): File {
        val file = File(recordingsDir, fileName)

        RandomAccessFile(file, "rw").use { raf ->
            val sampleRate = 48000
            val channels = 1
            val bitsPerSample = 16
            val byteRate = sampleRate * channels * (bitsPerSample / 8)
            val blockAlign = (channels * (bitsPerSample / 8)).toShort()
            val dataSize = audioData.size
            val fileSize = 36 + dataSize

            val header = ByteBuffer.allocate(44)
            header.order(ByteOrder.LITTLE_ENDIAN)

            // RIFF chunk descriptor
            header.put("RIFF".toByteArray())
            header.putInt(fileSize)
            header.put("WAVE".toByteArray())

            // fmt sub-chunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // Sub-chunk size
            header.putShort(1) // Audio format (1 = PCM)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign)
            header.putShort(bitsPerSample.toShort())

            // data sub-chunk
            header.put("data".toByteArray())
            header.putInt(dataSize)

            raf.write(header.array())
            raf.write(audioData)
        }

        return file
    }
}
