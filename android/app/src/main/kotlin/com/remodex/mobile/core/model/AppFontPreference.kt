package com.remodex.mobile.core.model

/**
 * User-selectable prose font preference (Compose-facing wiring comes in UI phases).
 * Keys mirror [AppFont.swift](CodexMobile/CodexMobile/Models/AppFont.swift).
 */
enum class AppFontStyle {
    system,
    geist,
    jetBrainsMono;

    val title: String
        get() =
            when (this) {
                system -> "System"
                geist -> "Geist"
                jetBrainsMono -> "JetBrains Mono"
            }

    val subtitle: String
        get() =
            when (this) {
                system -> "Use the system font for regular text. Code stays monospaced."
                geist -> "Use Geist for regular text. Code stays monospaced."
                jetBrainsMono -> "Use JetBrains Mono for regular text and code."
            }

    companion object {
        const val storageKey: String = "codex.appFontStyle"
        const val legacyStorageKey: String = "codex.useJetBrainsMono"
        val defaultStyle: AppFontStyle = system
    }
}
