package com.recorder.util

import org.junit.Test
import org.junit.Assert.*

class FormattingHelperTest {

    @Test
    fun formatDurationWithMs_zeroMillis_returnsZeroFormat() {
        val result = FormattingHelper.formatDurationWithMs(0L)
        assertEquals("00:00.0", result)
    }

    @Test
    fun formatDurationWithMs_oneSecond_returnsFormatted() {
        val result = FormattingHelper.formatDurationWithMs(1000L)
        assertEquals("00:01.0", result)
    }

    @Test
    fun formatDurationWithMs_oneMinute_returnsFormatted() {
        val result = FormattingHelper.formatDurationWithMs(60000L)
        assertEquals("01:00.0", result)
    }

    @Test
    fun formatDurationWithMs_oneMinute30Seconds_returnsFormatted() {
        val result = FormattingHelper.formatDurationWithMs(90500L)
        assertEquals("01:30.5", result)
    }

    @Test
    fun formatDurationWithMs_withHours_returnsFormattedWithHours() {
        // 1 hour, 30 minutes, 45 seconds, 600 millis
        val result = FormattingHelper.formatDurationWithMs(5445600L)
        assertEquals("01:30:45.6", result)
    }

    @Test
    fun formatDurationWithMs_multipleHours_returnsFormatted() {
        // 2 hours = 7200000
        val result = FormattingHelper.formatDurationWithMs(7200000L)
        assertEquals("02:00:00.0", result)
    }

    @Test
    fun formatDurationWithMs_fractionalSeconds_truncatesMillis() {
        // 1234 millis = 1 second and 234 millis
        val result = FormattingHelper.formatDurationWithMs(1234L)
        assertEquals("00:01.2", result)
    }

    @Test
    fun formatDurationWithMs_allNines_returnsFormatted() {
        // 9 minutes, 59 seconds, 900 millis
        val result = FormattingHelper.formatDurationWithMs(599900L)
        assertEquals("09:59.9", result)
    }

    @Test
    fun formatDurationWithMs_padding_ensuresLeadingZeros() {
        val result = FormattingHelper.formatDurationWithMs(5000L)
        assertEquals("00:05.0", result)
        assertTrue(result.startsWith("00:"))
    }

    @Test
    fun formatDurationWithMs_smallValue_returnsZeroPaddedMinutes() {
        val result = FormattingHelper.formatDurationWithMs(500L)
        // 500 millis = 0 minutes, 0 seconds, 500 millis (displayed as .5)
        assertEquals("00:00.5", result)
    }

    @Test
    fun formatDuration_zeroMillis_returnsZeroZeroFormat() {
        val result = FormattingHelper.formatDuration(0L)
        assertEquals("00:00", result)
    }

    @Test
    fun formatDuration_1000Millis_returnsOneSecond() {
        val result = FormattingHelper.formatDuration(1000L)
        assertEquals("00:01", result)
    }

    @Test
    fun formatDuration_60000Millis_returnsOneMinute() {
        val result = FormattingHelper.formatDuration(60000L)
        assertEquals("01:00", result)
    }

    @Test
    fun formatDuration_125000Millis_returnsMinutesAndSeconds() {
        val result = FormattingHelper.formatDuration(125000L)
        assertEquals("02:05", result)
    }

    @Test
    fun formatDuration_3661000Millis_returnsFormattedWithPadding() {
        val result = FormattingHelper.formatDuration(3661000L)
        assertEquals("61:01", result)
    }

    @Test
    fun formatDuration_500Millis_returnsZero() {
        val result = FormattingHelper.formatDuration(500L)
        assertEquals("00:00", result)
    }

    @Test
    fun formatDuration_largeValue_calculatesCorrectly() {
        // 1 hour = 3600000 millis
        val result = FormattingHelper.formatDuration(3600000L)
        assertEquals("60:00", result)
    }

    @Test
    fun formatDuration_mixedValues_calculatesCorrectly() {
        // 2 minutes 30 seconds = 150000 millis
        val result = FormattingHelper.formatDuration(150000L)
        assertEquals("02:30", result)
    }

    @Test
    fun formatTimestamp_returnsFormattedDate() {
        val millis = 1000L // Jan 1, 1970 00:00:01 UTC
        val result = FormattingHelper.formatTimestamp(millis)
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        // Format should contain dots and colons: dd.MM.yyyy HH:mm:ss
        assertTrue(result.contains("."))
        assertTrue(result.contains(":"))
    }

    @Test
    fun formatTimestamp_differentTimestamps_returnsDifferentResults() {
        val result1 = FormattingHelper.formatTimestamp(1000L)
        val result2 = FormattingHelper.formatTimestamp(2000L)
        assertNotEquals(result1, result2)
    }

    @Test
    fun formatTimestamp_knownEpoch_returnsExpectedFormat() {
        // Use a fixed date: 2024-06-15 12:30:45 UTC = 1718451045000L
        val result = FormattingHelper.formatTimestamp(1718451045000L)
        // Should match dd.MM.yyyy HH:mm:ss — verify structure and content
        assertTrue("Expected dd.MM.yyyy HH:mm:ss format", result.matches(Regex("""\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}:\d{2}""")))
    }

    // --- formatFileSize ---

    @Test
    fun formatFileSize_exactly1024KB_returnsInMB() {
        // 1024 KB is NOT > 1024, so it should return KB format
        val result = FormattingHelper.formatFileSize(1024L)
        assertEquals("1024 KB", result)
    }

    @Test
    fun formatFileSize_1025KB_returnsInMB() {
        // 1025 KB > 1024 → MB branch
        val result = FormattingHelper.formatFileSize(1025L)
        assertEquals("1.00 MB", result)
    }

    @Test
    fun formatFileSize_2048KB_returnsExactlyTwoMB() {
        val result = FormattingHelper.formatFileSize(2048L)
        assertEquals("2.00 MB", result)
    }

    @Test
    fun formatFileSize_1536KB_returnsOnePointFiveMB() {
        // 1536 / 1024 = 1.5
        val result = FormattingHelper.formatFileSize(1536L)
        assertEquals("1.50 MB", result)
    }

    @Test
    fun formatFileSize_512KB_returnsInKB() {
        val result = FormattingHelper.formatFileSize(512L)
        assertEquals("512 KB", result)
    }

    @Test
    fun formatFileSize_1KB_returnsOneKB() {
        val result = FormattingHelper.formatFileSize(1L)
        assertEquals("1 KB", result)
    }

    @Test
    fun formatFileSize_zeroKB_returnsZeroKB() {
        val result = FormattingHelper.formatFileSize(0L)
        assertEquals("0 KB", result)
    }

    @Test
    fun formatFileSize_largeValue_returnsCorrectMB() {
        // 10240 KB = 10.00 MB
        val result = FormattingHelper.formatFileSize(10240L)
        assertEquals("10.00 MB", result)
    }
}

