package com.remodex.mobile.ui.sidebar

import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexThreadSyncState
import java.time.Instant

/**
 * Parity with [SidebarThreadGrouping.swift](../../../../../../../../CodexMobile/CodexMobile/Views/Sidebar/SidebarThreadGrouping.swift).
 */
enum class SidebarThreadGroupKind {
    Project,
    Chats,
    Archived,
}

data class SidebarThreadGroup(
    val id: String,
    val label: String,
    val kind: SidebarThreadGroupKind,
    val sortDate: Instant,
    val projectPath: String?,
    val threads: List<CodexThread>,
    val visibleThreads: List<CodexThread> = threads,
    val hiddenCount: Int = 0,
    val totalCount: Int = threads.size,
)

object SidebarThreadGrouping {
    private const val CHATS_GROUP_ID = "__chats__"

    fun makeGroups(threads: List<CodexThread>): List<SidebarThreadGroup> {
        val archivedThreads = threads.filter { it.syncState == CodexThreadSyncState.archivedLocal }
        val projectGroups = makeProjectGroups(threads = threads)
        val chatsGroup = makeChatsGroup(threads = threads)
        val sortedArchived = sortThreadsByRecentActivity(archivedThreads)
        val archivedGroup =
            sortedArchived.firstOrNull()?.let { first ->
                SidebarThreadGroup(
                    id = "archived",
                    label = "Archived (${sortedArchived.size})",
                    kind = SidebarThreadGroupKind.Archived,
                    sortDate = first.updatedAt ?: first.createdAt ?: Instant.EPOCH,
                    projectPath = null,
                    threads = sortedArchived,
                )
            }
        return projectGroups + listOfNotNull(chatsGroup, archivedGroup)
    }

    fun applyGroupLimit(
        groups: List<SidebarThreadGroup>,
        limit: Int,
        expandedGroupIds: Set<String> = emptySet(),
        pinnedThreadIds: Set<String> = emptySet(),
    ): List<SidebarThreadGroup> {
        val safeLimit = limit.coerceAtLeast(0)
        return groups.map { group ->
            val expanded = group.id in expandedGroupIds
            if (expanded || group.threads.size <= safeLimit) {
                group.copy(
                    visibleThreads = group.threads,
                    hiddenCount = 0,
                    totalCount = group.threads.size,
                )
            } else {
                val head = group.threads.take(safeLimit)
                val pinnedFromTail =
                    if (pinnedThreadIds.isEmpty()) {
                        emptyList()
                    } else {
                        group.threads.drop(safeLimit).filter { it.id in pinnedThreadIds }
                    }
                val visible = head + pinnedFromTail
                group.copy(
                    visibleThreads = visible,
                    hiddenCount = group.threads.size - visible.size,
                    totalCount = group.threads.size,
                )
            }
        }
    }

    /**
     * Resolves all live thread IDs for a project group, including threads not in the
     * currently-visible filtered list. Parity: iOS [liveThreadIDsForProjectGroup].
     */
    fun liveThreadIdsForGroup(
        group: SidebarThreadGroup,
        allThreads: List<CodexThread>,
    ): List<String> {
        if (group.kind != SidebarThreadGroupKind.Project) return emptyList()
        return sortThreadsByRecentActivity(
            allThreads.filter { thread ->
                thread.syncState != CodexThreadSyncState.archivedLocal &&
                    projectGroupId(thread) == group.id
            },
        ).map { it.id }
    }

    private fun makeProjectGroup(
        projectKey: String,
        threads: List<CodexThread>,
    ): SidebarThreadGroup {
        val sortedThreads = sortThreadsByRecentActivity(threads)
        val representative = sortedThreads.first()
        val sortDate = representative.updatedAt ?: representative.createdAt ?: Instant.EPOCH
        return SidebarThreadGroup(
            id = "project:$projectKey",
            label = representative.projectDisplayName,
            kind = SidebarThreadGroupKind.Project,
            sortDate = sortDate,
            projectPath = representative.normalizedProjectPath,
            threads = sortedThreads,
        )
    }

    private fun makeProjectGroups(threads: List<CodexThread>): List<SidebarThreadGroup> {
        val liveByProject = LinkedHashMap<String, MutableList<CodexThread>>()
        for (thread in threads) {
            if (thread.syncState == CodexThreadSyncState.archivedLocal) continue
            if (thread.normalizedProjectPath == null) continue
            liveByProject.getOrPut(thread.projectKey) { mutableListOf() }.add(thread)
        }
        return liveByProject
            .map { (key, list) -> makeProjectGroup(key, list) }
            .sortedWith { lhs, rhs ->
                when {
                    lhs.sortDate != rhs.sortDate -> rhs.sortDate.compareTo(lhs.sortDate)
                    lhs.label != rhs.label -> lhs.label.compareTo(rhs.label, ignoreCase = true)
                    else -> lhs.id.compareTo(rhs.id)
                }
            }
    }

    private fun makeChatsGroup(threads: List<CodexThread>): SidebarThreadGroup? {
        val chatThreads =
            sortThreadsByRecentActivity(
                threads.filter { thread ->
                    thread.syncState != CodexThreadSyncState.archivedLocal &&
                        thread.normalizedProjectPath == null
                },
            )
        val first = chatThreads.firstOrNull() ?: return null
        return SidebarThreadGroup(
            id = CHATS_GROUP_ID,
            label = "Chats",
            kind = SidebarThreadGroupKind.Chats,
            sortDate = first.updatedAt ?: first.createdAt ?: Instant.EPOCH,
            projectPath = null,
            threads = chatThreads,
        )
    }

    private fun sortThreadsByRecentActivity(threads: List<CodexThread>): List<CodexThread> =
        threads.sortedWith { lhs, rhs ->
            val l = lhs.updatedAt ?: lhs.createdAt ?: Instant.EPOCH
            val r = rhs.updatedAt ?: rhs.createdAt ?: Instant.EPOCH
            when {
                l != r -> r.compareTo(l)
                else -> lhs.id.compareTo(rhs.id)
            }
        }

    private fun projectGroupId(thread: CodexThread): String = "project:${thread.projectKey}"
}
