package com.recordermetronome.data

data class ParsedAudioData(
    val audioData: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val hasValidHeader: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParsedAudioData

        if (!audioData.contentEquals(other.audioData)) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (bitsPerSample != other.bitsPerSample) return false
        if (hasValidHeader != other.hasValidHeader) return false

        return true
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + bitsPerSample
        result = 31 * result + hasValidHeader.hashCode()
        return result
    }
}

