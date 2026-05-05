package com.remodex.mobile.services

import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexBridgeUpdatePrompt

/** Matches iOS `CodexService+RuntimeCompatibility.serviceTierBridgeUpdatePrompt.command`. */
internal const val SERVICE_TIER_BRIDGE_UPDATE_COMMAND = "npm install -g remodex@latest"

/**
 * After the bridge rejects `serviceTier`, omit it for the rest of the session and optionally surface
 * [CodexBridgeUpdatePrompt] once (parity iOS `markServiceTierUnsupportedForCurrentBridge`).
 */
internal fun CodexService.markServiceTierUnsupportedForCurrentBridge() {
    supportsServiceTier = false
    if (_selectedServiceTier.value == null || hasPresentedServiceTierBridgeUpdatePrompt) return
    hasPresentedServiceTierBridgeUpdatePrompt = true
    _bridgeUpdatePrompt.value =
        CodexBridgeUpdatePrompt(
            title = appContext.getString(R.string.bridge_update_service_tier_title),
            message = appContext.getString(R.string.bridge_update_service_tier_message),
            command = SERVICE_TIER_BRIDGE_UPDATE_COMMAND,
        )
}
