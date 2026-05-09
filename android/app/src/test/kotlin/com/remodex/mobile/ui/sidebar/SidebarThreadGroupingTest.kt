package com.remodex.mobile.ui.sidebar

import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexThreadSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SidebarThreadGroupingTest {
    private fun thread(
        id: String,
        cwd: String? = null,
        parentThreadId: String? = null,
        syncState: CodexThreadSyncState = CodexThreadSyncState.live,
        updatedAt: Instant = Instant.EPOCH.plusSeconds(1000),
    ): CodexThread =
        CodexThread(
            id = id,
            title = "Thread $id",
            cwd = cwd,
            parentThreadId = parentThreadId,
            syncState = syncState,
            updatedAt = updatedAt,
        )

    @Test
    fun liveThreadIdsForGroup_returnsAllLiveThreadsInProjectGroup() {
        val t1 = thread("t1", cwd = "/Users/test/project-a")
        val t2 = thread("t2", cwd = "/Users/test/project-a")
        val t3 = thread("t3", cwd = "/Users/test/project-b")
        val allThreads = listOf(t1, t2, t3)

        val groups = SidebarThreadGrouping.makeGroups(allThreads)
        val groupA = groups.first { it.projectPath == "/Users/test/project-a" }

        val ids = SidebarThreadGrouping.liveThreadIdsForGroup(groupA, allThreads)
        assertEquals(2, ids.size)
        assertTrue(ids.contains("t1"))
        assertTrue(ids.contains("t2"))
    }

    @Test
    fun liveThreadIdsForGroup_excludesArchivedThreads() {
        val t1 = thread("t1", cwd = "/Users/test/project-a")
        val t2 =
            thread(
                "t2",
                cwd = "/Users/test/project-a",
                syncState = CodexThreadSyncState.archivedLocal,
            )
        val allThreads = listOf(t1, t2)

        val groups = SidebarThreadGrouping.makeGroups(allThreads)
        val groupA = groups.first { it.projectPath == "/Users/test/project-a" }

        val ids = SidebarThreadGrouping.liveThreadIdsForGroup(groupA, allThreads)
        assertEquals(1, ids.size)
        assertEquals("t1", ids.first())
    }

    @Test
    fun liveThreadIdsForGroup_returnsEmptyForNonProjectGroups() {
        val t1 =
            thread(
                "t1",
                cwd = "/Users/test/project-a",
                syncState = CodexThreadSyncState.archivedLocal,
            )
        val allThreads = listOf(t1)

        val groups = SidebarThreadGrouping.makeGroups(allThreads)
        val archivedGroup = groups.first { it.kind == SidebarThreadGroupKind.Archived }

        val ids = SidebarThreadGrouping.liveThreadIdsForGroup(archivedGroup, allThreads)
        assertTrue(ids.isEmpty())
    }

    @Test
    fun liveThreadIdsForGroup_includesThreadsNotInVisibleGroupSlice() {
        // The group only shows 1 thread due to filtering, but liveThreadIdsForGroup
        // should return ALL live threads in the project, not just the visible ones.
        val t1 = thread("t1", cwd = "/Users/test/project-a", updatedAt = Instant.EPOCH.plusSeconds(2000))
        val t2 = thread("t2", cwd = "/Users/test/project-a", updatedAt = Instant.EPOCH.plusSeconds(1000))
        val allThreads = listOf(t1, t2)

        // Simulate filtered view - t2 is not visible but still in allThreads
        val filtered = listOf(t1)
        val groups = SidebarThreadGrouping.makeGroups(filtered)
        val groupA = groups.first { it.projectPath == "/Users/test/project-a" }

        // Group visible slice has only t1
        assertEquals(1, groupA.threads.size)
        assertEquals("t1", groupA.threads.first().id)

        // But liveThreadIdsForGroup with full list returns both
        val ids = SidebarThreadGrouping.liveThreadIdsForGroup(groupA, allThreads)
        assertEquals(2, ids.size)
    }

    @Test
    fun makeGroups_groupsThreadsByProjectKey() {
        val t1 = thread("t1", cwd = "/Users/test/project-a")
        val t2 = thread("t2", cwd = "/Users/test/project-a")
        val t3 = thread("t3", cwd = "/Users/test/project-b")
        val all = listOf(t1, t2, t3)

        val groups = SidebarThreadGrouping.makeGroups(all)
        val projectGroups = groups.filter { it.kind == SidebarThreadGroupKind.Project }
        assertEquals(2, projectGroups.size)
    }

    @Test
    fun makeGroups_noProjectThreadsInChatsGroup() {
        val t1 = thread("t1", cwd = null)
        val t2 = thread("t2", cwd = "Cloud")
        val all = listOf(t1, t2)

        val groups = SidebarThreadGrouping.makeGroups(all)
        val chatsGroup =
            groups.firstOrNull {
                it.kind == SidebarThreadGroupKind.Chats && it.projectPath == null
            }
        assertTrue(chatsGroup != null)
        assertEquals("Chats", chatsGroup?.label)
        assertEquals(2, chatsGroup?.threads?.size)
    }

    @Test
    fun makeGroups_bucketsAdHocCodexCwdsIntoChats() {
        val adhoc = thread("t1", cwd = "/Users/test/Documents/Codex/2026-05-06/question")
        val session = thread("t2", cwd = "/Users/test/.codex/sessions/2026/05/06/foo.jsonl")
        val project = thread("t3", cwd = "/Users/test/project-a")

        val groups = SidebarThreadGrouping.makeGroups(listOf(adhoc, session, project))
        val chatsGroup = groups.first { it.kind == SidebarThreadGroupKind.Chats }
        val projectGroups = groups.filter { it.kind == SidebarThreadGroupKind.Project }

        assertEquals(listOf("t1", "t2").sorted(), chatsGroup.threads.map { it.id }.sorted())
        assertEquals(1, projectGroups.size)
        assertEquals("/Users/test/project-a", projectGroups.first().projectPath)
    }

    @Test
    fun makeGroups_keepsWindowsProjectPathsInProjectGroups() {
        val driveBackslash = thread("t1", cwd = "C:\\Users\\test\\project-a")
        val driveSlash = thread("t2", cwd = "C:/Users/test/project-b")
        val unc = thread("t3", cwd = "\\\\server\\share\\project-c")

        val groups = SidebarThreadGrouping.makeGroups(listOf(driveBackslash, driveSlash, unc))
        val projectPaths = groups.filter { it.kind == SidebarThreadGroupKind.Project }.map { it.projectPath }

        assertTrue(projectPaths.contains("C:\\Users\\test\\project-a"))
        assertTrue(projectPaths.contains("C:/Users/test/project-b"))
        assertTrue(projectPaths.contains("\\\\server\\share\\project-c"))
        assertFalse(groups.any { it.kind == SidebarThreadGroupKind.Chats })
    }

    @Test
    fun makeGroups_separatesArchivedThreads() {
        val live = thread("t1", cwd = "/Users/test/project-a")
        val archived =
            thread(
                "t2",
                cwd = "/Users/test/project-a",
                syncState = CodexThreadSyncState.archivedLocal,
            )
        val all = listOf(live, archived)

        val groups = SidebarThreadGrouping.makeGroups(all)
        val projectGroup = groups.first { it.kind == SidebarThreadGroupKind.Project }
        val archivedGroup = groups.first { it.kind == SidebarThreadGroupKind.Archived }

        assertEquals(1, projectGroup.threads.size)
        assertEquals("t1", projectGroup.threads.first().id)
        assertEquals(1, archivedGroup.threads.size)
        assertEquals("t2", archivedGroup.threads.first().id)
    }

    @Test
    fun applyGroupLimit_capsVisibleThreadsAndReportsHiddenCount() {
        val threads =
            (1..7).map { i ->
                thread(
                    id = "t$i",
                    cwd = "/Users/test/project-a",
                    updatedAt = Instant.EPOCH.plusSeconds(i.toLong()),
                )
            }
        val group = SidebarThreadGrouping.makeGroups(threads).first { it.kind == SidebarThreadGroupKind.Project }

        val limited = SidebarThreadGrouping.applyGroupLimit(listOf(group), limit = 5).first()

        assertEquals(listOf("t7", "t6", "t5", "t4", "t3"), limited.visibleThreads.map { it.id })
        assertEquals(2, limited.hiddenCount)
        assertEquals(7, limited.totalCount)
        assertEquals(7, limited.threads.size)
    }

    @Test
    fun applyGroupLimit_revealsAllWhenExpanded() {
        val threads =
            (1..7).map { i ->
                thread(
                    id = "t$i",
                    cwd = "/Users/test/project-a",
                    updatedAt = Instant.EPOCH.plusSeconds(i.toLong()),
                )
            }
        val group = SidebarThreadGrouping.makeGroups(threads).first { it.kind == SidebarThreadGroupKind.Project }

        val limited =
            SidebarThreadGrouping.applyGroupLimit(
                groups = listOf(group),
                limit = 5,
                expandedGroupIds = setOf(group.id),
            ).first()

        assertEquals(7, limited.visibleThreads.size)
        assertEquals(0, limited.hiddenCount)
    }

    @Test
    fun applyGroupLimit_pinsActiveThreadOutsideTopFive() {
        val threads =
            (1..7).map { i ->
                thread(
                    id = "t$i",
                    cwd = "/Users/test/project-a",
                    updatedAt = Instant.EPOCH.plusSeconds(i.toLong()),
                )
            }
        val group = SidebarThreadGrouping.makeGroups(threads).first { it.kind == SidebarThreadGroupKind.Project }

        val limited =
            SidebarThreadGrouping.applyGroupLimit(
                groups = listOf(group),
                limit = 5,
                pinnedThreadIds = setOf("t1"),
            ).first()

        assertTrue(limited.visibleThreads.map { it.id }.contains("t1"))
        assertEquals(6, limited.visibleThreads.size)
        assertEquals(1, limited.hiddenCount)
        assertFalse(limited.visibleThreads.map { it.id }.contains("t2"))
    }
}
