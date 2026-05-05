package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.persistence.RelaySessionSnapshot
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.pairing.buildWebSocketConnectParams

/**
 * Sends an explicit desktop handoff request over the existing bridge connection.
 *
 * Prefers the newer desktop-agnostic bridge method and falls back to the legacy
 * macOS-only name when connected to an older bridge build.
 */
sealed class DesktopHandoffError(
    message: String,
) : Exception(message) {
    data object Disconnected : DesktopHandoffError("Not connected to your desktop.")

    data object InvalidResponse : DesktopHandoffError("The desktop app did not return a valid response.")

    class BridgeFailure(
        val errorCode: String?,
        message: String,
    ) : DesktopHandoffError(message)
}

class DesktopHandoffService(
    private val repository: CodexRepository,
    private val savedRelaySnapshotProvider: (() -> RelaySessionSnapshot)? = null,
) {
    suspend fun continueOnDesktop(threadId: String) {
        val trimmedThreadId = threadId.trim()
        if (trimmedThreadId.isEmpty()) {
            throw DesktopHandoffError.BridgeFailure(
                errorCode = "missing_thread_id",
                message = "This chat does not have a valid thread id yet.",
            )
        }

        val params =
            JSONValue.Obj(
                mapOf(
                    "threadId" to JSONValue.Str(trimmedThreadId),
                ),
            )

        try {
            requireSuccess(
                repository.sendRequest(
                    method = "desktop/continueOnDesktop",
                    params = params,
                ),
            )
        } catch (error: CodexServiceError.RpcFailure) {
            if (!shouldFallbackToLegacyMacMethod(error)) {
                throw mapRpcFailure(error)
            }

            try {
                requireSuccess(
                    repository.sendRequest(
                        method = "desktop/continueOnMac",
                        params = params,
                    ),
                )
            } catch (legacyError: CodexServiceError) {
                throw mapServiceError(legacyError)
            }
        } catch (error: CodexServiceError) {
            throw mapServiceError(error)
        }
    }

    suspend fun wakeDisplay() {
        if (!repository.isSessionReady.value) {
            connectUsingSavedPairingForWake()
        }
        try {
            requireSuccess(
                repository.sendRequest(
                    method = "desktop/wakeDisplay",
                    params = JSONValue.Obj(emptyMap()),
                ),
            )
        } catch (error: CodexServiceError) {
            throw mapServiceError(error)
        }
    }

    private fun requireSuccess(response: com.remodex.mobile.core.model.RPCMessage) {
        val resultObject = response.result?.objectValue
        if (resultObject?.get("success")?.boolValue != true) {
            throw DesktopHandoffError.InvalidResponse
        }
    }

    private fun shouldFallbackToLegacyMacMethod(error: CodexServiceError.RpcFailure): Boolean {
        val message = error.rpcError.message.lowercase()
        return error.rpcError.code == -32601 ||
            message.contains("unknown desktop method") ||
            message.contains("desktop/continueondesktop") ||
            message.contains("method not found")
    }

    private fun mapServiceError(error: CodexServiceError): DesktopHandoffError =
        when (error) {
            CodexServiceError.Disconnected -> DesktopHandoffError.Disconnected
            is CodexServiceError.RpcFailure -> mapRpcFailure(error)
            else ->
                DesktopHandoffError.BridgeFailure(
                    errorCode = null,
                    message = error.message ?: "Could not continue this chat on your desktop.",
                )
        }

    private fun mapRpcFailure(error: CodexServiceError.RpcFailure): DesktopHandoffError {
        val errorCode = error.rpcError.data?.objectValue?.get("errorCode")?.stringValue
        val message =
            when (errorCode) {
                "missing_thread_id" -> "This chat does not have a valid thread id yet."
                "unsupported_platform" -> "Desktop handoff works only when the bridge is running on macOS or Windows."
                "handoff_failed" -> error.rpcError.message.ifBlank { "Could not open Codex on your desktop." }
                "wake_display_failed" -> error.rpcError.message.ifBlank { "Could not wake your computer's display right now." }
                "saved_pair_required" -> error.rpcError.message.ifBlank { "Reconnect to your paired computer or scan a new QR code first." }
                "unsupported_bridge_preferences" -> error.rpcError.message.ifBlank { "Update the Remodex bridge on your computer to sync this setting." }
                "invalid_bridge_preferences" -> error.rpcError.message.ifBlank { "The computer bridge rejected this setting update." }
                else -> error.rpcError.message.ifBlank { "Could not continue this chat on your desktop." }
            }
        return DesktopHandoffError.BridgeFailure(errorCode = errorCode, message = message)
    }

    private suspend fun connectUsingSavedPairingForWake() {
        val snapshot = savedRelaySnapshotProvider?.invoke()
        if (snapshot == null || snapshot.relayUrl.isNullOrBlank() || snapshot.relaySessionId.isNullOrBlank()) {
            throw DesktopHandoffError.BridgeFailure(
                errorCode = "saved_pair_required",
                message = "Reconnect to your paired computer or scan a new QR code first.",
            )
        }
        val (url, token) =
            try {
                buildWebSocketConnectParams(snapshot)
            } catch (error: Throwable) {
                throw DesktopHandoffError.BridgeFailure(
                    errorCode = "saved_pair_required",
                    message =
                        error.message?.trim()?.takeIf { it.isNotEmpty() }
                            ?: "Reconnect to your paired computer or scan a new QR code first.",
                )
            }
        try {
            repository.connect(serverUrl = url, token = token, role = null)
        } catch (error: CodexServiceError) {
            throw mapServiceError(error)
        }
    }
}
