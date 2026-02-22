package com.recordermetronome.data

import org.junit.Test
import org.junit.Assert.*

class RecordingFileTest {

    @Test
    fun recordingFile_creationWithAllFields_storesAllValues() {
        val name = "TestRecording"
        val filePath = "/path/to/file.wav"
        val duration = 5000L
        val createdTime = System.currentTimeMillis()
        val sizeKb = 512L

        val recording = RecordingFile(
            name = name,
            filePath = filePath,
            durationMs = duration,
            createdTime = createdTime,
            sizeKb = sizeKb
        )

        assertEquals(name, recording.name)
        assertEquals(filePath, recording.filePath)
        assertEquals(duration, recording.durationMs)
        assertEquals(createdTime, recording.createdTime)
        assertEquals(sizeKb, recording.sizeKb)
    }

    @Test
    fun recordingFile_creationWithoutSizeKb_defaultsToZero() {
        val recording = RecordingFile("Test", "/path", 5000L, 100L)
        assertEquals(0L, recording.sizeKb)
    }

    @Test
    fun recordingFile_dataClass_supportsEquality() {
        val recording1 = RecordingFile("Test", "/path", 5000L, 100L, 512L)
        val recording2 = RecordingFile("Test", "/path", 5000L, 100L, 512L)

        assertEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_dataClass_inequalityWithDifferentName() {
        val recording1 = RecordingFile("Test1", "/path", 5000L, 100L, 512L)
        val recording2 = RecordingFile("Test2", "/path", 5000L, 100L, 512L)

        assertNotEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_dataClass_inequalityWithDifferentPath() {
        val recording1 = RecordingFile("Test", "/path1", 5000L, 100L, 512L)
        val recording2 = RecordingFile("Test", "/path2", 5000L, 100L, 512L)

        assertNotEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_dataClass_inequalityWithDifferentDuration() {
        val recording1 = RecordingFile("Test", "/path", 5000L, 100L, 512L)
        val recording2 = RecordingFile("Test", "/path", 6000L, 100L, 512L)

        assertNotEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_dataClass_inequalityWithDifferentCreatedTime() {
        val recording1 = RecordingFile("Test", "/path", 5000L, 100L, 512L)
        val recording2 = RecordingFile("Test", "/path", 5000L, 200L, 512L)

        assertNotEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_dataClass_inequalityWithDifferentSizeKb() {
        val recording1 = RecordingFile("Test", "/path", 5000L, 100L, 512L)
        val recording2 = RecordingFile("Test", "/path", 5000L, 100L, 256L)

        assertNotEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_toString_containsAllFields() {
        val recording = RecordingFile("TestRec", "/path/to/rec.wav", 5000L, 123456L, 1024L)
        val stringRep = recording.toString()

        assertTrue(stringRep.contains("TestRec"))
        assertTrue(stringRep.contains("/path/to/rec.wav"))
        assertTrue(stringRep.contains("5000"))
        assertTrue(stringRep.contains("123456"))
        assertTrue(stringRep.contains("1024"))
    }

    @Test
    fun recordingFile_copy_createsNewInstanceWithChangedField() {
        val original = RecordingFile("Original", "/path", 5000L, 100L, 512L)
        val copied = original.copy(name = "Modified")

        assertEquals("Modified", copied.name)
        assertEquals(original.filePath, copied.filePath)
        assertEquals(original.durationMs, copied.durationMs)
        assertEquals(original.createdTime, copied.createdTime)
        assertEquals(original.sizeKb, copied.sizeKb)
    }

    @Test
    fun recordingFile_copyWithMultipleChanges() {
        val original = RecordingFile("Original", "/path", 5000L, 100L, 512L)
        val copied = original.copy(name = "New", durationMs = 10000L, sizeKb = 1024L)

        assertEquals("New", copied.name)
        assertEquals("/path", copied.filePath)
        assertEquals(10000L, copied.durationMs)
        assertEquals(100L, copied.createdTime)
        assertEquals(1024L, copied.sizeKb)
    }
}

