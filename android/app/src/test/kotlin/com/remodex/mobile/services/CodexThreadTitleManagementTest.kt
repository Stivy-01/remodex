package com.remodex.mobile.services

import com.remodex.mobile.core.model.CodexThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodexThreadTitleManagementTest {
    @Test
    fun fallbackThreadTitle_usesFirstFourCleanWordsAndCapitalizes() {
        assertEquals("Fix broken login flow", fallbackThreadTitle(" fix broken login flow today "))
        assertEquals("Image request", fallbackThreadTitle("Image request"))
    }

    @Test
    fun automaticThreadTitleSeedCandidate_usesAttachmentOnlyFallback() {
        val seed =
            automaticThreadTitleSeedCandidate(
                userInput = "   ",
                hasAttachments = true,
                thread = CodexThread(id = "thread-1", title = "Conversation"),
                hasExistingUserChatMessage = false,
                persistedName = null,
            )

        assertEquals("Image request", seed)
    }

    @Test
    fun automaticThreadTitleSeedCandidate_skipsThreadWithExistingUserMessage() {
        val seed =
            automaticThreadTitleSeedCandidate(
                userInput = "Fix login",
                hasAttachments = false,
                thread = CodexThread(id = "thread-1", title = "Conversation"),
                hasExistingUserChatMessage = true,
                persistedName = null,
            )

        assertNull(seed)
    }

    @Test
    fun automaticTitle_replacesGenericConversation() {
        val result =
            applyAutomaticThreadTitleSnapshot(
                list = listOf(CodexThread(id = "thread-1", title = "Conversation")),
                threadId = "thread-1",
                title = "Fix broken login flow",
                allowedCurrentTitles = setOf("Conversation", "Fix broken login flow"),
                persistedName = null,
            )

        assertTrue(result.applied)
        assertEquals("Fix broken login flow", result.threads.single().displayTitle)
        assertEquals("Fix broken login flow", result.persistedName)
    }

    @Test
    fun generatedTitle_replacesPreviousFallbackOnlyWhenStillAllowed() {
        val result =
            applyAutomaticThreadTitleSnapshot(
                list = listOf(CodexThread(id = "thread-1", title = "Fix broken login flow", name = "Fix broken login flow")),
                threadId = "thread-1",
                title = "Login flow fix",
                allowedCurrentTitles = setOf("Conversation", "Fix broken login flow"),
                persistedName = "Fix broken login flow",
            )

        assertTrue(result.applied)
        assertEquals("Login flow fix", result.threads.single().displayTitle)
    }

    @Test
    fun generatedTitle_doesNotOverwriteManualRename() {
        val result =
            applyAutomaticThreadTitleSnapshot(
                list = listOf(CodexThread(id = "thread-1", title = "Manual Rename", name = "Manual Rename")),
                threadId = "thread-1",
                title = "Generated title",
                allowedCurrentTitles = setOf("Conversation", "Fix broken login flow"),
                persistedName = "Manual Rename",
            )

        assertFalse(result.applied)
        assertEquals("Manual Rename", result.threads.single().displayTitle)
        assertNull(result.persistedName)
    }

    @Test
    fun mergeThreadListWithPersistedRenames_appliesLocalRenameOverServerFallback() {
        val merged =
            mergeThreadListWithPersistedRenames(
                fetched = listOf(CodexThread(id = "thread-1", title = "Conversation")),
                previous = listOf(CodexThread(id = "thread-1", title = "Phone Rename", name = "Phone Rename")),
                persistedRename = { tid -> if (tid == "thread-1") "Phone Rename" else null },
            )

        assertEquals("Phone Rename", merged.single().displayTitle)
        assertEquals("Phone Rename", merged.single().name)
    }

    @Test
    fun mergeThreadListWithPersistedRenames_preservesServerTitleWithoutLocalRename() {
        val merged =
            mergeThreadListWithPersistedRenames(
                fetched = listOf(CodexThread(id = "thread-1", title = "Server Rename", name = "Server Rename")),
                previous = emptyList(),
                persistedRename = { null },
            )

        assertEquals("Server Rename", merged.single().displayTitle)
    }
}
