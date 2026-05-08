package com.remodex.mobile.ui.beta

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remodex.mobile.beta.BetaEngagementRepository
import com.remodex.mobile.beta.BetaFeedbackCategory
import com.remodex.mobile.beta.BetaHqResponse
import com.remodex.mobile.beta.BetaLeaderboardResponse
import com.remodex.mobile.beta.BetaLeaderboardRow
import com.remodex.mobile.beta.BetaMission
import com.remodex.mobile.beta.BetaMissionStatus
import com.remodex.mobile.beta.DEFAULT_BETA_REWARD_COPY
import com.remodex.mobile.beta.TesterHqPresentation
import com.remodex.mobile.beta.TesterHqPresentationBuilder
import com.remodex.mobile.ui.theme.remodexScreenTopAppBarColors
import kotlinx.coroutines.launch

private enum class TesterHqMode {
    Hq,
    Leaderboard,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TesterHqScreen(
    repository: BetaEngagementRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onNavigateBack)
    val state by repository.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(TesterHqMode.Hq) }

    LaunchedEffect(state.joinState.optedIn) {
        if (state.enabled && state.joinState.optedIn) {
            repository.refreshHq(recordOpen = true)
        }
    }
    LaunchedEffect(mode, state.joinState.optedIn) {
        if (mode == TesterHqMode.Leaderboard && state.enabled && state.joinState.optedIn) {
            repository.refreshLeaderboard()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (mode == TesterHqMode.Leaderboard) "Leaderboard" else "Tester HQ",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = remodexScreenTopAppBarColors(),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (mode == TesterHqMode.Leaderboard) {
                                mode = TesterHqMode.Hq
                            } else {
                                onNavigateBack()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.enabled) {
                Text(
                    text = "Beta HQ is not configured for this build.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }

            if (!state.joinState.optedIn) {
                JoinBetaCard(
                    loading = state.loading,
                    errorMessage = state.errorMessage,
                    onJoin = { name -> scope.launch { repository.joinBeta(name) } },
                )
            } else if (mode == TesterHqMode.Leaderboard) {
                LeaderboardContent(
                    hq = state.hq,
                    leaderboard = state.leaderboard,
                    loading = state.leaderboardLoading,
                    errorMessage = state.errorMessage,
                    onRefresh = { scope.launch { repository.refreshLeaderboard() } },
                    onTesterHq = { mode = TesterHqMode.Hq },
                )
            } else {
                TesterHqContent(
                    hq = state.hq,
                    loading = state.loading,
                    errorMessage = state.errorMessage,
                    onRefresh = { scope.launch { repository.refreshHq(recordOpen = false) } },
                    onFeedback = { showFeedback = true },
                    onLeaderboard = {
                        mode = TesterHqMode.Leaderboard
                        scope.launch { repository.refreshLeaderboard() }
                    },
                )
            }

            state.lastFeedbackResult?.let { result ->
                TerminalPanel {
                    Text(
                        text = "Feedback sent",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text =
                            if (result.pointsAwarded > 0) {
                                "+${result.pointsAwarded} beta points"
                            } else {
                                result.message ?: "Thanks. This helps improve the Android beta."
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    BetaFeedbackSheet(
        visible = showFeedback,
        submitting = state.feedbackSubmitting,
        onDismiss = {
            showFeedback = false
            repository.clearFeedbackResult()
        },
        onSubmit = { category: BetaFeedbackCategory, message: String ->
            scope.launch {
                repository.submitFeedback(
                    category = category,
                    message = message,
                    screen = "tester_hq",
                )
                showFeedback = false
            }
        },
    )
}

@Composable
private fun JoinBetaCard(
    loading: Boolean,
    errorMessage: String?,
    onJoin: (String?) -> Unit,
) {
    var nickname by rememberSaveable { mutableStateOf("") }

    TerminalPanel {
        TerminalHeader(title = "Help shape Remodex Android", icon = ">_")
        Text(
            text = "Join the beta loop to see daily missions, send in-app feedback, and track useful testing contribution with an anonymous tester ID.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it.take(20) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nickname (optional)") },
            singleLine = true,
        )
        PrimaryTerminalButton(
            text = if (loading) "Joining..." else "Join beta",
            icon = Icons.Outlined.Send,
            enabled = !loading,
            onClick = { onJoin(nickname) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = DEFAULT_BETA_REWARD_COPY,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        errorMessage?.let { ErrorText(it) }
    }
}

@Composable
private fun TesterHqContent(
    hq: BetaHqResponse?,
    loading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onFeedback: () -> Unit,
    onLeaderboard: () -> Unit,
) {
    val presentation = TesterHqPresentationBuilder.build(hq)

    if (loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    SummaryPanel(presentation)

    hq?.currentBuild?.let { build ->
        TerminalPanel {
            TerminalHeader(title = "Latest update", icon = ">_")
            TwoColumnBlock(
                leftTitle = "Fixed",
                leftIcon = Icons.Outlined.BugReport,
                leftItems = build.changelog,
                rightTitle = "Today's test",
                rightIcon = Icons.Outlined.Star,
                rightItems = build.todayTest,
            )
            if (build.knownIssues.isNotEmpty()) {
                BulletList(title = "Known issues", icon = Icons.Outlined.BugReport, items = build.knownIssues)
            }
        }
    }

    MissionsPanel(missions = hq?.missions.orEmpty())

    TerminalPanel {
        SectionTitle(icon = Icons.Outlined.CardGiftcard, title = "Reward")
        Text(
            text = hq?.rewardCopy ?: DEFAULT_BETA_REWARD_COPY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryTerminalButton(
                text = "Send feedback",
                icon = Icons.Outlined.Send,
                onClick = onFeedback,
                modifier = Modifier.weight(1f),
            )
            SecondaryTerminalButton(
                text = "Refresh",
                icon = Icons.Outlined.Refresh,
                enabled = !loading,
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
            )
        }
        SecondaryTerminalButton(
            text = "Leaderboard",
            icon = Icons.Outlined.EmojiEvents,
            onClick = onLeaderboard,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    errorMessage?.let { ErrorText(it) }
}

@Composable
private fun LeaderboardContent(
    hq: BetaHqResponse?,
    leaderboard: BetaLeaderboardResponse?,
    loading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onTesterHq: () -> Unit,
) {
    if (loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    CompactBuildPill(hq?.currentBuild?.version)
    LeaderboardSummaryPanel(leaderboard = leaderboard, hq = hq)
    LeaderboardRowsPanel(leaderboard = leaderboard)

    Text(
        text = "Rank updates every ${leaderboard?.updatedEveryMinutes ?: 15} minutes",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SecondaryTerminalButton(
            text = "Refresh",
            icon = Icons.Outlined.Refresh,
            enabled = !loading,
            onClick = onRefresh,
            modifier = Modifier.weight(1f),
        )
        PrimaryTerminalButton(
            text = "Tester HQ",
            icon = Icons.Outlined.Star,
            onClick = onTesterHq,
            modifier = Modifier.weight(1f),
        )
    }
    errorMessage?.let { ErrorText(it) }
}

@Composable
private fun SummaryPanel(presentation: TesterHqPresentation) {
    TerminalPanel(borderAccent = true) {
        TerminalHeader(title = presentation.buildLabel, icon = ">_")
        DividerLine()
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 520.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatTile(icon = Icons.Outlined.Star, label = "Score", value = presentation.scoreLabel)
                    StatTile(icon = Icons.Outlined.Whatshot, label = "Streak", value = presentation.streakLabel)
                    StatTile(icon = Icons.Outlined.EmojiEvents, label = "Rank", value = presentation.rankLabel)
                    StatTile(icon = Icons.Outlined.Star, label = "Missions", value = presentation.missionSummary)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        StatTile(icon = Icons.Outlined.Star, label = "Score", value = presentation.scoreLabel)
                        StatTile(icon = Icons.Outlined.EmojiEvents, label = "Rank", value = presentation.rankLabel)
                    }
                    VerticalDividerLine()
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        StatTile(icon = Icons.Outlined.Whatshot, label = "Streak", value = presentation.streakLabel)
                        StatTile(icon = Icons.Outlined.Star, label = "Missions", value = presentation.missionSummary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionsPanel(missions: List<BetaMission>) {
    TerminalPanel {
        SectionTitle(icon = Icons.Outlined.Star, title = "Today's Missions")
        if (missions.isEmpty()) {
            Text(
                text = "No missions published for this build yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            missions.forEach { mission ->
                MissionRow(mission)
            }
        }
    }
}

@Composable
private fun MissionRow(mission: BetaMission) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                if (mission.status == BetaMissionStatus.Completed) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
            contentDescription = null,
            tint =
                if (mission.status == BetaMissionStatus.Completed) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.size(34.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mission.title,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
            )
            mission.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        VerticalDividerLine(modifier = Modifier.height(52.dp))
        Text(
            text = "+${mission.points}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun LeaderboardSummaryPanel(
    leaderboard: BetaLeaderboardResponse?,
    hq: BetaHqResponse?,
) {
    val profile = leaderboard?.profile ?: hq?.profile
    TerminalPanel(borderAccent = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LeaderboardMetric(
                icon = Icons.Outlined.Person,
                label = "Your rank",
                value = profile?.rank?.let { "#$it" } ?: "-",
                modifier = Modifier.weight(1f),
            )
            VerticalDividerLine(modifier = Modifier.height(104.dp))
            LeaderboardMetric(
                icon = Icons.Outlined.Star,
                label = "Your points",
                value = "${profile?.totalScore ?: 0} pts",
                modifier = Modifier.weight(1f),
            )
            VerticalDividerLine(modifier = Modifier.height(104.dp))
            LeaderboardMetric(
                icon = Icons.Outlined.EmojiEvents,
                label = "Top 30 cutoff",
                value = leaderboard?.top30Cutoff?.let { "$it pts" } ?: "-",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LeaderboardRowsPanel(leaderboard: BetaLeaderboardResponse?) {
    TerminalPanel {
        val rows = leaderboard?.rows.orEmpty()
        if (rows.isEmpty()) {
            Text(
                text = "No leaderboard rows yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            rows.forEachIndexed { index, row ->
                LeaderboardRow(row)
                if (index != rows.lastIndex) DividerLine()
            }
        }

        val current = rows.firstOrNull { it.isCurrentTester }
        val profile = leaderboard?.profile
        if (profile != null && current == null && profile.rank != null) {
            DividerLine()
            Text(
                text = "...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            LeaderboardRow(
                BetaLeaderboardRow(
                    rank = profile.rank,
                    displayName = "you",
                    totalPoints = profile.totalScore,
                    isCurrentTester = true,
                ),
            )
        }
    }
}

@Composable
private fun LeaderboardRow(row: BetaLeaderboardRow) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (row.isCurrentTester) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    } else {
                        Color.Transparent
                    },
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RankBadge(row.rank)
        IconFrame(icon = if (row.isCurrentTester) Icons.Outlined.Person else Icons.Outlined.Star)
        Text(
            text = row.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${row.totalPoints} pts",
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (row.rank <= 3 || row.isCurrentTester) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(
                    BorderStroke(
                        1.dp,
                        if (rank <= 3) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                    ),
                    CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun LeaderboardMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconFrame(icon)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CompactBuildPill(version: String?) {
    Box(
        modifier =
            Modifier
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)),
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = ">_ Build ${version ?: "-"}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun TwoColumnBlock(
    leftTitle: String,
    leftIcon: ImageVector,
    leftItems: List<String>,
    rightTitle: String,
    rightIcon: ImageVector,
    rightItems: List<String>,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BulletList(
            title = leftTitle,
            icon = leftIcon,
            items = leftItems,
            modifier = Modifier.weight(1f),
        )
        VerticalDividerLine(modifier = Modifier.heightIn(min = 82.dp))
        BulletList(
            title = rightTitle,
            icon = rightIcon,
            items = rightItems,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BulletList(
    title: String,
    icon: ImageVector,
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (items.isEmpty()) {
            Text(
                text = "-",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEach { item ->
                Text(
                    text = "- $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconFrame(icon)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun TerminalPanel(
    modifier: Modifier = Modifier,
    borderAccent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            ),
        border =
            BorderStroke(
                1.dp,
                if (borderAccent) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun TerminalHeader(
    title: String,
    icon: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(54.dp)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)),
                        RoundedCornerShape(14.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionTitle(
    icon: ImageVector,
    title: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconFrame(icon)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun IconFrame(icon: ImageVector) {
    Box(
        modifier =
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.52f))
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    RoundedCornerShape(12.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun PrimaryTerminalButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryTerminalButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    )
}

@Composable
private fun VerticalDividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
