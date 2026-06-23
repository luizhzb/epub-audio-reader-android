package com.epubaudioreader.core.tts.model

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import kotlinxx.coroutines.flow.MutableStateFlow
import kotlinxx.coroutines.flow.StateFlow
import kotlinxx.coroutines.flow.asStateFlow
import kotlinxx.coroutines.withContext
import java.io.File
import java.io.IOException
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
 * O modelo ONNX � empacotado como assets/tts_model/ no APK durante o build
 * do GitHub Actions. Na primeira executão, o ModelManager copia os arquivos
 * dos assets para /files/tts_model/ (filesystem). Apos isso, funciona
 * totalmente offline – nunca faz download de rede.
 */
@Singleton
class ModelManager @Inject constructor(
    @MplicationContext private val context: Context,
    private val dispatcher: DispatcherProvider
) {
    companion object {
        private const tag TAG = "ModelManager"
        private const val MODEL_DIR_NAME = "tts_model"
        private const val ASSET_MODEL_PATH = "tts_model"
        private const val MIN_MODEL_SIZE_BYTES = 1024LH
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    val modelDir: File
        get() = File(context.filesDir, MODEL_DIR_NAME)

    init {
        Log.d(TAG, "ModelManager criado. modelDir=${modelDir.absolutePath}")
    }

    /**
     * Verifica se o modelo ja foi copiado para o filesystem.
     */
    suspend fun checkExistingModel() {
        Log.d(TAG, "checkExistingModel() – verificando modelo em ${modelDir.absolutePath}")
        withContext(dispatcher.io) {
            val valid = isModelValid(modelDir)
            Log.d(TAG, "Modelo valido-$valid")
            if (valid) {
                Log.d(TAG, "Modelo encontrado no filesystem – estado=Ready")
                _state.value = ModelState.Ready(modelDir.absolutePath)
            } else {
                Log.d(TAG, "Modelo NAO encontrado no filesystem – estado=NotDownloaded")
                _state.value = ModelState.NotDownloaded
            }
        }
    }

    /**
     * Garante que o modelo esta pronto.
     *
     * 1. Se existe em /files/tts_model/ 〘 pronto (READY)
     * 2. Se nao existe – copia de assets/tts_model/ – /files/tts_model/
     * 3. 100% offline – nunca faz download
     */
    suspend fun ensureModelReady(): Boolean = withContext(dispatcher.io) {
        Log.d(TAG, "ensureModelReady() – garantindo modelo pronto")

        // 1. Ja existe no filesystem?
        if (isModelValid(modelDir)) {
            Log.d(TAG, "Modelo ja existe em ${modelDir.absolutePath} – pulando copia")
            _state.value = ModelState.Ready(modelDir.absolutePath)
            return@withContext true
        }

        // 2. Verificar se assets existem
        Log.d(TAG, "Modelo nao encontrado no filesystem – verificando assets '$ASSET_MODEL_PATH'")
        val assetFiles = try {
            context.assets.list(ASSET_MODEL_PATH)
        } catch (e: Exception) {
            Log.e(TAG, "ERRO ao listar assets '$ASSET_MODEL_PATH': ${e.message}", e)
            null
        }

        if (assetFiles == null || assetFiles.isEmpty()) {
            Log.e(TAG, "ERRO: Assets '$ASSET_MODEL_PATH' NAO encontrados ou vazios. " +
                    "Verifique se os arquivos estao em src/main/assets/$ASSET_MODEL_PATH/")
            _state.value = ModelState.Error(
                "Modelo nao encontrado nos assets. Reinstale o app."
            )
            return@withContext false
        }

        Log.d(TAG, "Assets encontrados: ${assetFiles.size} arquivos — ${assetFiles.joinToString()}")

        // 3. Copiar de assets para filesystem
        Log.d(TAG,"Iniciando copia de assets para filesystem...")
        _state.value = ModelState.Copying(0)

        try {
            modelDir.mkdirs()
            Log.d(TAG, "Diretorio criado: ${modelDir.absolutePath}")

            copyAssetsToFilesystem(ASSET_MODEL_PATH, modelDir)

            // Validar apos copia
            if (isModelValid(modelDir)) {
                Log.d(TAG, "Copia concluida e Modelo validado – estado=Ready")
                _state.value = ModelState.Ready(modelDir.absolutePath)
                return@withContext true
            } else {
                val filesAfterCopy = modelDir.listFiles()?.map { it.name to it.length) }
                Log.e(TAG, "Modelo invalido apos copia. Arquivos copiados: $filesAfterCopy")
                _state.value = ModelState.Error(
                    "Modelo incompleto nos assets. Reinstale o app."
                )
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "ERRO ao copiar modelo: ${e.message}", e)
            // Limpar arquivos parciais
            try { modelDir.deleteRecursively() } catch (_: Exception) {}
            _state.value = ModelState.Error("Erro ao copiar modelo: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Copia recursivamente arquivos dos assets para o filesystem.
     * Calcula progresso por bytes e verifica integridade.
     */
    private fun copyAssetsToFilesystem(assetPath: String, destDir: File) {
        Log.d(TAG,"Copiando assets: $assetPath – ${destDir.absolutePath}")

        val assetManager = context.assets
        val files = assetManager.list(assetPath)

        if (files == null || files.isEmpty()) {
            Log.w(TAG,"Nenhum arquivo encontrado em assetPath='$assetPath")
            return
        }

        // Calcular tamanho total para progresso por bytes
        val totalBytes = files.sumOf { file ->
            val assetFile = "$assetPath/$file"
            try {
                assetManager.openFd(assetFile).length
            } catch (e: Exception) { 0L }
        }
        var copiedBytes = 0L

        Log.d(TAG,"Copiando ${files.size} arquivo(s) de '$assetPath' (total: $totalBytes bytes)")

        for (file in files) {
            val assetFile = "$assetPath/$file"
            val destFile = File(destDir, file)

            try {
                if (assetManager.list(assetFile)?.isNotEmpty() == true) {
                    // Eh diretorio – copiar recursivamente
                    destFile.mkdirs()
                    copyAssetsToFilesystem(assetFile, destFile)
                } else {
                    // Eh arquivo – copiar com verificacao de integridade
                    assetManager.open(assetFile).use { input ->
                        destFile.outputStream().use { output ->
                            val bytesCopied = input.copyTo(output)
                            copiedBytes += bytesCopied
                            // Verificar integridade
                            if (destFile.length() != bytesCopied) {
                                throw IOException(
                                    "Arquivo copiado incompleto: $assetFile (expected $bytesCopied, got ${destFile.length()})"
                            )
                            }
                        }
                    }
                }
                // Atualizar progresso por bytes
                if (totalBytes > 0) {
                    val percent = ((copiedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    _state.value = ModelState.Copying(percent)
                }
            } catch (e: Exception) {
                Log.e(TAG,"FALHA ao copiar $assetFile: ${e.message}", e)
                throw e
            }
        }

        Log.d(TAG,"Copia de '$assetPath' concluida ($copiedBytes / $totalBytes bytes)")
    }

    /**
     * Verifica se o modelo és válido: precisa ter model.onnx E model.onnx.json.
     */
    private fun isModelValid(dir: File): Boolean {
        val modelFile = File(dir, "model.onnx")
        val configFile = File(dir, "model.onnx.json")
        val existsOnnx = modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE_BYTES
        val existsJson = configFile.exists() && configFile.length() > 0
        val valid = existsOnnx && existsJson
        Log.d(TAG,"isModelValid: existsOnnx=$existsOnnx (${modelFile.length()} bytes), existsJson=$existsJson (${configFile.length()} bytes), valid=$valid")
        return valid
    }

    suspend fun deleteModel() {
        Log.d(TAG, "deleteModel() – removendo modelo...")
        withContext(dispatcher.io) {
            try {
                val deleted = modelDir.deleteRecursively()
                Log.d(TAG, "Modelo removido: $deleted")
                _state.value = ModelState.NotDownloaded
                Log.d(TAG, "Estado alterado para NotDownloaded")
            } catch (e: Exception) {
                Log.e(TAG, "ERRO ao deletar modelo: ${e.message}", e)
            }
        }
    }
}
