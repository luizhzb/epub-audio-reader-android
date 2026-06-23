package com.epubaudioreader.core.tts.engine

import android.util.Log
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

    companion object {
        private const val TAG = "SherpaOnnxTTS"
    }

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
        Log.d(TAG, "Inicializando com dir=$modelDir")

        try {
            val dir = File(modelDir)
            val modelFile = File(dir, "model.onnx")
            val configFile = File(dir, "model.onnx.json")

            Log.d(TAG, "model.onnx existe=${modelFile.exists()}, path=${modelFile.absolutePath}")
            Log.d(TAG, "model.onnx.json existe=${configFile.exists()}, path=${configFile.absolutePath}")

            if (!modelFile.exists()) {
                Log.e(TAG, "ERRO: model.onnx NAO encontrado em ${modelFile.absolutePath}")
                return@withContext false
            }

            if (!configFile.exists()) {
                Log.w(TAG, "AVISO: model.onnx.json NAO encontrado — modelo pode nao funcionar corretamente")
            }

            // Verificar tamanho minimo do modelo (evita arquivo vazio/corrompido)
            val modelSize = modelFile.length()
            Log.d(TAG, "Tamanho do modelo: $modelSize bytes (${modelSize / (1024 * 1024)} MB)")
            if (modelSize < 1024 * 1024) {
                Log.e(TAG, "ERRO: model.onnx muito pequeno ($modelSize bytes) — possivelmente corrompido")
                return@withContext false
            }

            // Liberar instancia anterior se existir
            if (tts != null) {
                Log.d(TAG, "Liberando instancia TTS anterior")
                tts?.release()
                tts = null
            }

            // Config para modelo VITS/Piper
            Log.d(TAG, "Criando OfflineTtsConfig...")
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

            Log.d(TAG, "Criando instancia OfflineTts...")
            tts = OfflineTts(assetManager = null, config = config)

            val success = tts != null
            if (success) {
                Log.d(TAG, "TTS inicializado com sucesso! sampleRate=${tts?.sampleRate()}")
            } else {
                Log.e(TAG, "FALHA: OfflineTts retornou null")
            }
            return@withContext success

        } catch (e: Exception) {
            Log.e(TAG, "ERRO ao inicializar TTS: ${e.message}", e)
            tts = null
            return@withContext false
        }
    }

    /**
     * Sintetiza texto em audio PCM (float samples).
     */
    override fun synthesize(text: String): FloatArray? {
        val engine = tts
        if (engine == null) {
            Log.e(TAG, "synthesize() chamado mas engine nao esta inicializada")
            return null
        }

        if (text.isBlank()) {
            Log.w(TAG, "synthesize() chamado com texto vazio")
            return null
        }

        Log.d(TAG, "Sintetizando: ${text.length} chars | texto=\"${text.take(60)}${if (text.length > 60) "..." else ""}\"")

        return try {
            val audio: GeneratedAudio = engine.generate(text, sid = 0, speed = 1.0f)
            val samples = audio.samples
            Log.d(TAG, "Sintese OK: samples=${samples.size}, durationSec=${samples.size.toFloat() / sampleRate}")
            samples

        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "ERRO JNI (biblioteca nativa nao carregada): ${e.message}", e)
            null
        } catch (e: NullPointerException) {
            Log.e(TAG, "ERRO JNI (null pointer — engine nao pronta?): ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "ERRO ao sintetizar: ${e.message}", e)
            null
        }
    }

    override fun release() {
        Log.d(TAG, "Liberando engine TTS...")
        try {
            tts?.release()
            Log.d(TAG, "Engine liberada com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "ERRO ao liberar engine: ${e.message}", e)
        } finally {
            tts = null
        }
    }
}
