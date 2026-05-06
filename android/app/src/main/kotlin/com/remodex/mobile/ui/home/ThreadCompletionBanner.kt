package com.remodex.mobile.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.mobile.ui.theme.AgentLightColors
import com.remodex.mobile.ui.theme.RemodexGitAddition
import com.remodex.mobile.ui.theme.isAgentLightChrome

@Immutable
enum class GitActionProgressPhase {
    resolvingCommitMessage,
    committing,
    pushing,
    preparingPullRequest,
    done;

    val title: String
        get() =
            when (this) {
                resolvingCommitMessage -> "Committing..."
                committing -> "Committing..."
                pushing -> "Pushing..."
                preparingPullRequest -> "Preparing PR..."
                done -> "Done"
            }
}

@Immutable
data class GitActionProgressBannerState(
    val phase: GitActionProgressPhase,
    val includesPullRequest: Boolean = false,
) {
    val title: String get() = phase.title

    val steps: List<GitActionProgressStep>
        get() =
            buildList {
                add(
                    GitActionProgressStep(
                        text =
                            if (phase == GitActionProgressPhase.resolvingCommitMessage) {
                                "Preparing commit message..."
                            } else {
                                "Commit message ready"
                            },
                        status =
                            when (phase) {
                                GitActionProgressPhase.resolvingCommitMessage -> GitActionProgressStepStatus.active
                                else -> GitActionProgressStepStatus.complete
                            },
                    ),
                )
                add(
                    GitActionProgressStep(
                        text =
                            if (phase == GitActionProgressPhase.committing) {
                                "Committing..."
                            } else {
                                "Committed"
                            },
                        status =
                            when (phase) {
                                GitActionProgressPhase.resolvingCommitMessage -> GitActionProgressStepStatus.pending
                                GitActionProgressPhase.committing -> GitActionProgressStepStatus.active
                                else -> GitActionProgressStepStatus.complete
                            },
                    ),
                )
                add(
                    GitActionProgressStep(
                        text =
                            if (phase == GitActionProgressPhase.pushing) {
                                "Pushing..."
                            } else {
                                "Pushed"
                            },
                        status =
                            when (phase) {
                                GitActionProgressPhase.resolvingCommitMessage,
                                GitActionProgressPhase.committing,
                                -> GitActionProgressStepStatus.pending
                                GitActionProgressPhase.pushing -> GitActionProgressStepStatus.active
                                else -> GitActionProgressStepStatus.complete
                            },
                    ),
                )
                if (includesPullRequest) {
                    add(
                        GitActionProgressStep(
                            text =
                                if (phase == GitActionProgressPhase.preparingPullRequest) {
                                    "Preparing pull request..."
                                } else {
                                    "Pull request ready"
                                },
                            status =
                                when (phase) {
                                    GitActionProgressPhase.resolvingCommitMessage,
                                    GitActionProgressPhase.committing,
                                    GitActionProgressPhase.pushing,
                                    -> GitActionProgressStepStatus.pending
                                    GitActionProgressPhase.preparingPullRequest -> GitActionProgressStepStatus.active
                                    GitActionProgressPhase.done -> GitActionProgressStepStatus.complete
                                },
                        ),
                    )
                }
            }
}

@Immutable
data class GitActionProgressStep(
    val text: String,
    val status: GitActionProgressStepStatus,
)

@Immutable
enum class GitActionProgressStepStatus {
    complete,
    active,
    pending,
}

/**
 * Top banner when a thread completes (parity with [ThreadCompletionBannerView.swift](CodexMobile/CodexMobile/Views/Home/ThreadCompletionBannerView.swift)).
 * Wire [bannerMessage] from repository/service when Android exposes `threadCompletionBanner`.
 */
@Composable
fun ThreadCompletionBanner(
    bannerMessage: String?,
    gitProgress: GitActionProgressBannerState? = null,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = gitProgress != null || !bannerMessage.isNullOrBlank()
    var displayedGitProgress by remember { mutableStateOf<GitActionProgressBannerState?>(null) }
    var displayedBannerMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(gitProgress, bannerMessage) {
        if (gitProgress != null || !bannerMessage.isNullOrBlank()) {
            displayedGitProgress = gitProgress
            displayedBannerMessage = bannerMessage
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically(animationSpec = tween(220)) { -it / 3 } + fadeOut(animationSpec = tween(220)),
        modifier = modifier,
    ) {
        val progress = displayedGitProgress
        if (progress != null) {
            GitActionProgressBanner(progress = progress, modifier = Modifier.fillMaxWidth())
            return@AnimatedVisibility
        }
        val msg = displayedBannerMessage ?: return@AnimatedVisibility
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = bannerSurfaceColor(),
            tonalElevation = if (isAgentLightChrome()) 0.dp else 2.dp,
            shadowElevation = if (isAgentLightChrome()) 8.dp else 3.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onTap)
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
private fun GitActionProgressBanner(
    progress: GitActionProgressBannerState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .widthIn(max = 430.dp),
        shape = RoundedCornerShape(15.dp),
        color = bannerSurfaceColor(),
        tonalElevation = if (isAgentLightChrome()) 0.dp else 2.dp,
        shadowElevation = if (isAgentLightChrome()) 10.dp else 4.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Crossfade(
                targetState = progress.phase == GitActionProgressPhase.done,
                label = "git-progress-indicator",
            ) { isDone ->
                if (isDone) {
                    Box(
                        modifier =
                            Modifier
                                .padding(top = 1.dp)
                                .size(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✓",
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = RemodexGitAddition,
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .padding(top = 2.dp)
                                .size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = progress.title,
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                progress.steps.forEach { step ->
                    GitActionProgressStepRow(step = step)
                }
            }
        }
    }
}

@Composable
private fun GitActionProgressStepRow(step: GitActionProgressStep) {
    val statusText =
        when (step.status) {
            GitActionProgressStepStatus.complete -> "✓"
            GitActionProgressStepStatus.active -> "•"
            GitActionProgressStepStatus.pending -> " "
        }
    val contentColor =
        when (step.status) {
            GitActionProgressStepStatus.complete -> MaterialTheme.colorScheme.onSurfaceVariant
            GitActionProgressStepStatus.active -> MaterialTheme.colorScheme.onSurface
            GitActionProgressStepStatus.pending -> MaterialTheme.colorScheme.outlineVariant
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(width = 10.dp, height = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (step.status == GitActionProgressStepStatus.complete) {
                        RemodexGitAddition
                    } else {
                        contentColor
                    },
            )
        }
        Text(
            text = step.text,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            color = contentColor,
        )
    }
}

@Composable
private fun bannerSurfaceColor(): Color =
    if (isAgentLightChrome()) {
        AgentLightColors.Surface.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    }
