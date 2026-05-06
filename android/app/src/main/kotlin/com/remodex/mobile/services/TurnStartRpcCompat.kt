package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCMessage

/**
 * Runtime RPC compatibility: `sandboxPolicy` → legacy `sandbox` → minimal payload, plus
 * [approvalPolicy] candidate retries — used for `turn/start`, `thread/start`, and `thread/resume`
 * (iOS [CodexService+RuntimeConfig.sendRequestWithSandboxFallback]).
 */
internal enum class TurnStartEffortWireMode {
    UseEffort,
    UseReasoningEffort,
}

internal fun runtimeSandboxPolicyObject(accessMode: CodexAccessMode): JSONValue.Obj =
    when (accessMode) {
        CodexAccessMode.onRequest ->
            JSONValue.Obj(
                linkedMapOf(
                    "type" to JSONValue.Str("workspaceWrite"),
                    "networkAccess" to JSONValue.Bool(true),
                ),
            )
        CodexAccessMode.fullAccess ->
            JSONValue.Obj(
                linkedMapOf(
                    "type" to JSONValue.Str("dangerFullAccess"),
                ),
            )
    }

internal fun mergeTurnStartParams(
    base: JSONValue.Obj,
    extra: Map<String, JSONValue>,
): JSONValue.Obj {
    val out = LinkedHashMap(base.map)
    extra.forEach { (k, v) -> out[k] = v }
    return JSONValue.Obj(out)
}

internal fun shouldFallbackFromSandboxPolicy(error: Throwable): Boolean {
    val rpcFailure = error as? CodexServiceError.RpcFailure ?: return false
    val code = rpcFailure.rpcError.code
    if (code != -32602 && code != -32600) return false
    val loweredMessage = rpcFailure.rpcError.message.lowercase()
    if (loweredMessage.contains("thread not found") || loweredMessage.contains("unknown thread")) {
        return false
    }
    return loweredMessage.contains("invalid params") ||
        loweredMessage.contains("invalid param") ||
        loweredMessage.contains("unknown field") ||
        loweredMessage.contains("unexpected field") ||
        loweredMessage.contains("unrecognized field") ||
        loweredMessage.contains("failed to parse") ||
        loweredMessage.contains("unsupported")
}

internal fun shouldRetryWithApprovalPolicyFallback(error: Throwable): Boolean {
    val rpcFailure = error as? CodexServiceError.RpcFailure ?: return false
    val code = rpcFailure.rpcError.code
    if (code != -32600 && code != -32602) return false
    val message = rpcFailure.rpcError.message.lowercase()
    return message.contains("approval") ||
        message.contains("unknown variant") ||
        message.contains("expected one of") ||
        message.contains("onrequest") ||
        message.contains("on-request")
}

/**
 * iOS `runtimeServiceTierForTurn`: omit the `serviceTier` field after the bridge has rejected it once this session
 * (`supportsServiceTier` on `CodexService`).
 */
internal fun shouldWireServiceTier(
    supportsBridgeServiceTier: Boolean,
    hasTierSelection: Boolean,
): Boolean = supportsBridgeServiceTier && hasTierSelection

/**
 * When the primary wire key is [TurnStartEffortWireMode.UseEffort] (`effort`, matching iOS), retry with
 * `reasoningEffort` if the bridge rejects the param shape.
 */
internal fun shouldRetryTurnStartWithoutServiceTier(error: Throwable): Boolean {
    val rpcFailure = error as? CodexServiceError.RpcFailure ?: return false
    val code = rpcFailure.rpcError.code
    if (code != -32600 && code != -32602) return false
    val message = rpcFailure.rpcError.message.lowercase()
    return message.contains("servicetier") ||
        message.contains("service tier") ||
        message.contains("unknown field") ||
        message.contains("unexpected field") ||
        message.contains("unrecognized field") ||
        message.contains("invalid param") ||
        message.contains("invalid params") ||
        message.contains("failed to parse") ||
        message.contains("missing field") ||
        message.contains("expected")
}

internal suspend fun CodexService.sendRequestWithApprovalPolicyFallback(
    method: String,
    baseParams: JSONValue.Obj,
): RPCMessage {
    val policies = _selectedAccessMode.value.approvalPolicyCandidates
    var lastError: Throwable? = null
    for ((index, policy) in policies.withIndex()) {
        val params = mergeTurnStartParams(baseParams, mapOf("approvalPolicy" to JSONValue.Str(policy)))
        try {
            return sendRequestImpl(method, params)
        } catch (e: Throwable) {
            lastError = e
            val hasMore = index < policies.size - 1
            if (hasMore && shouldRetryWithApprovalPolicyFallback(e)) {
                continue
            }
            throw e
        }
    }
    throw lastError ?: CodexServiceError.InvalidResponse("$method failed with unknown approvalPolicy error")
}

internal suspend fun CodexService.sendRequestWithSandboxAndApprovalFallback(
    method: String,
    baseParams: JSONValue.Obj,
): RPCMessage {
    val accessMode = _selectedAccessMode.value

    val withSandboxPolicy =
        mergeTurnStartParams(
            baseParams,
            mapOf("sandboxPolicy" to runtimeSandboxPolicyObject(accessMode)),
        )
    try {
        return sendRequestWithApprovalPolicyFallback(method, withSandboxPolicy)
    } catch (e: Throwable) {
        if (!shouldFallbackFromSandboxPolicy(e)) throw e
    }

    val withSandboxLegacy =
        mergeTurnStartParams(
            baseParams,
            mapOf("sandbox" to JSONValue.Str(accessMode.sandboxLegacyValue)),
        )
    try {
        return sendRequestWithApprovalPolicyFallback(method, withSandboxLegacy)
    } catch (e: Throwable) {
        if (!shouldFallbackFromSandboxPolicy(e)) throw e
    }

    return sendRequestWithApprovalPolicyFallback(method, baseParams)
}

internal fun shouldRetryTurnStartEffortKeyAlias(
    error: Throwable,
    mode: TurnStartEffortWireMode,
): Boolean {
    if (mode != TurnStartEffortWireMode.UseEffort) return false
    val rpcFailure = error as? CodexServiceError.RpcFailure ?: return false
    val code = rpcFailure.rpcError.code
    if (code != -32600 && code != -32602) return false
    val m = rpcFailure.rpcError.message.lowercase()
    if (!(m.contains("effort") || m.contains("reasoning"))) return false
    return m.contains("unknown") ||
        m.contains("unexpected") ||
        m.contains("unrecognized") ||
        m.contains("invalid") ||
        m.contains("missing")
}
