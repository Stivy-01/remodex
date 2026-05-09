package com.remodex.mobile.beta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BetaJoinState(
    @SerialName("opted_in")
    val optedIn: Boolean = false,
    @SerialName("tester_id")
    val testerId: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
)

@Serializable
data class BetaTesterProfile(
    @SerialName("tester_id")
    val testerId: String,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("total_score")
    val totalScore: Int = 0,
    val rank: Int? = null,
    @SerialName("streak_days")
    val streakDays: Int = 0,
)

@Serializable
data class BetaMission(
    val id: String,
    val title: String,
    val description: String? = null,
    val points: Int = 0,
    val status: BetaMissionStatus = BetaMissionStatus.Pending,
)

@Serializable
enum class BetaMissionStatus {
    @SerialName("pending")
    Pending,

    @SerialName("completed")
    Completed,

    @SerialName("locked")
    Locked,
}

@Serializable
data class BetaBuildInfo(
    val version: String,
    val changelog: List<String> = emptyList(),
    @SerialName("today_test")
    val todayTest: List<String> = emptyList(),
    @SerialName("known_issues")
    val knownIssues: List<String> = emptyList(),
)

@Serializable
data class BetaHqResponse(
    val profile: BetaTesterProfile,
    @SerialName("current_build")
    val currentBuild: BetaBuildInfo? = null,
    val missions: List<BetaMission> = emptyList(),
    @SerialName("reward_copy")
    val rewardCopy: String = DEFAULT_BETA_REWARD_COPY,
    @SerialName("feedback_sent_today")
    val feedbackSentToday: Boolean = false,
)

@Serializable
data class BetaRegisterRequest(
    @SerialName("tester_id")
    val testerId: String,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("device_model")
    val deviceModel: String,
)

@Serializable
data class BetaOpenRequest(
    @SerialName("tester_id")
    val testerId: String,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("device_model")
    val deviceModel: String,
)

@Serializable
data class BetaFeedbackRequest(
    @SerialName("tester_id")
    val testerId: String,
    val type: BetaFeedbackCategory,
    val message: String,
    val screen: String? = null,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("device_model")
    val deviceModel: String,
)

@Serializable
data class BetaFeedbackResponse(
    val success: Boolean,
    @SerialName("points_awarded")
    val pointsAwarded: Int = 0,
    @SerialName("total_score")
    val totalScore: Int? = null,
    val message: String? = null,
)

@Serializable
data class BetaMissionEventRequest(
    @SerialName("tester_id")
    val testerId: String,
    @SerialName("event_type")
    val eventType: String,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("device_model")
    val deviceModel: String,
    val screen: String? = null,
)

@Serializable
data class BetaMissionEventResponse(
    val success: Boolean,
    @SerialName("event_type")
    val eventType: String? = null,
    @SerialName("mission_id")
    val missionId: String? = null,
    @SerialName("points_awarded")
    val pointsAwarded: Int = 0,
    @SerialName("total_score")
    val totalScore: Int? = null,
    val message: String? = null,
)

@Serializable
data class BetaLeaderboardResponse(
    val profile: BetaTesterProfile,
    val rows: List<BetaLeaderboardRow> = emptyList(),
    @SerialName("top_30_cutoff")
    val top30Cutoff: Int? = null,
    @SerialName("updated_every_minutes")
    val updatedEveryMinutes: Int = 15,
)

@Serializable
data class BetaLeaderboardRow(
    val rank: Int,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("total_points")
    val totalPoints: Int,
    @SerialName("is_current_tester")
    val isCurrentTester: Boolean = false,
)

@Serializable
enum class BetaFeedbackCategory {
    @SerialName("bug")
    Bug,

    @SerialName("crash")
    Crash,

    @SerialName("ux_issue")
    UxIssue,

    @SerialName("confusing_flow")
    ConfusingFlow,

    @SerialName("performance")
    Performance,

    @SerialName("feature_request")
    FeatureRequest,

    @SerialName("other")
    Other,
}

const val DEFAULT_BETA_REWARD_COPY =
    "Top 30 useful beta contributors will receive 1 free month after public release. " +
        "Points help us track participation, but final selection also considers useful feedback, " +
        "confirmed bugs, and testing quality. Reviews and ratings are never required or rewarded."
