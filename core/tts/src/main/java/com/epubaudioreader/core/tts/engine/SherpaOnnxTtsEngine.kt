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

    override fun initialize(assetManager: AssetManager): Boolean {
        return try {
            Log.i(TAG, "Inicializando TTS com modelo dos assets...")

            val dataDir = resolveDataDirPath()

            val config = getOfflineTtsConfig(
                modelDir = MODEL_DIR,
                modelName = MODEL_NAME,
                lexicon = "",
                dataDir = dataDir,
                dictDir = "",
                ruleFsts = "",
                ruleFars = "",
            )

            Log.i(TAG, "Criando OfflineTts com assetManager...")
            tts = OfflineTts(assetManager = assetManager, config = config)
            Log.i(TAG, "TTS inicializado! sampleRate=$sampleRate")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar TTS: ${e.message}", e)
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
            Log.i(TAG, "espeak-ng-data encontrado em: $externalDir/$DATA_DIR")
        } else {
            Log.w(TAG, "espeak-ng-data nao encontrado em $externalDir/$DATA_DIR. " +
                "Certifique-se de que ModelAssetLoader.prepareModel() foi chamado primeiro.")
        }

        return "$externalDir/$DATA_DIR"
    }
}
