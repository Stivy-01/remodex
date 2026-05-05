package com.remodex.mobile.core.persistence

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with 12-byte nonce prefix, compatible with Swift CryptoKit's `AES.GCM.seal` combined layout
 * (nonce || ciphertext || tag) for the same parameters (128-bit tag).
 */
internal object MessageHistoryAesGcm {
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    fun generateKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
    ): ByteArray {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_LENGTH_BITS, iv),
        )
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    fun decrypt(
        wrapped: ByteArray,
        key: ByteArray,
    ): ByteArray? {
        if (wrapped.size < IV_LENGTH + 16) return null
        return try {
            val iv = wrapped.copyOfRange(0, IV_LENGTH)
            val ciphertext = wrapped.copyOfRange(IV_LENGTH, wrapped.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_LENGTH_BITS, iv),
            )
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }
}
