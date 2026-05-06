package com.remodex.mobile.ui.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remodex.mobile.R
import com.remodex.mobile.core.model.UsageStatusRefreshPolicy
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.ui.theme.AgentLightColors
import com.remodex.mobile.ui.theme.isAgentLightChrome

/**
 * Compact circular usage indicator (Swift [ContextWindowProgressRing]).
 * Tap opens the full usage sheet; ~28dp footprint (scaled with secondary bar icons).
 */
@Composable
internal fun TurnComposerUsageRing(
    threadId: String,
    repository: CodexRepository,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val openSheetCd = stringResource(R.string.cd_turn_usage_open_sheet)
    val ready by repository.isSessionReady.collectAsStateWithLifecycle()
    val conn by repository.connectionState.collectAsStateWithLifecycle()
    val contextUsageMap by repository.contextWindowUsageByThread.collectAsStateWithLifecycle()
    val contextLoading by repository.contextWindowUsageLoadingThreads.collectAsStateWithLifecycle()
    val hasResolvedRateLimits by repository.hasResolvedRateLimitsSnapshot.collectAsStateWithLifecycle()
    val usage = contextUsageMap[threadId]
    val ctxLoading = contextLoading.contains(threadId)
    val connected = conn is ConnectionState.Connected
    val shouldAutoRefresh =
        UsageStatusRefreshPolicy.shouldAutoRefresh(
            sessionReady = ready,
            connected = connected,
            threadId = threadId,
            contextWindowUsageByThread = contextUsageMap,
            hasResolvedRateLimitsSnapshot = hasResolvedRateLimits,
        )

    LaunchedEffect(threadId, shouldAutoRefresh) {
        if (!shouldAutoRefresh) return@LaunchedEffect
        runCatching { repository.refreshUsageStatus(threadId) }
    }

    if (sheetOpen) {
        ThreadUsageStatusBottomSheet(
            visible = true,
            onDismiss = { sheetOpen = false },
            threadId = threadId,
            repository = repository,
        )
    }

    val progress = usage?.fractionUsed?.toFloat()?.coerceIn(0f, 1f) ?: 0f
    val label =
        when {
            !ready || !connected -> "-"
            ctxLoading -> "..."
            usage == null -> "?"
            else -> "${usage.percentUsed.coerceIn(0, 100)}"
        }

    val agentLightChrome = isAgentLightChrome()
    val trackSoft =
        if (agentLightChrome) {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }

    Box(
        modifier =
            modifier
                .then(
                    if (agentLightChrome) {
                        Modifier.shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.05f),
                            spotColor = Color.Black.copy(alpha = 0.06f),
                        )
                    } else {
                        Modifier
                    },
                )
                .size(28.dp)
                .then(
                    if (agentLightChrome) {
                        Modifier.clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .background(AgentLightColors.Surface.copy(alpha = 0.94f), CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = { sheetOpen = true })
                .semantics { contentDescription = openSheetCd },
        contentAlignment = Alignment.Center,
    ) {
        if (ctxLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = trackSoft,
            )
        } else {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = trackSoft,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
