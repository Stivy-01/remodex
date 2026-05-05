package com.remodex.mobile.core.model

data class CommandExecutionDetails(
    var fullCommand: String,
    var cwd: String? = null,
    var exitCode: Int? = null,
    var durationMs: Int? = null,
    var outputTail: String = "",
) {
    fun appendOutput(chunk: String) {
        outputTail += chunk
        trimOutputTail()
    }

    fun trimOutputTail() {
        val lines = outputTail.split("\n")
        if (lines.size > MAX_OUTPUT_LINES) {
            outputTail = lines.takeLast(MAX_OUTPUT_LINES).joinToString("\n")
        }
    }

    companion object {
        const val MAX_OUTPUT_LINES = 30
    }
}
