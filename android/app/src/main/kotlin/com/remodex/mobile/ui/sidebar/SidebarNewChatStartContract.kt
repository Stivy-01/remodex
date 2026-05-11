package com.remodex.mobile.ui.sidebar

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.data.CodexRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val SIDEBAR_NEW_CHAT_OPEN_TIMEOUT_MS = 3_000L

internal suspend fun startSidebarNewChat(
    repository: CodexRepository,
    cwd: String?,
    openTimeoutMillis: Long = SIDEBAR_NEW_CHAT_OPEN_TIMEOUT_MS,
): CodexThread {
    val normalizedCwd = cwd?.trim()?.takeIf { it.isNotEmpty() }
    val started = repository.startThread(cwd = normalizedCwd)
    repository.setActiveThreadId(started.id)
    val opened =
        withTimeoutOrNull(openTimeoutMillis) {
            combine(repository.activeThreadId, repository.threads) { activeId, threads ->
                activeId == started.id && threads.any { it.id == started.id }
            }.first { it }
        }
    if (opened != true) {
        throw CodexServiceError.InvalidResponse("New thread did not become active.")
    }
    return repository.threads.value.firstOrNull { it.id == started.id } ?: started
}
