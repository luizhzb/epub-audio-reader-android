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
 * Engine TTS usando Sherpa-ONNX com modelos VITS/Piper.
 */
@Singleton
class SherpaOnnxTtsEngine @Inject constructor() : TtsEngine {

    private var tts: OfflineTts? = null

    override val isInitialized: Boolean
        get() = tts != null

    override val sampleRate: Int
        get() = tts?.sampleRate() ?: 22050

    /**
     * Inicializa o TTS com o modelo do diretorio especificado.
     *
     * @param modelDir Diretorio contendo model.onnx e model.onnx.json
     */
    override suspend fun initialize(modelDir: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(modelDir)
            val modelFile = File(dir, "model.onnx")
            val configFile = File(dir, "model.onnx.json")

            if (!modelFile.exists()) {
                return@withContext false
            }

            // Config para modelo VITS/Piper
            val modelConfig = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    tokens = "",  // Piper nao usa tokens.txt separado
                    dataDir = "",  // embedded no modelo
                    dictDir = "",
                    lexicon = ""
                ),
                numThreads = 4,
                debug = false,
                provider = "cpu"
            )

            val config = OfflineTtsConfig(
                model = modelConfig,
                ruleFsts = "",
                ruleFars = "",
                maxNumSentences = 1
            )

            // Liberar instancia anterior se existir
            tts?.release()
            tts = OfflineTts(assetManager = null, config = config)

            return@withContext tts != null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Sintetiza texto em audio PCM (float samples).
     */
    override fun synthesize(text: String): FloatArray? {
        val engine = tts ?: return null
        return try {
            val audio: GeneratedAudio = engine.generate(text, sid = 0, speed = 1.0f)
            audio.samples
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun release() {
        tts?.release()
        tts = null
    }
}
