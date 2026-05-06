package com.remodex.mobile.data

/**
 * Plain-text body for item-scoped pending approval timeline rows (no Android resources).
 * Mirrors [com.remodex.mobile.ui.shell.PendingRequestPresentation.approvalMessageOrNull] shape.
 */
internal object PendingApprovalTimelineFormatter {
    fun bodyText(
        method: String,
        command: String?,
        reason: String?,
    ): String {
        val lines = mutableListOf<String>()
        reason?.trim()?.takeIf { it.isNotEmpty() }?.let { lines.add(it) }
        command?.trim()?.takeIf { it.isNotEmpty() }?.let { lines.add("Command: $it") }
        if (lines.isNotEmpty()) return lines.joinToString("\n\n")
        return fallbackHeadline(method)
    }

    private fun fallbackHeadline(method: String): String {
        val n = method.trim().lowercase().replace("_", "").replace("-", "")
        return when {
            n.contains("apply") && n.contains("patch") -> "Pending approval (apply patch)"
            n.contains("filechange") && n.contains("requestapproval") -> "Pending approval (file change)"
            (n.contains("commandexecution") || n.contains("command")) && n.contains("requestapproval") ->
                "Pending approval (command)"
            n.endsWith("requestapproval") -> "Pending approval (tool)"
            else -> "Pending approval"
        }
    }
}
