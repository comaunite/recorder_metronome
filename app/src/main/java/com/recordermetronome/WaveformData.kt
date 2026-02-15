package com.recordermetronome

data class WaveformData(
    val amplitudes: List<Float> = emptyList(),
    val maxAmplitude: Float = 1f,
    val currentPosition: Int = 0 // Current position index in the amplitudes list
)
