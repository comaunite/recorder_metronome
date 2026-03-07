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

    // --- validateNewFilename additional edge cases ---

    @Test
    fun validateNewFilename_matchesOneOfManyRecordings_returnsFalse() {
        val existingRecordings = listOf(
            RecorderFile("Alpha", "/path/alpha", 1000L, 0L),
            RecorderFile("Beta",  "/path/beta",  2000L, 0L),
            RecorderFile("Gamma", "/path/gamma", 3000L, 0L)
        )
        val result = FilenameValidator.validateNewFilename("Beta", existingRecordings)
        assertFalse(result.isValid)
        assertEquals("A recording with this name already exists", result.errorMessage)
    }

    @Test
    fun validateNewFilename_tabOnlyFilename_isBlank_returnsFalse() {
        val result = FilenameValidator.validateNewFilename("\t", emptyList())
        assertFalse(result.isValid)
        assertEquals("Name cannot be empty", result.errorMessage)
    }

    // --- validateRenameFilename additional edge cases ---

    @Test
    fun validateRenameFilename_newNameMatchesCurrentNameEntryInList_returnsValid() {
        // filename != currentName (different strings), but the only list entry whose
        // name == filename also has name == currentName — impossible by definition,
        // so test the realistic equivalent: list contains currentName entry only,
        // new filename is different and not in list → should be valid.
        val existingRecordings = listOf(
            RecorderFile("CurrentName", "/path/file", 1000L, 0L)
        )
        val result = FilenameValidator.validateRenameFilename(
            "BrandNewName",
            "CurrentName",
            existingRecordings
        )
        assertTrue(result.isValid)
        assertEquals("", result.errorMessage)
    }

    @Test
    fun validateRenameFilename_newNameExistsInListWithSameAsCurrentName_notBlocked() {
        // The any { it.name == filename && it.name != currentName } predicate:
        // if filename == "X" and currentName == "X", the predicate is false for that entry
        // (it.name != currentName is false), so it falls through to else → but
        // filename == currentName guard fires first → false.
        // Verify the correct branch fires (same-name guard, not duplicate guard).
        val existingRecordings = listOf(
            RecorderFile("SameName", "/path/file", 1000L, 0L)
        )
        val result = FilenameValidator.validateRenameFilename(
            "SameName",
            "SameName",
            existingRecordings
        )
        assertFalse(result.isValid)
        assertEquals("New name must be different from the current name", result.errorMessage)
    }

    @Test
    fun validateRenameFilename_emptyExistingList_validNewName_returnsTrue() {
        val result = FilenameValidator.validateRenameFilename("FreshName", "OldName", emptyList())
        assertTrue(result.isValid)
        assertEquals("", result.errorMessage)
    }

    @Test
    fun validateRenameFilename_tabOnlyFilename_isBlank_returnsFalse() {
        val result = FilenameValidator.validateRenameFilename("\t", "OldName", emptyList())
        assertFalse(result.isValid)
        assertEquals("Name cannot be empty", result.errorMessage)
    }

    // --- ValidationResult data class ---

    @Test
    fun validationResult_equality_sameValues_areEqual() {
        val a = FilenameValidator.ValidationResult(true, "")
        val b = FilenameValidator.ValidationResult(true, "")
        assertEquals(a, b)
    }

    @Test
    fun validationResult_equality_differentValues_areNotEqual() {
        val a = FilenameValidator.ValidationResult(true, "")
        val b = FilenameValidator.ValidationResult(false, "error")
        assertNotEquals(a, b)
    }

    @Test
    fun validationResult_copy_producesCorrectResult() {
        val original = FilenameValidator.ValidationResult(false, "some error")
        val copy = original.copy(isValid = true, errorMessage = "")
        assertTrue(copy.isValid)
        assertEquals("", copy.errorMessage)
    }
}

