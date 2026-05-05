package com.remodex.mobile.ui.turn

internal data class TurnComposerAutocompleteState(
    val title: String,
    val items: List<TurnComposerAutocompleteItem>,
) {
    val isVisible: Boolean get() = items.isNotEmpty()
}

internal data class TurnComposerAutocompleteItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val payload: ComposerMentionChipPayload,
    val replacementText: String,
)

