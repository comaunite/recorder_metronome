package com.recordermetronome.data

data class WaveformData(
    val amplitudes: List<Float> = emptyList(),
    val maxAmplitude: Float = 1f,
    val currentPosition: Int = 0 // Current position index in the amplitudes list
)

data class WaveformUpdate(
    val newAmplitudes: List<Float> = emptyList(),
    val maxAmplitude: Float = 10000f
)

data class PlaybackPosition(
    val currentIndex: Int = 0
)


