package com.remodex.mobile.ui.turn

/**
 * J.8 Pure composer snapshot + derivation (no Compose). Values parallel [TurnComposerBar] intents.
 *
 * Inputs are flattened for tests and future reducer wiring; [threadRunning]/[transcribing] complement
 * [voicePhase] so callers can model pipeline flags without collapsing everything into enum states.
 */
internal data class TurnComposerModel(
    val enabled: Boolean = true,
    val sending: Boolean = false,
    val draftText: String = "",
    val readyAttachmentCount: Int = 0,
    val hasBlockingAttachments: Boolean = false,
    val voicePhase: TurnVoicePhase = TurnVoicePhase.Idle,
    val threadRunning: Boolean = false,
    val transcribing: Boolean = false,
) {
    val derived: TurnComposerDerived
        get() = TurnComposerDerived.from(this)

    fun derive(): TurnComposerDerived = derived

    fun deriveInteractionLocks(): TurnComposerInteractionLocks {
        val d = derive()
        return TurnComposerInteractionLocks(
            sendLocked = !d.canSend,
            voiceStartLocked = !d.voiceEnabled,
            textInputLocked = !d.canEditText,
            runtimeControlsLocked = !enabled || d.showsSending,
        )
    }
}

internal data class TurnComposerDerived(
    val canEditText: Boolean,
    val canSend: Boolean,
    val showsSending: Boolean,
    val voiceEnabled: Boolean,
) {
    companion object {
        fun from(model: TurnComposerModel): TurnComposerDerived =
            with(model) {
                val hasRenderablePayload =
                    draftText.trim().isNotEmpty() || readyAttachmentCount > 0

                val canSend =
                    enabled &&
                        !sending &&
                        hasRenderablePayload &&
                        !hasBlockingAttachments

                val canEditText =
                    enabled &&
                        !sending &&
                        !hasBlockingAttachments &&
                        voicePhase == TurnVoicePhase.Idle &&
                        !transcribing

                val showsSending = sending

                val voiceEnabled =
                    enabled &&
                        !sending &&
                        !hasBlockingAttachments &&
                        voicePhase == TurnVoicePhase.Idle &&
                        !threadRunning &&
                        !transcribing

                TurnComposerDerived(
                    canEditText = canEditText,
                    canSend = canSend,
                    showsSending = showsSending,
                    voiceEnabled = voiceEnabled,
                )
            }
    }
}

/** J.8 baseline: inverted “lock” view of send / text / voice-start / runtime chips (parity with bar gating). */
internal data class TurnComposerInteractionLocks(
    val sendLocked: Boolean,
    val voiceStartLocked: Boolean,
    val textInputLocked: Boolean,
    val runtimeControlsLocked: Boolean,
)

internal object TurnComposerReviewModeRules {
    fun hasComposerContentConflictingWithReview(
        draftText: String,
        mentionChipCount: Int = 0,
        readyAttachmentCount: Int = 0,
        hasBlockingAttachments: Boolean = false,
        isPlanModeEnabled: Boolean = false,
    ): Boolean {
        if (mentionChipCount > 0 || readyAttachmentCount > 0 || hasBlockingAttachments || isPlanModeEnabled) {
            return true
        }
        val trimmed = draftText.trim()
        if (trimmed.isEmpty()) return false
        return !isOnlyTrailingSlashCommandToken(trimmed)
    }

    private fun isOnlyTrailingSlashCommandToken(value: String): Boolean =
        value == "/" || value.matches(Regex("""/[A-Za-z][A-Za-z-]*"""))
}

/**
 * Narrow event surface for future UI/store wiring (J.8 foundation only).
 */
internal sealed interface TurnComposerEvent {
    data class SetEnabled(val value: Boolean) : TurnComposerEvent
    data class SetSending(val value: Boolean) : TurnComposerEvent
    data class SetDraftText(val value: String) : TurnComposerEvent
    data class SetReadyAttachmentCount(val value: Int) : TurnComposerEvent
    data class SetHasBlockingAttachments(val value: Boolean) : TurnComposerEvent
    data class SetVoicePhase(val value: TurnVoicePhase) : TurnComposerEvent
    data class SetThreadRunning(val value: Boolean) : TurnComposerEvent
    data class SetTranscribing(val value: Boolean) : TurnComposerEvent
}

internal object TurnComposerReducer {
    fun reduce(state: TurnComposerModel, event: TurnComposerEvent): TurnComposerModel =
        when (event) {
            is TurnComposerEvent.SetEnabled -> state.copy(enabled = event.value)
            is TurnComposerEvent.SetSending -> state.copy(sending = event.value)
            is TurnComposerEvent.SetDraftText -> state.copy(draftText = event.value)
            is TurnComposerEvent.SetReadyAttachmentCount ->
                state.copy(readyAttachmentCount = coerceNonNegative(event.value))
            is TurnComposerEvent.SetHasBlockingAttachments ->
                state.copy(hasBlockingAttachments = event.value)
            is TurnComposerEvent.SetVoicePhase -> state.copy(voicePhase = event.value)
            is TurnComposerEvent.SetThreadRunning -> state.copy(threadRunning = event.value)
            is TurnComposerEvent.SetTranscribing -> state.copy(transcribing = event.value)
        }

    private fun coerceNonNegative(raw: Int): Int = maxOf(0, raw)
}
