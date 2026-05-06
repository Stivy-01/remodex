package com.remodex.mobile.core.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with an explicit 12-byte nonce (from [com.remodex.mobile.core.model.codexSecureNonce]).
 * Matches Swift `AES.GCM.seal` / `AES.GCM.open` with separate ciphertext and tag in the wire envelope.
 */
internal object SecureEnvelopeCipher {
    private const val TAG_BITS = 128

    fun seal(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
        require(nonce.size == 12) { "GCM nonce must be 12 bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        val combined = cipher.doFinal(plaintext)
        val ct = combined.copyOfRange(0, combined.size - 16)
        val tag = combined.copyOfRange(combined.size - 16, combined.size)
        return ct to tag
    }

    fun open(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        tag: ByteArray,
    ): ByteArray? {
        if (key.size != 32 || nonce.size != 12) return null
        return try {
            val combined = ciphertext + tag
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            cipher.doFinal(combined)
        } catch (_: Exception) {
            null
        }
    }
}
