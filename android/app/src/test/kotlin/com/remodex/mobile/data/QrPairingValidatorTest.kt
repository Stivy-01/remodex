package com.remodex.mobile.data

import com.remodex.mobile.core.model.CODEX_PAIRING_QR_VERSION
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QrPairingValidatorTest {
    @Test
    fun wrongVersion_requestsBridgeUpdate() {
        val json =
            """
            {"v":${CODEX_PAIRING_QR_VERSION + 1},"relay":"ws://127.0.0.1:9000","sessionId":"s",
            "macDeviceId":"m","macIdentityPublicKey":"k","expiresAt":9999999999999}
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
            "macDeviceId":"mac","macIdentityPublicKey":"pk","expiresAt":$expires}
            """.trimIndent()
        val r = validatePairingQrCode(json, nowEpochMillis = now)
        assertIs<QrPairingValidationResult.Success>(r)
        assertTrue(r.payload.sessionId == "sess")
    }
}
