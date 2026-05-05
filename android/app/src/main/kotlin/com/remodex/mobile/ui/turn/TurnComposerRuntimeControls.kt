package com.remodex.mobile.ui.turn

/** Sentinel `id` for implicit defaults (auto reasoning, baseline “Normal” speed). Must match bridge/thread semantics. */
internal const val TURN_COMPOSER_RUNTIME_AUTO_ID = "__auto__"

internal data class TurnComposerRuntimeOption(
    val id: String,
    val label: String,
    val detail: String? = null,
    val enabled: Boolean = true,
)

internal data class TurnComposerRuntimeSelectorState(
    val selected: TurnComposerRuntimeOption,
    val options: List<TurnComposerRuntimeOption> = listOf(selected),
    val enabled: Boolean = true,
)

internal data class TurnComposerRuntimeControlsState(
    val model: TurnComposerRuntimeSelectorState = defaultModel,
    val reasoningEffort: TurnComposerRuntimeSelectorState = defaultReasoningEffort,
    val accessMode: TurnComposerRuntimeSelectorState = defaultAccessMode,
    val serviceTier: TurnComposerRuntimeSelectorState = defaultServiceTier,
) {
    companion object {
        /** Placeholder when no bridge state is wired (previews); conversation UI supplies real options. */
        private const val UNSET_ID = "__unset__"
        private val unsetOption =
            TurnComposerRuntimeOption(id = UNSET_ID, label = "—", enabled = false)

        val defaultModel =
            TurnComposerRuntimeSelectorState(selected = unsetOption)
        val defaultReasoningEffort =
            TurnComposerRuntimeSelectorState(selected = unsetOption)
        val defaultAccessMode =
            TurnComposerRuntimeSelectorState(selected = unsetOption)
        val defaultServiceTier =
            TurnComposerRuntimeSelectorState(selected = unsetOption)
    }
}
