package com.recorder.util

import com.recorder.data.RecorderFile

object FilenameValidator {
    /**
     * Validate if a filename is valid for a new recording
     * @param filename The filename to validate
     * @param existingRecordings The list of existing recordings to check against
     * @return ValidationResult containing the validity and error message
     */
    fun validateNewFilename(filename: String, existingRecordings: List<RecorderFile>): ValidationResult {
        return when {
            filename.isBlank() -> ValidationResult(false, "Name cannot be empty")
            existingRecordings.any { it.name == filename } ->
                ValidationResult(false, "A recording with this name already exists")
            else -> ValidationResult(true, "")
        }
    }

    /**
     * Validate if a filename is valid for renaming a recording
     * @param filename The new filename
     * @param currentName The current recording name
     * @param existingRecordings The list of existing recordings to check against
     * @return ValidationResult containing the validity and error message
     */
    fun validateRenameFilename(
        filename: String,
        currentName: String,
        existingRecordings: List<RecorderFile>
    ): ValidationResult {
        return when {
            filename.isBlank() -> ValidationResult(false, "Name cannot be empty")
            filename == currentName -> ValidationResult(false, "New name must be different from the current name")
            existingRecordings.any { it.name == filename && it.name != currentName } ->
                ValidationResult(false, "A recording with this name already exists")
            else -> ValidationResult(true, "")
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )
}

