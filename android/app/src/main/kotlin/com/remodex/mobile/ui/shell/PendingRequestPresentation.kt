package com.remodex.mobile.ui.shell

import androidx.annotation.StringRes
import com.remodex.mobile.R

/**
 * User-facing copy for server-initiated approval requests (J.7b parity with iOS [approvalAlertMessage]).
 */
internal object PendingRequestPresentation {
    private fun normalizeMethod(method: String): String =
        method.trim().lowercase().replace("_", "").replace("-", "")

    @StringRes
    fun approvalKindTitleRes(method: String): Int {
        val n = normalizeMethod(method)
        return when {
            n.contains("apply") && n.contains("patch") -> R.string.approval_kind_apply_patch
            n.contains("filechange") && n.contains("requestapproval") -> R.string.approval_kind_file_change
            (n.contains("commandexecution") || n.contains("command")) && n.contains("requestapproval") ->
                R.string.approval_kind_command
            n.endsWith("requestapproval") -> R.string.approval_kind_tool
            else -> R.string.approval_kind_generic
        }
    }

    /**
     * `acceptForSession` applies only to shell command approvals (parity iOS `isCommandApproval`).
     */
    fun supportsAcceptForSession(method: String): Boolean =
        normalizeMethod(method) == "item/commandexecution/requestapproval"

    /**
     * [reason] and [formattedCommandLine] (e.g. from [R.string.approval_command_line]), separated
     * by a blank line (iOS [approvalAlertMessage]).
     * `null` when there is no content — use [R.string.approval_default_body] in that case.
     */
    fun approvalMessageOrNull(
        reason: String?,
        formattedCommandLine: String?,
    ): String? {
        val lines = mutableListOf<String>()
        reason?.trim()?.takeIf { it.isNotEmpty() }?.let { lines.add(it) }
        formattedCommandLine?.trim()?.takeIf { it.isNotEmpty() }?.let { lines.add(it) }
        if (lines.isEmpty()) return null
        return lines.joinToString("\n\n")
    }
}
