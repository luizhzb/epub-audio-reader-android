package com.epubaudioreader.core.tts.engine

/**
 * Interface para motores de síntese de voz TTS (Text-to-Speech).
 *
 * Implementações concretas como [SherpaOnnxTtsEngine] fornecem
 * a lógica de síntese usando diferentes bibliotecas e modelos.
 */
interface TtsEngine {

    /** Indica se o engine foi inicializado com sucesso. */
    val isInitialized: Boolean

    /** Taxa de amostragem do áudio sintetizado em Hz. */
    val sampleRate: Int

    /**
     * Inicializa o engine TTS carregando o modelo do diretório [modelDir].
     *
     * @param modelDir Caminho absoluto do diretório com os arquivos do modelo.
     * @return true se o modelo foi carregado com sucesso.
     */
    suspend fun initialize(modelDir: String): Boolean

    /**
     * Sintetiza o [text] em áudio.
     *
     * @param text Texto a ser sintetizado.
     * @return FloatArray com amostras PCM no intervalo [-1.0, 1.0], ou null se falhar.
     */
    fun synthesize(text: String): FloatArray?

    /**
     * Libera todos os recursos nativos do engine TTS.
     */
    fun release()
}
