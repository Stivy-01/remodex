package com.remodex.mobile.core.model

/**
 * Recoverable bridge UX prompt (parity iOS `CodexBridgeUpdatePrompt`): pairing mismatch,
 * npm upgrade path, or runtime capability gaps such as unsupported `serviceTier`.
 */
data class CodexBridgeUpdatePrompt(
    val title: String,
    val message: String,
    /** Non-empty npm install line shown when the bridge should be updated on the desktop. */
    val command: String?,
)
