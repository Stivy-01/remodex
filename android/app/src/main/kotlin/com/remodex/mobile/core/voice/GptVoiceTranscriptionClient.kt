package com.remodex.mobile.core.voice

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Direct ChatGPT transcription upload (parity with [GPTVoiceTranscriptionManager.transcribe] on iOS).
 * [httpClient] and [transcriptionUrl] are injectable for tests (e.g. [okhttp3.mockwebserver.MockWebServer.url]).
 */
class GptVoiceTranscriptionClient(
    private val httpClient: OkHttpClient,
    private val transcriptionUrl: HttpUrl = DEFAULT_TRANSCRIPTION_URL,
) {

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    fun transcribe(
        wavBytes: ByteArray,
        token: String,
    ): String {
        val boundary = "Remodex-${UUID.randomUUID()}"
        val fileBody = wavBytes.toRequestBody("audio/wav".toMediaType())
        val multipart =
            MultipartBody.Builder(boundary)
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "voice.wav", fileBody)
                .build()

        val request =
            Request.Builder()
                .url(transcriptionUrl)
                .post(multipart)
                .header("Authorization", "Bearer $token")
                .build()

        httpClient.newCall(request).execute().use { response ->
            val code = response.code
            if (code == 401 || code == 403) {
                throw GptVoiceTranscriptionError.AuthExpired
            }
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val serverMessage = extractErrorMessage(body)
                throw GptVoiceTranscriptionError.TranscriptionFailed(
                    serverMessage ?: "Transcription failed ($code).",
                )
            }
            return decodeTranscriptText(body)
        }
    }

    private fun extractErrorMessage(body: String): String? {
        val root =
            runCatching { json.parseToJsonElement(body) }
                .getOrNull()
                ?.jsonObject ?: return null
        val errObj = root["error"]?.jsonObject ?: return (root["message"] as? JsonPrimitive)?.content
        return (errObj["message"] as? JsonPrimitive)?.content
    }

    private fun decodeTranscriptText(body: String): String {
        val root =
            runCatching { json.parseToJsonElement(body) }
                .getOrNull() as? JsonObject
                ?: throw GptVoiceTranscriptionError.TranscriptionFailed("Could not parse transcript response.")
        for (key in listOf("text", "transcript")) {
            val el = root[key] ?: continue
            val text =
                (el as? JsonPrimitive)?.content?.trim().orEmpty()
            if (text.isNotEmpty()) return text
        }
        throw GptVoiceTranscriptionError.TranscriptionFailed("Transcript response was empty.")
    }

    companion object {
        val DEFAULT_TRANSCRIPTION_URL: HttpUrl =
            "https://chatgpt.com/backend-api/transcribe".toHttpUrl()
    }
}
