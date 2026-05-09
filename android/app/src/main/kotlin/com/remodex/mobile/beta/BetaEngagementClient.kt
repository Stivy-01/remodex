package com.remodex.mobile.beta

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface BetaEngagementApi {
    suspend fun recover(request: BetaRecoverRequest): BetaRecoverResponse
    suspend fun register(request: BetaRegisterRequest): BetaTesterProfile
    suspend fun recordOpen(request: BetaOpenRequest): BetaHqResponse
    suspend fun fetchHq(
        testerId: String,
        appVersion: String,
    ): BetaHqResponse

    suspend fun submitFeedback(request: BetaFeedbackRequest): BetaFeedbackResponse
    suspend fun recordMissionEvent(request: BetaMissionEventRequest): BetaMissionEventResponse
    suspend fun fetchLeaderboard(
        testerId: String,
        appVersion: String,
    ): BetaLeaderboardResponse
}

class BetaEngagementClient(
    private val httpClient: OkHttpClient,
    baseUrl: String,
    private val apiKey: String,
    private val json: Json = BetaJson,
) : BetaEngagementApi {
    private val baseHttpUrl: HttpUrl = baseUrl.toHttpUrl()

    override suspend fun recover(request: BetaRecoverRequest): BetaRecoverResponse =
        post("beta/recover", json.encodeToString(request))

    override suspend fun register(request: BetaRegisterRequest): BetaTesterProfile =
        post("beta/register", json.encodeToString(request))

    override suspend fun recordOpen(request: BetaOpenRequest): BetaHqResponse =
        post("beta/open", json.encodeToString(request))

    override suspend fun fetchHq(
        testerId: String,
        appVersion: String,
    ): BetaHqResponse =
        get(
            endpoint("beta/hq")
                .newBuilder()
                .addQueryParameter("testerId", testerId)
                .addQueryParameter("appVersion", appVersion)
                .build(),
        )

    override suspend fun submitFeedback(request: BetaFeedbackRequest): BetaFeedbackResponse =
        post("beta/feedback", json.encodeToString(request))

    override suspend fun recordMissionEvent(request: BetaMissionEventRequest): BetaMissionEventResponse =
        post("beta/mission-event", json.encodeToString(request))

    override suspend fun fetchLeaderboard(
        testerId: String,
        appVersion: String,
    ): BetaLeaderboardResponse =
        get(
            endpoint("beta/leaderboard")
                .newBuilder()
                .addQueryParameter("testerId", testerId)
                .addQueryParameter("appVersion", appVersion)
                .build(),
        )

    private suspend inline fun <reified T> get(url: HttpUrl): T =
        withContext(Dispatchers.IO) {
            val req =
                Request.Builder()
                    .url(url)
                    .headers()
                    .get()
                    .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("Beta request failed (${resp.code}): ${body.take(160)}")
                }
                json.decodeFromString<T>(body)
            }
        }

    private suspend inline fun <reified T> post(
        path: String,
        bodyJson: String,
    ): T =
        withContext(Dispatchers.IO) {
            val req =
                Request.Builder()
                    .url(endpoint(path))
                    .headers()
                    .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("Beta request failed (${resp.code}): ${body.take(160)}")
                }
                json.decodeFromString<T>(body)
            }
        }

    private fun Request.Builder.headers(): Request.Builder =
        apply {
            header("Accept", "application/json")
            header("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                header("Authorization", "Bearer $apiKey")
                header("apikey", apiKey)
            }
        }

    private fun endpoint(path: String): HttpUrl =
        baseHttpUrl
            .newBuilder()
            .addPathSegments(path.trim('/'))
            .build()
}

val BetaJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
