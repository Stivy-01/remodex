package com.remodex.mobile.services

import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexThread

/**
 * Parity with [CodexService.createContinuationThread] in
 * [CodexService+ThreadsTurns.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+ThreadsTurns.swift).
 */
internal suspend fun CodexService.createContinuationThreadInternal(
    archivedThreadId: String,
    priorThread: CodexThread?,
): CodexThread {
    val model = priorThread?.model?.trim()?.takeIf { it.isNotEmpty() }
    val cwd = priorThread?.gitWorkingDirectory
    val thread = startThreadInternal(model = model, cwd = cwd, serviceTier = null)
    val line =
        appContext.getString(R.string.thread_continuation_from_archived, archivedThreadId)
    messageTimelineStore.appendSystemLine(
        threadId = thread.id,
        turnId = null,
        text = line,
    )
    sessionPersistence.saveLastActiveThreadId(thread.id)
    return thread
}
