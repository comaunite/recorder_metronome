package com.recorder

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.recorder.data.RecorderFile
import com.recorder.services.FileService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class FileServiceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_recordings")
        if (!testDir.exists()) {
            testDir.mkdirs()
        } else {
            // Clean up previous test files
            testDir.listFiles()?.forEach { it.delete() }
        }
    }

    @After
    fun tearDown() {
        // Remove anything left in the real recordings dir by these tests
        FileService.getRecordingsDirectory(context).listFiles()
            ?.filter { it.name.startsWith("_instr_test_") }
            ?.forEach { it.delete() }
        testDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun useAppContext() {
        assertEquals("com.recorder", context.packageName)
    }

    @Test
    fun recordingsDirectory_created_successfully() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context)
        assertEquals("com.recorder", context.packageName)
    }

    @Test
    fun readRecorderFile_withValidWavHeader_parsesCorrectly() {
        // Create a test WAV file with valid header
        val wavFile = createTestWavFile(
            fileName = "test_valid.wav",
            sampleRate = 48000,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(100) { 42 }
        )

        val parsed = FileService.readRecorderFile(wavFile.absolutePath)

        assertEquals(48000, parsed.sampleRate)
        assertEquals(1, parsed.channels)
        assertEquals(16, parsed.bitsPerSample)
        assertTrue(parsed.hasValidHeader)
        assertEquals(100, parsed.audioData.size)
        assertTrue(parsed.audioData.all { it == 42.toByte() })
    }

    @Test
    fun readRecorderFile_withRawPcmData_treatsAsRawAndFallsBackToDefaults() {
        // Create a file with just raw PCM data smaller than 44 bytes (can't be a valid WAV header)
        val pcmFile = File(testDir, "test_raw.wav")
        val audioData = ByteArray(20) { 55 }
        pcmFile.writeBytes(audioData)

        val parsed = FileService.readRecorderFile(pcmFile.absolutePath)

        assertEquals(48000, parsed.sampleRate) // Default fallback
        assertEquals(1, parsed.channels) // Default fallback
        assertEquals(16, parsed.bitsPerSample) // Default fallback
        assertFalse(parsed.hasValidHeader)
        assertEquals(20, parsed.audioData.size)  // Should be the entire file since it's raw PCM
    }

    @Test
    fun readRecorderFile_withInvalidHeader_fallsBackToRawPcm() {
        // Create a file with invalid WAV header (0 sample rate)
        val invalidFile = File(testDir, "test_invalid.wav")
        val header = createWavHeader(
            sampleRate = 0, // Invalid
            channels = 1,
            bitsPerSample = 16,
            dataSize = 100
        )
        val audioData = ByteArray(100) { 77 }
        invalidFile.writeBytes(header + audioData)

        val parsed = FileService.readRecorderFile(invalidFile.absolutePath)

        assertFalse(parsed.hasValidHeader)
        // Should treat entire file as raw PCM (header + audio)
        assertEquals(144, parsed.audioData.size) // 44 header bytes + 100 audio bytes
    }

    @Test
    fun readRecorderFile_withNonExistentFile_throwsException() {
        val nonExistentPath = File(testDir, "non_existent.wav").absolutePath

        assertThrows(Exception::class.java) {
            FileService.readRecorderFile(nonExistentPath)
        }
    }

    @Test
    fun readRecorderFile_withEmptyFile_throwsException() {
        val emptyFile = File(testDir, "empty.wav")
        emptyFile.createNewFile()

        assertThrows(Exception::class.java) {
            FileService.readRecorderFile(emptyFile.absolutePath)
        }
    }

    @Test
    fun readRecorderFile_with48khzStereo_parsesCorrectly() {
        val wavFile = createTestWavFile(
            fileName = "test_stereo.wav",
            sampleRate = 48000,
            channels = 2,
            bitsPerSample = 16,
            audioData = ByteArray(200) { 99 }
        )

        val parsed = FileService.readRecorderFile(wavFile.absolutePath)

        assertEquals(48000, parsed.sampleRate)
        assertEquals(2, parsed.channels)
        assertEquals(16, parsed.bitsPerSample)
        assertTrue(parsed.hasValidHeader)
    }

    @Test
    fun readRecorderFile_withVariousAudioSizes_parsesCorrectly() {
        // Small audio
        val smallFile = createTestWavFile(
            fileName = "test_small.wav",
            sampleRate = 48000,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(10)
        )

        val smallParsed = FileService.readRecorderFile(smallFile.absolutePath)
        assertEquals(10, smallParsed.audioData.size)

        // Large audio
        val largeFile = createTestWavFile(
            fileName = "test_large.wav",
            sampleRate = 48000,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(10000)
        )

        val largeParsed = FileService.readRecorderFile(largeFile.absolutePath)
        assertEquals(10000, largeParsed.audioData.size)
    }

    @Test
    fun readRecorderFile_multipleReads_returnConsistentData() {
        val wavFile = createTestWavFile(
            fileName = "test_consistent.wav",
            sampleRate = 48000,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(100) { 88 }
        )

        val parsed1 = FileService.readRecorderFile(wavFile.absolutePath)
        val parsed2 = FileService.readRecorderFile(wavFile.absolutePath)

        assertEquals(parsed1.sampleRate, parsed2.sampleRate)
        assertEquals(parsed1.channels, parsed2.channels)
        assertEquals(parsed1.bitsPerSample, parsed2.bitsPerSample)
        assertEquals(parsed1.hasValidHeader, parsed2.hasValidHeader)
        assertTrue(parsed1.audioData.contentEquals(parsed2.audioData))
    }

    // ── saveRecording ────────────────────────────────────────────────────────────

    @Test
    fun saveRecording_createsWavFileOnDisk() {
        FileService.saveRecording(context, "_instr_test_save", ByteArray(100) { 42 })
        val file = File(FileService.getRecordingsDirectory(context), "_instr_test_save.wav")
        assertTrue("WAV file should exist after save", file.exists())
    }

    @Test
    fun saveRecording_writesRiffWaveHeader() {
        FileService.saveRecording(context, "_instr_test_header", ByteArray(100) { 1 })
        val bytes = File(FileService.getRecordingsDirectory(context), "_instr_test_header.wav").readBytes()
        assertEquals("RIFF", String(bytes.copyOf(4)))
        assertEquals("WAVE", String(bytes.copyOfRange(8, 12)))
        assertEquals("fmt ", String(bytes.copyOfRange(12, 16)))
        assertEquals("data", String(bytes.copyOfRange(36, 40)))
    }

    @Test
    fun saveRecording_embeds48kHzSampleRate() {
        FileService.saveRecording(context, "_instr_test_samplerate", ByteArray(100) { 1 })
        val bytes = File(FileService.getRecordingsDirectory(context), "_instr_test_samplerate.wav").readBytes()
        val sampleRate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(48000, sampleRate)
    }

    @Test
    fun saveRecording_audioDataIsIntact() {
        val audio = ByteArray(200) { it.toByte() }
        FileService.saveRecording(context, "_instr_test_data", audio)
        val bytes = File(FileService.getRecordingsDirectory(context), "_instr_test_data.wav").readBytes()
        val stored = bytes.copyOfRange(44, bytes.size)
        assertArrayEquals(audio, stored)
    }

    @Test
    fun saveRecording_totalFileSizeIs44PlusAudioLength() {
        val audio = ByteArray(512) { 7 }
        FileService.saveRecording(context, "_instr_test_size", audio)
        val file = File(FileService.getRecordingsDirectory(context), "_instr_test_size.wav")
        assertEquals(44L + audio.size, file.length())
    }

    @Test
    fun saveRecording_withEmptyData_doesNotCreateFile() {
        FileService.saveRecording(context, "_instr_test_empty", ByteArray(0))
        val file = File(FileService.getRecordingsDirectory(context), "_instr_test_empty.wav")
        assertFalse("Empty audio should not create a file", file.exists())
    }

    // ── deleteRecording ──────────────────────────────────────────────────────────

    @Test
    fun deleteRecording_removesFileFromDisk() {
        FileService.saveRecording(context, "_instr_test_del", ByteArray(100) { 1 })
        val file = File(FileService.getRecordingsDirectory(context), "_instr_test_del.wav")
        assertTrue(file.exists())

        FileService.deleteRecording(RecorderFile("_instr_test_del", file.absolutePath, 0, 0))

        assertFalse("File should be gone after delete", file.exists())
    }

    @Test
    fun deleteRecording_nonExistentFile_doesNotCrash() {
        val ghost = RecorderFile("ghost", "/no/such/file.wav", 0, 0)
        // Must not throw
        FileService.deleteRecording(ghost)
    }

    @Test
    fun deleteRecording_leavesOtherFilesUntouched() {
        FileService.saveRecording(context, "_instr_test_del_a", ByteArray(100) { 1 })
        FileService.saveRecording(context, "_instr_test_del_b", ByteArray(100) { 2 })
        val dir = FileService.getRecordingsDirectory(context)
        val fileA = File(dir, "_instr_test_del_a.wav")
        val fileB = File(dir, "_instr_test_del_b.wav")

        FileService.deleteRecording(RecorderFile("_instr_test_del_a", fileA.absolutePath, 0, 0))

        assertFalse(fileA.exists())
        assertTrue("Sibling file must survive the delete", fileB.exists())
        fileB.delete()
    }

    // ── renameRecording ──────────────────────────────────────────────────────────

    @Test
    fun renameRecording_oldFileDisappearsNewFileAppears() {
        FileService.saveRecording(context, "_instr_test_ren_old", ByteArray(100) { 3 })
        val dir = FileService.getRecordingsDirectory(context)
        val oldFile = File(dir, "_instr_test_ren_old.wav")
        val newFile = File(dir, "_instr_test_ren_new.wav")
        try {
            assertTrue(oldFile.exists())
            FileService.renameRecording(
                RecorderFile("_instr_test_ren_old", oldFile.absolutePath, 0, 0),
                "_instr_test_ren_new"
            )
            assertFalse("Old file must be gone", oldFile.exists())
            assertTrue("New file must exist", newFile.exists())
        } finally {
            oldFile.delete(); newFile.delete()
        }
    }

    @Test
    fun renameRecording_audioDataSurvivesRename() {
        val audio = ByteArray(100) { 99.toByte() }
        FileService.saveRecording(context, "_instr_test_ren_data_old", audio)
        val dir = FileService.getRecordingsDirectory(context)
        val oldFile = File(dir, "_instr_test_ren_data_old.wav")
        val newFile = File(dir, "_instr_test_ren_data_new.wav")
        try {
            FileService.renameRecording(
                RecorderFile("_instr_test_ren_data_old", oldFile.absolutePath, 0, 0),
                "_instr_test_ren_data_new"
            )
            val stored = newFile.readBytes().copyOfRange(44, newFile.readBytes().size)
            assertArrayEquals(audio, stored)
        } finally {
            oldFile.delete(); newFile.delete()
        }
    }

    @Test
    fun renameRecording_nonExistentFile_doesNotCrash() {
        val ghost = RecorderFile("ghost", "/no/such/ghost.wav", 0, 0)
        FileService.renameRecording(ghost, "anything")
    }

    // ── getRecorderFiles ─────────────────────────────────────────────────────────

    @Test
    fun getRecorderFiles_returnsSavedFile() {
        FileService.saveRecording(context, "_instr_test_list", ByteArray(100) { 5 })
        try {
            val recordings = FileService.getRecorderFiles(context)
            assertTrue(
                "Should contain the saved file",
                recordings.any { it.name == "_instr_test_list" }
            )
        } finally {
            File(FileService.getRecordingsDirectory(context), "_instr_test_list.wav").delete()
        }
    }

    @Test
    fun getRecorderFiles_afterDelete_fileIsGone() {
        FileService.saveRecording(context, "_instr_test_list_del", ByteArray(100) { 6 })
        val file = File(FileService.getRecordingsDirectory(context), "_instr_test_list_del.wav")
        val recording = RecorderFile("_instr_test_list_del", file.absolutePath, 0, 0)

        FileService.deleteRecording(recording)

        val recordings = FileService.getRecorderFiles(context)
        assertFalse(recordings.any { it.name == "_instr_test_list_del" })
    }

    @Test
    fun getRecorderFiles_afterRename_appearsUnderNewName() {
        FileService.saveRecording(context, "_instr_test_list_ren_old", ByteArray(100) { 8 })
        val dir = FileService.getRecordingsDirectory(context)
        val oldFile = File(dir, "_instr_test_list_ren_old.wav")
        val newFile = File(dir, "_instr_test_list_ren_new.wav")
        try {
            FileService.renameRecording(
                RecorderFile("_instr_test_list_ren_old", oldFile.absolutePath, 0, 0),
                "_instr_test_list_ren_new"
            )
            val recordings = FileService.getRecorderFiles(context)
            assertFalse(recordings.any { it.name == "_instr_test_list_ren_old" })
            assertTrue(recordings.any { it.name == "_instr_test_list_ren_new" })
        } finally {
            oldFile.delete(); newFile.delete()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    // Helper functions
    private fun createTestWavFile(
        fileName: String,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        audioData: ByteArray
    ): File {
        val file = File(testDir, fileName)
        val header = createWavHeader(sampleRate, channels, bitsPerSample, audioData.size)
        file.writeBytes(header + audioData)
        return file
    }

    private fun createWavHeader(
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        dataSize: Int
    ): ByteArray {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize) // File size - 8
        buffer.put("WAVE".toByteArray())

        // fmt sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Sub-chunk size
        buffer.putShort(1) // Audio format: PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * (bitsPerSample / 8)) // Byte rate
        buffer.putShort((channels * (bitsPerSample / 8)).toShort()) // Block align
        buffer.putShort(bitsPerSample.toShort())

        // data sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        return header
    }
}


