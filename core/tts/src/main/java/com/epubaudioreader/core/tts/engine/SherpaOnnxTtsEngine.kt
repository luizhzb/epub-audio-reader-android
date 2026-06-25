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

/**
 * Engine TTS Sherpa-ONNX com correcao CRITICA para espeak-ng-data.
 *
 * PROBLEMA: O Sherpa-ONNX acessa espeak-ng-data via fopen()/ifstream() nativo,
 * que NAO funciona com o formato zip do APK. O dataDir precisa ser um caminho
 * absoluto no filesystem real.
 *
 * SOLUCAO: Copia espeak-ng-data dos assets para context.filesDir antes de
 * inicializar, e passa o caminho absoluto copiado.
 */
@Singleton
class SherpaOnnxTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsEngine {

    companion object {
        private const val TAG = "SherpaOnnxTtsEngine"
        private const val MODEL_DIR = "vits-piper-pt_BR-faber-medium"
        private const val MODEL_NAME = "pt_BR-faber-medium.onnx"
        private const val DATA_DIR_ASSET = "$MODEL_DIR/espeak-ng-data"
        private const val DATA_DIR_DEST = "tts_model/espeak-ng-data"

        /** Arquivos essenciais que devem existir apos a copia */
        private val REQUIRED_FILES = listOf("phondata", "phonindex", "phontab", "intonations")
    }

    @Volatile
    private var tts: OfflineTts? = null

    override val isInitialized: Boolean
        get() = tts != null

    override val sampleRate: Int
        get() = tts?.sampleRate() ?: 22050

