package com.epubaudioreader.core.tts.engine

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine TTS Sherpa-ONNX com verificacao de modelo antes de inicializar.
 *
 * CORRECAO CRITICA: Verifica se o modelo .onnx existe nos assets ANTES de chamar
 * OfflineTts(). Se o modelo nao existir (download do CI falhou), retorna false
 * em vez de crashar com SIGSEGV.
 */
@Singleton
class SherpaOnnxTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsEngine {

    companion object {
        private const val TAG = "SherpaOnnxTtsEngine"
        private const val MODEL_DIR = "vits-piper-pt_BR-faber-medium"
        private const val MODEL_NAME = "pt_BR-faber-medium.onnx"
        private const val DATA_DIR = "vits-piper-pt_BR-faber-medium/espeak-ng-data"
    }

    @Volatile
    private var tts: OfflineTts? = null

    override val isInitialized: Boolean
        get() = tts != null

    override val sampleRate: Int
        get() = tts?.sampleRate() ?: 22050

    /**
     * CORRECAO CRITICA: Verifica modelo nos assets antes de criar OfflineTts.
     * Se o modelo nao existe, retorna false com mensagem clara.
     */
    override fun initialize(assetManager: AssetManager): Boolean {
        return try {
            Log.i(TAG, "[TTS_TRACE] Inicializando TTS...")

            // === VERIFICACAO CRITICA: Modelo existe nos assets? ===
            val modelPath = "$MODEL_DIR/$MODEL_NAME"
            val modelExists = try {
                assetManager.open(modelPath).close()
                true
            } catch (e: Exception) {
                Log.e(TAG, "[TTS_TRACE] Modelo NAO ENCONTRADO nos assets: $modelPath")
                false
            }

            if (!modelExists) {
                // Listar o que existe nos assets para debug
                try {
                    val rootFiles = assetManager.list("") ?: emptyArray()
                    Log.e(TAG, "[TTS_TRACE] Assets na raiz: ${rootFiles.joinToString()}")
                    val modelDirFiles = assetManager.list(MODEL_DIR) ?: emptyArray()
                    Log.e(TAG, "[TTS_TRACE] Assets em $MODEL_DIR: ${modelDirFiles.joinToString()}")
                } catch (_: Exception) {}
                Log.e(TAG, "[TTS_TRACE] Modelo .onnx ausente no APK. Download do CI pode ter falhado.")
                return false
            }

            Log.i(TAG, "[TTS_TRACE] Modelo encontrado nos assets: $modelPath")

            val dataDir = resolveDataDirPath()

            val config = getOfflineTtsConfig(
                modelDir = MODEL_DIR,
                modelName = MODEL_NAME,
                acousticModelName = "",
                vocoder = "",
                voices = "",
                lexicon = "",
                dataDir = dataDir,
                dictDir = "",
                ruleFsts = "",
                ruleFars = "",
            )

            Log.i(TAG, "[TTS_TRACE] Criando OfflineTts...")
            tts = OfflineTts(assetManager = assetManager, config = config)
            Log.i(TAG, "[TTS_TRACE] TTS inicializado! sampleRate=$sampleRate")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TRACE] Erro ao inicializar TTS: ${e.message}", e)
            false
        }
    }

    override fun getTts(): OfflineTts? = tts

    override fun release() {
        try {
            tts?.free()
        } catch (_: Exception) {}
        tts = null
    }

    private fun resolveDataDirPath(): String {
        val externalDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dataDirFile = File(externalDir, DATA_DIR)

        if (dataDirFile.exists() && dataDirFile.list()?.isNotEmpty() == true) {
            Log.i(TAG, "[TTS_TRACE] espeak-ng-data encontrado em: $externalDir/$DATA_DIR")
        } else {
            Log.w(TAG, "[TTS_TRACE] espeak-ng-data nao encontrado em $externalDir/$DATA_DIR")
        }

        return "$externalDir/$DATA_DIR"
    }
}