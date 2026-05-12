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
        val turnObjects = threadObject["turns"]?.arrayValue?.mapNotNull { it.objectValue }.orEmpty()
        val latestTurnId = latestTurnIdFromTurns(turnObjects) ?: latestTurnIdFromThread(threadObject)

        explicitActiveTurnSnapshot(threadObject, latestTurnId)?.let { return it }

        var hasInterruptibleWithoutId = false
        for (turnObject in turnObjects.asReversed()) {
            val status = normalizedInterruptTurnStatus(turnObject)
            if (!isInterruptibleTurnStatus(status)) continue

            val id = turnIdFromTurnObject(turnObject)
            if (id != null) {
                return ThreadTurnInterruptSnapshot(id, false, latestTurnId)
            }
            hasInterruptibleWithoutId = true
        }

        return ThreadTurnInterruptSnapshot(null, hasInterruptibleWithoutId, latestTurnId)
    }

    private fun explicitActiveTurnSnapshot(
        threadObject: Map<String, JSONValue>,
        latestTurnId: String?,
    ): ThreadTurnInterruptSnapshot? {
        for (turnObject in activeTurnObjects(threadObject)) {
            val status = normalizedInterruptTurnStatus(turnObject)
            if (!isInterruptibleTurnStatus(status)) continue

            val id = turnIdFromTurnObject(turnObject)
            if (id != null) {
                return ThreadTurnInterruptSnapshot(id, false, latestTurnId ?: id)
            }
            return ThreadTurnInterruptSnapshot(null, true, latestTurnId)
        }

        activeTurnIdFromThread(threadObject)?.let { id ->
            return ThreadTurnInterruptSnapshot(id, false, latestTurnId ?: id)
        }

        val threadStatus = normalizedInterruptTurnStatus(threadObject)
        if (topLevelTurnIdCanBeActive(threadObject, threadStatus)) {
            topLevelTurnIdFromThread(threadObject)?.let { id ->
                return ThreadTurnInterruptSnapshot(id, false, latestTurnId ?: id)
            }
        }

        if (threadHasRunningFlag(threadObject) || hasExplicitInterruptibleStatus(threadObject, threadStatus)) {
            return ThreadTurnInterruptSnapshot(null, true, latestTurnId)
        }

        return null
    }

    private fun activeTurnObjects(threadObject: Map<String, JSONValue>): List<Map<String, JSONValue>> =
        listOfNotNull(
            threadObject["activeTurn"]?.objectValue,
            threadObject["active_turn"]?.objectValue,
            threadObject["currentTurn"]?.objectValue,
            threadObject["current_turn"]?.objectValue,
            threadObject["runningTurn"]?.objectValue,
            threadObject["running_turn"]?.objectValue,
            threadObject["inFlightTurn"]?.objectValue,
            threadObject["in_flight_turn"]?.objectValue,
            threadObject["turn"]?.objectValue,
        )

    private fun latestTurnIdFromTurns(turnObjects: List<Map<String, JSONValue>>): String? =
        turnObjects.asReversed().firstNotNullOfOrNull { turn -> turnIdFromTurnObject(turn) }

    private fun latestTurnIdFromThread(threadObject: Map<String, JSONValue>): String? =
        normalizeTurnId(
            threadObject["latestTurnId"]?.stringValue
                ?: threadObject["latest_turn_id"]?.stringValue
                ?: threadObject["lastTurnId"]?.stringValue
                ?: threadObject["last_turn_id"]?.stringValue,
        )

    private fun activeTurnIdFromThread(threadObject: Map<String, JSONValue>): String? =
        normalizeTurnId(
            threadObject["activeTurnId"]?.stringValue
                ?: threadObject["active_turn_id"]?.stringValue
                ?: threadObject["currentTurnId"]?.stringValue
                ?: threadObject["current_turn_id"]?.stringValue
                ?: threadObject["runningTurnId"]?.stringValue
                ?: threadObject["running_turn_id"]?.stringValue
                ?: threadObject["inFlightTurnId"]?.stringValue
                ?: threadObject["in_flight_turn_id"]?.stringValue
                ?: threadObject["interruptibleTurnId"]?.stringValue
                ?: threadObject["interruptible_turn_id"]?.stringValue,
        )

    private fun topLevelTurnIdFromThread(threadObject: Map<String, JSONValue>): String? =
        normalizeTurnId(
            threadObject["turnId"]?.stringValue
                ?: threadObject["turn_id"]?.stringValue,
        )

    private fun topLevelTurnIdCanBeActive(
        threadObject: Map<String, JSONValue>,
        threadStatus: String?,
    ): Boolean =
        topLevelTurnIdFromThread(threadObject) != null &&
            (threadHasRunningFlag(threadObject) || hasExplicitInterruptibleStatus(threadObject, threadStatus))

    private fun hasExplicitInterruptibleStatus(
        threadObject: Map<String, JSONValue>,
        threadStatus: String?,
    ): Boolean =
        hasStatusField(threadObject) && isInterruptibleTurnStatus(threadStatus)

    private fun hasStatusField(threadObject: Map<String, JSONValue>): Boolean =
        threadObject["status"]?.stringValue != null ||
            threadObject["turnStatus"]?.stringValue != null ||
            threadObject["turn_status"]?.stringValue != null ||
            threadObject["state"]?.stringValue != null ||
            threadObject["phase"]?.stringValue != null ||
            threadObject["lifecycleState"]?.stringValue != null ||
            threadObject["lifecycle_state"]?.stringValue != null

    private fun threadHasRunningFlag(threadObject: Map<String, JSONValue>): Boolean =
        listOfNotNull(
            threadObject["isRunning"]?.boolValue,
            threadObject["is_running"]?.boolValue,
            threadObject["running"]?.boolValue,
            threadObject["hasActiveTurn"]?.boolValue,
            threadObject["has_active_turn"]?.boolValue,
            threadObject["inProgress"]?.boolValue,
            threadObject["in_progress"]?.boolValue,
            threadObject["hasInFlightTurn"]?.boolValue,
            threadObject["has_in_flight_turn"]?.boolValue,
        ).any { it }

    private fun turnIdFromTurnObject(turnObject: Map<String, JSONValue>): String? =
        normalizeTurnId(
            turnObject["id"]?.stringValue
                ?: turnObject["turnId"]?.stringValue
                ?: turnObject["turn_id"]?.stringValue,
        )

    private fun normalizeTurnId(raw: String?): String? {
        val t = raw?.trim() ?: return null
        return t.ifEmpty { null }
    }

    private fun normalizedInterruptTurnStatus(turnObject: Map<String, JSONValue>): String? {
        val status =
            turnObject["status"]?.stringValue
                ?: turnObject["turnStatus"]?.stringValue
                ?: turnObject["turn_status"]?.stringValue
                ?: turnObject["state"]?.stringValue
                ?: turnObject["phase"]?.stringValue
                ?: turnObject["lifecycleState"]?.stringValue
                ?: turnObject["lifecycle_state"]?.stringValue
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
