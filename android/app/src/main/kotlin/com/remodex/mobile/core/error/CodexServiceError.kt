package com.remodex.mobile.core.error

import com.remodex.mobile.core.model.RPCError

/**
 * Taxonomy of failures from the bridge client layer (transport, RPC, validation).
 * Mirrors [CodexServiceError.swift](CodexMobile/CodexMobile/Services/CodexServiceError.swift).
 */
sealed class CodexServiceError(
    message: String,
) : Exception(message) {
    class InvalidServerURL(
        val value: String,
    ) : CodexServiceError("Invalid server URL: $value")

    class InvalidInput(
        reason: String,
    ) : CodexServiceError(reason)

    class InvalidResponse(
        reason: String,
    ) : CodexServiceError(reason)

    data object EncodingFailed : CodexServiceError("Unable to encode JSON-RPC payload")

    data object Disconnected : CodexServiceError("WebSocket not connected")

    data object NoPendingApproval : CodexServiceError("No pending approval request")

    class RpcFailure(
        val rpcError: RPCError,
    ) : CodexServiceError("RPC error ${rpcError.code}: ${rpcError.message}")

    /** After [com.remodex.mobile.services.handleMissingThread]; clearer than raw -32600 in UI. */
    data object ThreadRemovedOnServer : CodexServiceError(
        "This conversation is no longer on the bridge. Choose another thread or start a new chat.",
    )
}
