package com.recordermetronome.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.recordermetronome.data.RecordingFile
import com.recordermetronome.data.ParsedAudioData
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

                // Check if header is valid
                if (sampleRate > 0 && channels > 0 && bitsPerSample > 0) {
                    // Valid WAV header found
                    val dataSize = file.length() - 44
                    val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)

                    if (bytesPerSecond > 0) {
                        val durationSeconds = dataSize / bytesPerSecond
                        durationInMs = durationSeconds * 1000
                    }
                } else {
                    // Invalid header, assume entire file is raw PCM data (44100 Hz, 1 channel, 16-bit)
                    val assumedSampleRate = 44100
                    val assumedChannels = 1
                    val assumedBitsPerSample = 16
                    val dataSize = file.length()
                    val bytesPerSecond = assumedSampleRate * assumedChannels * (assumedBitsPerSample / 8)

                    if (bytesPerSecond > 0) {
                        val durationSeconds = dataSize / bytesPerSecond
                        durationInMs = durationSeconds * 1000
                    }
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

                // Write WAV file with proper header
                writeWavFile(outputFile, audioData)

                println("Recording saved: ${outputFile.absolutePath}")
            } catch (e: Exception) {
                println("Error saving recording: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Write audio data as a proper WAV file with header
     */
    private fun writeWavFile(file: File, audioData: ByteArray) {
        val sampleRate = 44100
        val channels = 1 // Mono
        val bitsPerSample = 16

        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val dataSize = audioData.size

        file.outputStream().use { output ->
            // Write WAV header
            val header = ByteArray(44)
            val buffer = ByteBuffer.wrap(header)
            buffer.order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            buffer.put("RIFF".toByteArray()) // Position 0-3
            buffer.putInt(36 + dataSize) // File size - 8 (position 4-7)
            buffer.put("WAVE".toByteArray()) // Position 8-11

            // fmt sub-chunk
            buffer.put("fmt ".toByteArray()) // Position 12-15
            buffer.putInt(16) // Sub-chunk size (position 16-19)
            buffer.putShort(1) // Audio format: PCM (position 20-21)
            buffer.putShort(channels.toShort()) // Number of channels (position 22-23)
            buffer.putInt(sampleRate) // Sample rate (position 24-27)
            buffer.putInt(byteRate) // Byte rate (position 28-31)
            buffer.putShort(blockAlign.toShort()) // Block align (position 32-33)
            buffer.putShort(bitsPerSample.toShort()) // Bits per sample (position 34-35)

            // data sub-chunk
            buffer.put("data".toByteArray()) // Position 36-39
            buffer.putInt(dataSize) // Position 40-43

            // Write header
            output.write(header)
            // Write audio data
            output.write(audioData)
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

    /**
     * Read and parse a recording file, extracting audio data and WAV header information
     */
    fun readRecordingFile(filePath: String): ParsedAudioData {
        val file = File(filePath)
        if (!file.exists()) {
            println("LOAD ERROR: File does not exist: $filePath")
            throw Exception("File does not exist")
        }

        val audioBytes = file.readBytes()
        if (audioBytes.isEmpty()) {
            println("LOAD ERROR: File is empty")
            throw Exception("File is empty")
        }

        var audioData = ByteArray(0)
        var parsedSampleRate = 44100 // Default fallback
        var parsedChannels = 1
        var parsedBitsPerSample = 16
        var hasValidHeader = false

        // Try to parse WAV header
        if (audioBytes.size > 44) {
            val buffer = ByteBuffer.wrap(audioBytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)

            val sampleRate = buffer.getInt(24)
            val channels = buffer.getShort(22).toInt()
            val bitsPerSample = buffer.getShort(34).toInt()

            if (sampleRate > 0 && channels > 0 && bitsPerSample > 0) {
                // Valid header found
                audioData = audioBytes.copyOfRange(44, audioBytes.size)
                parsedSampleRate = sampleRate
                parsedChannels = channels
                parsedBitsPerSample = bitsPerSample
                hasValidHeader = true
                println("LOAD: Found valid WAV header - SR:$sampleRate CH:$channels BPS:$bitsPerSample")
            }
        }

        // If no valid header found, treat entire file as raw PCM data
        if (!hasValidHeader) {
            println("LOAD: No valid WAV header found, treating entire file as raw PCM data")
            audioData = audioBytes
        }

        return ParsedAudioData(
            audioData = audioData,
            sampleRate = parsedSampleRate,
            channels = parsedChannels,
            bitsPerSample = parsedBitsPerSample,
            hasValidHeader = hasValidHeader
        )
    }
}

