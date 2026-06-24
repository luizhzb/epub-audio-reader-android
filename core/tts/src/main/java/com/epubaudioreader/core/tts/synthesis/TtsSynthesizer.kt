package com.epubaudioreader.core.tts.synthesis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.epubaudioreader.core.common.result.Result
import com.epubaudioreader.core.tts.engine.TtsEngine
import com.k2fsa.sherpa.onnx.GenerationConfig
import kotlinx.coroutines.*
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
                t.flush()
                t.play()
                stopped = false
                val written = t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                Log.d(TAG, "playSamples: escritos $written floats")
                if (written < 0) {
                    throw IllegalStateException("AudioTrack.write retornou erro $written")
                }
                // NAO chamar stop() aqui: o AudioTrack continua tocando o buffer.
                // O PlaybackCoordinator chama stop() quando o usuario pausa.
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

                // Gerar com callback (thread de background)
                val genConfig = GenerationConfig(sid = sid, speed = speed)
                tts.generateWithConfigAndCallback(
                    text = text,
                    config = genConfig,
                    callback = this::callback
                )

                Log.d(TAG, "speak: geracao concluida")
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
                val samples = mutableListOf<Float>()
                val memoryCallback: (FloatArray) -> Int = { chunk ->
                    try {
                        if (chunk.isNotEmpty()) {
                            samples.addAll(chunk.toList())
                        }
                        1
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro acumulando samples: ${e.message}", e)
                        0
                    }
                }

                val genConfig = GenerationConfig(sid = sid, speed = speed)
                tts.generateWithConfigAndCallback(
                    text = text,
                    config = genConfig,
                    callback = memoryCallback
                )

                val result = SynthesizedAudio(samples.toFloatArray(), tts.sampleRate())
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
