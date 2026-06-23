package com.epubaudioreader.core.tts.pipeline

/**
 * Representa um segmento de audio sintetizado ou em processo de sintese.
 *
 * @property segmentId Identificador do segmento (corresponde ao indice na lista de TextSegments)
 * @property samples Array de amostras PCM float (pode estar vazio se o audio for tocado diretamente pelo synthesizer)
 * @property sampleRate Taxa de amostragem do audio (ex: 22050 Hz)
 * @property status Estado atual do segmento no pipeline
 */
data class AudioSegment(
    val segmentId: Int,
    val samples: FloatArray,
    val sampleRate: Int,
    val status: SegmentStatus
) {
    /** Duracao do audio em milissegundos. Zero se nao houver samples ou sampleRate invalido. */
    val durationMs: Long
        get() = if (sampleRate > 0) (samples.size * 1000L / sampleRate) else 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioSegment
        return segmentId == other.segmentId &&
                sampleRate == other.sampleRate &&
                status == other.status &&
                samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = segmentId
        result = 31 * result + samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + status.hashCode()
        return result
    }

    override fun toString(): String {
        return "AudioSegment(segmentId=$segmentId, sampleRate=$sampleRate, status=$status, durationMs=$durationMs, samples=${samples.size})"
    }
}

/**
 * Estados possiveis de um segmento no pipeline de sintese.
 */
enum class SegmentStatus {
    /** Aguardando inicio da sintese. */
    PENDING,
    /** Sintese em andamento. */
    SYNTHESIZING,
    /** Audio pronto para playback. */
    READY,
    /** Falha durante a sintese. */
    ERROR
}
