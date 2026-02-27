package com.recorder.util

import com.recorder.data.RecorderFile
import org.junit.Test
import org.junit.Assert.*

class FilenameValidatorTest {

    @Test
    fun validateNewFilename_emptyFilename_returnsFalse() {
        val result = FilenameValidator.validateNewFilename("", emptyList())
        assertFalse(result.isValid)
        assertEquals("Name cannot be empty", result.errorMessage)
    }

    @Test
    fun validateNewFilename_blankFilename_returnsFalse() {
        val result = FilenameValidator.validateNewFilename("   ", emptyList())
        assertFalse(result.isValid)
        assertEquals("Name cannot be empty", result.errorMessage)
    }

    @Test
    fun validateNewFilename_validFilename_returnsTrue() {
        val result = FilenameValidator.validateNewFilename("MyRecording", emptyList())
        assertTrue(result.isValid)
        assertEquals("", result.errorMessage)
    }

    @Test
    fun validateNewFilename_duplicateFileName_returnsFalse() {
        val existingRecordings = listOf(
            RecorderFile("MyRecording", "/path/to/file", 5000L, System.currentTimeMillis())
        )
        val result = FilenameValidator.validateNewFilename("MyRecording", existingRecordings)
        assertFalse(result.isValid)
        assertEquals("A recording with this name already exists", result.errorMessage)
    }

    @Test
    fun validateNewFilename_differentFileName_returnsTrue() {
        val existingRecordings = listOf(
            RecorderFile("Recording1", "/path/to/file1", 5000L, System.currentTimeMillis()),
            RecorderFile("Recording2", "/path/to/file2", 3000L, System.currentTimeMillis())
        )
        val result = FilenameValidator.validateNewFilename("Recording3", existingRecordings)
        assertTrue(result.isValid)
        assertEquals("", result.errorMessage)
    }

    @Test
    fun validateRenameFilename_emptyFilename_returnsFalse() {
        val result = FilenameValidator.validateRenameFilename("", "OldName", emptyList())
        assertFalse(result.isValid)
        assertEquals("Name cannot be empty", result.errorMessage)
    }

    @Test
    fun validateRenameFilename_blankFilename_returnsFalse() {
        val result = FilenameValidator.validateRenameFilename("   ", "OldName", emptyList())
        assertFalse(result.isValid)
        assertEquals("Name cannot be empty", result.errorMessage)
    }

    @Test
    fun validateRenameFilename_sameAsCurrentName_returnsFalse() {
        val result = FilenameValidator.validateRenameFilename("OldName", "OldName", emptyList())
        assertFalse(result.isValid)
        assertEquals("New name must be different from the current name", result.errorMessage)
    }

    @Test
    fun validateRenameFilename_newNameAlreadyExists_returnsFalse() {
        val existingRecordings = listOf(
            RecorderFile("Recording1", "/path/to/file1", 5000L, System.currentTimeMillis()),
            RecorderFile("Recording2", "/path/to/file2", 3000L, System.currentTimeMillis())
        )
        val result = FilenameValidator.validateRenameFilename(
            "Recording1",
            "OldName",
            existingRecordings
        )
        assertFalse(result.isValid)
        assertEquals("A recording with this name already exists", result.errorMessage)
    }

    @Test
    fun validateRenameFilename_validNewName_returnsTrue() {
        val existingRecordings = listOf(
            RecorderFile("Recording1", "/path/to/file1", 5000L, System.currentTimeMillis()),
            RecorderFile("Recording2", "/path/to/file2", 3000L, System.currentTimeMillis())
        )
        val result = FilenameValidator.validateRenameFilename(
            "NewName",
            "OldName",
            existingRecordings
        )
        assertTrue(result.isValid)
        assertEquals("", result.errorMessage)
    }

    @Test
    fun validateRenameFilename_renameToExistingButSameName_returnsTrue() {
        val existingRecordings = listOf(
            RecorderFile("OldName", "/path/to/file", 5000L, System.currentTimeMillis())
        )
        val result = FilenameValidator.validateRenameFilename(
            "OldName",
            "OldName",
            existingRecordings
        )
        assertFalse(result.isValid)
    }

    @Test
    fun validateRenameFilename_caseSensitive_treatsAsNewName() {
        val existingRecordings = listOf(
            RecorderFile("recording", "/path/to/file", 5000L, System.currentTimeMillis())
        )
        val result = FilenameValidator.validateRenameFilename(
            "Recording",
            "oldname",
            existingRecordings
        )
        assertTrue(result.isValid)
    }
}

