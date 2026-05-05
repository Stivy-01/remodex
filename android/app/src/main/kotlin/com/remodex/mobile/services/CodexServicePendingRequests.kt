package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun CodexService.enqueuePendingApprovalRequest(
    request: PendingApprovalRequest,
    responder: (PendingApprovalDecision) -> Unit,
) {
    pendingApprovalResponders[request.id] = responder
    _pendingApprovalRequest.value = request
    notifyPendingApprovalAttention(request)
}

internal fun CodexService.enqueuePendingStructuredInputRequest(
    request: PendingStructuredInputRequest,
    responder: (answersByQuestionId: Map<String, List<String>>) -> Unit,
) {
    pendingStructuredInputResponders[request.id] = responder
    _pendingStructuredInputRequest.value = request
    notifyStructuredInputAttention(request)
}

suspend fun CodexService.resolvePendingApprovalForRepository(
    requestId: String,
    decision: PendingApprovalDecision,
) = withContext(Dispatchers.IO) {
    val id = requestId.trim()
    val responder = pendingApprovalResponders.remove(id) ?: throw CodexServiceError.NoPendingApproval
    if (_pendingApprovalRequest.value?.id == id) {
        _pendingApprovalRequest.value = null
    }
    messageTimelineStore.removeEphemeralPendingServerMarker(id)
    responder(decision)
}

suspend fun CodexService.resolvePendingStructuredInputForRepository(
    requestId: String,
    answersByQuestionId: Map<String, List<String>>,
) = withContext(Dispatchers.IO) {
    val id = requestId.trim()
    val responder =
        pendingStructuredInputResponders.remove(id) ?: throw CodexServiceError.NoPendingApproval
    if (_pendingStructuredInputRequest.value?.id == id) {
        _pendingStructuredInputRequest.value = null
    }
    messageTimelineStore.removeEphemeralPendingServerMarker(id)
    responder(answersByQuestionId)
}

internal fun CodexService.clearPendingServerRequests() {
    pendingApprovalResponders.clear()
    pendingStructuredInputResponders.clear()
    _pendingApprovalRequest.value = null
    _pendingStructuredInputRequest.value = null
}
