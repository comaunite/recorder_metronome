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

        val recording = RecordingFile(
            name = name,
            filePath = filePath,
            durationMs = duration,
            createdTime = createdTime
        )

        assertEquals(name, recording.name)
        assertEquals(filePath, recording.filePath)
        assertEquals(duration, recording.durationMs)
        assertEquals(createdTime, recording.createdTime)
    }

    @Test
    fun recordingFile_dataClass_supportsEquality() {
        val recording1 = RecordingFile("Test", "/path", 5000L, 100L)
        val recording2 = RecordingFile("Test", "/path", 5000L, 100L)

        assertEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_dataClass_inequalityWithDifferentName() {
        val recording1 = RecordingFile("Test1", "/path", 5000L, 100L)
        val recording2 = RecordingFile("Test2", "/path", 5000L, 100L)

        assertNotEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_dataClass_inequalityWithDifferentPath() {
        val recording1 = RecordingFile("Test", "/path1", 5000L, 100L)
        val recording2 = RecordingFile("Test", "/path2", 5000L, 100L)

        assertNotEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_dataClass_inequalityWithDifferentDuration() {
        val recording1 = RecordingFile("Test", "/path", 5000L, 100L)
        val recording2 = RecordingFile("Test", "/path", 6000L, 100L)

        assertNotEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_dataClass_inequalityWithDifferentCreatedTime() {
        val recording1 = RecordingFile("Test", "/path", 5000L, 100L)
        val recording2 = RecordingFile("Test", "/path", 5000L, 200L)

        assertNotEquals(recording1, recording2)
    }

    @Test
    fun recordingFile_toString_containsAllFields() {
        val recording = RecordingFile("TestRec", "/path/to/rec.wav", 5000L, 123456L)
        val stringRep = recording.toString()

        assertTrue(stringRep.contains("TestRec"))
        assertTrue(stringRep.contains("/path/to/rec.wav"))
        assertTrue(stringRep.contains("5000"))
        assertTrue(stringRep.contains("123456"))
    }

    @Test
    fun recordingFile_copy_createsNewInstanceWithChangedField() {
        val original = RecordingFile("Original", "/path", 5000L, 100L)
        val copied = original.copy(name = "Modified")

        assertEquals("Modified", copied.name)
        assertEquals(original.filePath, copied.filePath)
        assertEquals(original.durationMs, copied.durationMs)
        assertEquals(original.createdTime, copied.createdTime)
    }

    @Test
    fun recordingFile_copyWithMultipleChanges() {
        val original = RecordingFile("Original", "/path", 5000L, 100L)
        val copied = original.copy(name = "New", durationMs = 10000L)

        assertEquals("New", copied.name)
        assertEquals("/path", copied.filePath)
        assertEquals(10000L, copied.durationMs)
        assertEquals(100L, copied.createdTime)
    }
}

