package com.epubaudioreader.core.tts.synthesis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.tts.engine.TtsEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsSynthesizer @Inject constructor(
    private val ttsEngine: TtsEngine
) {
    companion object {
        private const val TAG = "TtsSynthesizer"
    }

    private var track: AudioTrack? = null
    @Volatile
    private var stopped: Boolean = true
    private val lock = Object()

    /**
     * Mutex que serializa chamadas ao motor Sherpa-ONNX.
     *
     * OfflineTts nao e thread-safe: gerar varios segmentos em paralelo
     * (como o pipeline faz no prefetch) corrompe o estado nativo e pode
     * travar ou retornar audio vazio. Apenas uma geracao por vez.
     *
     * Operacoes de AudioTrack (playSamples/stop/etc.) continuam usando
     * [lock] e podem rodar concorrentemente com sintese para memoria,
     * permitindo prefetch enquanto o segmento atual toca.
     */
    private val generationMutex = Mutex()

    val isPlaying: Boolean
        get() = synchronized(lock) { !stopped }

    fun isAudioTrackInitialized(): Boolean = synchronized(lock) { track != null }

    fun initAudioTrack() {
        val tts = ttsEngine.getTts() ?: throw IllegalStateException("TTS nao inicializado")
        val sampleRate = tts.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i(TAG, "AudioTrack sampleRate=$sampleRate bufLength=$bufLength")

        if (bufLength <= 0) {
            throw IllegalStateException("Formato PCM_FLOAT nao suportado pelo dispositivo (bufLength=$bufLength)")
        }

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
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
            Log.i(TAG, "AudioTrack criado (state=${track?.state})")
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
     * Toca samples PCM float previamente sintetizados no AudioTrack.
     * Usado pelo PlaybackCoordinator quando o pipeline ja tem o audio em cache.
     *
     * Aguarda o audio terminar de tocar antes de retornar (BUG-001 fix).
     */
    fun playSamples(samples: FloatArray, sampleRate: Int): kotlin.Result<Unit> {
        return try {
            if (samples.isEmpty()) {
                Log.w(TAG, "playSamples recebeu samples vazio")
                return kotlin.Result.success(Unit)
            }

            val bufLength = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            if (bufLength <= 0) {
                return kotlin.Result.failure(
                    IllegalStateException("Formato PCM_FLOAT nao suportado (bufLength=$bufLength)")
                )
            }

            synchronized(lock) {
                // Se o sampleRate mudou ou o track esta parado/liberado, recria.
                // AudioTrack em MODE_STREAM nao permite transicoes arbitrarias
                // STOPPED -> PAUSE; recriar e o mais seguro.
                val needsRecreate = track?.sampleRate != sampleRate ||
                        track?.state == AudioTrack.STATE_UNINITIALIZED ||
                        track?.playState == AudioTrack.PLAYSTATE_STOPPED

                if (needsRecreate) {
                    Log.d(TAG, "Recriando AudioTrack sampleRate=$sampleRate")
                    try { track?.stop() } catch (_: Exception) {}
                    try { track?.release() } catch (_: Exception) {}

                    val attr = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
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

                Log.d(TAG, "playSamples: ${samples.size} samples, sampleRate=$sampleRate, playState=${t.playState}")
                // BUG-003: Removido flush() que cortava audio pendente
                t.play()
                stopped = false
                val written = t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                Log.d(TAG, "playSamples: escritos $written floats")
                if (written < 0) {
                    throw IllegalStateException("AudioTrack.write retornou erro $written")
                }

                // BUG-001: Aguardar o audio terminar de tocar antes de retornar
                val durationMs = (samples.size / sampleRate.toFloat() * 1000).toLong()
                Log.d(TAG, "playSamples: aguardando ${durationMs}ms para audio terminar")
                Thread.sleep(durationMs)
                Log.d(TAG, "playSamples: audio concluido")
            }
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro em playSamples: ${e.message}", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Callback chamado pelo JNI do Sherpa-ONNX durante a geracao.
     * Retorna 1 para continuar, 0 para parar.
     *
     * IMPORTANTE: Este callback e invocado por thread nativa do JNI.
     * Qualquer excecao nao tratada causa crash fatal (signal 6/11).
     * SEMPRE usar try/catch aqui.
     */
    private fun callback(samples: FloatArray): Int {
        return try {
            synchronized(lock) {
                if (!stopped) {
                    val t = track
                    if (t != null) {
                        val written = t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
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
     *
     * Aguarda o playback completar antes de retornar (BUG-002 fix).
     */
    suspend fun speak(text: String, sid: Int = 0, speed: Float = 1.0f, onComplete: (() -> Unit)? = null): kotlin.Result<Unit> {
        return try {
            if (text.isBlank()) return kotlin.Result.success(Unit)

            val tts = ttsEngine.getTts()
                ?: return kotlin.Result.failure(IllegalStateException("TTS nao inicializado"))

            // Serializa geracao para evitar corrupcao do motor nativo.
            generationMutex.withLock {
                Log.d(TAG, "speak: '${text.take(60)}...'")

                // Resetar AudioTrack entre geracoes
                synchronized(lock) {
                    track?.flush()
                    track?.play()
                    stopped = false
                }

                // Contador de samples para calcular duracao do playback (BUG-002)
                var totalSamplesWritten = 0

                // Gerar com callback (thread de background)
                tts.generateWithCallback(
                    text = text,
                    sid = sid,
                    speed = speed,
                    callback = { samples ->
                        val result = callback(samples)
                        if (result == 1) {
                            totalSamplesWritten += samples.size
                        }
                        result
                    }
                )

                // BUG-002: Aguardar o audio terminar de tocar antes de retornar
                if (totalSamplesWritten > 0 && !stopped) {
                    val sr = tts.sampleRate()
                    val durationMs = (totalSamplesWritten / sr.toFloat() * 1000).toLong()
                    Log.d(TAG, "speak: aguardando ${durationMs}ms para playback terminar")
                    Thread.sleep(durationMs)
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
     * Sintetiza o texto para memoria, retornando os samples PCM float.
     * Util para prefetch no pipeline de sintese.
     *
     * Usa FloatArray diretamente para evitar OOM com boxing (BUG-010 fix).
     */
    suspend fun synthesizeToMemory(
        text: String,
        sid: Int = 0,
        speed: Float = 1.0f
    ): Result<SynthesizedAudio> {
        return try {
            val tts = ttsEngine.getTts()
                ?: return Result.Error(IllegalStateException("TTS nao inicializado"))
            if (text.isBlank()) {
                return Result.Success(SynthesizedAudio(FloatArray(0), tts.sampleRate()))
            }

            // Serializa geracao para evitar corrupcao do motor nativo.
            generationMutex.withLock {
                Log.d(TAG, "synthesizeToMemory: '${text.take(60)}...'")

                // BUG-010: Usar FloatArray em vez de MutableList<Float> para evitar OOM
                var samples = FloatArray(0)
                var totalSize = 0

                val memoryCallback: (FloatArray) -> Int = { chunk ->
                    try {
                        if (chunk.isNotEmpty()) {
                            val newSize = totalSize + chunk.size
                            if (newSize > samples.size) {
                                // Realocar com margem de crescimento (1.5x + margem minima)
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

                tts.generateWithCallback(
                    text = text,
                    sid = sid,
                    speed = speed,
                    callback = memoryCallback
                )

                // Truncar array para o tamanho exato
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
}
