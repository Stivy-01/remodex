package com.remodex.mobile.core.model

/**
 * UX state for a command / tool execution row in the turn timeline (no raw shell in the list).
 */
enum class ExecutionStatus {
    Running,
    Completed,
    Failed,
    Cancelled,
}

/**
 * @param title User-facing status line (resolved when building this model).
 * @param subtitle Short humanized action summary; null to hide second line.
 * @param commandPreview Sanitized one-line preview for the detail sheet (not necessarily full raw).
 */
data class ToolExecutionUi(
    val status: ExecutionStatus,
    val title: String,
    val subtitle: String?,
    val commandPreview: String?,
    val durationMs: Long?,
    val isExpandable: Boolean = true,
)
