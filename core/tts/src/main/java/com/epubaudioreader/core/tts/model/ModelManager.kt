package com.epubaudioreader.core.tts.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.epubaudioreader.core.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estados possíveis do gerenciamento de modelo TTS.
 */
sealed class ModelState {
    /** Modelo não foi baixado ainda. */
    data object NotDownloaded : ModelState()

    /** Download em andamento com percentual de progresso (0-100). */
    data class Downloading(val percent: Int) : ModelState()

    /** Modelo pronto para uso. */
    data class Ready(val modelDir: String) : ModelState()

    /** Erro durante operação. */
    data class Error(val message: String) : ModelState()
}

/**
 * Gerenciador responsável por baixar, verificar e manter modelos TTS
 * no diretório de arquivos do aplicativo.
 *
 * Suporta dois modos de download:
 * 1. Arquivos individuais (.onnx, .json, tokens.txt) via HTTP
 * 2. Pacote .tar.bz2 completo via Sherpa-ONNX releases
 *
 * Exemplo de uso:
 * ```
 * val modelManager = ModelManager(context, dispatcherProvider)
 * modelManager.state.collect { state ->
 *     when (state) {
 *         is ModelState.Ready -> ttsEngine.initialize(state.modelDir)
 *         is ModelState.Downloading -> showProgress(state.percent)
 *         is ModelState.Error -> showError(state.message)
 *         else -> Unit
 *     }
 * }
 * modelManager.downloadModel(DefaultVoiceConfigs.PT_BR_FABER_LOW)
 * ```
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcher: DispatcherProvider
) {
    companion object {
        private const val MODEL_DIR_NAME = "tts_model"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT_MS = 30000
        private const val READ_TIMEOUT_MS = 60000
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val scope = CoroutineScope(dispatcher.io + SupervisorJob())
    private val isDownloading = AtomicBoolean(false)

    /** Diretório raiz onde o modelo é armazenado. */
    val modelDir: File
        get() = File(context.filesDir, MODEL_DIR_NAME)

    init {
        scope.launch {
            checkExistingModel()
        }
    }

    /**
     * Verifica se há um modelo válido já baixado no diretório.
     */
    private suspend fun checkExistingModel() {
        withContext(dispatcher.io) {
            val dir = modelDir
            if (dir.exists() && isModelValid(dir)) {
                _state.value = ModelState.Ready(dir.absolutePath)
            } else {
                _state.value = ModelState.NotDownloaded
            }
        }
    }

    /**
     * Verifica se os arquivos essenciais do modelo existem e são válidos.
     */
    private fun isModelValid(dir: File): Boolean {
        val modelFile = File(dir, "model.onnx")
        val tokensFile = File(dir, "tokens.txt")

        if (!modelFile.exists() || modelFile.length() == 0L) return false
        if (!tokensFile.exists() || tokensFile.length() == 0L) return false

        // Verifica se o modelo tem tamanho mínimo razoável (> 1MB)
        if (modelFile.length() < 1024 * 1024) return false

        return true
    }

    /**
     * Inicia o download do modelo especificado pela [voiceConfig].
     *
     * Se a URL terminar em .tar.bz2, extrai o pacote após o download.
     * Caso contrário, baixa os arquivos individuais (.onnx, .json, tokens.txt).
     *
     * @param voiceConfig Configuração da voz com URLs dos arquivos.
     * @return true se o download foi iniciado com sucesso.
     */
    suspend fun downloadModel(voiceConfig: VoiceConfig = DefaultVoiceConfigs.DEFAULT): Boolean {
        if (isDownloading.getAndSet(true)) {
            return false // Ja ha download em andamento
        }

        _state.value = ModelState.Downloading(0)

        return try {
            withContext(dispatcher.io) {
                modelDir.mkdirs()

                val isTarBz2 = voiceConfig.modelUrl.endsWith(".tar.bz2")

                if (isTarBz2) {
                    downloadTarBz2Package(voiceConfig)
                } else {
                    downloadIndividualFiles(voiceConfig)
                }
            }
        } catch (e: Exception) {
            _state.value = ModelState.Error("Falha no download: ${e.message}")
            false
        } finally {
            isDownloading.set(false)
        }
    }

    /**
     * Baixa arquivos individuais do modelo (.onnx, tokens.txt, config.json).
     */
    private suspend fun downloadIndividualFiles(voiceConfig: VoiceConfig): Boolean {
        return try {
            // Download do modelo .onnx (maior arquivo)
            val modelFile = File(modelDir, "model.onnx")
            val modelSuccess = downloadFileWithProgress(
                url = voiceConfig.modelUrl,
                destination = modelFile,
                description = "model.onnx"
            )
            if (!modelSuccess) {
                _state.value = ModelState.Error("Falha ao baixar o modelo ONNX. Verifique a URL: ${voiceConfig.modelUrl}")
                return false
            }

            // Download do tokens.txt
            _state.value = ModelState.Downloading(85)
            val tokensFile = File(modelDir, "tokens.txt")
            val tokensSuccess = downloadFile(
                url = voiceConfig.tokensUrl,
                destination = tokensFile
            )
            if (!tokensSuccess) {
                _state.value = ModelState.Error("Falha ao baixar tokens.txt. Verifique a URL: ${voiceConfig.tokensUrl}")
                return false
            }

            // Download opcional do config.json
            if (voiceConfig.configUrl.isNotBlank()) {
                _state.value = ModelState.Downloading(95)
                val configFile = File(modelDir, "model.onnx.json")
                downloadFile(
                    url = voiceConfig.configUrl,
                    destination = configFile
                )
            }

            // Verifica integridade
            if (isModelValid(modelDir)) {
                _state.value = ModelState.Ready(modelDir.absolutePath)
                true
            } else {
                _state.value = ModelState.Error("Arquivos do modelo estao incompletos ou corrompidos")
                false
            }
        } catch (e: IOException) {
            _state.value = ModelState.Error("Erro de I/O: ${e.message}")
            false
        }
    }

    /**
     * Baixa um arquivo individual via HTTP com relatorio de progresso.
     */
    private suspend fun downloadFileWithProgress(
        url: String,
        destination: File,
        description: String
    ): Boolean {
        return try {
            val connection = openConnection(url)
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return false
            }

            val totalBytes = connection.contentLength.toLong()
            val input = BufferedInputStream(connection.inputStream)
            val output = FileOutputStream(destination)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalRead = 0L

            input.use { inp ->
                output.use { out ->
                    while (inp.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        // Reporta progresso
                        if (totalBytes > 0) {
                            val percent = ((totalRead * 80) / totalBytes).toInt()
                            _state.value = ModelState.Downloading(percent.coerceIn(0, 80))
                        }
                    }
                    out.flush()
                }
            }

            connection.disconnect()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Limpa arquivo parcial em caso de erro
            if (destination.exists()) {
                destination.delete()
            }
            false
        }
    }

    /**
     * Baixa um arquivo individual via HTTP (sem progresso detalhado).
     */
    private suspend fun downloadFile(url: String, destination: File): Boolean {
        return try {
            val connection = openConnection(url)
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return false
            }

            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }

            connection.disconnect()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (destination.exists()) {
                destination.delete()
            }
            false
        }
    }

    /**
     * Baixa e extrai um pacote .tar.bz2 completo.
     *
     * NOTA: Para extracao .tar.bz2 e necessario adicionar a dependencia
     * 'org.apache.commons:commons-compress:1.26.0' no build.gradle.kts
     * ou usar o comando 'tar' nativo do sistema.
     */
    private suspend fun downloadTarBz2Package(voiceConfig: VoiceConfig): Boolean {
        return try {
            val tarFile = File(context.cacheDir, "tts_model.tar.bz2")

            // Download do pacote
            val success = downloadFileWithProgress(
                url = voiceConfig.modelUrl,
                destination = tarFile,
                description = "tts_model.tar.bz2"
            )

            if (!success || !tarFile.exists()) {
                _state.value = ModelState.Error("Falha ao baixar pacote do modelo")
                return false
            }

            _state.value = ModelState.Downloading(90)

            // Extrai o pacote usando tar nativo
            extractTarBz2(tarFile, modelDir)

            // Move arquivos do subdiretorio extraido para o diretorio raiz
            flattenModelDirectory(modelDir)

            // Limpa arquivo tar
            tarFile.delete()

            if (isModelValid(modelDir)) {
                _state.value = ModelState.Ready(modelDir.absolutePath)
                true
            } else {
                _state.value = ModelState.Error("Arquivos extraidos estao incompletos")
                false
            }
        } catch (e: Exception) {
            _state.value = ModelState.Error("Erro ao processar pacote: ${e.message}")
            false
        }
    }

    /**
     * Extrai um arquivo .tar.bz2 para o diretorio de destino.
     * Usa o comando 'tar' nativo do sistema quando disponivel (mais eficiente).
     * Fallback para extracao manual com Apache Commons Compress.
     */
    private fun extractTarBz2(tarFile: File, destinationDir: File) {
        try {
            // Tenta usar ProcessBuilder para tar nativo (mais eficiente)
            val process = ProcessBuilder(
                "tar", "-xjf", tarFile.absolutePath,
                "-C", destinationDir.absolutePath
            )
            process.redirectErrorStream(true)
            val proc = process.start()
            val exitCode = proc.waitFor()

            if (exitCode != 0) {
                // Fallback: extrai manualmente com Apache Commons Compress
                extractTarBz2WithCommonsCompress(tarFile, destinationDir)
            }
        } catch (e: Exception) {
            extractTarBz2WithCommonsCompress(tarFile, destinationDir)
        }
    }

    /**
     * Extracao de .tar.bz2 usando Apache Commons Compress.
     * Requer a dependencia: org.apache.commons:commons-compress
     */
    private fun extractTarBz2WithCommonsCompress(tarFile: File, destinationDir: File) {
        try {
            val fis = java.io.FileInputStream(tarFile)
            val bis = BufferedInputStream(fis)
            val bz2In = org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(bis)
            val tarIn = org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bz2In)

            tarIn.use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val outFile = File(destinationDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { output ->
                            tar.copyTo(output, BUFFER_SIZE)
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        } catch (e: NoClassDefFoundError) {
            throw IOException(
                "Nao foi possivel extrair .tar.bz2. " +
                "Adicione a dependencia 'org.apache.commons:commons-compress:1.26.0' " +
                "ou use arquivos individuais em vez de pacote .tar.bz2.",
                e
            )
        }
    }

    /**
     * Move arquivos de subdiretorios para o diretorio raiz do modelo.
     * Alguns pacotes .tar.bz2 extraem para um subdiretorio.
     */
    private fun flattenModelDirectory(dir: File) {
        val subDirs = dir.listFiles { file -> file.isDirectory } ?: return

        for (subDir in subDirs) {
            // Mantem subdiretorios especiais do modelo
            if (subDir.name == "dict" || subDir.name == "espeak-ng-data") {
                continue
            }

            // Procura por model.onnx em subdiretorios
            val modelFile = File(subDir, "model.onnx")
            if (modelFile.exists()) {
                // Move todos os arquivos do subdiretorio para o diretorio raiz
                subDir.listFiles()?.forEach { file ->
                    val dest = File(dir, file.name)
                    if (!dest.exists()) {
                        file.renameTo(dest)
                    }
                }
            }
        }
    }

    /**
     * Configura uma conexao HTTP com headers padrao.
     */
    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", "EPUBAudioReader/1.0")
            setInstanceFollowRedirects(true)
        }
    }

    /**
     * Verifica a integridade do modelo comparando tamanho esperado.
     *
     * @param expectedModelSize Tamanho esperado do arquivo model.onnx em bytes.
     * @return true se o modelo parece valido.
     */
    fun verifyModelIntegrity(expectedModelSize: Long = 0): Boolean {
        val dir = modelDir
        if (!isModelValid(dir)) return false

        if (expectedModelSize > 0) {
            val modelFile = File(dir, "model.onnx")
            if (modelFile.length() != expectedModelSize) return false
        }

        return true
    }

    /**
     * Retorna o tamanho total do modelo em bytes.
     */
    fun getModelSize(): Long {
        val dir = modelDir
        if (!dir.exists()) return 0

        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Lista os arquivos do modelo no diretorio.
     */
    fun listModelFiles(): List<String> {
        val dir = modelDir
        if (!dir.exists()) return emptyList()

        return dir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(dir).path }
            .toList()
    }

    /**
     * Deleta todos os arquivos do modelo baixado.
     */
    suspend fun deleteModel() {
        withContext(dispatcher.io) {
            try {
                modelDir.deleteRecursively()
                _state.value = ModelState.NotDownloaded
            } catch (e: Exception) {
                _state.value = ModelState.Error("Falha ao deletar modelo: ${e.message}")
            }
        }
    }

    /**
     * Libera recursos do gerenciador.
     */
    fun release() {
        scope.cancel()
    }
}
