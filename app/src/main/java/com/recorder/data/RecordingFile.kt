package com.recorder.data

data class RecordingFile(
    val name: String,
    val filePath: String,
    val durationMs: Long, // in milliseconds
    val createdTime: Long, // in milliseconds since epoch
    val sizeKb: Long = 0
)

