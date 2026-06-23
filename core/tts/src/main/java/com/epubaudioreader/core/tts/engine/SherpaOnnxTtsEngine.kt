package com.epubaudioreader.core.tts.engine

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
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

    private var tts: OfflineTts? = null

    override val isInitialized: Boolean
        get() = tts != null

    override val sampleRate: Int
        get() = tts?.sampleRate() ?: 22050

    override fun initialize(assetManager: AssetManager): Boolean {
        return try {
            Log.i(TAG, "Inicializando TTS com modelo dos assets...")

            val dataDir = copyDataDir(assetManager)

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

    private fun copyDataDir(assetManager: AssetManager): String {
        return try {
            val externalDir = context.getExternalFilesDir(null) ?: context.filesDir
            val dataDirFile = File(externalDir, DATA_DIR)

            if (dataDirFile.exists() && dataDirFile.list()?.isNotEmpty() == true) {
                Log.i(TAG, "espeak-ng-data ja copiado")
                return "$externalDir/$DATA_DIR"
            }

            Log.i(TAG, "Copiando espeak-ng-data...")
            copyAssetsRecursively(assetManager, DATA_DIR, externalDir.absolutePath)
            Log.i(TAG, "espeak-ng-data copiado")
            "$externalDir/$DATA_DIR"
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao copiar espeak-ng-data: ${e.message}")
            ""
        }
    }

    private fun copyAssetsRecursively(assetManager: AssetManager, path: String, destRoot: String) {
        val assets = assetManager.list(path) ?: return
        if (assets.isEmpty()) {
            try {
                val destFile = File(destRoot, path)
                destFile.parentFile?.mkdirs()
                assetManager.open(path).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao copiar $path: ${e.message}")
            }
        } else {
            val destDir = File(destRoot, path)
            destDir.mkdirs()
            for (asset in assets) {
                val childPath = if (path.isEmpty()) asset else "$path/$asset"
                copyAssetsRecursively(assetManager, childPath, destRoot)
            }
        }
    }
}
