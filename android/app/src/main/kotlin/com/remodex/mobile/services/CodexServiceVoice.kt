package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCError
import com.remodex.mobile.core.voice.CodexVoiceTranscriptionPreflight
import com.remodex.mobile.core.voice.GptVoiceTranscriptionClient
import com.remodex.mobile.core.voice.GptVoiceTranscriptionError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Voice transcription transport (parity [CodexService+Voice.swift](CodexMobile/CodexMobile/Services/CodexService+Voice.swift)).
 * Bridge auth + ChatGPT multipart upload; no recorder/composer UI.
 */

internal fun parseVoiceAuthTokenFromResult(result: JSONValue?): String {
    val root =
        result as? JSONValue.Obj
            ?: throw CodexServiceError.InvalidResponse("voice/resolveAuth did not return a valid token")
    return extractVoiceAuthToken(root.map)
        ?: throw CodexServiceError.InvalidResponse("voice/resolveAuth did not return a valid token")
}

private fun extractVoiceAuthToken(map: Map<String, JSONValue>): String? {
    map["token"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let {
        return it
    }
    for (key in listOf("data", "result")) {
        val nested = (map[key] as? JSONValue.Obj)?.map ?: continue
        extractVoiceAuthToken(nested)?.let {
            return it
        }
    }
    return null
}

internal suspend fun transcribeWavWithSingleAuthRetry(
    wavBytes: ByteArray,
    resolveToken: suspend () -> String,
    transcribe: suspend (ByteArray, String) -> String,
): String {
    val first = resolveToken()
    return try {
        transcribe(wavBytes, first)
    } catch (_: GptVoiceTranscriptionError.AuthExpired) {
        val second = resolveToken()
        transcribe(wavBytes, second)
    }
}

internal fun CodexService.consumeUnsupportedVoiceBridgeAuth(error: Throwable): Boolean {
    val rpc = (error as? CodexServiceError.RpcFailure)?.rpcError ?: return false
    if (!rpcIndicatesUnsupportedVoiceBridgeAuth(rpc)) return false
    supportsBridgeVoiceAuth = false
    return true
}

/** Exposed for JVM tests (parity iOS `shouldTreatAsUnsupportedVoiceBridgeAuth`). */
internal fun rpcIndicatesUnsupportedVoiceBridgeAuth(rpc: RPCError): Boolean {
    if (rpc.code == -32601) return true
    val message = rpc.message.lowercase()
    val mentionsUnsupportedRequest =
        message.contains("method not found") ||
            message.contains("unknown method") ||
            message.contains("not implemented") ||
            message.contains("does not support") ||
            message.contains("unknown variant") ||
            message.contains("expected one of")
    val mentionsBridgeVoiceMethod =
        message.contains("voice/resolveauth") ||
            message.contains("voice resolveauth") ||
            message.contains("voice/resolveauth`") ||
            message.contains("voice/transcribe") ||
            message.contains("voice transcribe") ||
            message.contains("voice/transcribe`")
    if (rpc.code != -32600 && rpc.code != -32602 && rpc.code != -32000) {
        return mentionsUnsupportedRequest && mentionsBridgeVoiceMethod
    }
    return mentionsUnsupportedRequest && mentionsBridgeVoiceMethod
}

private suspend fun CodexService.resolveVoiceAuthToken(): String {
    val response =
        try {
            sendRequestImpl("voice/resolveAuth", null)
        } catch (e: Exception) {
            consumeUnsupportedVoiceBridgeAuth(e)
            throw e
        }
    return parseVoiceAuthTokenFromResult(response.result)
}

internal suspend fun CodexService.transcribeBridgeVoiceWavImpl(
    wavBytes: ByteArray,
    durationSeconds: Double,
): String =
    withContext(Dispatchers.IO) {
        if (!sessionReady) throw CodexServiceError.Disconnected
        CodexVoiceTranscriptionPreflight(
            byteCount = wavBytes.size,
            durationSeconds = durationSeconds,
        ).validate()
        val client = GptVoiceTranscriptionClient(httpCallClient)
        transcribeWavWithSingleAuthRetry(
            wavBytes = wavBytes,
            resolveToken = { resolveVoiceAuthToken() },
            transcribe = { bytes, token -> client.transcribe(bytes, token) },
        )
    }
