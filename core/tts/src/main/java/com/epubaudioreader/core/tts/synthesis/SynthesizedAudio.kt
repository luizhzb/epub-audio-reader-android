package com.epubaudioreader.core.tts.synthesis

/**
 * Áudio sintetizado em memória.
 *
 * @property samples Amostras PCM float
 * @property sampleRate Taxa de amostragem (ex: 22050 Hz)
 */
data class SynthesizedAudio(
    val samples: FloatArray,
    val sampleRate: Int
) {
    val durationMs: Long
        get() = if (sampleRate > 0) (samples.size * 1000L / sampleRate) else 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SynthesizedAudio
        return sampleRate == other.sampleRate && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = sampleRate
        result = 31 * result + samples.contentHashCode()
        return result
    }
}
