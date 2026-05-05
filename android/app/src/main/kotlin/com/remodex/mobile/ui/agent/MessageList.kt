package com.remodex.mobile.ui.agent

import android.content.ClipData
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.AIChangeSet
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CommandExecutionDetails
import com.remodex.mobile.ui.turn.TurnMessageRow
import com.remodex.mobile.ui.turn.TurnTimelineGroupedRunsRow
import kotlinx.coroutines.launch

/**
 * Scrollable conversation timeline. SwiftUI: `ScrollView` + `LazyVStack` pattern.
 */
@Composable
fun MessageList(
    messages: List<CodexMessage>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    hiddenEarlierCount: Int = 0,
    canLoadOlderRemoteHistory: Boolean = false,
    isLoadingOlderHistory: Boolean = false,
    olderHistoryError: String? = null,
    onLoadEarlierMessages: (() -> Unit)? = null,
    commandExecutionDetailsByItemId: Map<String, CommandExecutionDetails> = emptyMap(),
    verticalItemSpacing: Dp = 10.dp,
    contentPadding: PaddingValues =
        PaddingValues(top = 10.dp, bottom = 18.dp, start = 2.dp, end = 2.dp),
    emptyContent: @Composable BoxScope.() -> Unit = {
        Text(
            text = stringResource(R.string.turn_timeline_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 24.dp),
        )
    },
    messageContent: @Composable (CodexMessage) -> Unit = { msg ->
        when (msg.role) {
            CodexMessageRole.user ->
                UserMessageBubble(message = msg)
            CodexMessageRole.assistant ->
                AssistantMessageBlock(message = msg)
            CodexMessageRole.system ->
                TurnMessageRow(
                    message = msg,
                    commandExecutionDetails = msg.itemId?.let { commandExecutionDetailsByItemId[it] },
                )
        }
    },
    /** When non-null, shows copy + fork icon row under the last assistant message (no card container). */
    onForkThread: (() -> Unit)? = null,
    forkThreadEnabled: Boolean = false,
    assistantUndoChangeSetsByMessageId: Map<String, AIChangeSet> = emptyMap(),
    applyingUndoChangeSetIds: Set<String> = emptySet(),
    onUndoAssistantChanges: ((AIChangeSet) -> Unit)? = null,
) {
    val lastAssistantIndex = messages.indexOfLast { it.role == CodexMessageRole.assistant }
    val showTrailingActions = lastAssistantIndex >= 0 && onForkThread != null
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val timelineItems = remember(messages) { messages.toTimelineListItems() }
    val lastAssistantDisplayIndex =
        remember(timelineItems) {
            timelineItems.indexOfLast { item ->
                item is TimelineListItem.Single && item.message.role == CodexMessageRole.assistant
            }
        }
    val lastAssistantText =
        if (lastAssistantIndex >= 0) {
            messages[lastAssistantIndex].text.trim()
        } else {
            ""
        }
    val lastAssistantChangeSet =
        if (lastAssistantIndex >= 0) {
            assistantUndoChangeSetsByMessageId[messages[lastAssistantIndex].id]
        } else {
            null
        }
    val copyEnabled = lastAssistantText.isNotEmpty()
    val copyActionLabel = stringResource(R.string.turn_message_action_copy_cd)
    val forkActionLabel = stringResource(R.string.turn_message_action_fork_cd)
    val undoActionLabel = stringResource(R.string.turn_message_action_undo_changes)

    Box(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                emptyContent()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(verticalItemSpacing),
                contentPadding = contentPadding,
            ) {
                if ((hiddenEarlierCount > 0 || canLoadOlderRemoteHistory) && onLoadEarlierMessages != null) {
                    item(key = "load-earlier-messages") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TextButton(
                                onClick = onLoadEarlierMessages,
                                enabled = !isLoadingOlderHistory,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (isLoadingOlderHistory) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        if (hiddenEarlierCount > 0) {
                                            stringResource(R.string.turn_timeline_load_earlier_messages, hiddenEarlierCount)
                                        } else {
                                            stringResource(R.string.turn_timeline_load_older_history)
                                        },
                                    )
                                }
                            }
                            olderHistoryError?.takeIf { it.isNotBlank() }?.let { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }
                    }
                }
                itemsIndexed(
                    items = timelineItems,
                    key = { _, item -> item.stableKey },
                    contentType = { _, item ->
                        when (item) {
                            is TimelineListItem.Single -> item.message.role
                            is TimelineListItem.CommandExecutionGroup -> "timeline_cmd_group"
                            is TimelineListItem.FileChangeGroup -> "timeline_fc_group"
                        }
                    },
                ) { index, item ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        when (item) {
                            is TimelineListItem.Single ->
                                messageContent(item.message)
                            is TimelineListItem.CommandExecutionGroup ->
                                TurnTimelineGroupedRunsRow(
                                    groupKey = item.stableKey,
                                    collapsedTitle =
                                        stringResource(R.string.turn_timeline_group_executed_commands),
                                    messages = item.messages,
                                    commandExecutionDetailsByItemId = commandExecutionDetailsByItemId,
                                )
                            is TimelineListItem.FileChangeGroup ->
                                TurnTimelineGroupedRunsRow(
                                    groupKey = item.stableKey,
                                    collapsedTitle =
                                        stringResource(R.string.turn_timeline_group_modified_files),
                                    messages = item.messages,
                                    commandExecutionDetailsByItemId = commandExecutionDetailsByItemId,
                                )
                        }
                        if (showTrailingActions && index == lastAssistantDisplayIndex) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 2.dp, top = 2.dp, bottom = 0.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (copyEnabled) {
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipData.newPlainText(
                                                        copyActionLabel,
                                                        lastAssistantText,
                                                    ).toClipEntry(),
                                                )
                                            }
                                        }
                                    },
                                    enabled = copyEnabled,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = copyActionLabel,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = { onForkThread?.invoke() },
                                    enabled = forkThreadEnabled,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountTree,
                                        contentDescription = forkActionLabel,
                                        tint =
                                            if (forkThreadEnabled) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                        },
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        lastAssistantChangeSet?.let { onUndoAssistantChanges?.invoke(it) }
                                    },
                                    enabled =
                                        lastAssistantChangeSet != null &&
                                            onUndoAssistantChanges != null &&
                                            lastAssistantChangeSet.id !in applyingUndoChangeSetIds,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                                        contentDescription = undoActionLabel,
                                        tint =
                                            if (
                                                lastAssistantChangeSet != null &&
                                                lastAssistantChangeSet.id !in applyingUndoChangeSetIds
                                            ) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
