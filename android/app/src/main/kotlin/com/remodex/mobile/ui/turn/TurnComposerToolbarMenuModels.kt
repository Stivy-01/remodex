package com.remodex.mobile.ui.turn

/**
 * Describes the overflow / compact “Chat Runtime” menu aligned in spirit with iOS
 * [TurnComposerRuntimeMenuBuilder]: root group with **Reasoning** (optional) and **Speed** sections.
 *
 * UI surfaces can map this to DropdownMenu, PopupMenu, BottomSheet, etc. without recomputing derivation.
 */
internal data class TurnComposerToolbarMenu(
    val rootTitle: String,
    val rootIconKind: TurnComposerToolbarIconKind,
    val sections: List<TurnComposerToolbarMenuSection>,
)

internal data class TurnComposerToolbarMenuSection(
    val title: String,
    val iconKind: TurnComposerToolbarIconKind,
    val items: List<TurnComposerToolbarMenuItem>,
)

internal data class TurnComposerToolbarMenuItem(
    /** Stable key for analytics / tests; maps to [TurnComposerRuntimeOption.id] where applicable. */
    val actionKey: TurnComposerToolbarActionKey,
    val title: String,
    val detail: String? = null,
    val checked: Boolean,
    /** `false` when the underlying selector is globally disabled (mirrors bar chip interactivity). */
    val enabled: Boolean,
)

internal data class TurnComposerToolbarActionKey(
    val kind: Kind,
    /** Reasoning effort string, or [TURN_COMPOSER_RUNTIME_AUTO_ID] for tier “Normal”. */
    val id: String,
) {
    internal enum class Kind {
        ReasoningEffort,
        ServiceTierSpeed,
    }
}

internal enum class TurnComposerToolbarIconKind {
    /** iOS `slider.horizontal.3` — host maps to Material symbol or icon. */
    ChatRuntimeRoot,
    /** iOS `brain` */
    ReasoningSection,
    /** iOS `bolt.fill` */
    SpeedSection,
}

/** User-visible labels (callers can wire [com.remodex.mobile.R.string] when integrating UI). */
internal data class TurnComposerToolbarLabels(
    val chatRuntimeRootTitle: String = "Chat Runtime",
    val reasoningSectionTitle: String = "Reasoning",
    val speedSectionTitle: String = "Speed",
)
