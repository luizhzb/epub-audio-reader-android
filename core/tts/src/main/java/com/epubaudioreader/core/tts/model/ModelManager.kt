package com.epubaudioreader.core.tts.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estados possiveis do gerenciamento de modelo TTS.
 */
sealed class ModelState {
    data object NotDownloaded : ModelState()
    data class Copying(val percent: Int) : ModelState()
    data object Initializing : ModelState()
    data class Ready(val modelDir: String) : ModelState()
    data class Error(val message: String) : ModelState()
}

/**
 * Gerenciador de modelo TTS — 100% offline.
 *
 * O modelo ONNX eh empacotado como assets/tts_model/ no APK durante o build
 * do GitHub Actions. Na primeira execucao, o ModelManager copia os arquivos
 * dos assets para /files/tts_model/ (filesystem). Apos isso, funciona
 * totalmente offline — nunca faz download de rede.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcher: DispatcherProvider
) {
    companion object {
        private const val MODEL_DIR_NAME = "tts_model"
        private const val ASSET_MODEL_PATH = "tts_model"
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    val modelDir: File
        get() = File(context.filesDir, MODEL_DIR_NAME)

    /**
     * Verifica se o modelo ja foi copiado para o filesystem.
     */
    suspend fun checkExistingModel() {
        withContext(dispatcher.io) {
            if (isModelValid(modelDir)) {
                _state.value = ModelState.Ready(modelDir.absolutePath)
            } else {
                _state.value = ModelState.NotDownloaded
            }
        }
    }

    /**
     * Garante que o modelo esta pronto.
     *
     * 1. Se existe em /files/tts_model/ → pronto (READY)
     * 2. Se nao existe → copia de assets/tts_model/ → /files/tts_model/
     * 3. 100% offline — nunca faz download
     */
    suspend fun ensureModelReady(): Boolean = withContext(dispatcher.io) {
        // 1. Ja existe?
        if (isModelValid(modelDir)) {
            _state.value = ModelState.Ready(modelDir.absolutePath)
            return@withContext true
        }

        // 2. Copiar de assets
        _state.value = ModelState.Copying(0)
        try {
            modelDir.mkdirs()
            copyAssetsToFilesystem(ASSET_MODEL_PATH, modelDir)

            if (isModelValid(modelDir)) {
                _state.value = ModelState.Ready(modelDir.absolutePath)
                return@withContext true
            } else {
                _state.value = ModelState.Error("Modelo incompleto nos assets. Reinstale o app.")
                return@withContext false
            }
        } catch (e: Exception) {
            _state.value = ModelState.Error("Erro ao copiar modelo: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Copia recursivamente arquivos dos assets para o filesystem.
     */
    private fun copyAssetsToFilesystem(assetPath: String, destDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        val total = files.size
        for ((index, file) in files.withIndex()) {
            val assetFile = "$assetPath/$file"
            val destFile = File(destDir, file)

            // Atualiza progresso
            val percent = ((index + 1) * 100) / total
            _state.value = ModelState.Copying(percent)

            if (assetManager.list(assetFile)?.isNotEmpty() == true) {
                destFile.mkdirs()
                copyAssetsToFilesystem(assetFile, destFile)
            } else {
                assetManager.open(assetFile).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun isModelValid(dir: File): Boolean {
        val modelFile = File(dir, "model.onnx")
        return modelFile.exists() && modelFile.length() > 1024 * 1024
    }

    suspend fun deleteModel() {
        withContext(dispatcher.io) {
            try {
                modelDir.deleteRecursively()
                _state.value = ModelState.NotDownloaded
            } catch (_: Exception) {}
        }
    }
}
