package com.remodex.mobile.data

import com.remodex.mobile.core.model.CommandExecutionDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class CommandExecutionDetailsStore {
    private val lock = Any()
    private val _detailsByItemId = MutableStateFlow<Map<String, CommandExecutionDetails>>(emptyMap())
    val detailsByItemId: StateFlow<Map<String, CommandExecutionDetails>> = _detailsByItemId.asStateFlow()

    fun upsertFromState(
        itemId: String?,
        fullCommand: String?,
        cwd: String?,
        exitCode: Int?,
        durationMs: Int?,
    ) {
        val key = itemId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val command = fullCommand?.trim().orEmpty()
        synchronized(lock) {
            val current = _detailsByItemId.value
            val existing = current[key]
            val next =
                if (existing == null) {
                    CommandExecutionDetails(
                        fullCommand = command.ifBlank { "command" },
                        cwd = cwd?.trim()?.takeIf { it.isNotEmpty() },
                        exitCode = exitCode,
                        durationMs = durationMs,
                    )
                } else {
                    existing.copy(
                        fullCommand = selectCommand(existing.fullCommand, command),
                        cwd = cwd?.trim()?.takeIf { it.isNotEmpty() } ?: existing.cwd,
                        exitCode = exitCode ?: existing.exitCode,
                        durationMs = durationMs ?: existing.durationMs,
                        outputTail = existing.outputTail,
                    )
                }
            _detailsByItemId.value = current + (key to next)
        }
    }

    fun appendOutput(
        itemId: String?,
        chunk: String,
    ) {
        val key = itemId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (chunk.isEmpty()) return
        synchronized(lock) {
            val current = _detailsByItemId.value
            val existing = current[key] ?: CommandExecutionDetails(fullCommand = "command")
            val next = existing.copy(outputTail = existing.outputTail + chunk)
            next.trimOutputTail()
            _detailsByItemId.value = current + (key to next)
        }
    }

    fun clear() {
        synchronized(lock) {
            _detailsByItemId.value = emptyMap()
        }
    }

    private fun selectCommand(
        existing: String,
        incoming: String,
    ): String {
        val trimmedExisting = existing.trim()
        val trimmedIncoming = incoming.trim()
        if (trimmedIncoming.isEmpty()) return trimmedExisting.ifBlank { "command" }
        if (trimmedExisting.isBlank() || trimmedExisting == "command") return trimmedIncoming
        return if (trimmedIncoming.length > trimmedExisting.length) trimmedIncoming else trimmedExisting
    }
}
