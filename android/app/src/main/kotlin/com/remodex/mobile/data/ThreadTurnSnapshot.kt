package com.remodex.mobile.data

import com.remodex.mobile.core.model.JSONValue

/**
 * Estrae da `thread` (payload `thread/read`) il turno interrompibile corrente.
 * Port ridotto da [readThreadTurnStateSnapshot](CodexMobile/CodexMobile/Services/CodexService+ThreadsTurns.swift).
 */
internal data class ThreadTurnInterruptSnapshot(
    val interruptibleTurnId: String?,
    val hasInterruptibleTurnWithoutId: Boolean,
    val latestTurnId: String?,
)

internal object ThreadTurnSnapshot {
    fun fromThreadObject(threadObject: Map<String, JSONValue>): ThreadTurnInterruptSnapshot {
        val turns = threadObject["turns"]?.arrayValue ?: return ThreadTurnInterruptSnapshot(null, false, null)
        val turnObjects = turns.mapNotNull { it.objectValue }
        if (turnObjects.isEmpty()) {
            return ThreadTurnInterruptSnapshot(null, false, null)
        }

        val latestTurnId =
            turnObjects.asReversed().firstNotNullOfOrNull { turn ->
                normalizeTurnId(
                    turn["id"]?.stringValue
                        ?: turn["turnId"]?.stringValue
                        ?: turn["turn_id"]?.stringValue,
                )
            }

        var hasInterruptibleWithoutId = false
        for (turnObject in turnObjects.asReversed()) {
            val status = normalizedInterruptTurnStatus(turnObject)
            if (!isInterruptibleTurnStatus(status)) continue

            val id =
                normalizeTurnId(
                    turnObject["id"]?.stringValue
                        ?: turnObject["turnId"]?.stringValue
                        ?: turnObject["turn_id"]?.stringValue,
                )
            if (id != null) {
                return ThreadTurnInterruptSnapshot(id, false, latestTurnId)
            }
            hasInterruptibleWithoutId = true
        }

        return ThreadTurnInterruptSnapshot(null, hasInterruptibleWithoutId, latestTurnId)
    }

    private fun normalizeTurnId(raw: String?): String? {
        val t = raw?.trim() ?: return null
        return t.ifEmpty { null }
    }

    private fun normalizedInterruptTurnStatus(turnObject: Map<String, JSONValue>): String? {
        val status =
            turnObject["status"]?.stringValue
                ?: turnObject["turnStatus"]?.stringValue
                ?: turnObject["turn_status"]?.stringValue
                ?: return null
        val trimmed = status.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.replace("_", "").replace("-", "").lowercase()
    }

    private fun isInterruptibleTurnStatus(normalized: String?): Boolean {
        if (normalized == null) return true
        if (normalized.contains("inprogress") ||
            normalized.contains("running") ||
            normalized.contains("pending") ||
            normalized.contains("started")
        ) {
            return true
        }
        if (normalized.contains("complete") ||
            normalized.contains("failed") ||
            normalized.contains("error") ||
            normalized.contains("interrupt") ||
            normalized.contains("cancel") ||
            normalized.contains("stopped")
        ) {
            return false
        }
        return true
    }
}
