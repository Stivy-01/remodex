package com.remodex.mobile.core.model

/** User resolution for server `requestApproval` RPCs (parity iOS [approvePendingRequest]). */
enum class PendingApprovalDecision {
    Decline,
    Accept,
    AcceptForSession,
}

data class PendingApprovalRequest(
    val id: String,
    val method: String,
    val threadId: String?,
    val turnId: String?,
    /** Item scope when the bridge sends `itemId` / envelope (parity timeline rows). */
    val itemId: String?,
    val command: String?,
    val reason: String?,
)

data class PendingStructuredInputRequest(
    val id: String,
    val threadId: String?,
    val turnId: String?,
    val questions: List<PendingStructuredInputQuestion>,
)

data class PendingStructuredInputQuestion(
    val id: String,
    val header: String,
    val question: String,
    val options: List<PendingStructuredInputOption> = emptyList(),
)

data class PendingStructuredInputOption(
    val label: String,
    val description: String?,
)
