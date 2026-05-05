package com.remodex.mobile.core.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class GptVoiceTranscriptionClientTest {
    @Test
    fun parsesTextField() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"text":"  hello  "}"""))
            val url = server.url("/backend-api/transcribe")
            val client = GptVoiceTranscriptionClient(OkHttpClient(), url)
            val out = client.transcribe(byteArrayOf(0x52, 0x49), "test-token")
            assertEquals("hello", out)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("Bearer test-token", req.getHeader("Authorization"))
        }
    }

    @Test
    fun parsesTranscriptField() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"transcript":"x"}"""))
            val client = GptVoiceTranscriptionClient(OkHttpClient(), server.url("/backend-api/transcribe"))
            assertEquals("x", client.transcribe(byteArrayOf(), "t"))
        }
    }

    @Test
    fun authExpiredOn401() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(401))
            val client = GptVoiceTranscriptionClient(OkHttpClient(), server.url("/backend-api/transcribe"))
            assertFailsWith<GptVoiceTranscriptionError.AuthExpired> {
                client.transcribe(byteArrayOf(), "t")
            }
        }
    }

    @Test
    fun authExpiredOn403() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(403))
            val client = GptVoiceTranscriptionClient(OkHttpClient(), server.url("/backend-api/transcribe"))
            assertFailsWith<GptVoiceTranscriptionError.AuthExpired> {
                client.transcribe(byteArrayOf(), "t")
            }
        }
    }

    @Test
    fun usesErrorObjectMessageOnFailure() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("""{"error":{"message":"rate limited"}}"""),
            )
            val client = GptVoiceTranscriptionClient(OkHttpClient(), server.url("/backend-api/transcribe"))
            val err =
                assertFailsWith<GptVoiceTranscriptionError.TranscriptionFailed> {
                    client.transcribe(byteArrayOf(), "t")
                }
            assertEquals("rate limited", err.message)
        }
    }
}
