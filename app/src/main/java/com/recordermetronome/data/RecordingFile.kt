package com.recordermetronome.data

data class RecordingFile(
    val name: String,
    val filePath: String,
    val duration: Long, // in milliseconds
    val createdTime: Long // in milliseconds since epoch
)

