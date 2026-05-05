package com.remodex.mobile.data

import com.remodex.mobile.core.model.JSONValue

internal data class CommandExecutionEventState(
    val itemId: String?,
    val phase: String,
    val fullCommand: String,
    val shortCommand: String,
    val cwd: String?,
    val exitCode: Int?,
    val durationMs: Int?,
    val outputChunk: String?,
)

internal object CommandExecutionEventParser {
    fun parse(
        params: Map<String, JSONValue>?,
        eventObject: Map<String, JSONValue>?,
        method: String,
    ): CommandExecutionEventState {
        val p = params.orEmpty()
        val event = eventObject ?: envelopeEventObject(p)
        val itemId = extractItemId(p, event)
        val fullCommand = extractCommand(event, p).ifBlank { "command" }
        val phase = extractPhase(event, p, method)
        return CommandExecutionEventState(
            itemId = itemId,
            phase = phase,
            fullCommand = fullCommand,
            shortCommand = shortCommandPreview(fullCommand),
            cwd = extractCwd(event, p),
            exitCode = extractExitCode(event, p),
            durationMs = extractDurationMs(event, p),
            outputChunk = extractOutputChunk(event, p),
        )
    }

    private fun envelopeEventObject(params: Map<String, JSONValue>): Map<String, JSONValue>? =
        params["msg"]?.objectValue ?: params["event"]?.objectValue

    private fun extractItemId(
        params: Map<String, JSONValue>,
        event: Map<String, JSONValue>?,
    ): String? {
        val item = params["item"]?.objectValue
        val eventItem = event?.get("item")?.objectValue
        return firstNonBlankString(
            params["itemId"],
            params["item_id"],
            params["id"],
            params["call_id"],
            params["callId"],
            item?.get("id"),
            event?.get("itemId"),
            event?.get("item_id"),
            event?.get("id"),
            event?.get("call_id"),
            event?.get("callId"),
            eventItem?.get("id"),
        )
    }

    private fun extractCommand(
        event: Map<String, JSONValue>?,
        params: Map<String, JSONValue>,
    ): String =
        firstCommandValue(event) ?: firstCommandValue(params) ?: "command"

    private fun firstCommandValue(obj: Map<String, JSONValue>?): String? {
        if (obj == null) return null
        val keys = listOf("command", "cmd", "raw_command", "rawCommand", "input", "invocation")
        for (key in keys) {
            val value = obj[key] ?: continue
            commandString(value)?.let { return it }
        }
        return null
    }

    private fun commandString(value: JSONValue): String? {
        value.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        value.arrayValue
            ?.mapNotNull { element -> element.stringValue?.trim()?.takeIf { it.isNotEmpty() } }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.let { return it }
        return null
    }

    private fun extractCwd(
        event: Map<String, JSONValue>?,
        params: Map<String, JSONValue>,
    ): String? =
        firstNonBlankString(
            event?.get("cwd"),
            event?.get("working_directory"),
            params["cwd"],
            params["working_directory"],
        )

    private fun extractExitCode(
        event: Map<String, JSONValue>?,
        params: Map<String, JSONValue>,
    ): Int? =
        firstInt(
            event?.get("exitCode"),
            event?.get("exit_code"),
            event?.get("result")?.objectValue?.get("exitCode"),
            event?.get("result")?.objectValue?.get("exit_code"),
            params["exitCode"],
            params["exit_code"],
            params["result"]?.objectValue?.get("exitCode"),
            params["result"]?.objectValue?.get("exit_code"),
        )

    private fun extractDurationMs(
        event: Map<String, JSONValue>?,
        params: Map<String, JSONValue>,
    ): Int? =
        firstInt(
            event?.get("durationMs"),
            event?.get("duration_ms"),
            event?.get("result")?.objectValue?.get("durationMs"),
            event?.get("result")?.objectValue?.get("duration_ms"),
            params["durationMs"],
            params["duration_ms"],
            params["result"]?.objectValue?.get("durationMs"),
            params["result"]?.objectValue?.get("duration_ms"),
        )

    private fun extractOutputChunk(
        event: Map<String, JSONValue>?,
        params: Map<String, JSONValue>,
    ): String? =
        firstNonEmptyString(
            params["delta"],
            params["textDelta"],
            params["text_delta"],
            params["outputDelta"],
            params["output_delta"],
            params["chunk"],
            params["output"],
            params["text"],
            event?.get("delta"),
            event?.get("textDelta"),
            event?.get("text_delta"),
            event?.get("outputDelta"),
            event?.get("output_delta"),
            event?.get("chunk"),
            event?.get("output"),
            event?.get("text"),
        )

    private fun extractPhase(
        event: Map<String, JSONValue>?,
        params: Map<String, JSONValue>,
        method: String,
    ): String {
        val status =
            firstNonBlankString(
                event?.get("status"),
                event?.get("phase"),
                event?.get("state"),
                event?.get("result")?.objectValue?.get("status"),
                params["status"],
                params["phase"],
                params["state"],
                params["result"]?.objectValue?.get("status"),
            )?.lowercase()
        if (status != null) {
            when {
                "fail" in status || "error" in status -> return "failed"
                "cancel" in status || "abort" in status || "interrupt" in status -> return "stopped"
                "complete" in status || "success" in status || "done" in status -> return "completed"
            }
        }
        val normalized = method.lowercase()
        return when {
            normalized.endsWith("exec_command_end") || normalized.contains("commandexecution/completed") ||
                normalized.contains("command_execution/completed") -> "completed"
            else -> "running"
        }
    }

    private fun firstNonBlankString(vararg values: JSONValue?): String? {
        for (value in values) {
            value?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun firstNonEmptyString(vararg values: JSONValue?): String? {
        for (value in values) {
            value?.stringValue?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun firstInt(vararg values: JSONValue?): Int? {
        for (value in values) {
            when (value) {
                is JSONValue.NumLong -> return value.value.toInt()
                is JSONValue.NumDouble -> return value.value.toInt()
                is JSONValue.Str -> value.value.trim().toIntOrNull()?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    private fun shortCommandPreview(command: String): String {
        val trimmed = command.trim().ifBlank { return "command" }
        return if (trimmed.length <= 80) trimmed else trimmed.take(77).trimEnd() + "..."
    }
}
