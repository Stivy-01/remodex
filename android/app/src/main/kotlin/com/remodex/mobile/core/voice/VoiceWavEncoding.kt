package com.remodex.mobile.core.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Pure JVM helpers for 16-bit mono PCM WAV at a chosen sample rate (parity with
 * [GPTVoiceTranscriptionManager](CodexMobile/CodexMobile/Services/GPTVoiceTranscriptionManager.swift)
 * `resample` + `encodeWAV`). No [android.media.AudioRecord]; safe for unit tests on the JVM.
 */
object VoiceWavEncoding {
    /** Target rate for ChatGPT voice upload clips (matches iOS `targetSampleRate`). */
    const val TARGET_SAMPLE_RATE_HZ: Int = 24_000

    private const val RATE_EQUAL_EPSILON: Double = 1.0

    /**
     * Clamps to [-1, 1] and scales like iOS `floatToInt16` (`value * Int16.max` with `Int16.max` = 32767).
     */
    fun floatSampleToPcm16(value: Float): Short {
        val coerced = value.coerceIn(-1f, 1f)
        return (coerced * 32767f).toInt().toShort()
    }

    /**
     * Linear-interpolation resample from float32 source samples to int16 PCM at [dstSampleRateHz],
     * matching iOS `resample(_:from:to:)`.
     */
    fun resampleFloatToPcm16(
        samples: FloatArray,
        srcSampleRateHz: Double,
        dstSampleRateHz: Double,
    ): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)
        if (abs(srcSampleRateHz - dstSampleRateHz) < RATE_EQUAL_EPSILON) {
            return ShortArray(samples.size) { i -> floatSampleToPcm16(samples[i]) }
        }
        val ratio = dstSampleRateHz / srcSampleRateHz
        val outCount = (samples.size.toDouble() * ratio).toInt()
        if (outCount <= 0) return ShortArray(0)
        val out = ShortArray(outCount)
        val lastIndex = samples.lastIndex
        for (i in 0 until outCount) {
            val srcIdx = i / ratio
            val idx = srcIdx.toInt()
            val frac = (srcIdx - idx).toFloat()
            val s0 = samples[minOf(idx, lastIndex)]
            val s1 = samples[minOf(idx + 1, lastIndex)]
            out[i] = floatSampleToPcm16(s0 + frac * (s1 - s0))
        }
        return out
    }

    /**
     * Builds a minimal RIFF/WAVE PCM16 mono file (little-endian), matching iOS `encodeWAV`.
     */
    fun pcm16MonoLeToWav(pcmSamples: ShortArray, sampleRateHz: Int): ByteArray {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        val dataSize = pcmSamples.size * 2
        val riffChunkSize = 36 + dataSize
        val buf =
            ByteBuffer
                .allocate(44 + dataSize)
                .order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(riffChunkSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(1)
        buf.putInt(sampleRateHz)
        buf.putInt(sampleRateHz * 2)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (s in pcmSamples) {
            buf.putShort(s)
        }
        return buf.array()
    }
}
