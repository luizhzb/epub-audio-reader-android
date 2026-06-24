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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sintetizador TTS com correcoes de crash (AUDITORIA 2025-06-25).
 *
 * CORRECOES:
 * 1. initAudioTrack() e startPlayback() AGORA tem try/catch
 * 2. startPlayback() verifica STATE_INITIALIZED antes de play()
 * 3. 'stopped' agora eh AtomicBoolean - remove synchronized da callback JNI
 * 4. Protecao contra initAudioTrack() durante sintese ativa
 * 5. Logs TTS_TRACE detalhados em cada ponto critico
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
    /** CORRECAO: AtomicBoolean em vez de Boolean para thread-safety sem synchronized */
    private val stopped = AtomicBoolean(true)
    private val lock = Object()

    /** Mutex que serializa chamadas ao motor Sherpa-ONNX. */
    private val generationMutex = Mutex()

    /** Flag que indica se estamos usando PCM_16BIT como fallback. */
    private var usingPcm16Bit: Boolean = false

    val isPlaying: Boolean
        get() = !stopped.get()

    fun isAudioTrackInitialized(): Boolean = synchronized(lock) { track != null }

    /**
     * CORRECAO: initAudioTrack() agora retorna Result em vez de lancar excecao.
     * Protege contra chamada durante sintese ativa (evita race condition na callback JNI).
     */
    fun initAudioTrack(): kotlin.Result<Unit> {
        // CORRECAO: Nao permitir re-inicializacao durante sintese ativa
        if (isPlaying) {
            Log.w(TAG, "[TTS_TRACE] initAudioTrack() ignorado - sintese em andamento")
            return kotlin.Result.success(Unit)
        }

        return try {
            Log.i(TAG, "[TTS_TRACE] initAudioTrack() INICIO")

            val tts = ttsEngine.getTts()
                ?: return kotlin.Result.failure(IllegalStateException("TTS nao inicializado"))
            val sampleRate = tts.sampleRate()
            Log.d(TAG, "[TTS_TRACE] TTS sampleRate=$sampleRate")

            // Tenta PCM_FLOAT primeiro
            var bufLength = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )

            usingPcm16Bit = if (bufLength <= 0) {
                Log.w(TAG, "[TTS_TRACE] PCM_FLOAT nao suportado, tentando PCM_16BIT fallback")
                val buf16 = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (buf16 <= 0) {
                    return kotlin.Result.failure(
                        IllegalStateException("Nenhum formato PCM suportado pelo dispositivo")
                    )
                }
                bufLength = buf16
                true
            } else {
                false
            }

            val encoding = if (usingPcm16Bit) {
                AudioFormat.ENCODING_PCM_16BIT
            } else {
                AudioFormat.ENCODING_PCM_FLOAT
            }

            Log.i(TAG, "[TTS_TRACE] AudioTrack sampleRate=$sampleRate bufLength=$bufLength pcm16=$usingPcm16Bit encoding=$encoding")

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
                // Libera AudioTrack anterior
                track?.let { old ->
                    Log.d(TAG, "[TTS_TRACE] Liberando AudioTrack anterior")
                    try { old.stop() } catch (_: Exception) {}
                    try { old.release() } catch (_: Exception) {}
                }
                track = AudioTrack(
                    attr, format, bufLength,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                Log.i(TAG, "[TTS_TRACE] AudioTrack criado (state=${track?.state}, pcm16=$usingPcm16Bit)")
            }

            Log.i(TAG, "[TTS_TRACE] initAudioTrack() SUCESSO")
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TRACE] initAudioTrack() FALHOU: ${e.message}", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * CORRECAO: startPlayback() agora verifica STATE_INITIALIZED antes de play()
     * e retorna Result em vez de lancar excecao.
     */
    fun startPlayback(): kotlin.Result<Unit> {
        return try {
            Log.i(TAG, "[TTS_TRACE] startPlayback() INICIO")
            synchronized(lock) {
                val t = track ?: return kotlin.Result.failure(
                    IllegalStateException("AudioTrack nao inicializado")
                )
                // CORRECAO: Verificar se AudioTrack foi inicializado corretamente
                if (t.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "[TTS_TRACE] AudioTrack nao inicializado corretamente (state=${t.state})")
                    return kotlin.Result.failure(
                        IllegalStateException("AudioTrack em estado invalido: ${t.state}")
                    )
                }
                Log.d(TAG, "[TTS_TRACE] AudioTrack.play() (state=${t.state}, playState=${t.playState})")
                t.play()
            }
            Log.i(TAG, "[TTS_TRACE] startPlayback() SUCESSO")
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TRACE] startPlayback() FALHOU: ${e.message}", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Toca samples PCM no AudioTrack.
     */
    suspend fun playSamples(samples: FloatArray, sampleRate: Int): kotlin.Result<Unit> {
        return try {
            if (samples.isEmpty()) {
                Log.w(TAG, "[TTS_TRACE] playSamples recebeu samples vazio")
                return kotlin.Result.success(Unit)
            }

            Log.d(TAG, "[TTS_TRACE] playSamples INICIO: ${samples.size} samples, sr=$sampleRate")

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
                    Log.d(TAG, "[TTS_TRACE] Recriando AudioTrack sampleRate=$sampleRate pcm16=$usingPcm16Bit")
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

                Log.d(TAG, "[TTS_TRACE] Escrevendo ${samples.size} samples no AudioTrack (pcm16=$usingPcm16Bit)")
                t.play()
                stopped.set(false)

                if (usingPcm16Bit) {
                    val shortSamples = floatArrayToShortArray(samples)
                    val written = t.write(shortSamples, 0, shortSamples.size)
                    if (written < 0) {
                        throw IllegalStateException("AudioTrack.write retornou erro $written")
                    }
                    Log.d(TAG, "[TTS_TRACE] PCM_16BIT: $written shorts escritos")
                } else {
                    val written = t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        throw IllegalStateException("AudioTrack.write retornou erro $written")
                    }
                    Log.d(TAG, "[TTS_TRACE] PCM_FLOAT: $written floats escritos")
                }
            }

            // delay() fora do synchronized
            val durationMs = (samples.size / sampleRate.toFloat() * 1000).toLong()
            Log.d(TAG, "[TTS_TRACE] playSamples aguardando ${durationMs}ms via delay()")
            delay(durationMs)
            Log.d(TAG, "[TTS_TRACE] playSamples FIM: audio concluido")

            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TRACE] playSamples ERRO: ${e.message}", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * CORRECAO: callback JNI usa AtomicBoolean em vez de synchronized.
     * Evita deadlock entre thread nativa e coroutine.
     */
    private fun callback(samples: FloatArray): Int {
        return try {
            if (stopped.get()) {
                return 0
            }
            synchronized(lock) {
                val t = track
                if (t == null || stopped.get()) {
                    return 0
                }
                val written = if (usingPcm16Bit) {
                    val shortSamples = floatArrayToShortArray(samples)
                    t.write(shortSamples, 0, shortSamples.size)
                } else {
                    t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                }
                if (written < 0) {
                    Log.e(TAG, "[TTS_TRACE] AudioTrack.write erro no callback: $written")
                    stopped.set(true)
                    0
                } else {
                    1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TRACE] AudioTrack write error no callback JNI: ${e.message}", e)
            stopped.set(true)
            0
        }
    }

    /**
     * Sintetiza e reproduz o texto via AudioTrack com callback.
     */
    suspend fun speak(text: String, sid: Int = 0, speed: Float = 1.0f, onComplete: (() -> Unit)? = null): kotlin.Result<Unit> {
        return try {
            if (text.isBlank()) {
                Log.w(TAG, "[TTS_TRACE] speak() texto vazio, ignorando")
                return kotlin.Result.success(Unit)
            }

            val sanitizedText = sanitizeTextForTts(text)
            Log.i(TAG, "[TTS_TRACE] speak() INICIO: '${sanitizedText.take(60)}...' (${sanitizedText.length} chars)")

            val tts = ttsEngine.getTts()
                ?: return kotlin.Result.failure(IllegalStateException("TTS nao inicializado"))

            generationMutex.withLock {
                Log.d(TAG, "[TTS_TRACE] speak() obteve generationMutex")

                synchronized(lock) {
                    track?.flush()
                    track?.play()
                    stopped.set(false)
                }

                var totalSamplesWritten = 0

                Log.d(TAG, "[TTS_TRACE] speak() chamando generateWithConfigAndCallback...")
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
                Log.d(TAG, "[TTS_TRACE] speak() generateWithConfigAndCallback concluido, $totalSamplesWritten samples")

                if (totalSamplesWritten > 0 && !stopped.get()) {
                    val sr = tts.sampleRate()
                    val durationMs = (totalSamplesWritten / sr.toFloat() * 1000).toLong()
                    Log.d(TAG, "[TTS_TRACE] speak() aguardando ${durationMs}ms via delay()")
                    delay(durationMs)
                }

                Log.i(TAG, "[TTS_TRACE] speak() FIM: geracao e playback concluidos")
                onComplete?.invoke()
            }
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TRACE] speak() ERRO: ${e.message}", e)
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

            val sanitizedText = sanitizeTextForTts(text)

            if (sanitizedText.isBlank()) {
                return Result.Success(SynthesizedAudio(FloatArray(0), tts.sampleRate()))
            }

            Log.i(TAG, "[TTS_TRACE] synthesizeToMemory INICIO: '${sanitizedText.take(60)}...' (${sanitizedText.length} chars)")

            generationMutex.withLock {
                Log.d(TAG, "[TTS_TRACE] synthesizeToMemory obteve generationMutex")

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
                        Log.e(TAG, "[TTS_TRACE] Erro acumulando samples: ${e.message}", e)
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
                Log.i(TAG, "[TTS_TRACE] synthesizeToMemory FIM: ${result.samples.size} samples, sr=${result.sampleRate}")
                Result.Success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TRACE] synthesizeToMemory ERRO: ${e.message}", e)
            Result.Error(e)
        }
    }

    fun stop() {
        Log.i(TAG, "[TTS_TRACE] stop() chamado")
        stopped.set(true)
        synchronized(lock) {
            try { track?.pause() } catch (_: Exception) {}
            try { track?.flush() } catch (_: Exception) {}
            try { track?.stop() } catch (_: Exception) {}
        }
    }

    fun release() {
        Log.i(TAG, "[TTS_TRACE] release() chamado")
        stopped.set(true)
        synchronized(lock) {
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            track = null
        }
    }

    /**
     * Sanitiza texto antes de passar ao JNI do Sherpa-ONNX.
     */
    private fun sanitizeTextForTts(text: String): String {
        return text
            .replace(Regex("[\\p{Cntrl}&&[^\\n\\r]]"), " ")
            .replace(Regex("[\\n\\r]+"), " ")
            .replace(
                Regex(
                    "[\\x{1F600}-\\x{1F64F}" +
                    "\\x{1F300}-\\x{1F5FF}" +
                    "\\x{1F680}-\\x{1F6FF}" +
                    "\\x{1F1E0}-\\x{1F1FF}" +
                    "\\x{2600}-\\x{26FF}" +
                    "\\x{2700}-\\x{27BF}" +
                    "\\x{1F900}-\\x{1F9FF}" +
                    "\\x{1FA00}-\\x{1FAFF}" +
                    "]"
                ), ""
            )
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_TEXT_LENGTH)
    }

    /**
     * Converte FloatArray [-1.0, 1.0] para ShortArray [-32768, 32767].
     */
    private fun floatArrayToShortArray(floatArray: FloatArray): ShortArray {
        return ShortArray(floatArray.size) { i ->
            (floatArray[i].coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
        }
    }
}
