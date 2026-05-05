package com.remodex.mobile.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.generators.HKDFBytesGenerator

/**
 * Curve25519 / Ed25519 / HKDF-SHA256 aligned with Swift CryptoKit usage in
 * [CodexService+SecureTransport.swift](CodexMobile/CodexMobile/Services/CodexService+SecureTransport.swift).
 */
internal object RemodexNativeCrypto {
    private val secureRandom = SecureRandom()

    fun generateX25519KeyPair(): Pair<ByteArray, ByteArray> {
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(secureRandom))
        val kp = gen.generateKeyPair()
        val priv = kp.private as X25519PrivateKeyParameters
        val pub = kp.public as X25519PublicKeyParameters
        return Pair(priv.encoded, pub.encoded)
    }

    fun x25519SharedSecret(
        ourPrivateKey: ByteArray,
        theirPublicKey: ByteArray,
    ): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(ourPrivateKey, 0))
        val out = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(theirPublicKey, 0), out, 0)
        return out
    }

    fun generateEd25519KeyPair(): Pair<ByteArray, ByteArray> {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(secureRandom))
        val kp = gen.generateKeyPair()
        val priv = kp.private as Ed25519PrivateKeyParameters
        val pub = kp.public as Ed25519PublicKeyParameters
        return Pair(priv.encoded, pub.encoded)
    }

    fun ed25519Sign(
        message: ByteArray,
        privateKey32: ByteArray,
    ): ByteArray {
        val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey32, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun ed25519Verify(
        message: ByteArray,
        signature: ByteArray,
        publicKey32: ByteArray,
    ): Boolean {
        val verifier = org.bouncycastle.crypto.signers.Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey32, 0))
        verifier.update(message, 0, message.size)
        return verifier.verifySignature(signature)
    }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, salt, info))
        val out = ByteArray(length)
        hkdf.generateBytes(out, 0, length)
        return out
    }

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { secureRandom.nextBytes(it) }
}
