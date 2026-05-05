package com.remodex.mobile.core.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VoiceWavEncodingTest {
    @Test
    fun floatToPcm16_matchesIosClamping() {
        assertEquals(32767, VoiceWavEncoding.floatSampleToPcm16(1f).toInt())
        assertEquals(-32767, VoiceWavEncoding.floatSampleToPcm16(-1f).toInt())
        assertEquals(0, VoiceWavEncoding.floatSampleToPcm16(0f).toInt())
        assertEquals(16383, VoiceWavEncoding.floatSampleToPcm16(0.5f).toInt())
        assertEquals(32767, VoiceWavEncoding.floatSampleToPcm16(2f).toInt())
        assertEquals(-32767, VoiceWavEncoding.floatSampleToPcm16(-2f).toInt())
    }

    @Test
    fun resample_sameRate_isDirectQuantization() {
        val src = floatArrayOf(0f, 0.5f, -0.25f)
        val out =
            VoiceWavEncoding.resampleFloatToPcm16(
                src,
                srcSampleRateHz = 24_000.0,
                dstSampleRateHz = 24_000.0,
            )
        assertEquals(3, out.size)
        assertEquals(VoiceWavEncoding.floatSampleToPcm16(0f), out[0])
        assertEquals(VoiceWavEncoding.floatSampleToPcm16(0.5f), out[1])
        assertEquals(VoiceWavEncoding.floatSampleToPcm16(-0.25f), out[2])
    }

    @Test
    fun resample_halfRate_halvesLength() {
        val src = FloatArray(480) { 0.1f }
        val out =
            VoiceWavEncoding.resampleFloatToPcm16(
                src,
                srcSampleRateHz = 48_000.0,
                dstSampleRateHz = 24_000.0,
            )
        assertEquals(240, out.size)
        val expected = VoiceWavEncoding.floatSampleToPcm16(0.1f)
        assertEquals(expected, out[0])
        assertEquals(expected, out[out.lastIndex])
    }

    @Test
    fun resample_halfRate_usesLinearInterpolationWhenFractionNonZero() {
        val src = floatArrayOf(0f, 0.25f, 0.75f, 1f)
        val out =
            VoiceWavEncoding.resampleFloatToPcm16(
                src,
                srcSampleRateHz = 48_000.0,
                dstSampleRateHz = 24_000.0,
            )
        assertEquals(2, out.size)
        assertEquals(VoiceWavEncoding.floatSampleToPcm16(0f), out[0])
        assertEquals(VoiceWavEncoding.floatSampleToPcm16(0.75f), out[1])
    }

    @Test
    fun resample_empty_returnsEmpty() {
        assertContentEquals(
            ShortArray(0),
            VoiceWavEncoding.resampleFloatToPcm16(
                floatArrayOf(),
                srcSampleRateHz = 48_000.0,
                dstSampleRateHz = 24_000.0,
            ),
        )
    }

    @Test
    fun wav_emptyPcm_has44ByteHeaderAndZeroData() {
        val wav = VoiceWavEncoding.pcm16MonoLeToWav(ShortArray(0), VoiceWavEncoding.TARGET_SAMPLE_RATE_HZ)
        assertEquals(44, wav.size)
        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4)
        bb.get(riff)
        assertContentEquals("RIFF".toByteArray(Charsets.US_ASCII), riff)
        assertEquals(36, bb.int)
        val wave = ByteArray(4)
        bb.get(wave)
        assertContentEquals("WAVE".toByteArray(Charsets.US_ASCII), wave)
    }

    @Test
    fun wav_roundTrip_pcmMatchesAndHeaderIsPcm16Mono24k() {
        val pcm = shortArrayOf(100, -200, 3000, -32767)
        val wav = VoiceWavEncoding.pcm16MonoLeToWav(pcm, VoiceWavEncoding.TARGET_SAMPLE_RATE_HZ)
        assertEquals(44 + pcm.size * 2, wav.size)
        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4)
        bb.get(riff)
        assertContentEquals("RIFF".toByteArray(Charsets.US_ASCII), riff)
        assertEquals(36 + pcm.size * 2, bb.int)
        val wave = ByteArray(4)
        bb.get(wave)
        assertContentEquals("WAVE".toByteArray(Charsets.US_ASCII), wave)
        val fmt = ByteArray(4)
        bb.get(fmt)
        assertContentEquals("fmt ".toByteArray(Charsets.US_ASCII), fmt)
        assertEquals(16, bb.int)
        assertEquals(1.toShort(), bb.short)
        assertEquals(1.toShort(), bb.short)
        assertEquals(VoiceWavEncoding.TARGET_SAMPLE_RATE_HZ, bb.int)
        assertEquals(VoiceWavEncoding.TARGET_SAMPLE_RATE_HZ * 2, bb.int)
        assertEquals(2.toShort(), bb.short)
        assertEquals(16.toShort(), bb.short)
        val data = ByteArray(4)
        bb.get(data)
        assertContentEquals("data".toByteArray(Charsets.US_ASCII), data)
        assertEquals(pcm.size * 2, bb.int)
        val decoded = ShortArray(pcm.size) { bb.short }
        assertContentEquals(pcm, decoded)
    }

    @Test
    fun wav_requiresPositiveSampleRate() {
        assertFailsWith<IllegalArgumentException> {
            VoiceWavEncoding.pcm16MonoLeToWav(shortArrayOf(0), 0)
        }
    }

    @Test
    fun endToEnd_floatsTo24kWav() {
        val floats =
            FloatArray(240) { i ->
                kotlin.math.sin(2.0 * kotlin.math.PI * i / 240.0).toFloat() * 0.2f
            }
        val pcm =
            VoiceWavEncoding.resampleFloatToPcm16(
                floats,
                srcSampleRateHz = 24_000.0,
                dstSampleRateHz = 24_000.0,
            )
        val wav = VoiceWavEncoding.pcm16MonoLeToWav(pcm, VoiceWavEncoding.TARGET_SAMPLE_RATE_HZ)
        assertEquals(44 + pcm.size * 2, wav.size)
        assertContentEquals(pcm, readWavPcmPayload(wav))
    }

    private fun readWavPcmPayload(wav: ByteArray): ShortArray {
        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(40)
        val dataSize = bb.int
        val sampleCount = dataSize / 2
        return ShortArray(sampleCount) { bb.short }
    }
}
