package com.remodex.mobile.core.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Minimal [AudioRecord] capture → 16-bit mono PCM at device rate → resample to [VoiceWavEncoding.TARGET_SAMPLE_RATE_HZ] WAV.
 * Call [start] on a thread where mic permission is already granted; [stopAndEncodeWav] joins the capture thread.
 */
class BridgeVoiceRecorder {
    @Volatile
    private var captureRunning = false

    private var captureThread: Thread? = null
    private val chunkList = Collections.synchronizedList(mutableListOf<ShortArray>())
    private var inputSampleRateHz = 0

    fun start(onAudioLevel: ((Float) -> Unit)? = null): Boolean {
        synchronized(this) {
            if (captureRunning) return false
            chunkList.clear()
            val opened = openAudioRecord() ?: return false
            val (rec, rate) = opened
            inputSampleRateHz = rate
            captureRunning = true
            captureThread =
                thread(start = true, name = "remodex-voice") {
                    runCapture(rec, onAudioLevel)
                }
            return true
        }
    }

    fun stopAndEncodeWav(): Result<Pair<ByteArray, Double>> {
        synchronized(this) {
            if (!captureRunning && captureThread == null) {
                return Result.failure(IllegalStateException("not_recording"))
            }
            captureRunning = false
        }
        captureThread?.join(10_000L)
        captureThread = null
        val rate = inputSampleRateHz
        inputSampleRateHz = 0
        if (rate <= 0) {
            synchronized(chunkList) { chunkList.clear() }
            return Result.failure(IllegalStateException("no_sample_rate"))
        }
        val chunks =
            synchronized(chunkList) {
                val copy = ArrayList(chunkList)
                chunkList.clear()
                copy
            }
        val total = chunks.sumOf { it.size }
        if (total == 0) {
            return Result.failure(IllegalStateException("empty_audio"))
        }
        val pcm = ShortArray(total)
        var offset = 0
        for (c in chunks) {
            c.copyInto(pcm, destinationOffset = offset)
            offset += c.size
        }
        val durationSeconds = total.toDouble() / rate
        val floats = FloatArray(total) { i -> pcm[i] / 32768f }
        val resampled =
            VoiceWavEncoding.resampleFloatToPcm16(
                floats,
                rate.toDouble(),
                VoiceWavEncoding.TARGET_SAMPLE_RATE_HZ.toDouble(),
            )
        val wav =
            VoiceWavEncoding.pcm16MonoLeToWav(
                resampled,
                VoiceWavEncoding.TARGET_SAMPLE_RATE_HZ,
            )
        return Result.success(wav to durationSeconds)
    }

    fun cancel() {
        synchronized(this) {
            if (!captureRunning && captureThread == null) return
            captureRunning = false
        }
        captureThread?.join(10_000L)
        captureThread = null
        synchronized(chunkList) { chunkList.clear() }
        inputSampleRateHz = 0
    }

    private fun runCapture(
        rec: AudioRecord,
        onAudioLevel: ((Float) -> Unit)?,
    ) {
        try {
            rec.startRecording()
            val buf = ShortArray(2048)
            while (captureRunning) {
                val n = rec.read(buf, 0, buf.size)
                when {
                    n > 0 -> {
                        chunkList.add(buf.copyOf(n))
                        onAudioLevel?.invoke(normalizedRmsLevel(buf, n))
                    }
                    n == AudioRecord.ERROR_INVALID_OPERATION || n == AudioRecord.ERROR_BAD_VALUE -> break
                }
            }
        } catch (_: SecurityException) {
        } finally {
            try {
                rec.stop()
            } catch (_: Exception) {
            }
            try {
                rec.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun normalizedRmsLevel(
        samples: ShortArray,
        count: Int,
    ): Float {
        if (count <= 0) return 0f
        var sumOfSquares = 0.0
        for (i in 0 until count) {
            val sample = samples[i] / 32768.0
            sumOfSquares += sample * sample
        }
        val rms = sqrt(sumOfSquares / count)
        val dB = 20.0 * log10(max(rms, 1e-6))
        return min(1.0, max(0.0, (dB + 50.0) / 50.0)).toFloat()
    }

    private fun openAudioRecord(): Pair<AudioRecord, Int>? {
        val rates = intArrayOf(24000, 44100, 48000, 16000)
        for (rate in rates) {
            val minBuf =
                AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            if (minBuf <= 0) continue
            val bufferSize = max(minBuf * 2, 4096)
            val rec =
                try {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                    )
                } catch (_: Exception) {
                    continue
                }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                continue
            }
            return rec to rate
        }
        return null
    }
}
