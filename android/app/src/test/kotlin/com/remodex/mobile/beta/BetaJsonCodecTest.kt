package com.remodex.mobile.beta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString

class BetaJsonCodecTest {
    @Test
    fun feedbackRequestUsesStableWireNames() {
        val encoded =
            BetaJson.encodeToString(
                BetaFeedbackRequest(
                    testerId = "00000000-0000-0000-0000-000000000001",
                    type = BetaFeedbackCategory.ConfusingFlow,
                    message = "The QR step was unclear.",
                    screen = "qr_connection",
                    appVersion = "0.1.0",
                    deviceModel = "Pixel",
                ),
            )

        assertEquals(
            """{"tester_id":"00000000-0000-0000-0000-000000000001","type":"confusing_flow","message":"The QR step was unclear.","screen":"qr_connection","app_version":"0.1.0","device_model":"Pixel"}""",
            encoded,
        )
    }

    @Test
    fun hqResponseDecodesWithMissingOptionalFields() {
        val decoded =
            BetaJson.decodeFromString<BetaHqResponse>(
                """
                {
                  "profile": {
                    "tester_id": "00000000-0000-0000-0000-000000000001"
                  }
                }
                """.trimIndent(),
            )

        assertEquals("00000000-0000-0000-0000-000000000001", decoded.profile.testerId)
        assertEquals(0, decoded.profile.totalScore)
        assertEquals(emptyList(), decoded.missions)
        assertEquals(DEFAULT_BETA_REWARD_COPY, decoded.rewardCopy)
    }
}

