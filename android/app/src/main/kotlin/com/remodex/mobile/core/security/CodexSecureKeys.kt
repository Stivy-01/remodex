package com.remodex.mobile.core.security

/**
 * Keychain account names mirrored from [CodexSecureKeys](CodexMobile/CodexMobile/Services/SecureStore.swift).
 */
object CodexSecureKeys {
    const val relaySessionId = "codex.relay.sessionId"
    const val relayUrl = "codex.relay.url"
    const val relayMacDeviceId = "codex.relay.macDeviceId"
    const val relayMacIdentityPublicKey = "codex.relay.macIdentityPublicKey"
    const val relayProtocolVersion = "codex.relay.protocolVersion"
    const val relayLastAppliedBridgeOutboundSeq = "codex.relay.lastAppliedBridgeOutboundSeq"
    const val pushDeviceToken = "codex.push.deviceToken"
    const val trustedMacRegistry = "codex.secure.trustedMacRegistry"
    const val lastTrustedMacDeviceId = "codex.secure.lastTrustedMacDeviceId"
    const val phoneIdentityState = "codex.secure.phoneIdentityState"
    const val messageHistoryKey = "codex.local.messageHistoryKey"
}
