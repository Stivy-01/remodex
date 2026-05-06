package com.remodex.mobile.ui.turn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexRateLimitBucket
import com.remodex.mobile.core.model.CodexRateLimitDisplayRow
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.data.CodexRepository
import kotlinx.coroutines.launch

/**
 * J.7d: compact read-only usage (context window + account rate limit rows) above the git branch
 * row; state mirrors Settings and updates from repository flows + manual refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TurnComposerUsageStrip(
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
    val contextErrors by repository.contextWindowUsageErrorByThread.collectAsStateWithLifecycle()
    val buckets by repository.rateLimitBuckets.collectAsStateWithLifecycle()
    val isLoadingRL by repository.isLoadingRateLimits.collectAsStateWithLifecycle()
    val rlErr by repository.rateLimitsErrorMessage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val refreshCd = stringResource(R.string.turn_usage_strip_refresh_cd)
    val loadingCd = stringResource(R.string.cd_usage_combined_usage_loading)
    val displayRows = remember(buckets) { CodexRateLimitBucket.visibleDisplayRows(buckets) }
    val usage: ContextWindowUsage? = contextUsageMap[threadId]
    val ctxLoading = contextLoading.contains(threadId)
    val ctxErr = contextErrors[threadId]
    val connected = conn is ConnectionState.Connected
    val anyLoading = ctxLoading || isLoadingRL
    val canRefresh = ready && connected && !anyLoading

    if (sheetOpen) {
        ThreadUsageStatusBottomSheet(
            visible = true,
            onDismiss = { sheetOpen = false },
            threadId = threadId,
            repository = repository,
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(onClick = { sheetOpen = true })
                        .semantics { contentDescription = openSheetCd },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!ready || !connected) {
                    Text(
                        text = stringResource(R.string.usage_rate_limits_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ContextWindowStripLines(
                        usage = usage,
                        loading = ctxLoading,
                        error = ctxErr,
                    )
                    RateLimitStripLines(
                        displayRows = displayRows,
                        isLoading = isLoadingRL,
                        error = rlErr,
                    )
                }
            }
            if (anyLoading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .semantics { contentDescription = loadingCd },
                    strokeWidth = 2.dp,
                )
            }
            IconButton(
                onClick = {
                    scope.launch {
                        runCatching { repository.refreshContextWindowUsage(threadId) }
                        runCatching { repository.refreshRateLimits() }
                    }
                },
                enabled = canRefresh,
                modifier = Modifier.semantics { contentDescription = refreshCd },
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun ContextWindowStripLines(
    usage: ContextWindowUsage?,
    loading: Boolean,
    error: String?,
) {
    when {
        loading -> {
            Text(
                text = stringResource(R.string.turn_usage_strip_context_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error != null -> {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        usage == null -> {
            Text(
                text = stringResource(R.string.usage_context_window_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text =
                        stringResource(
                            R.string.usage_context_window_tokens,
                            usage.tokensUsedFormatted,
                            usage.tokenLimitFormatted,
                            usage.percentUsed,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(
                    progress = { usage.fractionUsed.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RateLimitStripLines(
    displayRows: List<CodexRateLimitDisplayRow>,
    isLoading: Boolean,
    error: String?,
) {
    when {
        displayRows.isNotEmpty() -> {
            displayRows.take(2).forEach { row ->
                Text(
                    text =
                        stringResource(
                            R.string.turn_usage_strip_limit_line,
                            row.label,
                            row.window.clampedUsedPercent,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        isLoading -> {
            Text(
                text = stringResource(R.string.turn_usage_strip_limits_loading),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error != null -> {
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        else -> {
            Text(
                text = stringResource(R.string.turn_usage_strip_limits_none),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
