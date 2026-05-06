package com.remodex.mobile.ui.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnComposerToolbarActionsBuilderTest {

    @Test
    fun build_idle_withPayload_showsMicWhenVoiceUiOn() {
        val actions =
            TurnComposerToolbarActionsBuilder.build(
                TurnComposerModel(draftText = "hi"),
                voiceUiEnabled = true,
            )
        assertTrue(actions.sendButtonEnabled)
        assertTrue(actions.textFieldEnabled)
        assertTrue(actions.voiceControlEnabled)
        assertEquals(TurnComposerVoiceToolbarIcon.Mic, actions.voiceIcon)
    }

    @Test
    fun build_idle_hidesMicWhenVoiceUiOff() {
        val actions =
            TurnComposerToolbarActionsBuilder.build(
                TurnComposerModel(draftText = "hi"),
                voiceUiEnabled = false,
            )
        assertFalse(actions.voiceControlEnabled)
        assertEquals(TurnComposerVoiceToolbarIcon.None, actions.voiceIcon)
    }

    @Test
    fun build_recording_showsStopAndEnablesControl() {
        val actions =
            TurnComposerToolbarActionsBuilder.build(
                TurnComposerModel(
                    draftText = "x",
                    voicePhase = TurnVoicePhase.Recording,
                ),
                voiceUiEnabled = false,
            )
        assertTrue(actions.voiceControlEnabled)
        assertEquals(TurnComposerVoiceToolbarIcon.Stop, actions.voiceIcon)
        assertFalse(actions.textFieldEnabled)
    }

    @Test
    fun build_transcribing_showsProgressAndDisablesControl() {
        val actions =
            TurnComposerToolbarActionsBuilder.build(
                TurnComposerModel(
                    voicePhase = TurnVoicePhase.Transcribing,
                ),
            )
        assertFalse(actions.voiceControlEnabled)
        assertEquals(TurnComposerVoiceToolbarIcon.Progress, actions.voiceIcon)
        assertFalse(actions.textFieldEnabled)
    }

    @Test
    fun build_sendShowsProgressTracksSending() {
        val actions =
            TurnComposerToolbarActionsBuilder.build(
                TurnComposerModel(
                    sending = true,
                    draftText = "x",
                ),
            )
        assertTrue(actions.sendShowsProgress)
        assertFalse(actions.sendButtonEnabled)
    }
}
