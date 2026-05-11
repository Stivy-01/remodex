package com.remodex.mobile.ui.turn

/**
 * J.9 baseline: primary composer toolbar affordances derived from [TurnComposerModel] / [TurnComposerDerived].
 * Keeps voice phase visuals aligned with [TurnComposerBar] (mic vs stop vs transcribing).
 */
internal data class TurnComposerToolbarActions(
    val sendButtonEnabled: Boolean,
    val sendShowsProgress: Boolean,
    val stopButtonVisible: Boolean,
    val stopButtonEnabled: Boolean,
    val textFieldEnabled: Boolean,
    val voiceControlEnabled: Boolean,
    val voiceIcon: TurnComposerVoiceToolbarIcon,
)

internal enum class TurnComposerVoiceToolbarIcon {
    None,
    Mic,
    Stop,
    Progress,
}

internal object TurnComposerToolbarActionsBuilder {
    /**
     * @param voiceUiEnabled Bridge flag from shell (same role as `voiceEnabled` on [TurnComposerBar]); when false, voice chrome is suppressed.
     */
    fun build(
        model: TurnComposerModel,
        voiceUiEnabled: Boolean = true,
    ): TurnComposerToolbarActions {
        val d = model.derive()
        val icon =
            when (model.voicePhase) {
                TurnVoicePhase.Transcribing -> TurnComposerVoiceToolbarIcon.Progress
                TurnVoicePhase.Recording -> TurnComposerVoiceToolbarIcon.Stop
                TurnVoicePhase.Idle ->
                    if (voiceUiEnabled && d.voiceEnabled) {
                        TurnComposerVoiceToolbarIcon.Mic
                    } else {
                        TurnComposerVoiceToolbarIcon.None
                    }
            }
        val voiceControlEnabled =
            when (model.voicePhase) {
                TurnVoicePhase.Transcribing -> false
                TurnVoicePhase.Recording -> true
                TurnVoicePhase.Idle -> voiceUiEnabled && d.voiceEnabled
            }
        return TurnComposerToolbarActions(
            sendButtonEnabled = d.canSend,
            sendShowsProgress = d.showsSending,
            stopButtonVisible = model.sending || model.threadRunning,
            stopButtonEnabled = model.enabled && (model.sending || model.threadRunning),
            textFieldEnabled = d.canEditText,
            voiceControlEnabled = voiceControlEnabled,
            voiceIcon = icon,
        )
    }
}
