package com.remodex.mobile.services

import com.remodex.mobile.R
import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCMessage
import com.remodex.mobile.core.model.UsageStatusRefreshPolicy
import com.remodex.mobile.core.transport.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Account rate limits (`account/rateLimits/read`, `account/rateLimits/updated`).
 * Parity with [CodexService+Status.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+Status.swift).
 */
internal fun CodexService.applyRateLimitsPayload(
    payloadObject: Map<String, JSONValue>,
    mergeWithExisting: Boolean,
) {
    val decoded = RateLimitPayloadCodec.decodeRateLimitBuckets(payloadObject)
    val resolved =
        if (mergeWithExisting) {
            RateLimitPayloadCodec.mergeRateLimitBuckets(
                _rateLimitBuckets.value,
                decoded,
            )
        } else {
            decoded
        }
    _rateLimitBuckets.value =
        resolved.sortedWith { lhs, rhs ->
            if (lhs.sortDurationMins == rhs.sortDurationMins) {
                lhs.displayLabel.compareTo(rhs.displayLabel, ignoreCase = true)
            } else {
                lhs.sortDurationMins.compareTo(rhs.sortDurationMins)
            }
        }
}

internal fun CodexService.handleRateLimitsUpdatedParams(params: Map<String, JSONValue>?) {
    if (params == null) return
    applyRateLimitsPayload(params, mergeWithExisting = true)
    _hasResolvedRateLimitsSnapshot.value = true
    _rateLimitsErrorMessage.value = null
}

internal suspend fun CodexService.refreshRateLimitsForRepository() =
    withContext(Dispatchers.IO) {
        refreshRateLimitsInternal()
    }

internal suspend fun CodexService.refreshUsageStatusForRepository(threadId: String?) =
    withContext(Dispatchers.IO) {
        val tid = threadId?.trim().orEmpty()
        if (tid.isNotEmpty()) {
            refreshContextWindowUsageInternal(tid)
        }
        refreshRateLimitsInternal()
    }

internal fun CodexService.shouldAutoRefreshUsageStatusForRepository(threadId: String?): Boolean =
    UsageStatusRefreshPolicy.shouldAutoRefresh(
        sessionReady = sessionReady,
        connected = _connectionState.value is ConnectionState.Connected,
        threadId = threadId,
        contextWindowUsageByThread = _contextWindowUsageByThread.value,
        hasResolvedRateLimitsSnapshot = _hasResolvedRateLimitsSnapshot.value,
    )

internal suspend fun CodexService.refreshRateLimitsInternal() {
    if (!sessionReady) {
        return
    }
    _isLoadingRateLimits.value = true
    try {
        val response = fetchRateLimitsWithCompatRetry()
        val resultObject = response.result?.objectValue
        if (resultObject == null) {
            throw CodexServiceError.InvalidResponse("account/rateLimits/read response missing payload")
        }
        applyRateLimitsPayload(resultObject, mergeWithExisting = false)
        _hasResolvedRateLimitsSnapshot.value = true
        _rateLimitsErrorMessage.value = null
    } catch (e: Exception) {
        _hasResolvedRateLimitsSnapshot.value = false
        _rateLimitBuckets.value = emptyList()
        val message = e.message?.trim().orEmpty()
        _rateLimitsErrorMessage.value =
            if (message.isEmpty()) {
                appContext.getString(R.string.usage_rate_limits_load_failed)
            } else {
                message
            }
    } finally {
        _isLoadingRateLimits.value = false
    }
}

private suspend fun CodexService.fetchRateLimitsWithCompatRetry(): RPCMessage {
    return try {
        sendRequestImpl("account/rateLimits/read", JSONValue.Null)
    } catch (e: Exception) {
        if (!shouldRetryRateLimitsWithEmptyParams(e)) throw e
        sendRequestImpl("account/rateLimits/read", JSONValue.Obj(emptyMap()))
    }
}

private fun shouldRetryRateLimitsWithEmptyParams(e: Throwable): Boolean {
    val rpc = (e as? CodexServiceError.RpcFailure)?.rpcError ?: return false
    if (rpc.code != -32602 && rpc.code != -32600) return false
    val lowered = rpc.message.lowercase()
    return lowered.contains("invalid params") ||
        lowered.contains("invalid param") ||
        lowered.contains("failed to parse") ||
        lowered.contains("expected") ||
        lowered.contains("missing field `params`") ||
        lowered.contains("missing field params")
}
