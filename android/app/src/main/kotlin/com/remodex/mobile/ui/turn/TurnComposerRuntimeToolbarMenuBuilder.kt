package com.remodex.mobile.ui.turn

/**
 * Pure derivation of the compact runtime menu (iOS [TurnComposerRuntimeMenuBuilder] parity):
 * - Optional **Reasoning** section when the model exposes at least one non-auto effort (mirrors `reasoningDisplayOptions` non-empty).
 * - **Speed** section listing “Normal” (`[TURN_COMPOSER_RUNTIME_AUTO_ID]`) plus each configured service tier row.
 */
internal object TurnComposerRuntimeToolbarMenuBuilder {

    fun build(
        labels: TurnComposerToolbarLabels = TurnComposerToolbarLabels(),
        runtime: TurnComposerRuntimeControlsState,
    ): TurnComposerToolbarMenu {
        val sections = mutableListOf<TurnComposerToolbarMenuSection>()
        makeReasoningSection(labels, runtime)?.let { sections.add(it) }
        sections.add(makeSpeedSection(labels, runtime))
        return TurnComposerToolbarMenu(
            rootTitle = labels.chatRuntimeRootTitle,
            rootIconKind = TurnComposerToolbarIconKind.ChatRuntimeRoot,
            sections = sections,
        )
    }

    private fun makeReasoningSection(
        labels: TurnComposerToolbarLabels,
        runtime: TurnComposerRuntimeControlsState,
    ): TurnComposerToolbarMenuSection? {
        val reasoning = runtime.reasoningEffort
        val effortRows =
            reasoning.options.filter { it.id != TURN_COMPOSER_RUNTIME_AUTO_ID }
        if (effortRows.isEmpty()) {
            return null
        }
        val resolvedId = reasoning.selected.id
        val items =
            effortRows.map { option ->
                TurnComposerToolbarMenuItem(
                    actionKey =
                        TurnComposerToolbarActionKey(
                            kind = TurnComposerToolbarActionKey.Kind.ReasoningEffort,
                            id = option.id,
                        ),
                    title = option.label,
                    detail = option.detail,
                    checked = resolvedId == option.id,
                    enabled = reasoning.enabled && option.enabled,
                )
            }
        return TurnComposerToolbarMenuSection(
            title = labels.reasoningSectionTitle,
            iconKind = TurnComposerToolbarIconKind.ReasoningSection,
            items = items,
        )
    }

    private fun makeSpeedSection(
        labels: TurnComposerToolbarLabels,
        runtime: TurnComposerRuntimeControlsState,
    ): TurnComposerToolbarMenuSection {
        val tier = runtime.serviceTier
        val resolvedId = tier.selected.id
        val items =
            tier.options.map { option ->
                TurnComposerToolbarMenuItem(
                    actionKey =
                        TurnComposerToolbarActionKey(
                            kind = TurnComposerToolbarActionKey.Kind.ServiceTierSpeed,
                            id = option.id,
                        ),
                    title = option.label,
                    detail = option.detail,
                    checked = resolvedId == option.id,
                    enabled = tier.enabled && option.enabled,
                )
            }
        return TurnComposerToolbarMenuSection(
            title = labels.speedSectionTitle,
            iconKind = TurnComposerToolbarIconKind.SpeedSection,
            items = items,
        )
    }
}