    /**
     * Inicializa o TTS copiando espeak-ng-data para o filesystem e passando
     * caminho absoluto para o Sherpa-ONNX.
     */
    override fun initialize(assetManager: AssetManager): Boolean {
        return try {
            Log.i(TAG, "[TTS_TRACE] Inicializando TTS...")

            // === 1. Verificar modelo .onnx nos assets ===
            val modelPath = "$MODEL_DIR/$MODEL_NAME"
            val modelExists = try {
                assetManager.open(modelPath).close()
                true
            } catch (e: Exception) {
                Log.e(TAG, "[TTS_TRACE] Modelo .onnx NAO ENCONTRADO: $modelPath")
                false
            }

            if (!modelExists) {
                try {
                    val rootFiles = assetManager.list("") ?: emptyArray()
                    Log.e(TAG, "[TTS_TRACE] Assets na raiz: ${rootFiles.joinToString()}")
                    val modelDirFiles = assetManager.list(MODEL_DIR) ?: emptyArray()
                    Log.e(TAG, "[TTS_TRACE] Assets em $MODEL_DIR: ${modelDirFiles.joinToString()}")
                } catch (_: Exception) {}
                return false
            }
            Log.i(TAG, "[TTS_TRACE] Modelo .onnx OK")

            // === 2. CORRECAO CRITICA: Copiar espeak-ng-data para filesystem ===
            val dataDirAbsolutePath = copyEspeakDataToFilesystem(assetManager)
            if (dataDirAbsolutePath == null) {
                Log.e(TAG, "[TTS_TRACE] FALHA: espeak-ng-data nao pode ser copiado para filesystem")
                return false
            }
            Log.i(TAG, "[TTS_TRACE] espeak-ng-data copiado para: $dataDirAbsolutePath")

            // === 3. Verificar arquivos essenciais ===
            if (!verifyEspeakData(dataDirAbsolutePath)) {
                Log.e(TAG, "[TTS_TRACE] FALHA: arquivos essenciais do espeak-ng-data ausentes")
                return false
            }
            Log.i(TAG, "[TTS_TRACE] Arquivos essenciais do espeak-ng-data OK")

            // === 4. Criar OfflineTts com caminho absoluto ===
            val config = getOfflineTtsConfig(
                modelDir = MODEL_DIR,
                modelName = MODEL_NAME,
                acousticModelName = "",
                vocoder = "",
                voices = "",
                lexicon = "",
                dataDir = dataDirAbsolutePath,  // <- CAMINHO ABSOLUTO NO FILESYSTEM
                dictDir = "",
                ruleFsts = "",
                ruleFars = "",
            )

            Log.i(TAG, "[TTS_TRACE] Criando OfflineTts (dataDir=$dataDirAbsolutePath)...")
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

    /**
     * CORRECAO CRITICA: Copia espeak-ng-data dos assets para o filesystem.
     * Usa abordagem atomica (temp + rename) para evitar estado inconsistente.
     * Retorna o caminho absoluto do diretorio copiado, ou null se falhar.
     */
    private fun copyEspeakDataToFilesystem(assetManager: AssetManager): String? {
        val destDir = File(context.filesDir, DATA_DIR_DEST)

        // Se ja existe e tem os arquivos essenciais, reutiliza
        if (destDir.exists() && verifyEspeakData(destDir.absolutePath)) {
            Log.i(TAG, "[TTS_TRACE] espeak-ng-data ja existe em filesystem: ${destDir.absolutePath}")
            return destDir.absolutePath
        }

        Log.i(TAG, "[TTS_TRACE] Copiando espeak-ng-data dos assets para filesystem...")

        // Abordagem atomica: copiar para temp primeiro, depois rename
        val tempDir = File(context.filesDir, "${DATA_DIR_DEST}_tmp")
        try {
            // Limpar temp se existir de copia anterior interrompida
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            // Copiar recursivamente
            copyAssetsRecursively(assetManager, DATA_DIR_ASSET, tempDir)

            // Verificar se copiou corretamente
            if (!verifyEspeakData(tempDir.absolutePath)) {
                Log.e(TAG, "[TTS_TRACE] Copia incompleta - arquivos essenciais faltando")
                tempDir.deleteRecursively()
                return null
            }

            // Rename atomico: temp -> destino
            if (destDir.exists()) destDir.deleteRecursively()
            val renamed = tempDir.renameTo(destDir)
            if (!renamed) {
                Log.e(TAG, "[TTS_TRACE] Falha ao renomear temp dir")
                tempDir.deleteRecursively()
                return null
            }

            Log.i(TAG, "[TTS_TRACE] Copia concluida: ${destDir.absolutePath}")
            return destDir.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TRACE] Erro ao copiar espeak-ng-data: ${e.message}", e)
            tempDir.deleteRecursively()
            return null
        }
    }

    /**
     * Verifica se os arquivos essenciais do espeak-ng-data existem.
     */
    private fun verifyEspeakData(dataDirPath: String): Boolean {
        val dataDir = File(dataDirPath)
        if (!dataDir.exists() || !dataDir.isDirectory) {
            Log.w(TAG, "[TTS_TRACE] verifyEspeakData: diretorio nao existe: $dataDirPath")
            return false
        }

        val missingFiles = REQUIRED_FILES.filter { required ->
            val file = File(dataDir, required)
            val exists = file.exists()
            if (!exists) Log.w(TAG, "[TTS_TRACE] Arquivo essencial ausente: $required")
            !exists
        }

        if (missingFiles.isEmpty()) {
            val allFiles = dataDir.list()?.size ?: 0
            Log.i(TAG, "[TTS_TRACE] verifyEspeakData: OK ($allFiles arquivos)")
        } else {
            Log.w(TAG, "[TTS_TRACE] verifyEspeakData: FALTAM ${missingFiles.size} arquivos: $missingFiles")
        }

        return missingFiles.isEmpty()
    }

    /**
     * Copia arquivos dos assets recursivamente.
     */
    private fun copyAssetsRecursively(assetManager: AssetManager, assetPath: String, destDir: File) {
        val assets = assetManager.list(assetPath) ?: return

        if (assets.isEmpty()) {
            // Arquivo individual
            try {
                val relativePath = assetPath.removePrefix(DATA_DIR_ASSET).trimStart('/')
                val destFile = if (relativePath.isEmpty()) {
                    // Arquivo na raiz do espeak-ng-data
                    File(destDir, assetPath.substringAfterLast('/'))
                } else {
                    File(destDir, relativePath)
                }
                destFile.parentFile?.mkdirs()
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[TTS_TRACE] Falha ao copiar arquivo $assetPath: ${e.message}")
            }
        } else {
            // Diretorio
            for (asset in assets) {
                val childPath = "$assetPath/$asset"
                val childAssets = assetManager.list(childPath)
                if (childAssets != null && childAssets.isEmpty()) {
                    // Arquivo
                    try {
                        val relativePath = childPath.removePrefix(DATA_DIR_ASSET).trimStart('/')
                        val destFile = File(destDir, relativePath)
                        destFile.parentFile?.mkdirs()
                        assetManager.open(childPath).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[TTS_TRACE] Falha ao copiar $childPath: ${e.message}")
                    }
                } else {
                    // Subdiretorio
                    val relativePath = childPath.removePrefix(DATA_DIR_ASSET).trimStart('/')
                    val subDir = File(destDir, relativePath)
                    subDir.mkdirs()
                    copyAssetsRecursively(assetManager, childPath, destDir)
                }
            }
        }
    }
}