package com.remodex.mobile.beta

import kotlin.test.Test
import kotlin.test.assertEquals

class TesterHqPresentationTest {
    @Test
    fun formatsHqLabels() {
        val presentation =
            TesterHqPresentationBuilder.build(
                BetaHqResponse(
                    profile =
                        BetaTesterProfile(
                            testerId = "tester-id",
                            totalScore = 285,
                            rank = 12,
                            streakDays = 4,
                        ),
                    currentBuild = BetaBuildInfo(version = "0.1.8"),
                    missions =
                        listOf(
                            BetaMission("open", "Open build", status = BetaMissionStatus.Completed),
                            BetaMission("feedback", "Send feedback", status = BetaMissionStatus.Pending),
                        ),
                ),
            )

        assertEquals("285 pts", presentation.scoreLabel)
        assertEquals("4 days", presentation.streakLabel)
        assertEquals("#12", presentation.rankLabel)
        assertEquals("Build 0.1.8", presentation.buildLabel)
        assertEquals("1 / 2 complete", presentation.missionSummary)
    }

    @Test
    fun formatsEmptyHq() {
        val presentation = TesterHqPresentationBuilder.build(null)

        assertEquals("0 pts", presentation.scoreLabel)
        assertEquals("0 days", presentation.streakLabel)
        assertEquals("Unranked", presentation.rankLabel)
        assertEquals("Build unavailable", presentation.buildLabel)
        assertEquals("No missions yet", presentation.missionSummary)
    }
}

