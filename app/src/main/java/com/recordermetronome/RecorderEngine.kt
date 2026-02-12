package com.recordermetronome

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission

class RecorderEngine {
    private val sampleRate = 44100
    private val bufferSize =
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile private var isRecording = false
    fun isRecording() = isRecording

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        recorder?.startRecording()
        isRecording = true

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                recorder?.read(buffer, 0, buffer.size)
                // discard data for now
            }
        }.also { it.start() }
    }

    fun stop() {
        isRecording = false
        recordingThread?.join()
        recorder?.stop()
        recorder?.release()
        recorder = null
    }
}
