package com.remodex.mobile.core.notification

import com.remodex.mobile.core.model.JSONValue

/** Terminal turn state for notification policy (subset of iOS [CodexTurnTerminalState]). */
enum class TurnTerminalStateForNotification {
    Completed,
    Failed,
    Stopped,
}

/** Vocabulary for local run-completion alerts (iOS [CodexRunCompletionResult]). */
enum class RunCompletionAttentionKind {
    Completed,
    Failed,
}

/**
 * Pure parsing for when to show a run-completion notification after `turn/completed`.
 * Mirrors [CodexService.parseTurnTerminalState] / [CodexService.parseTurnFailureMessage] (iOS).
 */
object TurnCompletionNotificationLogic {
    fun normalizeThreadStatusType(raw: String): String =
        raw
            .trim()
            .lowercase()
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")

    fun envelopeEventObject(params: Map<String, JSONValue>?): Map<String, JSONValue>? =
        params?.get("msg")?.objectValue ?: params?.get("event")?.objectValue

    fun parseTurnFailureMessage(params: Map<String, JSONValue>?): String? {
        val p = params ?: return null
        val turnObject = p["turn"]?.objectValue
        val status =
            turnObject?.get("status")?.stringValue?.trim()
                ?: p["status"]?.stringValue?.trim()
        if (status != "failed") return null
        val errObj = turnObject?.get("error")?.objectValue ?: p["error"]?.objectValue
        return errObj?.get("message")?.stringValue?.trim()?.takeIf { it.isNotEmpty() }
            ?: p["errorMessage"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Turn failed with no details"
    }

    fun parseTurnTerminalState(
        params: Map<String, JSONValue>?,
        turnFailureMessage: String?,
    ): TurnTerminalStateForNotification {
        if (turnFailureMessage != null) return TurnTerminalStateForNotification.Failed
        val p = params ?: return TurnTerminalStateForNotification.Completed
        val eventObject = envelopeEventObject(p)
        val turnObject = p["turn"]?.objectValue
        val statusObject =
            turnObject?.get("status")?.objectValue
                ?: p["status"]?.objectValue
                ?: eventObject?.get("status")?.objectValue
        val rawStatus =
            firstNonEmptyString(
                listOf(
                    firstStringIn(turnObject, listOf("status")),
                    firstStringIn(p, listOf("status")),
                    firstStringIn(eventObject, listOf("status")),
                    firstStringIn(statusObject, listOf("type", "statusType", "status_type")),
                ),
            ).orEmpty()
        val normalized = normalizeThreadStatusType(rawStatus)
        if (normalized.contains("cancel") ||
            normalized.contains("abort") ||
            normalized.contains("interrupt") ||
            normalized.contains("stopped")
        ) {
            return TurnTerminalStateForNotification.Stopped
        }
        if (normalized.contains("fail") || normalized.contains("error")) {
            return TurnTerminalStateForNotification.Failed
        }
        return TurnTerminalStateForNotification.Completed
    }

    fun attentionKindFromTerminalState(
        state: TurnTerminalStateForNotification,
    ): RunCompletionAttentionKind? =
        when (state) {
            TurnTerminalStateForNotification.Completed -> RunCompletionAttentionKind.Completed
            TurnTerminalStateForNotification.Failed -> RunCompletionAttentionKind.Failed
            TurnTerminalStateForNotification.Stopped -> null
        }

    private fun firstStringIn(
        obj: Map<String, JSONValue>?,
        keys: List<String>,
    ): String? {
        if (obj == null) return null
        for (k in keys) {
            obj[k]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun firstNonEmptyString(values: List<String?>): String? {
        for (v in values) {
            v?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }
}
