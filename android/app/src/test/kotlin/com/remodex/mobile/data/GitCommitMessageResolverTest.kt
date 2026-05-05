package com.remodex.mobile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class GitCommitMessageResolverTest {
    @Test
    fun remodexResolveCommitMessage_returnsRawWhenProvided() =
        runTest {
            assertEquals(
                "Hello",
                remodexResolveCommitMessage("  Hello  ") { error("should not be called") },
            )
        }

    @Test
    fun remodexResolveCommitMessage_usesGeneratedDraftWhenRawBlank() =
        runTest {
            assertEquals(
                "Update git flow\n\n- Draft",
                remodexResolveCommitMessage("   ") { "Update git flow\n\n- Draft" },
            )
        }

    @Test
    fun remodexResolveCommitMessage_returnsNullWhenGeneratedDraftBlank() =
        runTest {
            assertNull(remodexResolveCommitMessage("") { "   " })
        }
}

