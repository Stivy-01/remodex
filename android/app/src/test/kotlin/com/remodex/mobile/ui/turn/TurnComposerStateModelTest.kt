package com.remodex.mobile.ui.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnComposerStateModelTest {
    @Test
    fun derive_matches_bar_canSend_plaintext() {
        val m =
            TurnComposerModel(
                enabled = true,
                sending = false,
                draftText = " hi ",
                readyAttachmentCount = 0,
                hasBlockingAttachments = false,
            )
        assertTrue(m.derive().canSend)
        assertFalse(m.derive().showsSending)
        assertTrue(m.derive().canEditText)
        assertTrue(m.derive().voiceEnabled)
    }

    @Test
    fun derive_canSend_from_attachments_only() {
        val m =
            TurnComposerModel(
                draftText = "",
                readyAttachmentCount = 1,
            )
        assertTrue(m.derive().canSend)
    }

    @Test
    fun derive_canSend_blocked_while_blocking_attach() {
        val m =
            TurnComposerModel(
                draftText = "x",
                hasBlockingAttachments = true,
            )
        assertFalse(m.derive().canSend)
        assertFalse(m.derive().voiceEnabled)
    }

    @Test
    fun derive_voiceDisabled_when_recording_even_if_idle_pipeline_flags() {
        val m =
            TurnComposerModel(
                draftText = "x",
                voicePhase = TurnVoicePhase.Recording,
                threadRunning = false,
                transcribing = false,
            )
        assertFalse(m.derive().voiceEnabled)
        assertFalse(m.derive().canEditText)
    }

    @Test
    fun derive_voiceDisabled_when_threadRunning() {
        val m =
            TurnComposerModel(
                draftText = "x",
                threadRunning = true,
            )
        assertFalse(m.derive().voiceEnabled)
    }

    @Test
    fun derive_voiceDisabled_when_transcribing_pipeline_flag() {
        val m =
            TurnComposerModel(
                transcribing = true,
            )
        assertFalse(m.derive().voiceEnabled)
        assertFalse(m.derive().canEditText)
    }

    @Test
    fun showsSending_tracksSending() {
        val m =
            TurnComposerModel(
                sending = true,
                draftText = "x",
            )
        assertTrue(m.derive().showsSending)
        assertFalse(m.derive().canSend)
    }

    @Test
    fun reducer_negative_attachment_count_clamped() {
        var s =
            TurnComposerReducer.reduce(
                TurnComposerModel(),
                TurnComposerEvent.SetReadyAttachmentCount(-3),
            )
        assertEquals(0, s.readyAttachmentCount)
        s =
            TurnComposerReducer.reduce(s, TurnComposerEvent.SetDraftText("a"))
        s = TurnComposerReducer.reduce(s, TurnComposerEvent.SetSending(true))
        assertTrue(s.sending && s.enabled)
    }

    @Test
    fun reducer_fold_matches_snapshot_derive() {
        val folded =
            listOf<TurnComposerEvent>(
                    TurnComposerEvent.SetEnabled(false),
                    TurnComposerEvent.SetEnabled(true),
                    TurnComposerEvent.SetDraftText("go"),
                    TurnComposerEvent.SetReadyAttachmentCount(2),
                    TurnComposerEvent.SetHasBlockingAttachments(false),
                )
                .fold(TurnComposerModel()) { state, evt -> TurnComposerReducer.reduce(state, evt) }

        assertEquals("go", folded.draftText)
        assertEquals(2, folded.readyAttachmentCount)
        assertEquals(folded.derive(), TurnComposerDerived.from(folded))
        assertTrue(folded.derive().canSend)
    }

    @Test
    fun deriveInteractionLocks_default_all_unlockExceptSendWhenEmpty() {
        val locks = TurnComposerModel().deriveInteractionLocks()
        assertTrue(locks.sendLocked)
        assertFalse(locks.voiceStartLocked)
        assertFalse(locks.textInputLocked)
        assertFalse(locks.runtimeControlsLocked)
    }

    @Test
    fun deriveInteractionLocks_runtimeLocked_whenSending() {
        val locks =
            TurnComposerModel(
                enabled = true,
                sending = true,
                draftText = "x",
            ).deriveInteractionLocks()
        assertTrue(locks.runtimeControlsLocked)
        assertTrue(locks.sendLocked)
    }

    @Test
    fun deriveInteractionLocks_runtimeLocked_whenDisabled() {
        val locks =
            TurnComposerModel(
                enabled = false,
                draftText = "x",
            ).deriveInteractionLocks()
        assertTrue(locks.runtimeControlsLocked)
    }

    @Test
    fun reviewModeRules_trailingSlashCommandDoesNotConflict() {
        assertFalse(TurnComposerReviewModeRules.hasComposerContentConflictingWithReview("/review"))
        assertFalse(TurnComposerReviewModeRules.hasComposerContentConflictingWithReview("/"))
        assertFalse(TurnComposerReviewModeRules.hasComposerContentConflictingWithReview(" /review-base "))
    }

    @Test
    fun reviewModeRules_existingContentConflicts() {
        assertTrue(TurnComposerReviewModeRules.hasComposerContentConflictingWithReview("Please review this too"))
        assertTrue(TurnComposerReviewModeRules.hasComposerContentConflictingWithReview("Follow up /review"))
        assertTrue(TurnComposerReviewModeRules.hasComposerContentConflictingWithReview("", mentionChipCount = 1))
        assertTrue(TurnComposerReviewModeRules.hasComposerContentConflictingWithReview("", readyAttachmentCount = 1))
        assertTrue(TurnComposerReviewModeRules.hasComposerContentConflictingWithReview("", hasBlockingAttachments = true))
        assertTrue(TurnComposerReviewModeRules.hasComposerContentConflictingWithReview("", isPlanModeEnabled = true))
    }
}
