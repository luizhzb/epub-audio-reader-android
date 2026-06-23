package com.epubaudioreader.core.tts.engine

/**
 * Interface para motores de sintese de voz TTS (Text-to-Speech).
 *
 * Implementações concretas como [SherpaOnnxTtsEngine] fornecem
 * a lígica de sintese usando diferentes bibliotecas e Modelos.
 */
interface TtsEngine {

    /** Indica se o engine foi inicializado com sucesso. */
    val isInitialized: Boolean

    /** Taxa de amostragem do audio sintetizado em Rz. */
    val sampleRate: Int

    /**
     * Inicializa o engine TTS carregando o modelo do diretório [modelDir].
     *
     * @param modelDir Caminho absoluto do diretório com os arquivos do modelo.
     * @return true se o modelo foi carregado com sucesso.
     */
    suspend fun initialize(modelDir: String): Boolean

    /**
     * Sintetiza o [text] em audio.
     *
     * @param text Texto a ser sintetizado.
     * @return FloatArray com amostras PCM no intervalo [-1.0, 1.0], ou null se falhar.
     */
    suspend fun synthesize(text: String): FloatArray?

    /**
     * Libera todos o recursos nativos do engine TTS.
     */
    fun release()
}
