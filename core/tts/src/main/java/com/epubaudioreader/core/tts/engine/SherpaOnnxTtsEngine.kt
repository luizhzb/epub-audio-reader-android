package com.epubaudioreader.core.tts.engine

import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação do [TtsEngine] usando a biblioteca Sherpa-ONNX [OfflineTts].
 *
 * Integra modelos VITS/Piper para síntese de voz de alta qualidade offline.
 * Suporta múltiplos idiomas via modelos ONNX carregados dinamicamente.
 *
 * @sampleRate padrão de 22050 Hz (Piper models).
 */
@Singleton
class SherpaOnnxTtsEngine @Inject constructor() : TtsEngine {

    companion object {
        const val DEFAULT_SAMPLE_RATE = 22050
        const val DEFAULT_NUM_THREADS = 4
        const val DEFAULT_PROVIDER = "cpu"
        const val DEFAULT_SPEAKER_ID = 0
        const val DEFAULT_SPEED = 1.0f
        const val DEFAULT_MAX_NUM_SENTENCES = 1
    }

    private var tts: OfflineTts? = null
    private var currentModelDir: String = ""

    override val isInitialized: Boolean
        get() = tts != null

    override val sampleRate: Int
        get() = tts?.sampleRate() ?: DEFAULT_SAMPLE_RATE

    /**
     * Inicializa o engine TTS carregando o modelo do diretório [modelDir].
     *
     * O diretório deve conter os seguintes arquivos (estrutura Sherpa-ONNX/Piper):
     * - model.onnx          : modelo neural ONNX
     * - tokens.txt          : mapeamento de tokens
     * - lexicon.txt         : léxico de pronúncia (opcional)
     * - dict/               : diretório de dicionários (opcional)
     * - espeak-ng-data/     : dados do espeak-ng (opcional, para G2P)
     *
     * @param modelDir Caminho absoluto do diretório com os arquivos do modelo.
     * @return true se o modelo foi carregado com sucesso.
     */
    override suspend fun initialize(modelDir: String): Boolean = withContext(Dispatchers.Default) {
        try {
            release()

            val modelFile = File(modelDir, "model.onnx")
            val tokensFile = File(modelDir, "tokens.txt")
            val lexiconFile = File(modelDir, "lexicon.txt")
            val dictDir = File(modelDir, "dict")
            val dataDir = File(modelDir, "espeak-ng-data")

            // Validação dos arquivos obrigatórios
            if (!modelFile.exists()) {
                throw IllegalStateException(
                    "Modelo ONNX não encontrado: ${modelFile.absolutePath}. " +
                    "Certifique-se de que o arquivo 'model.onnx' existe no diretório."
                )
            }
            if (!tokensFile.exists()) {
                throw IllegalStateException(
                    "Arquivo de tokens não encontrado: ${tokensFile.absolutePath}. " +
                    "Certifique-se de que o arquivo 'tokens.txt' existe no diretório."
                )
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelFile.absolutePath,
                        tokens = tokensFile.absolutePath,
                        dataDir = if (dataDir.exists()) dataDir.absolutePath else "",
                        dictDir = if (dictDir.exists()) dictDir.absolutePath else "",
                        lexicon = if (lexiconFile.exists()) lexiconFile.absolutePath else ""
                    ),
                    numThreads = DEFAULT_NUM_THREADS,
                    debug = false,
                    provider = DEFAULT_PROVIDER
                ),
                ruleFsts = "",
                ruleFars = "",
                maxNumSentences = DEFAULT_MAX_NUM_SENTENCES
            )

            tts = OfflineTts(assetManager = null, config = config)
            currentModelDir = modelDir

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sintetiza o [text] em áudio usando o modelo carregado.
     *
     * @param text Texto a ser sintetizado. Deve estar em português para modelos pt-BR.
     * @return FloatArray com amostras PCM no intervalo [-1.0, 1.0], ou null se falhar.
     */
    override fun synthesize(text: String): FloatArray? {
        val currentTts = tts ?: run {
            return null
        }

        return try {
            if (text.isBlank()) {
                return FloatArray(0)
            }

            val audio: GeneratedAudio = currentTts.generate(
                text = text.trim(),
                sid = DEFAULT_SPEAKER_ID,
                speed = DEFAULT_SPEED
            )

            audio.samples
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Libera todos os recursos nativos do Sherpa-ONNX.
     * Deve ser chamado quando o engine não for mais necessário.
     */
    override fun release() {
        try {
            tts?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tts = null
            currentModelDir = ""
        }
    }
}
