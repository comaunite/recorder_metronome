package com.recordermetronome.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormattingHelper {
    /**
     * Format duration in milliseconds to MM:SS format
     */
    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds)
    }

    /**
     * Format duration in milliseconds to HH:MM:SS format
     */
    fun formatDurationWithMs(millis: Long): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000
        val ms = millis % 1000

        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d.%01d", hours, minutes, seconds, ms / 100)
        }

        return String.format(Locale.US, "%02d:%02d.%01d", minutes, seconds, ms / 100)
    }

    /**
     * Format timestamp for display
     */
    fun formatTimestamp(millis: Long): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
        return dateFormat.format(Date(millis))
    }

    /**
     * Format file size in KB or MB
     */
    fun formatFileSize(sizeKb: Long): String {
        return if (sizeKb > 1024) {
            String.format(Locale.US, "%.2f MB", sizeKb / 1024f)
        } else {
            String.format(Locale.US, "%d KB", sizeKb)
        }
    }
}