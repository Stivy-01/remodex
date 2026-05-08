package com.remodex.mobile.beta

data class TesterHqPresentation(
    val scoreLabel: String,
    val streakLabel: String,
    val rankLabel: String,
    val buildLabel: String,
    val missionSummary: String,
)

object TesterHqPresentationBuilder {
    fun build(hq: BetaHqResponse?): TesterHqPresentation {
        val profile = hq?.profile
        val build = hq?.currentBuild
        val done = hq?.missions.orEmpty().count { it.status == BetaMissionStatus.Completed }
        val total = hq?.missions.orEmpty().size
        return TesterHqPresentation(
            scoreLabel = "${profile?.totalScore ?: 0} pts",
            streakLabel = "${profile?.streakDays ?: 0} days",
            rankLabel = profile?.rank?.let { "#$it" } ?: "Unranked",
            buildLabel = build?.version?.let { "Build $it" } ?: "Build unavailable",
            missionSummary = if (total == 0) "No missions yet" else "$done / $total complete",
        )
    }
}

