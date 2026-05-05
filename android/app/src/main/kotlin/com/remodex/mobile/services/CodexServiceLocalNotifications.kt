package com.remodex.mobile.services

import com.remodex.mobile.R
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import com.remodex.mobile.core.notification.RunCompletionAttentionKind

private fun CodexService.threadDisplayTitleForNotification(threadId: String): String {
    val tid = threadId.trim()
    if (tid.isEmpty()) return appContext.getString(R.string.notification_default_thread_title)
    return _threads.value.firstOrNull { it.id == tid }?.displayTitle?.trim()?.takeIf { it.isNotEmpty() }
        ?: appContext.getString(R.string.notification_default_thread_title)
}

internal fun CodexService.notifyRunCompletionAttention(
    threadId: String,
    turnId: String?,
    kind: RunCompletionAttentionKind,
) {
    localNotificationPresenter.maybeNotifyRunCompletion(
        threadId = threadId,
        turnId = turnId,
        kind = kind,
        displayTitle = threadDisplayTitleForNotification(threadId),
    )
}

internal fun CodexService.notifyPendingApprovalAttention(request: PendingApprovalRequest) {
    val th = request.threadId?.trim()?.takeIf { it.isNotEmpty() } ?: return
    localNotificationPresenter.maybeNotifyPendingApproval(
        request = request,
        displayTitle = threadDisplayTitleForNotification(th),
    )
}

internal fun CodexService.notifyStructuredInputAttention(request: PendingStructuredInputRequest) {
    val th = request.threadId?.trim()?.takeIf { it.isNotEmpty() } ?: return
    localNotificationPresenter.maybeNotifyStructuredInput(
        request = request,
        displayTitle = threadDisplayTitleForNotification(th),
    )
}
