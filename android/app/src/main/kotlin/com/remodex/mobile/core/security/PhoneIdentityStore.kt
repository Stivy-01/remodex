package com.remodex.mobile.core.security

import com.remodex.mobile.core.crypto.RemodexNativeCrypto
import com.remodex.mobile.core.model.CodexPhoneIdentityState
import java.util.Base64
import java.util.UUID

/**
 * Persists or creates the phone Ed25519 identity used for the secure relay handshake.
 * Mirrors [codexPhoneIdentityStateFromSecureStore](CodexMobile/CodexMobile/Services/CodexSecureTransportModels.swift).
 */
object PhoneIdentityStore {
    fun loadOrCreate(secureStore: SecureStore): CodexPhoneIdentityState {
        secureStore.readCodable<CodexPhoneIdentityState>(CodexSecureKeys.phoneIdentityState)?.let {
            return it
        }
        val (priv, pub) = RemodexNativeCrypto.generateEd25519KeyPair()
        val next =
            CodexPhoneIdentityState(
                phoneDeviceId = UUID.randomUUID().toString(),
                phoneIdentityPrivateKey = Base64.getEncoder().encodeToString(priv),
                phoneIdentityPublicKey = Base64.getEncoder().encodeToString(pub),
            )
        secureStore.writeCodable(CodexSecureKeys.phoneIdentityState, next)
        return next
    }
}
