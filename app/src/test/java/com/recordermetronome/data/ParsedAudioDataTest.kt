package com.recordermetronome.data

import org.junit.Test
import org.junit.Assert.*

class ParsedAudioDataTest {

    @Test
    fun parsedAudioData_withValidHeader_storesAllValues() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val sampleRate = 44100
        val channels = 1
        val bitsPerSample = 16
        val hasValidHeader = true

        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            hasValidHeader = hasValidHeader
        )

        assertTrue(parsed.audioData.contentEquals(audioData))
        assertEquals(sampleRate, parsed.sampleRate)
        assertEquals(channels, parsed.channels)
        assertEquals(bitsPerSample, parsed.bitsPerSample)
        assertTrue(parsed.hasValidHeader)
    }

    @Test
    fun parsedAudioData_withoutValidHeader_storesDefaultValues() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = false
        )

        assertFalse(parsed.hasValidHeader)
    }

    @Test
    fun parsedAudioData_withEmptyAudioData_storesEmptyArray() {
        val audioData = byteArrayOf()
        val parsed = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        assertEquals(0, parsed.audioData.size)
    }

    @Test
    fun parsedAudioData_equality_withSameValues_areEqual() {
        val audioData1 = byteArrayOf(1, 2, 3, 4, 5)
        val audioData2 = byteArrayOf(1, 2, 3, 4, 5)

        val parsed1 = ParsedAudioData(
            audioData = audioData1,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        val parsed2 = ParsedAudioData(
            audioData = audioData2,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        assertEquals(parsed1, parsed2)
    }

    @Test
    fun parsedAudioData_inequality_withDifferentAudioData() {
        val parsed1 = ParsedAudioData(
            audioData = byteArrayOf(1, 2, 3),
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        val parsed2 = ParsedAudioData(
            audioData = byteArrayOf(1, 2, 4),
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        assertNotEquals(parsed1, parsed2)
    }

    @Test
    fun parsedAudioData_inequality_withDifferentSampleRate() {
        val audioData = byteArrayOf(1, 2, 3)
        val parsed1 = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        val parsed2 = ParsedAudioData(
            audioData = audioData,
            sampleRate = 48000,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        assertNotEquals(parsed1, parsed2)
    }

    @Test
    fun parsedAudioData_inequality_withDifferentChannels() {
        val audioData = byteArrayOf(1, 2, 3)
        val parsed1 = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        val parsed2 = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 2,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        assertNotEquals(parsed1, parsed2)
    }

    @Test
    fun parsedAudioData_inequality_withDifferentBitsPerSample() {
        val audioData = byteArrayOf(1, 2, 3)
        val parsed1 = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        val parsed2 = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 24,
            hasValidHeader = true
        )

        assertNotEquals(parsed1, parsed2)
    }

    @Test
    fun parsedAudioData_inequality_withDifferentValidHeader() {
        val audioData = byteArrayOf(1, 2, 3)
        val parsed1 = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        val parsed2 = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = false
        )

        assertNotEquals(parsed1, parsed2)
    }

    @Test
    fun parsedAudioData_hashCode_withSameValues_areEqual() {
        val audioData1 = byteArrayOf(1, 2, 3, 4, 5)
        val audioData2 = byteArrayOf(1, 2, 3, 4, 5)

        val parsed1 = ParsedAudioData(
            audioData = audioData1,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        val parsed2 = ParsedAudioData(
            audioData = audioData2,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        assertEquals(parsed1.hashCode(), parsed2.hashCode())
    }

    @Test
    fun parsedAudioData_withDifferentAudioSizes_createsCorrectly() {
        val smallAudio = byteArrayOf(1, 2, 3)
        val largeAudio = ByteArray(1000) { i -> (i % 256).toByte() }

        val parsedSmall = ParsedAudioData(
            audioData = smallAudio,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        val parsedLarge = ParsedAudioData(
            audioData = largeAudio,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        assertEquals(3, parsedSmall.audioData.size)
        assertEquals(1000, parsedLarge.audioData.size)
    }

    @Test
    fun parsedAudioData_withDifferentAudioFormats_createsCorrectly() {
        val audioData = byteArrayOf(1, 2, 3, 4)

        // Mono, 16-bit, 44100Hz
        val mono16 = ParsedAudioData(
            audioData = audioData,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            hasValidHeader = true
        )

        // Stereo, 24-bit, 48000Hz
        val stereo24 = ParsedAudioData(
            audioData = audioData,
            sampleRate = 48000,
            channels = 2,
            bitsPerSample = 24,
            hasValidHeader = true
        )

        assertEquals(1, mono16.channels)
        assertEquals(16, mono16.bitsPerSample)
        assertEquals(2, stereo24.channels)
        assertEquals(24, stereo24.bitsPerSample)
    }
}

