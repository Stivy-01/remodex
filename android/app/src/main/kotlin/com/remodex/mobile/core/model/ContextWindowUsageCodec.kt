package com.remodex.mobile.core.model

import kotlin.math.max
import kotlin.math.min

/**
 * Decodes bridge `usage` objects for context window display.
 * Key aliases mirror [CodexService+IncomingSupport.swift](../../../../../../../CodexMobile/CodexMobile/Services/CodexService+IncomingSupport.swift) `extractContextWindowUsage`.
 */
object ContextWindowUsageCodec {
    /**
     * [thread/tokenUsage/updated] and similar pushes: `usage` object or envelope `usage`, else root params
     * (parity [CodexService+Incoming.swift] `handleThreadTokenUsageUpdated`).
     */
    fun decodeFromIncomingUsageParams(params: Map<String, JSONValue>?): ContextWindowUsage? {
        if (params == null) return null
        val env = params["msg"]?.objectValue ?: params["event"]?.objectValue
        val usageObj = params["usage"]?.objectValue ?: env?.get("usage")?.objectValue
        val candidate =
            when {
                usageObj != null -> usageObj
                else -> params
            }
        return decodeObject(candidate)
    }

    /**
     * Root `thread` object from [thread/read] may embed usage fields at top level
     * (parity `extractContextWindowUsageIfAvailable`).
     */
    fun decodeFromThreadReadThreadObject(threadObject: Map<String, JSONValue>): ContextWindowUsage? =
        decodeObject(threadObject)

    /**
     * Legacy `codex/event` payloads with `type == token_count` / nested `info`
     * (parity `extractContextWindowUsageFromTokenCountPayload`).
     */
    fun decodeFromLegacyTokenCountPayload(payload: Map<String, JSONValue>): ContextWindowUsage? {
        val infoObject = payload["info"]?.objectValue ?: payload
        val infoRoot = JSONValue.Obj(infoObject)

        fun JSONValue.objMap(): Map<String, JSONValue>? = (this as? JSONValue.Obj)?.map

        val lastUsageRoot =
            firstJsonChild(
                infoRoot,
                listOf("last_token_usage", "lastTokenUsage"),
            )
        val totalUsageRoot =
            firstJsonChild(
                infoRoot,
                listOf("total_token_usage", "totalTokenUsage", "last_token_usage", "lastTokenUsage"),
            ) ?: infoRoot

        val preferred = lastUsageRoot ?: totalUsageRoot
        val preferredMap = preferred.objMap() ?: return null

        val explicitTotal =
            firstInt(
                preferredMap,
                listOf("total_tokens", "totalTokens"),
            )
        val inputTokens =
            firstInt(
                preferredMap,
                listOf("input_tokens", "inputTokens"),
            ) ?: 0
        val outputTokens =
            firstInt(
                preferredMap,
                listOf("output_tokens", "outputTokens"),
            ) ?: 0
        val reasoningTokens =
            firstInt(
                preferredMap,
                listOf("reasoning_output_tokens", "reasoningOutputTokens"),
            ) ?: 0

        val tokenLimit =
            firstInt(
                infoObject,
                listOf(
                    "model_context_window",
                    "modelContextWindow",
                    "context_window",
                    "contextWindow",
                    "tokenLimit",
                    "token_limit",
                ),
            )

        if (tokenLimit == null || tokenLimit <= 0) return null

        val resolvedUsed =
            explicitTotal ?: (inputTokens + outputTokens + reasoningTokens)

        return ContextWindowUsage(
            tokensUsed = min(max(0, resolvedUsed), tokenLimit),
            tokenLimit = tokenLimit,
        )
    }

    private fun firstJsonChild(
        root: JSONValue,
        keys: List<String>,
    ): JSONValue? {
        val obj = root as? JSONValue.Obj ?: return null
        for (k in keys) {
            obj.map[k]?.let { return it }
        }
        return null
    }

    fun decode(usageValue: JSONValue?): ContextWindowUsage? {
        val obj =
            when (usageValue) {
                null, JSONValue.Null -> return null
                is JSONValue.Obj -> usageValue.map
                else -> return null
            }
        return decodeObject(obj)
    }

    fun decodeObject(usageObject: Map<String, JSONValue>): ContextWindowUsage? {
        val tokensUsed =
            firstInt(
                usageObject,
                listOf(
                    "tokensUsed",
                    "tokens_used",
                    "totalTokens",
                    "total_tokens",
                    "usedTokens",
                    "used_tokens",
                    "inputTokens",
                    "input_tokens",
                ),
            )

        val explicitLimit =
            firstInt(
                usageObject,
                listOf(
                    "tokenLimit",
                    "token_limit",
                    "maxTokens",
                    "max_tokens",
                    "contextWindow",
                    "context_window",
                    "contextSize",
                    "context_size",
                    "maxContextTokens",
                    "max_context_tokens",
                    "inputTokenLimit",
                    "input_token_limit",
                    "maxInputTokens",
                    "max_input_tokens",
                ),
            )

        val tokensRemaining =
            firstInt(
                usageObject,
                listOf(
                    "tokensRemaining",
                    "tokens_remaining",
                    "remainingTokens",
                    "remaining_tokens",
                    "remainingInputTokens",
                    "remaining_input_tokens",
                ),
            )

        val resolvedTokensUsed = max(0, tokensUsed ?: 0)
        val resolvedTokenLimit =
            explicitLimit
                ?: tokensRemaining?.let { rem -> resolvedTokensUsed + max(0, rem) }

        if (resolvedTokenLimit == null || resolvedTokenLimit <= 0) return null

        return ContextWindowUsage(
            tokensUsed = min(resolvedTokensUsed, resolvedTokenLimit),
            tokenLimit = resolvedTokenLimit,
        )
    }

    private fun firstInt(
        o: Map<String, JSONValue>,
        keys: List<String>,
    ): Int? {
        for (k in keys) {
            when (val v = o[k]) {
                is JSONValue.NumLong -> return v.value.toInt()
                is JSONValue.NumDouble -> return v.value.toInt()
                is JSONValue.Str -> v.value.toIntOrNull()?.let { return it }
                else -> continue
            }
        }
        return null
    }
}
