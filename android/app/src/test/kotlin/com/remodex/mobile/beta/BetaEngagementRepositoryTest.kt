package com.remodex.mobile.beta

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BetaEngagementRepositoryTest {
    @Test
    fun disabledRepositoryDoesNotCallApiOrOptIn() =
        runTest {
            val api = TrackingBetaApi()
            val store = BetaTesterStore(InMemoryBetaKeyValueStore(), idFactory = { "tester-id" })
            val repo =
                BetaEngagementRepository(
                    enabled = false,
                    store = store,
                    api = api,
                    appVersionProvider = { "0.1.0" },
                    deviceModelProvider = { "Pixel" },
                )

            repo.joinBeta("Tester")

            assertEquals(0, api.registerCalls)
            assertFalse(repo.uiState.value.joinState.optedIn)
            assertNull(repo.uiState.value.joinState.testerId)
        }

    @Test
    fun joinRegistersAndRecordsOpen() =
        runTest {
            val api = TrackingBetaApi()
            val repo = enabledRepo(api)

            repo.joinBeta("Tester")

            assertEquals(1, api.registerCalls)
            assertEquals(1, api.openCalls)
            assertTrue(repo.uiState.value.joinState.optedIn)
            assertEquals(42, repo.uiState.value.hq?.profile?.totalScore)
        }

    @Test
    fun networkFailureIsNonBlockingUiError() =
        runTest {
            val api = TrackingBetaApi(failOpen = true)
            val repo = enabledRepo(api)

            repo.joinBeta("Tester")

            assertTrue(repo.uiState.value.joinState.optedIn)
            assertEquals("offline", repo.uiState.value.errorMessage)
            assertFalse(repo.uiState.value.loading)
        }

    @Test
    fun feedbackUpdatesScoreFromServer() =
        runTest {
            val api = TrackingBetaApi()
            val repo = enabledRepo(api)

            repo.joinBeta("Tester")
            repo.submitFeedback(BetaFeedbackCategory.Bug, "Found a bug", "tester_hq")

            assertEquals(1, api.feedbackCalls)
            assertEquals(82, repo.uiState.value.lastFeedbackResult?.totalScore)
            assertEquals(1, api.hqCalls)
        }

    @Test
    fun leaderboardRefreshLoadsRows() =
        runTest {
            val api = TrackingBetaApi()
            val repo = enabledRepo(api)

            repo.joinBeta("Tester")
            repo.refreshLeaderboard()

            assertEquals(1, api.leaderboardCalls)
            assertEquals(2, repo.uiState.value.leaderboard?.profile?.rank)
            assertEquals(2, repo.uiState.value.leaderboard?.rows?.size)
        }
}

private fun enabledRepo(api: TrackingBetaApi): BetaEngagementRepository =
    BetaEngagementRepository(
        enabled = true,
        store = BetaTesterStore(InMemoryBetaKeyValueStore(), idFactory = { "tester-id" }),
        api = api,
        appVersionProvider = { "0.1.0" },
        deviceModelProvider = { "Pixel" },
    )

private class TrackingBetaApi(
    private val failOpen: Boolean = false,
) : BetaEngagementApi {
    var registerCalls = 0
    var openCalls = 0
    var hqCalls = 0
    var feedbackCalls = 0
    var leaderboardCalls = 0

    override suspend fun register(request: BetaRegisterRequest): BetaTesterProfile {
        registerCalls++
        return BetaTesterProfile(testerId = request.testerId, displayName = request.displayName)
    }

    override suspend fun recordOpen(request: BetaOpenRequest): BetaHqResponse {
        openCalls++
        if (failOpen) throw IOException("offline")
        return hq(score = 42)
    }

    override suspend fun fetchHq(
        testerId: String,
        appVersion: String,
    ): BetaHqResponse {
        hqCalls++
        return hq(score = 82)
    }

    override suspend fun submitFeedback(request: BetaFeedbackRequest): BetaFeedbackResponse {
        feedbackCalls++
        return BetaFeedbackResponse(success = true, pointsAwarded = 40, totalScore = 82)
    }

    override suspend fun fetchLeaderboard(
        testerId: String,
        appVersion: String,
    ): BetaLeaderboardResponse {
        leaderboardCalls++
        return BetaLeaderboardResponse(
            profile =
                BetaTesterProfile(
                    testerId = testerId,
                    totalScore = 82,
                    rank = 2,
                    streakDays = 2,
                ),
            rows =
                listOf(
                    BetaLeaderboardRow(
                        rank = 1,
                        displayName = "Tester-1",
                        totalPoints = 120,
                    ),
                    BetaLeaderboardRow(
                        rank = 2,
                        displayName = "Tester",
                        totalPoints = 82,
                        isCurrentTester = true,
                    ),
                ),
            top30Cutoff = 10,
        )
    }

    private fun hq(score: Int): BetaHqResponse =
        BetaHqResponse(
            profile =
                BetaTesterProfile(
                    testerId = "tester-id",
                    displayName = "Tester",
                    totalScore = score,
                    rank = 3,
                    streakDays = 2,
                ),
        )
}
