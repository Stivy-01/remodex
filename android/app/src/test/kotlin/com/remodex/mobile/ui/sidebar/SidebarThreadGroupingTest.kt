package com.remodex.mobile.ui.sidebar

import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexThreadSyncState
import org.junit.Assert.assertEquals
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
    fun makeGroups_noProjectThreadsInCloudGroup() {
        val t1 = thread("t1", cwd = null)
        val t2 = thread("t2", cwd = null)
        val all = listOf(t1, t2)

        val groups = SidebarThreadGrouping.makeGroups(all)
        val cloudGroup =
            groups.firstOrNull {
                it.kind == SidebarThreadGroupKind.Project && it.projectPath == null
            }
        assertTrue(cloudGroup != null)
        assertEquals("Cloud", cloudGroup?.label)
        assertEquals(2, cloudGroup?.threads?.size)
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
}
