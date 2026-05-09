package com.remodex.mobile.beta

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BetaHqUiState(
    val enabled: Boolean,
    val joinState: BetaJoinState = BetaJoinState(),
    val loading: Boolean = false,
    val feedbackSubmitting: Boolean = false,
    val leaderboardLoading: Boolean = false,
    val hq: BetaHqResponse? = null,
    val leaderboard: BetaLeaderboardResponse? = null,
    val errorMessage: String? = null,
    val lastFeedbackResult: BetaFeedbackResponse? = null,
)

class BetaEngagementRepository(
    private val enabled: Boolean,
    private val store: BetaTesterStore,
    private val api: BetaEngagementApi?,
    private val appVersionProvider: () -> String,
    private val deviceModelProvider: () -> String,
) {
    private val _uiState =
        MutableStateFlow(
            BetaHqUiState(
                enabled = enabled,
                joinState = store.currentJoinState(),
            ),
        )
    val uiState: StateFlow<BetaHqUiState> = _uiState.asStateFlow()

    suspend fun joinBeta(displayName: String? = null) {
        if (!enabled) return
        val betaApi = api ?: return
        val testerId = store.getOrCreateTesterId()
        val name = BetaTesterStore.normalizedDisplayName(displayName)
        store.markOptedIn(name)
        updateState {
            copy(
                joinState = store.currentJoinState(),
                loading = true,
                errorMessage = null,
                lastFeedbackResult = null,
            )
        }
        runCatching {
            betaApi.register(
                BetaRegisterRequest(
                    testerId = testerId,
                    displayName = name,
                    appVersion = appVersionProvider(),
                    deviceModel = deviceModelProvider(),
                ),
            )
            betaApi.recordOpen(
                BetaOpenRequest(
                    testerId = testerId,
                    appVersion = appVersionProvider(),
                    deviceModel = deviceModelProvider(),
                ),
            )
        }.fold(
            onSuccess = { hq ->
                updateState {
                    copy(
                        loading = false,
                        joinState = store.currentJoinState(),
                        hq = hq,
                        errorMessage = null,
                    )
                }
            },
            onFailure = { error ->
                updateState {
                    copy(
                        loading = false,
                        joinState = store.currentJoinState(),
                        errorMessage = error.userVisibleBetaMessage(),
                    )
                }
            },
        )
    }

    suspend fun refreshHq(recordOpen: Boolean = false) {
        if (!enabled) return
        val betaApi = api ?: return
        val testerId = store.currentJoinState().testerId?.trim().orEmpty()
        if (!store.currentJoinState().optedIn || testerId.isEmpty()) return
        updateState { copy(loading = true, errorMessage = null) }
        runCatching {
            if (recordOpen) {
                betaApi.recordOpen(
                    BetaOpenRequest(
                        testerId = testerId,
                        appVersion = appVersionProvider(),
                        deviceModel = deviceModelProvider(),
                    ),
                )
            } else {
                betaApi.fetchHq(testerId = testerId, appVersion = appVersionProvider())
            }
        }.fold(
            onSuccess = { hq ->
                updateState {
                    copy(
                        loading = false,
                        hq = hq,
                        joinState = store.currentJoinState(),
                        errorMessage = null,
                    )
                }
            },
            onFailure = { error ->
                updateState {
                    copy(
                        loading = false,
                        joinState = store.currentJoinState(),
                        errorMessage = error.userVisibleBetaMessage(),
                    )
                }
            },
        )
    }

    suspend fun submitFeedback(
        category: BetaFeedbackCategory,
        message: String,
        screen: String?,
    ) {
        if (!enabled) return
        val betaApi = api ?: return
        val testerId = store.currentJoinState().testerId?.trim().orEmpty()
        if (!store.currentJoinState().optedIn || testerId.isEmpty() || message.trim().isEmpty()) return
        updateState { copy(feedbackSubmitting = true, errorMessage = null, lastFeedbackResult = null) }
        runCatching {
            betaApi.submitFeedback(
                BetaFeedbackRequest(
                    testerId = testerId,
                    type = category,
                    message = message.trim(),
                    screen = screen?.trim()?.takeIf { it.isNotEmpty() },
                    appVersion = appVersionProvider(),
                    deviceModel = deviceModelProvider(),
                ),
            )
        }.fold(
            onSuccess = { result ->
                updateState {
                    copy(
                        feedbackSubmitting = false,
                        lastFeedbackResult = result,
                        hq =
                            hq?.let { current ->
                                result.totalScore?.let { score ->
                                    current.copy(
                                        profile = current.profile.copy(totalScore = score),
                                        feedbackSentToday = true,
                                    )
                                } ?: current.copy(feedbackSentToday = true)
                            },
                        errorMessage = null,
                    )
                }
                refreshHq(recordOpen = false)
            },
            onFailure = { error ->
                updateState {
                    copy(
                        feedbackSubmitting = false,
                        errorMessage = error.userVisibleBetaMessage(),
                    )
                }
            },
        )
    }

    suspend fun refreshLeaderboard() {
        if (!enabled) return
        val betaApi = api ?: return
        val testerId = store.currentJoinState().testerId?.trim().orEmpty()
        if (!store.currentJoinState().optedIn || testerId.isEmpty()) return
        updateState { copy(leaderboardLoading = true, errorMessage = null) }
        runCatching {
            betaApi.fetchLeaderboard(
                testerId = testerId,
                appVersion = appVersionProvider(),
            )
        }.fold(
            onSuccess = { leaderboard ->
                recordMissionEvent(
                    eventType = "leaderboard_refreshed",
                    screen = "tester_hq",
                    refreshAfter = false,
                )
                updateState {
                    copy(
                        leaderboardLoading = false,
                        leaderboard = leaderboard,
                        errorMessage = null,
                    )
                }
            },
            onFailure = { error ->
                updateState {
                    copy(
                        leaderboardLoading = false,
                        errorMessage = error.userVisibleBetaMessage(),
                    )
                }
            },
        )
    }

    suspend fun recordMissionEvent(
        eventType: String,
        screen: String? = null,
        refreshAfter: Boolean = true,
    ): BetaMissionEventResponse? {
        if (!enabled) return null
        val betaApi = api ?: return null
        val testerId = store.currentJoinState().testerId?.trim().orEmpty()
        if (!store.currentJoinState().optedIn || testerId.isEmpty() || eventType.isBlank()) return null
        return runCatching {
            betaApi.recordMissionEvent(
                BetaMissionEventRequest(
                    testerId = testerId,
                    eventType = eventType.trim(),
                    appVersion = appVersionProvider(),
                    deviceModel = deviceModelProvider(),
                    screen = screen?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }.fold(
            onSuccess = { result ->
                result.totalScore?.let { score ->
                    updateState {
                        copy(
                            hq = hq?.copy(profile = hq.profile.copy(totalScore = score)),
                            errorMessage = null,
                        )
                    }
                }
                if (refreshAfter && result.success) {
                    refreshHq(recordOpen = false)
                }
                result
            },
            onFailure = {
                null
            },
        )
    }

    fun clearFeedbackResult() {
        updateState { copy(lastFeedbackResult = null) }
    }

    private fun updateState(transform: BetaHqUiState.() -> BetaHqUiState) {
        _uiState.value = _uiState.value.transform()
    }
}

private fun Throwable.userVisibleBetaMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Unable to reach the beta server."
