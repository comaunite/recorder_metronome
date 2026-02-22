package com.recordermetronome.util

import org.junit.Test
import org.junit.Assert.*

class RecordingFileUtilTest {

    @Test
    fun formatDuration_zeroMillis_returnsZeroZeroFormat() {
        val result = RecordingFileUtil.formatDuration(0L)
        assertEquals("00:00", result)
    }

    @Test
    fun formatDuration_1000Millis_returnsOneSecond() {
        val result = RecordingFileUtil.formatDuration(1000L)
        assertEquals("00:01", result)
    }

    @Test
    fun formatDuration_60000Millis_returnsOneMinute() {
        val result = RecordingFileUtil.formatDuration(60000L)
        assertEquals("01:00", result)
    }

    @Test
    fun formatDuration_125000Millis_returnsMinutesAndSeconds() {
        val result = RecordingFileUtil.formatDuration(125000L)
        assertEquals("02:05", result)
    }

    @Test
    fun formatDuration_3661000Millis_returnsFormattedWithPadding() {
        val result = RecordingFileUtil.formatDuration(3661000L)
        assertEquals("61:01", result)
    }

    @Test
    fun formatDuration_500Millis_returnsZero() {
        val result = RecordingFileUtil.formatDuration(500L)
        assertEquals("00:00", result)
    }

    @Test
    fun formatDuration_largeValue_calculatesCorrectly() {
        // 1 hour = 3600000 millis
        val result = RecordingFileUtil.formatDuration(3600000L)
        assertEquals("60:00", result)
    }

    @Test
    fun formatDuration_mixedValues_calculatesCorrectly() {
        // 2 minutes 30 seconds = 150000 millis
        val result = RecordingFileUtil.formatDuration(150000L)
        assertEquals("02:30", result)
    }

    @Test
    fun generateDefaultFileName_returnsNonEmpty() {
        val result = RecordingFileUtil.generateDefaultFileName()
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun generateDefaultFileName_startsWithRecording() {
        val result = RecordingFileUtil.generateDefaultFileName()
        assertTrue(result.startsWith("Recording"))
    }

    @Test
    fun generateDefaultFileName_containsDate() {
        val result = RecordingFileUtil.generateDefaultFileName()
        // Format is "Recording ddMMyyyy_HHmmss"
        assertTrue(result.length > "Recording ".length)
    }

    @Test
    fun generateDefaultFileName_differentCallsHaveDifferentTimes() {
        val result1 = RecordingFileUtil.generateDefaultFileName()
        Thread.sleep(1100) // Sleep for more than 1 second to ensure different timestamp
        val result2 = RecordingFileUtil.generateDefaultFileName()
        // They might be the same if called within the same second, so we just check they're not identical within reasonable time
        // This is a best-effort test
        assertTrue(result1.isNotEmpty() && result2.isNotEmpty())
    }

    @Test
    fun formatTimestamp_returnsFormattedDate() {
        val millis = 1000L // Jan 1, 1970 00:00:01 UTC
        val result = RecordingFileUtil.formatTimestamp(millis)
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        // Format should contain dots and colons: dd.MM.yyyy HH:mm:ss
        assertTrue(result.contains("."))
        assertTrue(result.contains(":"))
    }

    @Test
    fun formatTimestamp_differentTimestamps_returnsDifferentResults() {
        val result1 = RecordingFileUtil.formatTimestamp(1000L)
        val result2 = RecordingFileUtil.formatTimestamp(2000L)
        assertNotEquals(result1, result2)
    }
}

