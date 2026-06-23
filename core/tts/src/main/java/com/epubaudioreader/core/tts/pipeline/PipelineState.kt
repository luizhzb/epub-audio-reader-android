package com.epubaudioreader.core.tts.pipeline

/**
 * Estado observavel do pipeline de sintese de audio.
 *
 * @property isRunning true se o pipeline esta ativo
 * @property totalSegments Numero total de segmentos no pipeline
 * @property readyCount Quantidade de segmentos com status READY
 * @property currentIndex Indice do segmento atual sendo tocado/consumido
 * @property error Mensagem de erro, se houver
 */
data class PipelineState(
    val isRunning: Boolean = false,
    val totalSegments: Int = 0,
    val readyCount: Int = 0,
    val currentIndex: Int = 0,
    val error: String? = null
)
