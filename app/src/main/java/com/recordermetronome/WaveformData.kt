package com.recordermetronome

data class WaveformData(
    val amplitudes: List<Float> = emptyList(),
    val maxAmplitude: Float = 1f
)
