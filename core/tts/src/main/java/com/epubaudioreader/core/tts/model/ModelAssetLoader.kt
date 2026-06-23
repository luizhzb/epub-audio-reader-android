package com.epubaudioreader.core.tts.model

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.epubaudioreader.core.tts.engine.TtsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelLoadState {
    object NotLoaded : ModelLoadState()
    data class Copying(val percent: Int) : ModelLoadState()
    object Loading : ModelLoadState()
    object Ready : ModelLoadState()
    data class Error(val message: String) : ModelLoadState()
}

@Singleton
class ModelAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsEngine: TtsEngine
) {
    companion object {
        private const val TAG = "ModelAssetLoader"
        private const val DATA_DIR = "vits-piper-pt_BR-faber-medium/espeak-ng-data"
    }

    private val _state = MutableStateFlow<ModelLoadState>(ModelLoadState.NotLoaded)
    val state: StateFlow<ModelLoadState> = _state.asStateFlow()

    val modelDir: File
        get() = File(context.filesDir, "tts_model")

    /**
     * Verifica se o modelo existe nos assets e prepara o TTS.
     * Usa newFromAsset() do Sherpa-ONNX que carrega diretamente do APK.
     */
    suspend fun prepareModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = ModelLoadState.Loading
            Log.i(TAG, "Verificando modelo nos assets...")

            // Verificar se assets existem
            val assetManager = context.assets
            val assets = assetManager.list("") ?: emptyArray()

            // Verificar se o diretorio do modelo existe nos assets
            val modelAssets = assetManager.list("vits-piper-pt_BR-faber-medium") ?: emptyArray()
            if (modelAssets.isEmpty()) {
                Log.e(TAG, "Modelo NAO encontrado nos assets!")
                Log.e(TAG, "Assets disponiveis: ${assets.toList()}")
                _state.value = ModelLoadState.Error("Modelo TTS nao encontrado no APK. Assets: ${assets.toList()}")
                return@withContext false
            }

            Log.i(TAG, "Modelo encontrado nos assets: ${modelAssets.toList()}")

            // Copiar espeak-ng-data para external files (requerido pelo JNI)
            copyEspeakDataIfNeeded(assetManager)

            // Inicializar TTS com AssetManager (newFromAsset carrega diretamente do APK)
            Log.i(TAG, "Inicializando TTS engine com AssetManager...")
            val success = ttsEngine.initialize(assetManager)

            if (success) {
                Log.i(TAG, "TTS inicializado com sucesso!")
                _state.value = ModelLoadState.Ready
            } else {
                Log.e(TAG, "Falha ao inicializar TTS engine")
                _state.value = ModelLoadState.Error("Falha ao inicializar TTS engine")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao preparar modelo: ${e.message}", e)
            _state.value = ModelLoadState.Error("Erro: ${e.message}")
            false
        }
    }

    fun checkExistingModel() {
        if (ttsEngine.isInitialized) {
            _state.value = ModelLoadState.Ready
        } else {
            _state.value = ModelLoadState.NotLoaded
        }
    }

    private fun copyEspeakDataIfNeeded(assetManager: AssetManager) {
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dataDirFile = File(destDir, DATA_DIR)

        if (dataDirFile.exists() && dataDirFile.list()?.isNotEmpty() == true) {
            Log.i(TAG, "espeak-ng-data ja existe em $dataDirFile")
            return
        }

        Log.i(TAG, "Copiando espeak-ng-data...")
        _state.value = ModelLoadState.Copying(0)

        try {
            copyAssetsRecursively(assetManager, DATA_DIR, destDir.absolutePath)
            Log.i(TAG, "espeak-ng-data copiado com sucesso")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao copiar espeak-ng-data: ${e.message}")
            // Nao e fatal - alguns modelos funcionam sem
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
