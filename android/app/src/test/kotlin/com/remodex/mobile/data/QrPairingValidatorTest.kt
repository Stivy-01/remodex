package com.remodex.mobile.data

import com.remodex.mobile.core.model.CODEX_PAIRING_QR_VERSION
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QrPairingValidatorTest {
    private val validMacIdentityPublicKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun wrongVersion_requestsBridgeUpdate() {
        val json =
            """
            {"v":${CODEX_PAIRING_QR_VERSION + 1},"relay":"ws://127.0.0.1:9000","sessionId":"s",
            "macDeviceId":"m","macIdentityPublicKey":"$validMacIdentityPublicKey","expiresAt":9999999999999}
            """.trimIndent()
        val r = validatePairingQrCode(json)
        assertIs<QrPairingValidationResult.BridgeUpdateRequired>(r)
    }

    @Test
    fun validPayload_succeeds() {
        val now = 1_700_000_000_000L
        val expires = now + 3600_000L
        val json =
            """
            {"v":$CODEX_PAIRING_QR_VERSION,"relay":"ws://192.168.1.5:9000","sessionId":"sess",
            "macDeviceId":"mac","macIdentityPublicKey":"$validMacIdentityPublicKey","expiresAt":$expires}
            """.trimIndent()
        val r = validatePairingQrCode(json, nowEpochMillis = now)
        assertIs<QrPairingValidationResult.Success>(r)
        assertTrue(r.payload.sessionId == "sess")
    }

    @Test
    fun publicCleartextRelay_isRejected() {
        val now = 1_700_000_000_000L
        val expires = now + 3600_000L
        val json =
            """
            {"v":$CODEX_PAIRING_QR_VERSION,"relay":"ws://relay.example.com:9000","sessionId":"sess",
            "macDeviceId":"mac","macIdentityPublicKey":"$validMacIdentityPublicKey","expiresAt":$expires}
            """.trimIndent()
        val r = validatePairingQrCode(json, nowEpochMillis = now)
        assertIs<QrPairingValidationResult.ScanError>(r)
    }

    @Test
    fun malformedMacIdentityKey_isRejected() {
        val now = 1_700_000_000_000L
        val expires = now + 3600_000L
        val json =
            """
            {"v":$CODEX_PAIRING_QR_VERSION,"relay":"ws://192.168.1.5:9000","sessionId":"sess",
            "macDeviceId":"mac","macIdentityPublicKey":"not-a-key","expiresAt":$expires}
            """.trimIndent()
        val r = validatePairingQrCode(json, nowEpochMillis = now)
        assertIs<QrPairingValidationResult.ScanError>(r)
    }

    @Test
    fun unsafeIdentifierChars_areRejected() {
        val now = 1_700_000_000_000L
        val expires = now + 3600_000L
        val json =
            """
            {"v":$CODEX_PAIRING_QR_VERSION,"relay":"ws://192.168.1.5:9000","sessionId":"sess token",
            "macDeviceId":"mac","macIdentityPublicKey":"$validMacIdentityPublicKey","expiresAt":$expires}
            """.trimIndent()
        val r = validatePairingQrCode(json, nowEpochMillis = now)
        assertIs<QrPairingValidationResult.ScanError>(r)
    }

    @Test
    fun resolvePairingCode_buildsPayloadFromRelayResponse() = runTest {
        val now = 1_700_000_000_000L
        val expires = now + 3600_000L
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("content-type", "application/json")
                    .setBody(
                        """
                        {"ok":true,"v":$CODEX_PAIRING_QR_VERSION,"sessionId":"sess",
                        "macDeviceId":"mac","macIdentityPublicKey":"$validMacIdentityPublicKey","expiresAt":$expires}
                        """.trimIndent(),
                    ),
            )

            server.start()
            val relayUrl = "ws://127.0.0.1:${server.port}/relay"
            val result =
                resolvePairingCode(
                    httpClient = OkHttpClient(),
                    relayUrl = relayUrl,
                    code = "AB23-CD34EF",
                    nowEpochMillis = now,
                )

            if (result is QrPairingValidationResult.ScanError) fail(result.message)
            val success = assertIs<QrPairingValidationResult.Success>(result)
            assertEquals("sess", success.payload.sessionId)
            assertEquals(relayUrl, success.payload.relay)
            val request = server.takeRequest()
            assertEquals("/v1/pairing/code/resolve", request.path)
        }
    }

    @Test
    fun resolvePairingCode_rejectsMalformedRelayResponseFields() = runTest {
        val now = 1_700_000_000_000L
        val expires = now + 3600_000L
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("content-type", "application/json")
                    .setBody(
                        """
                        {"ok":true,"v":$CODEX_PAIRING_QR_VERSION,"sessionId":"sess with spaces",
                        "macDeviceId":"mac","macIdentityPublicKey":"$validMacIdentityPublicKey","expiresAt":$expires}
                        """.trimIndent(),
                    ),
            )

            server.start()
            val relayUrl = "ws://127.0.0.1:${server.port}/relay"
            val result =
                resolvePairingCode(
                    httpClient = OkHttpClient(),
                    relayUrl = relayUrl,
                    code = "AB23-CD34EF",
                    nowEpochMillis = now,
                )

            assertIs<QrPairingValidationResult.ScanError>(result)
        }
    }
}
