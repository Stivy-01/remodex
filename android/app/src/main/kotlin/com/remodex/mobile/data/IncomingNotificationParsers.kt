package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue

/** Shared param extraction for Mac→phone notifications (iOS IncomingSupport patterns). */
internal object IncomingNotificationParsers {
    fun envelopeEvent(params: Map<String, JSONValue>?): Map<String, JSONValue>? =
        params?.get("msg")?.objectValue ?: params?.get("event")?.objectValue

    fun normalizedAssistantPhase(rawPhase: String?): String? =
        rawPhase
            ?.trim()
            ?.replace("-", "_")
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }

    fun extractAssistantPhase(
        params: Map<String, JSONValue>?,
        itemObject: Map<String, JSONValue>? = null,
    ): String? {
        val ev = envelopeEvent(params)
        listOf(
            params?.get("phase")?.stringValue,
            ev?.get("phase")?.stringValue,
            itemObject?.get("phase")?.stringValue,
            params?.get("event")?.objectValue?.get("phase")?.stringValue,
        ).forEach { value ->
            normalizedAssistantPhase(value)?.let { return it }
        }
        return null
    }

    fun extractThreadId(params: Map<String, JSONValue>?): String? {
        if (params == null) return null
        fun norm(s: String?) = CodexThread.normalizeIdentifier(s)
        norm(params["threadId"]?.stringValue)?.let { return it }
        norm(params["thread_id"]?.stringValue)?.let { return it }
        norm(params["conversationId"]?.stringValue)?.let { return it }
        norm(params["conversation_id"]?.stringValue)?.let { return it }
        norm(params["thread"]?.objectValue?.get("id")?.stringValue)?.let { return it }
        norm(params["turn"]?.objectValue?.get("threadId")?.stringValue)?.let { return it }
        norm(params["turn"]?.objectValue?.get("thread_id")?.stringValue)?.let { return it }
        norm(params["item"]?.objectValue?.get("threadId")?.stringValue)?.let { return it }
        norm(params["item"]?.objectValue?.get("thread_id")?.stringValue)?.let { return it }
        envelopeEvent(params)?.let { ev ->
            norm(ev["threadId"]?.stringValue)?.let { return it }
            norm(ev["thread_id"]?.stringValue)?.let { return it }
            norm(ev["conversationId"]?.stringValue)?.let { return it }
            norm(ev["conversation_id"]?.stringValue)?.let { return it }
            norm(ev["thread"]?.objectValue?.get("id")?.stringValue)?.let { return it }
            norm(ev["turn"]?.objectValue?.get("threadId")?.stringValue)?.let { return it }
            norm(ev["turn"]?.objectValue?.get("thread_id")?.stringValue)?.let { return it }
            norm(ev["item"]?.objectValue?.get("threadId")?.stringValue)?.let { return it }
            norm(ev["item"]?.objectValue?.get("thread_id")?.stringValue)?.let { return it }
        }
        extractThreadIdFromNestedEvent(params["event"]?.objectValue)?.let { return it }
        return null
    }

    private fun extractThreadIdFromNestedEvent(event: Map<String, JSONValue>?): String? {
        if (event == null) return null
        fun norm(s: String?) = CodexThread.normalizeIdentifier(s)
        norm(event["threadId"]?.stringValue)?.let { return it }
        norm(event["thread_id"]?.stringValue)?.let { return it }
        norm(event["conversationId"]?.stringValue)?.let { return it }
        norm(event["conversation_id"]?.stringValue)?.let { return it }
        norm(event["thread"]?.objectValue?.get("id")?.stringValue)?.let { return it }
        norm(event["turn"]?.objectValue?.get("threadId")?.stringValue)?.let { return it }
        norm(event["turn"]?.objectValue?.get("thread_id")?.stringValue)?.let { return it }
        return null
    }

    /**
     * Normalizes params for legacy `token_count` handling (parity Swift `handleLegacyTokenCountEvent`).
     */
    fun normalizedLegacyTokenCountParams(
        paramsObject: Map<String, JSONValue>?,
        payload: Map<String, JSONValue>,
    ): Map<String, JSONValue> {
        val base = paramsObject?.toMutableMap() ?: mutableMapOf()
        if (!base.containsKey("event")) {
            base["event"] = JSONValue.Obj(payload)
        }
        if (base["threadId"] == null) {
            firstNonEmptyString(
                payload,
                listOf("threadId", "thread_id", "conversationId", "conversation_id"),
            )?.let { base["threadId"] = JSONValue.Str(it) }
        }
        if (base["turnId"] == null) {
            firstNonEmptyString(payload, listOf("turnId", "turn_id", "id"))
                ?.let { base["turnId"] = JSONValue.Str(it) }
        }
        return base
    }

    private fun firstNonEmptyString(
        o: Map<String, JSONValue>,
        keys: List<String>,
    ): String? {
        for (k in keys) {
            o[k]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    fun extractTurnId(params: Map<String, JSONValue>?): String? {
        if (params == null) return null
        params["turn"]?.objectValue?.get("id")?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        params["turnId"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        params["turn_id"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        envelopeEvent(params)?.let { ev ->
            ev["turnId"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            ev["turn"]?.objectValue?.get("id")?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    /** Per `turn/started` / `turn/completed`: come iOS `extractTurnIDForTurnLifecycleEvent` (anche `id` top-level / envelope). */
    fun extractTurnIdForTurnLifecycleEvent(params: Map<String, JSONValue>?): String? {
        if (params == null) return null
        extractTurnId(params)?.let { return it }
        fun norm(s: String?) = CodexThread.normalizeIdentifier(s)
        norm(params["id"]?.stringValue)?.let { return it }
        val ev = envelopeEvent(params)
        norm(ev?.get("id")?.stringValue)?.let { return it }
        val nested = params["event"]?.objectValue
        norm(nested?.get("id")?.stringValue)?.let { return it }
        return null
    }

    fun extractItemId(params: Map<String, JSONValue>?): String? {
        if (params == null) return null
        val ev = envelopeEvent(params)
        val candidates =
            listOf(
                params["itemId"]?.stringValue,
                params["item_id"]?.stringValue,
                params["call_id"]?.stringValue,
                params["callId"]?.stringValue,
                params["item"]?.objectValue?.get("id")?.stringValue,
                ev?.get("itemId")?.stringValue,
                ev?.get("item_id")?.stringValue,
                ev?.get("call_id")?.stringValue,
                ev?.get("callId")?.stringValue,
                ev?.get("item")?.objectValue?.get("id")?.stringValue,
            )
        for (s in candidates) {
            s?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    /**
     * Delta testo generico (reasoning, file change, tool, …) — parity iOS `extractTextDelta`.
     */
    fun extractTextDelta(params: Map<String, JSONValue>?): String? {
        if (params == null) return null
        val ev = envelopeEvent(params)
        val nested = params["event"]?.objectValue
        listOf(
            params["delta"]?.stringValue,
            params["textDelta"]?.stringValue,
            params["text_delta"]?.stringValue,
            params["text"]?.stringValue,
            params["summary"]?.stringValue,
            params["part"]?.stringValue,
            ev?.get("delta")?.stringValue,
            ev?.get("text")?.stringValue,
            ev?.get("summary")?.stringValue,
            ev?.get("part")?.stringValue,
            nested?.get("delta")?.stringValue,
            nested?.get("text")?.stringValue,
        ).forEach { s ->
            s?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    fun extractIncomingItemObject(
        params: Map<String, JSONValue>,
        event: Map<String, JSONValue>?,
    ): Map<String, JSONValue>? {
        params["item"]?.objectValue?.let { return it }
        event?.get("item")?.objectValue?.let { return it }
        params["event"]?.objectValue?.get("item")?.objectValue?.let { return it }
        if (isLikelyIncomingItemPayload(params)) return params
        if (event != null && isLikelyIncomingItemPayload(event)) return event
        val nested = params["event"]?.objectValue
        if (nested != null && isLikelyIncomingItemPayload(nested)) return nested
        return null
    }

    private fun isLikelyIncomingItemPayload(obj: Map<String, JSONValue>): Boolean {
        val type = obj["type"]?.stringValue ?: return false
        if (normalizedItemType(type).isEmpty()) return false
        val keys = obj.keys
        return keys.any {
            it == "content" ||
                it == "status" ||
                it == "output" ||
                it == "changes" ||
                it == "files" ||
                it == "diff" ||
                it == "patch" ||
                it == "result" ||
                it == "payload" ||
                it == "data"
        }
    }

    private fun normalizedItemType(raw: String): String =
        raw.replace("_", "").replace("-", "").lowercase()

    fun extractAssistantDelta(
        params: Map<String, JSONValue>?,
    ): String? {
        if (params == null) return null
        extractTextDelta(params)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val ev = envelopeEvent(params)
        listOf(
            { params["delta"]?.stringValue },
            { params["text"]?.stringValue },
            { ev?.get("delta")?.stringValue },
            { ev?.get("text")?.stringValue },
        ).forEach { fn ->
            fn()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    fun extractErrorMessage(params: Map<String, JSONValue>?): String? {
        if (params == null) return null
        val ev = envelopeEvent(params)
        listOf(
            params["message"]?.stringValue,
            params["error"]?.objectValue?.get("message")?.stringValue,
            ev?.get("message")?.stringValue,
            ev?.get("error")?.objectValue?.get("message")?.stringValue,
        ).forEach { s ->
            s?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return "Server error"
    }

    fun extractUserMirrorText(params: Map<String, JSONValue>?): String? {
        if (params == null) return null
        listOf(
            params["message"]?.stringValue,
            params["text"]?.stringValue,
            envelopeEvent(params)?.get("message")?.stringValue,
        ).forEach { s ->
            s?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    fun resolveThreadId(
        params: Map<String, JSONValue>?,
        turnIdHint: String?,
    ): String? {
        extractThreadId(params)?.let { return it }
        if (turnIdHint != null) {
            // Bridge may only send turnId; thread mapping is filled by earlier events on iOS.
            return null
        }
        return null
    }
}
