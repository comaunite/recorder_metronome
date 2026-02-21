package com.recordermetronome.util

import android.content.Context
import com.recordermetronome.data.RecordingFile
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingFileUtil {
    /**
     * Get the recordings directory
     */
    fun getRecordingsDirectory(context: Context): File {
        val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        return recordingsDir
    }

    /**
     * Get all recording files from the recordings directory
     */
    fun getRecordingFiles(context: Context): List<RecordingFile> {
        val recordingsDir = getRecordingsDirectory(context)
        val recordingFiles = mutableListOf<RecordingFile>()

        recordingsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".wav")) {
                try {
                    val duration = getWavDuration(file)
                    val createdTime = file.lastModified()
                    recordingFiles.add(
                        RecordingFile(
                            name = file.nameWithoutExtension,
                            filePath = file.absolutePath,
                            duration = duration,
                            createdTime = createdTime
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Sort by creation time (newest first)
        return recordingFiles.sortedByDescending { it.createdTime }
    }

    /**
     * Get duration of a WAV file in milliseconds
     */
    private fun getWavDuration(file: File): Long {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                70L
                // TODO: Fix the length calculation
//                // Seek to byte rate position (at offset 28 in WAV header)
//                raf.seek(24)
//                val byteRateBytes = ByteArray(4)
//                raf.read(byteRateBytes)
//                val byteRate = byteRateBytes.toInt()
//
//                // Seek to data chunk size (after "data" marker)
//                val fileBytes = raf.readBytes()
//                raf.seek(0)
//                val header = raf.readBytes()
//
//                val dataSize = getWavDataSize(header)
//                if (dataSize > 0 && byteRate > 0) {
//                    (dataSize * 1000L) / byteRate
//                } else {
//                    0L
//                }
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Extract data chunk size from WAV header
     */
    private fun getWavDataSize(wavHeader: ByteArray): Int {
        return try {
            // Find "data" marker (0x64617461)
            var dataPos = 12 // Start after RIFF header
            while (dataPos < wavHeader.size - 8) {
                if (wavHeader[dataPos].toInt() == 0x64 &&
                    wavHeader[dataPos + 1].toInt() == 0x61 &&
                    wavHeader[dataPos + 2].toInt() == 0x74 &&
                    wavHeader[dataPos + 3].toInt() == 0x61
                ) {
                    // Found data marker, read size at next 4 bytes
                    return ((wavHeader[dataPos + 7].toInt() and 0xff) shl 24) or
                            ((wavHeader[dataPos + 6].toInt() and 0xff) shl 16) or
                            ((wavHeader[dataPos + 5].toInt() and 0xff) shl 8) or
                            (wavHeader[dataPos + 4].toInt() and 0xff)
                }
                dataPos++
            }
            0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Format duration in milliseconds to MM:SS format
     */
    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    /**
     * Format timestamp for display
     */
    fun formatTimestamp(millis: Long): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
        return dateFormat.format(Date(millis))
    }

    /**
     * Generate default file name for new recordings
     */
    fun generateDefaultFileName(): String {
        val dateFormat = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.US)
        return "Recording ${dateFormat.format(Date())}"
    }
}

