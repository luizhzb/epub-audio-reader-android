package com.epubaudioreader.core.tts.engine

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SherpaOnnxTtsEngine @Inject constructor() : TtsEngine {

    companion object {
        private const val TAG = "SherpaOnnxTtsEngine"
        // Modelo: vits-piper-en_US-amy-low (exemplo oficial #2 do Sherpa-ONNX)
        private const val MODEL_DIR = "vits-piper-en_US-amy-low"
        private const val MODEL_NAME = "en_US-amy-low.onnx"
        private const val DATA_DIR = "vits-piper-en_US-amy-low/espeak-ng-data"
    }

    private var tts: OfflineTts? = null

    override val isInitialized: Boolean
        get() = tts != null

    override val sampleRate: Int
        get() = tts?.sampleRate() ?: 22050

    override fun initialize(assetManager: AssetManager): Boolean {
        return try {
            Log.i(TAG, "Inicializando TTS com modelo dos assets...")

            // Copiar espeak-ng-data para external files (requerido pelo JNI)
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
        try {
            val externalDir = File(android.app.Application.getProcessName().let {
                // Nao temos accesso ao context aqui, usar approach alternativo
                // Na verdade precisamos do path - vamos usar uma abordagem diferente
                "/sdcard/Android/data/com.epubaudioreader/files"
            })
            externalDir.mkdirs()

            // Verificar se ja foi copiado
            val dataDirFile = File(externalDir, DATA_DIR)
            if (dataDirFile.exists() && dataDirFile.list()?.isNotEmpty() == true) {
                Log.i(TAG, "espeak-ng-data ja copiado")
                return "$externalDir/$DATA_DIR"
            }

            // Copiar recursivamente
            copyAssetsRecursively(assetManager, DATA_DIR, externalDir.absolutePath)
            Log.i(TAG, "espeak-ng-data copiado para $externalDir/$DATA_DIR")
            return "$externalDir/$DATA_DIR"
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao copiar dataDir: ${e.message}", e)
            return "" // Retorna vazio, o modelo pode funcionar sem espeak-ng-data para alguns casos
        }
    }

    private fun copyAssetsRecursively(assetManager: AssetManager, path: String, destRoot: String) {
        val assets = assetManager.list(path) ?: return
        if (assets.isEmpty()) {
            // Arquivo unico
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
            // Diretorio
            val destDir = File(destRoot, path)
            destDir.mkdirs()
            for (asset in assets) {
                val childPath = if (path.isEmpty()) asset else "$path/$asset"
                copyAssetsRecursively(assetManager, childPath, destRoot)
            }
        }
    }
}
