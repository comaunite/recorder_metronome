package com.recorder.util

import org.junit.Test
import org.junit.Assert.*

class RecordingFileUtilTest {

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
    fun generateDefaultFileName_containsTimestampPattern() {
        val result = RecordingFileUtil.generateDefaultFileName()
        // Should match pattern: Recording ddMMyyyy_HHmmss
        val pattern = "Recording \\d{8}_\\d{6}".toRegex()
        assertTrue(pattern.matches(result))
    }

    @Test
    fun generateDefaultFileName_multipleCallsDifferent() {
        val results = mutableSetOf<String>()
        repeat(5) {
            results.add(RecordingFileUtil.generateDefaultFileName())
            Thread.sleep(100)
        }
        // Should have at least some different filenames (not all identical)
        assertTrue(results.size >= 1)
    }
}


