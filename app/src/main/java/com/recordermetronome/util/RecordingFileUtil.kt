package com.recordermetronome.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.recordermetronome.data.RecordingFile
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
                    val durationInMs = getWavDuration(file)
                    val createdTime = file.lastModified()
                    recordingFiles.add(
                        RecordingFile(
                            name = file.nameWithoutExtension,
                            filePath = file.absolutePath,
                            durationMs = durationInMs,
                            createdTime = createdTime,
                            sizeKb = file.length() / 1024
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
        var durationInMs = 0L

        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(44)
                raf.readFully(header)

                val buffer = ByteBuffer.wrap(header)
                buffer.order(ByteOrder.LITTLE_ENDIAN)

                val sampleRate = buffer.getInt(24)
                val channels = buffer.getShort(22).toInt()
                val bitsPerSample = buffer.getShort(34).toInt()

                // Validate parsed values
                if (sampleRate <= 0 || channels <= 0 || bitsPerSample <= 0) {
                    return 0L
                }

                val dataSize = file.length() - 44
                val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)

                if (bytesPerSecond > 0) {
                    val durationSeconds = dataSize / bytesPerSecond
                    durationInMs = durationSeconds * 1000
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return durationInMs
    }

    /**
     * Generate default file name for new recordings
     */
    fun generateDefaultFileName(): String {
        val dateFormat = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.US)
        return "Recording ${dateFormat.format(Date())}"
    }

    /**
     * Save audio data to disk in the recordings directory
     */
    fun saveRecording(context: Context, fileName: String, audioData: ByteArray) {
        if (audioData.isNotEmpty()) {
            try {
                // Create recordings directory
                val recordingsDir = getRecordingsDirectory(context)

                // Create file with .wav extension
                val outputFile = File(recordingsDir, "$fileName.wav")

                // Write audio data to file
                outputFile.writeBytes(audioData)

                println("Recording saved: ${outputFile.absolutePath}")
            } catch (e: Exception) {
                println("Error saving recording: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Delete a recording file
     */
    fun deleteRecording(recording: RecordingFile) {
        try {
            val file = File(recording.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            println("Error deleting recording: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Rename a recording file
     */
    fun renameRecording(recording: RecordingFile, newName: String) {
        try {
            val oldFile = File(recording.filePath)
            if (oldFile.exists()) {
                val newFile = File(oldFile.parentFile, "$newName.wav")
                if (newFile.exists()) {
                    throw Exception("File with the same name already exists")
                }
                oldFile.renameTo(newFile)
            }
        } catch (e: Exception) {
            println("Error renaming recording: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Share a recording file
     */
    fun shareRecording(context: Context, recording: RecordingFile) {
        val file = File(recording.filePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "audio/wav"
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Recording"))
    }
}

