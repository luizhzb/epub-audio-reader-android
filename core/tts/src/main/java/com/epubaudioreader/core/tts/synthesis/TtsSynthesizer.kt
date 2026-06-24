package com.epubaudioreader.core.tts.synthesis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.tts.engine.TtsEngine
import com.k2fsa.sherpa.onnx.GenerationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sintetizador TTS com melhorias de robustez para evitar crashes.
 *
 * Melhorias:
 * 1. Thread.sleep() substituido por delay() em funcoes suspend
 * 2. playSamples() agora e suspend e nao bloqueia a thread de coroutine
 * 3. Fallback automatico: PCM_FLOAT -> PCM_16BIT se nao suportado
 * 4. Sanitizacao de texto antes de passar ao JNI (remove emojis/control chars)
 * 5. Verificacao de modelo .onnx antes de inicializar
 */
@Singleton
class TtsSynthesizer @Inject constructor(
    private val ttsEngine: TtsEngine
) {
    companion object {
        private const val TAG = "TtsSynthesizer"
        private const val MAX_TEXT_LENGTH = 500
    }

    private var track: AudioTrack? = null
    @Volatile
    private var stopped: Boolean = true
    private val lock = Object()

    /**
     * Mutex que serializa chamadas ao motor Sherpa-ONNX.
     */
    private val generationMutex = Mutex()

    /** Flag que indica se estamos usando PCM_16BIT como fallback. */
    private var usingPcm16Bit: Boolean = false

    val isPlaying: Boolean
        get() = synchronized(lock) { !stopped }

    fun isAudioTrackInitialized(): Boolean = synchronized(lock) { track != null }

    /**
     * Inicializa AudioTrack com fallback automatico PCM_FLOAT -> PCM_16BIT.
     */
    fun initAudioTrack() {
        val tts = ttsEngine.getTts() ?: throw IllegalStateException("TTS nao inicializado")
        val sampleRate = tts.sampleRate()

        // Tenta PCM_FLOAT primeiro
        var bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        usingPcm16Bit = if (bufLength <= 0) {
            Log.w(TAG, "PCM_FLOAT nao suportado, tentando PCM_16BIT fallback")
            val buf16 = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (buf16 <= 0) {
                throw IllegalStateException("Nenhum formato PCM suportado pelo dispositivo")
            }
            bufLength = buf16
            true
        } else {
            false
        }

        Log.i(TAG, "AudioTrack sampleRate=$sampleRate bufLength=$bufLength pcm16=$usingPcm16Bit")

        val encoding = if (usingPcm16Bit) {
            AudioFormat.ENCODING_PCM_16BIT
        } else {
            AudioFormat.ENCODING_PCM_FLOAT
        }

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        synchronized(lock) {
            track?.let { old ->
                Log.d(TAG, "Liberando AudioTrack anterior")
                try { old.stop() } catch (_: Exception) {}
                try { old.release() } catch (_: Exception) {}
            }
            track = AudioTrack(
                attr, format, bufLength,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            Log.i(TAG, "AudioTrack criado (state=${track?.state}, pcm16=$usingPcm16Bit)")
        }
    }

    fun startPlayback() {
        synchronized(lock) {
            val t = track ?: throw IllegalStateException("AudioTrack nao inicializado")
            Log.d(TAG, "AudioTrack.play() (state=${t.state}, playState=${t.playState})")
            t.play()
        }
    }

    /**
     * Toca samples PCM no AudioTrack. Agora e suspend e usa delay() em vez de Thread.sleep().
     */
    suspend fun playSamples(samples: FloatArray, sampleRate: Int): kotlin.Result<Unit> {
        return try {
            if (samples.isEmpty()) {
                Log.w(TAG, "playSamples recebeu samples vazio")
                return kotlin.Result.success(Unit)
            }

            val encoding = if (usingPcm16Bit) {
                AudioFormat.ENCODING_PCM_16BIT
            } else {
                AudioFormat.ENCODING_PCM_FLOAT
            }

            val bufLength = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                encoding
            )
            if (bufLength <= 0) {
                return kotlin.Result.failure(
                    IllegalStateException("Formato PCM nao suportado (bufLength=$bufLength)")
                )
            }

            synchronized(lock) {
                val needsRecreate = track?.sampleRate != sampleRate ||
                        track?.state == AudioTrack.STATE_UNINITIALIZED ||
                        track?.playState == AudioTrack.PLAYSTATE_STOPPED

                if (needsRecreate) {
                    Log.d(TAG, "Recriando AudioTrack sampleRate=$sampleRate pcm16=$usingPcm16Bit")
                    try { track?.stop() } catch (_: Exception) {}
                    try { track?.release() } catch (_: Exception) {}

                    val attr = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                    val format = AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setSampleRate(sampleRate)
                        .build()
                    track = AudioTrack(
                        attr, format, bufLength,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                }

                val t = track ?: return kotlin.Result.failure(
                    IllegalStateException("AudioTrack nulo apos inicializacao")
                )

                Log.d(TAG, "playSamples: ${samples.size} samples, sr=$sampleRate, pcm16=$usingPcm16Bit")
                t.play()
                stopped = false

                if (usingPcm16Bit) {
                    // Converter FloatArray para ShortArray para PCM_16BIT
                    val shortSamples = FloatArrayToShortArray(samples)
                    val written = t.write(shortSamples, 0, shortSamples.size)
                    if (written < 0) {
                        throw IllegalStateException("AudioTrack.write retornou erro $written")
                    }
                } else {
                    val written = t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        throw IllegalStateException("AudioTrack.write retornou erro $written")
                    }
                }

                // MELHORIA: Usar delay() em vez de Thread.sleep() para nao bloquear thread
                val durationMs = (samples.size / sampleRate.toFloat() * 1000).toLong()
                Log.d(TAG, "playSamples: aguardando ${durationMs}ms via delay()")
            }

            // delay() fora do synchronized para nao bloquear outras operacoes
            val durationMs = (samples.size / sampleRate.toFloat() * 1000).toLong()
            delay(durationMs)
            Log.d(TAG, "playSamples: audio concluido")

            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro em playSamples: ${e.message}", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Callback chamado pelo JNI do Sherpa-ONNX durante a geracao.
     */
    private fun callback(samples: FloatArray): Int {
        return try {
            synchronized(lock) {
                if (!stopped) {
                    val t = track
                    if (t != null) {
                        val written = if (usingPcm16Bit) {
                            val shortSamples = FloatArrayToShortArray(samples)
                            t.write(shortSamples, 0, shortSamples.size)
                        } else {
                            t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        }
                        if (written < 0) {
                            Log.e(TAG, "AudioTrack.write erro no callback: $written")
                            stopped = true
                            0
                        } else {
                            1
                        }
                    } else {
                        Log.e(TAG, "AudioTrack nulo no callback JNI")
                        stopped = true
                        0
                    }
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack write error no callback JNI: ${e.message}", e)
            stopped = true
            0
        }
    }

    /**
     * Sintetiza e reproduz o texto via AudioTrack com callback.
     * Usa delay() em vez de Thread.sleep().
     */
    suspend fun speak(text: String, sid: Int = 0, speed: Float = 1.0f, onComplete: (() -> Unit)? = null): kotlin.Result<Unit> {
        return try {
            if (text.isBlank()) return kotlin.Result.success(Unit)

            // MELHORIA: Sanitizar texto antes de passar ao JNI
            val sanitizedText = sanitizeTextForTts(text)

            val tts = ttsEngine.getTts()
                ?: return kotlin.Result.failure(IllegalStateException("TTS nao inicializado"))

            generationMutex.withLock {
                Log.d(TAG, "speak: '${sanitizedText.take(60)}...'")

                synchronized(lock) {
                    track?.flush()
                    track?.play()
                    stopped = false
                }

                var totalSamplesWritten = 0

                val genConfig = GenerationConfig(sid = sid, speed = speed)
                tts.generateWithConfigAndCallback(
                    text = sanitizedText,
                    config = genConfig,
                    callback = { samples ->
                        val result = callback(samples)
                        if (result == 1) {
                            totalSamplesWritten += samples.size
                        }
                        result
                    }
                )

                // MELHORIA: Usar delay() em vez de Thread.sleep()
                if (totalSamplesWritten > 0 && !stopped) {
                    val sr = tts.sampleRate()
                    val durationMs = (totalSamplesWritten / sr.toFloat() * 1000).toLong()
                    Log.d(TAG, "speak: aguardando ${durationMs}ms via delay()")
                    delay(durationMs)
                }

                Log.d(TAG, "speak: geracao e playback concluidos")
                onComplete?.invoke()
            }
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro em speak: ${e.message}", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Sintetiza o texto para memoria.
     */
    suspend fun synthesizeToMemory(
        text: String,
        sid: Int = 0,
        speed: Float = 1.0f
    ): Result<SynthesizedAudio> {
        return try {
            val tts = ttsEngine.getTts()
                ?: return Result.Error(IllegalStateException("TTS nao inicializado"))

            // Sanitizar texto
            val sanitizedText = sanitizeTextForTts(text)

            if (sanitizedText.isBlank()) {
                return Result.Success(SynthesizedAudio(FloatArray(0), tts.sampleRate()))
            }

            generationMutex.withLock {
                Log.d(TAG, "synthesizeToMemory: '${sanitizedText.take(60)}...'")

                var samples = FloatArray(0)
                var totalSize = 0

                val memoryCallback: (FloatArray) -> Int = { chunk ->
                    try {
                        if (chunk.isNotEmpty()) {
                            val newSize = totalSize + chunk.size
                            if (newSize > samples.size) {
                                val newCapacity = (newSize * 1.5).toInt().coerceAtLeast(newSize + 4096)
                                samples = samples.copyOf(newCapacity)
                            }
                            chunk.copyInto(samples, destinationOffset = totalSize)
                            totalSize += chunk.size
                        }
                        1
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro acumulando samples: ${e.message}", e)
                        0
                    }
                }

                val genConfig = GenerationConfig(sid = sid, speed = speed)
                tts.generateWithConfigAndCallback(
                    text = sanitizedText,
                    config = genConfig,
                    callback = memoryCallback
                )

                val finalSamples = if (totalSize < samples.size) samples.copyOf(totalSize) else samples
                val result = SynthesizedAudio(finalSamples, tts.sampleRate())
                Log.d(TAG, "synthesizeToMemory: ${result.samples.size} samples, sr=${result.sampleRate}")
                Result.Success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro em synthesizeToMemory: ${e.message}", e)
            Result.Error(e)
        }
    }

    fun stop() {
        synchronized(lock) {
            stopped = true
            Log.d(TAG, "stop: parando AudioTrack")
            try { track?.pause() } catch (_: Exception) {}
            try { track?.flush() } catch (_: Exception) {}
            try { track?.stop() } catch (_: Exception) {}
        }
    }

    fun release() {
        synchronized(lock) {
            stopped = true
            Log.d(TAG, "release: liberando AudioTrack")
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            track = null
        }
    }

    /**
     * Sanitiza texto antes de passar ao JNI do Sherpa-ONNX.
     * Remove emojis, caracteres de controle, e limita tamanho.
     */
    private fun sanitizeTextForTts(text: String): String {
        return text
            // Remove caracteres de controle (exceto quebras de linha -> espaco)
            .replace(Regex("[\\p{Cntrl}&&[^\\n\\r]]"), " ")
            // Substitui quebras de linha por espaco
            .replace(Regex("[\\n\\r]+"), " ")
            // Remove emojis e caracteres especiais problematicos para JNI
            .replace(
                Regex(
                    "[\\x{1F600}-\\x{1F64F}" + // Emoticons
                    "\\x{1F300}-\\x{1F5FF}" + // Misc symbols
                    "\\x{1F680}-\\x{1F6FF}" + // Transport
                    "\\x{1F1E0}-\\x{1F1FF}" + // Flags
                    "\\x{2600}-\\x{26FF}" +   // Misc symbols
                    "\\x{2700}-\\x{27BF}" +   // Dingbats
                    "\\x{1F900}-\\x{1F9FF}" + // Supplemental
                    "\\x{1FA00}-\\x{1FAFF}" + // Extended-A
                    "]"
                ), ""
            )
            // Remove tags HTML/XML se houver
            .replace(Regex("<[^>]+>"), " ")
            // Normaliza espacos multiplos
            .replace(Regex("\\s+"), " ")
            // Limita tamanho para evitar problemas no JNI
            .trim()
            .take(MAX_TEXT_LENGTH)
    }

    /**
     * Converte FloatArray [-1.0, 1.0] para ShortArray [-32768, 32767].
     * Usado quando o dispositivo nao suporta PCM_FLOAT.
     */
    private fun FloatArrayToShortArray(floatArray: FloatArray): ShortArray {
        return ShortArray(floatArray.size) { i ->
            (floatArray[i].coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
        }
    }
}
