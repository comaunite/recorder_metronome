package com.recorder.services

import com.recorder.data.RecorderFile
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
class FileServiceTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("recorder_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /** Builds a minimal valid WAV file in [dir] and returns it. */
    private fun createWavFile(
        dir: File,
        name: String,
        sampleRate: Int = 48000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
        audioData: ByteArray = ByteArray(100) { 42 }
    ): File {
        val file = File(dir, "$name.wav")
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val dataSize = audioData.size

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray())
        buf.putInt(dataSize)

        file.outputStream().use { it.write(header); it.write(audioData) }
        return file
    }

    /** Creates a file with all-zero bytes (invalid WAV header). */
    private fun createRawPcmFile(dir: File, name: String, size: Int = 200): File {
        val file = File(dir, "$name.wav")
        file.writeBytes(ByteArray(size))
        return file
    }

    // ─── generateDefaultFileName ────────────────────────────────────────────────

    @Test
    fun generateDefaultFileName_returnsNonEmpty() {
        assertTrue(FileService.generateDefaultFileName().isNotEmpty())
    }

    @Test
    fun generateDefaultFileName_startsWithRecording() {
        assertTrue(FileService.generateDefaultFileName().startsWith("Recording "))
    }

    @Test
    fun generateDefaultFileName_matchesDatePattern() {
        val result = FileService.generateDefaultFileName()
        assertTrue(result.matches(Regex("Recording \\d{8}_\\d{6}")))
    }

    @Test
    fun generateDefaultFileName_calledTwiceWithinSameSecond_samePattern() {
        val r1 = FileService.generateDefaultFileName()
        val r2 = FileService.generateDefaultFileName()
        // Both must match the pattern regardless of whether they differ in value
        assertTrue(r1.matches(Regex("Recording \\d{8}_\\d{6}")))
        assertTrue(r2.matches(Regex("Recording \\d{8}_\\d{6}")))
    }

    @Test
    fun generateDefaultFileName_afterOneSecond_returnsDifferentValue() {
        val r1 = FileService.generateDefaultFileName()
        Thread.sleep(1100)
        val r2 = FileService.generateDefaultFileName()
        assertNotEquals(r1, r2)
    }

    // ─── getRecordingsDirectory ──────────────────────────────────────────────────

    @Test
    fun getRecordingsDirectory_returnsDirectory() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        assertNotNull(dir)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun getRecordingsDirectory_calledTwice_returnsSamePath() {
        val context = RuntimeEnvironment.getApplication()
        val dir1 = FileService.getRecordingsDirectory(context)
        val dir2 = FileService.getRecordingsDirectory(context)
        assertEquals(dir1.absolutePath, dir2.absolutePath)
    }

    @Test
    fun getRecordingsDirectory_nameIsRecordings() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        assertEquals("recordings", dir.name)
    }

    // ─── getRecorderFiles ────────────────────────────────────────────────────────

    @Test
    fun getRecorderFiles_emptyDirectory_returnsEmptyList() {
        val context = RuntimeEnvironment.getApplication()
        // Ensure directory exists but is empty
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        val result = FileService.getRecorderFiles(context)
        assertTrue(result.isEmpty())
    }

    @Test
    fun getRecorderFiles_singleWavFile_returnsOneEntry() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        createWavFile(dir, "test_single")

        val result = FileService.getRecorderFiles(context)
        assertEquals(1, result.size)
        assertEquals("test_single", result[0].name)
    }

    @Test
    fun getRecorderFiles_nonWavFilesAreIgnored() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        createWavFile(dir, "valid")
        File(dir, "ignore.mp3").writeBytes(ByteArray(10))
        File(dir, "ignore.txt").writeText("text")

        val result = FileService.getRecorderFiles(context)
        assertEquals(1, result.size)
        assertEquals("valid", result[0].name)
    }

    @Test
    fun getRecorderFiles_multipleWavFiles_sortedNewestFirst() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }

        val older = createWavFile(dir, "older")
        older.setLastModified(1_000_000L)
        val newer = createWavFile(dir, "newer")
        newer.setLastModified(2_000_000L)

        val result = FileService.getRecorderFiles(context)
        assertEquals(2, result.size)
        assertEquals("newer", result[0].name)
        assertEquals("older", result[1].name)
    }

    @Test
    fun getRecorderFiles_wavFile_populatesSizeKb() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        createWavFile(dir, "sized", audioData = ByteArray(2048))

        val result = FileService.getRecorderFiles(context)
        assertEquals(1, result.size)
        assertTrue(result[0].sizeKb > 0)
    }

    @Test
    fun getRecorderFiles_wavFile_populatesDurationMs() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        // 48000 Hz, mono, 16-bit → 88200 bytes/s; use 88200 bytes of audio → 1000 ms
        createWavFile(dir, "duration", audioData = ByteArray(88200))

        val result = FileService.getRecorderFiles(context)
        assertEquals(1, result.size)
        assertEquals(1000L, result[0].durationMs)
    }

    @Test
    fun getRecorderFiles_rawPcmWavFile_doesNotCrash() {
        // A file with all-zero header (invalid WAV) should not throw; it falls back
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        createRawPcmFile(dir, "rawpcm")

        val result = FileService.getRecorderFiles(context)
        assertEquals(1, result.size)
    }

    // ─── saveRecording ───────────────────────────────────────────────────────────

    @Test
    fun saveRecording_emptyAudioData_doesNotCreateFile() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        FileService.saveRecording(context, "empty", ByteArray(0))
        assertFalse(File(dir, "empty.wav").exists())
    }

    @Test
    fun saveRecording_nonEmptyAudioData_createsWavFile() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        FileService.saveRecording(context, "saved", ByteArray(100) { 7 })
        assertTrue(File(dir, "saved.wav").exists())
    }

    @Test
    fun saveRecording_createdFile_hasWavHeader() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        FileService.saveRecording(context, "headered", ByteArray(100) { 1 })
        val file = File(dir, "headered.wav")
        val bytes = file.readBytes()
        // First 4 bytes should be "RIFF"
        assertEquals("RIFF", String(bytes.copyOf(4)))
        // Bytes 8-11 should be "WAVE"
        assertEquals("WAVE", String(bytes.copyOfRange(8, 12)))
    }

    @Test
    fun saveRecording_createdFile_containsAudioData() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        val audio = ByteArray(50) { 99.toByte() }
        FileService.saveRecording(context, "audio", audio)
        val file = File(dir, "audio.wav")
        val bytes = file.readBytes()
        // Audio data starts after the 44-byte header
        val storedAudio = bytes.copyOfRange(44, bytes.size)
        assertArrayEquals(audio, storedAudio)
    }

    @Test
    fun saveRecording_createdFile_totalSizeIs44PlusAudioLength() {
        val context = RuntimeEnvironment.getApplication()
        val dir = FileService.getRecordingsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
        val audio = ByteArray(256) { 3 }
        FileService.saveRecording(context, "size_check", audio)
        val file = File(dir, "size_check.wav")
        assertEquals(44L + audio.size, file.length())
    }

    // ─── deleteRecording ─────────────────────────────────────────────────────────

    @Test
    fun deleteRecording_existingFile_deletesIt() {
        val file = File(tempDir, "to_delete.wav")
        file.writeBytes(ByteArray(10))
        assertTrue(file.exists())

        val recording = RecorderFile("to_delete", file.absolutePath, 0L, 0L)
        FileService.deleteRecording(recording)
        assertFalse(file.exists())
    }

    @Test
    fun deleteRecording_nonExistentFile_doesNotCrash() {
        val recording = RecorderFile("ghost", "/non/existent/path/ghost.wav", 0L, 0L)
        // Should not throw
        FileService.deleteRecording(recording)
    }

    // ─── renameRecording ─────────────────────────────────────────────────────────

    @Test
    fun renameRecording_existingFile_renamesSuccessfully() {
        val file = File(tempDir, "old.wav")
        file.writeBytes(ByteArray(10))

        val recording = RecorderFile("old", file.absolutePath, 0L, 0L)
        FileService.renameRecording(recording, "new")

        assertFalse(file.exists())
        assertTrue(File(tempDir, "new.wav").exists())
    }

    @Test
    fun renameRecording_nonExistentFile_doesNotCrash() {
        val recording = RecorderFile("ghost", "/non/existent/ghost.wav", 0L, 0L)
        // Should not throw
        FileService.renameRecording(recording, "newname")
    }

    @Test
    fun renameRecording_targetAlreadyExists_doesNotOverwrite() {
        val original = File(tempDir, "original.wav")
        original.writeBytes(ByteArray(10) { 1 })
        val conflict = File(tempDir, "conflict.wav")
        conflict.writeBytes(ByteArray(10) { 2 })

        val recording = RecorderFile("original", original.absolutePath, 0L, 0L)
        // Should swallow the exception internally and not crash
        FileService.renameRecording(recording, "conflict")

        // Original should still exist because rename was blocked
        assertTrue(original.exists())
        // Conflict file must be unchanged
        assertTrue(conflict.readBytes().all { it == 2.toByte() })
    }

    // ─── readRecorderFile ────────────────────────────────────────────────────────

    @Test
    fun readRecorderFile_nonExistentFile_throwsException() {
        assertThrows(Exception::class.java) {
            FileService.readRecorderFile("/no/such/file.wav")
        }
    }

    @Test
    fun readRecorderFile_emptyFile_throwsException() {
        val file = File(tempDir, "empty.wav")
        file.writeBytes(ByteArray(0))
        assertThrows(Exception::class.java) {
            FileService.readRecorderFile(file.absolutePath)
        }
    }

    @Test
    fun readRecorderFile_validWavHeader_parsesCorrectly() {
        val audio = ByteArray(100) { 42 }
        val file = createWavFile(tempDir, "valid", sampleRate = 48000, channels = 1,
            bitsPerSample = 16, audioData = audio)

        val parsed = FileService.readRecorderFile(file.absolutePath)

        assertTrue(parsed.hasValidHeader)
        assertEquals(48000, parsed.sampleRate)
        assertEquals(1, parsed.channels)
        assertEquals(16, parsed.bitsPerSample)
        assertEquals(100, parsed.audioData.size)
        assertTrue(parsed.audioData.all { it == 42.toByte() })
    }

    @Test
    fun readRecorderFile_validWavHeader_differentParams_parsedCorrectly() {
        val audio = ByteArray(200) { 7 }
        val file = createWavFile(tempDir, "stereo", sampleRate = 48000, channels = 2,
            bitsPerSample = 16, audioData = audio)

        val parsed = FileService.readRecorderFile(file.absolutePath)

        assertTrue(parsed.hasValidHeader)
        assertEquals(48000, parsed.sampleRate)
        assertEquals(2, parsed.channels)
    }

    @Test
    fun readRecorderFile_rawPcmNoHeader_treatsEntireFileAsAudio() {
        // File shorter than 44 bytes — no header parse attempted
        val file = File(tempDir, "short.wav")
        file.writeBytes(ByteArray(20) { 5 })

        val parsed = FileService.readRecorderFile(file.absolutePath)

        assertFalse(parsed.hasValidHeader)
        assertEquals(20, parsed.audioData.size)
        // Defaults
        assertEquals(48000, parsed.sampleRate)
        assertEquals(1, parsed.channels)
        assertEquals(16, parsed.bitsPerSample)
    }

    @Test
    fun readRecorderFile_allZeroHeader_invalidHeaderFallback() {
        // File > 44 bytes but all zeros → sampleRate/channels/bitsPerSample all 0 → invalid
        val file = File(tempDir, "zeroed.wav")
        file.writeBytes(ByteArray(100))

        val parsed = FileService.readRecorderFile(file.absolutePath)

        assertFalse(parsed.hasValidHeader)
        assertEquals(100, parsed.audioData.size)
        assertEquals(48000, parsed.sampleRate)
        assertEquals(1, parsed.channels)
        assertEquals(16, parsed.bitsPerSample)
    }

    @Test
    fun readRecorderFile_exactly44Bytes_treatedAsNoValidHeader() {
        // Exactly 44 bytes: audioBytes.size > 44 is false, so header is not parsed
        val file = File(tempDir, "exact44.wav")
        file.writeBytes(ByteArray(44) { 1 })

        val parsed = FileService.readRecorderFile(file.absolutePath)

        assertFalse(parsed.hasValidHeader)
        assertEquals(44, parsed.audioData.size)
    }

    @Test
    fun readRecorderFile_validHeader_audioDataExcludesHeader() {
        val audio = ByteArray(50) { 9 }
        val file = createWavFile(tempDir, "strip_header", audioData = audio)

        val parsed = FileService.readRecorderFile(file.absolutePath)

        // audioData should be exactly the 50 bytes after the 44-byte header
        assertEquals(50, parsed.audioData.size)
        assertTrue(parsed.audioData.all { it == 9.toByte() })
    }
}
