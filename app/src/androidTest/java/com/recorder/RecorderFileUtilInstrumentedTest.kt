package com.recorder

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import com.recorder.util.RecorderFileUtil
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class RecorderFileUtilInstrumentedTest {
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
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(100) { 42 }
        )

        val parsed = RecorderFileUtil.readRecorderFile(wavFile.absolutePath)

        assertEquals(44100, parsed.sampleRate)
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

        val parsed = RecorderFileUtil.readRecorderFile(pcmFile.absolutePath)

        assertEquals(44100, parsed.sampleRate) // Default fallback
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

        val parsed = RecorderFileUtil.readRecorderFile(invalidFile.absolutePath)

        assertFalse(parsed.hasValidHeader)
        // Should treat entire file as raw PCM (header + audio)
        assertEquals(144, parsed.audioData.size) // 44 header bytes + 100 audio bytes
    }

    @Test
    fun readRecorderFile_withNonExistentFile_throwsException() {
        val nonExistentPath = File(testDir, "non_existent.wav").absolutePath

        assertThrows(Exception::class.java) {
            RecorderFileUtil.readRecorderFile(nonExistentPath)
        }
    }

    @Test
    fun readRecorderFile_withEmptyFile_throwsException() {
        val emptyFile = File(testDir, "empty.wav")
        emptyFile.createNewFile()

        assertThrows(Exception::class.java) {
            RecorderFileUtil.readRecorderFile(emptyFile.absolutePath)
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

        val parsed = RecorderFileUtil.readRecorderFile(wavFile.absolutePath)

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
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(10)
        )

        val smallParsed = RecorderFileUtil.readRecorderFile(smallFile.absolutePath)
        assertEquals(10, smallParsed.audioData.size)

        // Large audio
        val largeFile = createTestWavFile(
            fileName = "test_large.wav",
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(10000)
        )

        val largeParsed = RecorderFileUtil.readRecorderFile(largeFile.absolutePath)
        assertEquals(10000, largeParsed.audioData.size)
    }

    @Test
    fun readRecorderFile_multipleReads_returnConsistentData() {
        val wavFile = createTestWavFile(
            fileName = "test_consistent.wav",
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            audioData = ByteArray(100) { 88 }
        )

        val parsed1 = RecorderFileUtil.readRecorderFile(wavFile.absolutePath)
        val parsed2 = RecorderFileUtil.readRecorderFile(wavFile.absolutePath)

        assertEquals(parsed1.sampleRate, parsed2.sampleRate)
        assertEquals(parsed1.channels, parsed2.channels)
        assertEquals(parsed1.bitsPerSample, parsed2.bitsPerSample)
        assertEquals(parsed1.hasValidHeader, parsed2.hasValidHeader)
        assertTrue(parsed1.audioData.contentEquals(parsed2.audioData))
    }

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


