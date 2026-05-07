package com.remodex.mobile.ui.agent

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import java.time.Duration

/** More than this many identical tool rows in a row → one collapsible group. */
internal const val TIMELINE_TOOL_GROUP_THRESHOLD = 3
private const val TIMELINE_ASSISTANT_CHUNK_TARGET_CHARS = 3_200
private const val TIMELINE_ASSISTANT_CHUNK_MIN_CHARS = 1_600

/**
 * Flat timeline row or a consecutive run of [CodexMessageKind.commandExecution] /
 * [CodexMessageKind.fileChange] (system) that was folded because the run length is **greater than**
 * [TIMELINE_TOOL_GROUP_THRESHOLD] (i.e. 4+ messages).
 */
internal sealed interface TimelineListItem {
    val stableKey: String

    data class Single(val message: CodexMessage) : TimelineListItem {
        override val stableKey: String get() = message.id
    }

    data class MessageChunk(
        val message: CodexMessage,
        val chunkIndex: Int,
        val chunkText: String,
        val isFirstChunk: Boolean,
        val isLastChunk: Boolean,
    ) : TimelineListItem {
        override val stableKey: String get() = "${message.id}-chunk-$chunkIndex"
    }

    data class AssistantWorkGroup(
        val groupKey: String,
        val messages: List<CodexMessage>,
        val duration: Duration?,
    ) : TimelineListItem {
        init {
            require(messages.isNotEmpty())
        }

        override val stableKey: String get() = groupKey
    }

    data class CommandExecutionGroup(val messages: List<CodexMessage>) : TimelineListItem {
        init {
            require(messages.isNotEmpty())
        }

        override val stableKey: String get() = "${messages.first().id}-cmd-group"
    }

    data class FileChangeGroup(val messages: List<CodexMessage>) : TimelineListItem {
        init {
            require(messages.isNotEmpty())
        }

        override val stableKey: String get() = "${messages.first().id}-fc-group"
    }
}

/**
 * Collapse long consecutive runs of command-execution or file-change system messages into
 * [TimelineListItem.CommandExecutionGroup] / [TimelineListItem.FileChangeGroup].
 */
internal fun List<CodexMessage>.toTimelineListItems(
    collapseLatestTurn: Boolean = true,
): List<TimelineListItem> {
    if (isEmpty()) return emptyList()
    val visibleMessages =
        filterNot { message ->
            message.role == CodexMessageRole.system && message.kind == CodexMessageKind.thinking
        }
    if (visibleMessages.isEmpty()) return emptyList()
    val out = ArrayList<TimelineListItem>(visibleMessages.size)
    var i = 0
    while (i < visibleMessages.size) {
        val msg = visibleMessages[i]
        val kind = msg.kind
        if (msg.role == CodexMessageRole.system &&
            (kind == CodexMessageKind.commandExecution || kind == CodexMessageKind.fileChange)
        ) {
            var j = i
            while (j + 1 < visibleMessages.size) {
                val next = visibleMessages[j + 1]
                if (next.role == CodexMessageRole.system && next.kind == kind) {
                    j++
                } else {
                    break
                }
            }
            val run = visibleMessages.subList(i, j + 1).toList()
            if (run.size > TIMELINE_TOOL_GROUP_THRESHOLD) {
                when (kind) {
                    CodexMessageKind.commandExecution ->
                        out += TimelineListItem.CommandExecutionGroup(run)
                    CodexMessageKind.fileChange ->
                        out += TimelineListItem.FileChangeGroup(run)
                    else -> error("unexpected kind in tool run")
                }
            } else {
                run.forEach { out += TimelineListItem.Single(it) }
            }
            i = j + 1
        } else {
            val chunks = msg.toAssistantTimelineChunks()
            if (chunks == null) {
                out += TimelineListItem.Single(msg)
            } else {
                out += chunks
            }
            i++
        }
    }
    val latestTurnId =
        if (collapseLatestTurn) {
            null
        } else {
            visibleMessages
                .lastOrNull { !it.turnId.isNullOrBlank() }
                ?.turnId
        }
    return out.collapseAssistantWorkGroups(excludedTurnId = latestTurnId)
}

private fun CodexMessage.toAssistantTimelineChunks(): List<TimelineListItem.MessageChunk>? {
    if (role != CodexMessageRole.assistant || kind != CodexMessageKind.chat) return null
    val chunks = splitAssistantMarkdownForTimeline(text)
    if (chunks.size <= 1) return null
    return chunks.mapIndexed { index, chunk ->
        TimelineListItem.MessageChunk(
            message = this,
            chunkIndex = index,
            chunkText = chunk,
            isFirstChunk = index == 0,
            isLastChunk = index == chunks.lastIndex,
        )
    }
}

internal fun splitAssistantMarkdownForTimeline(text: String): List<String> {
    if (text.length <= TIMELINE_ASSISTANT_CHUNK_TARGET_CHARS) return listOf(text)
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    var inFence = false
    var fenceMarker: Char? = null
    var fenceLength = 0

    fun flush() {
        val chunk = current.toString().trim()
        if (chunk.isNotBlank()) {
            chunks += chunk
        }
        current.clear()
    }

    text.lineSequence().forEach { line ->
        val trimmed = line.trimStart()
        val markerChar =
            when {
                trimmed.startsWith("```") -> '`'
                trimmed.startsWith("~~~") -> '~'
                else -> null
            }
        if (markerChar != null) {
            val markerRunLength = trimmed.takeWhile { it == markerChar }.length
            if (!inFence && markerRunLength >= 3) {
                inFence = true
                fenceMarker = markerChar
                fenceLength = markerRunLength
            } else if (inFence && markerChar == fenceMarker && markerRunLength >= fenceLength) {
                inFence = false
                fenceMarker = null
                fenceLength = 0
            }
        }

        if (current.isNotEmpty()) current.append('\n')
        current.append(line)

        val canSplit = !inFence && current.length >= TIMELINE_ASSISTANT_CHUNK_MIN_CHARS
        if (canSplit && (current.length >= TIMELINE_ASSISTANT_CHUNK_TARGET_CHARS || line.isBlank())) {
            flush()
        }
    }
    flush()
    return chunks.filter { it.isNotBlank() }.ifEmpty { listOf(text) }
}

private fun List<TimelineListItem>.collapseAssistantWorkGroups(excludedTurnId: String?): List<TimelineListItem> {
    if (size < 2) return this
    val turnItems =
        mapIndexedNotNull { index, item ->
            val messages = item.timelineMessages()
            val turnId = messages.firstNotNullOfOrNull { it.turnId?.takeIf(String::isNotBlank) }
                ?: return@mapIndexedNotNull null
            IndexedTurnItem(
                index = index,
                turnId = turnId,
                item = item,
                messages = messages,
            )
        }
    val hiddenIndexes = mutableSetOf<Int>()
    val workGroupsByInsertIndex = mutableMapOf<Int, TimelineListItem.AssistantWorkGroup>()
    turnItems
        .groupBy { it.turnId }
        .values
        .forEach { itemsForTurn ->
            if (itemsForTurn.firstOrNull()?.turnId == excludedTurnId) return@forEach
            val finalAssistantMessage = itemsForTurn.lastAssistantChatMessageOrNull() ?: return@forEach
            if (finalAssistantMessage.isStreaming) return@forEach
            val hiddenItems =
                itemsForTurn.filter { item ->
                    finalAssistantMessage.id !in item.messages.map { it.id } &&
                        item.item.isAssistantWorkItem()
                }
            if (hiddenItems.isEmpty()) return@forEach
            hiddenIndexes += hiddenItems.map { it.index }
            val workMessages = hiddenItems.flatMap { it.messages }
            val first = workMessages.minByOrNull { it.createdAt }
            val duration =
                if (first != null && finalAssistantMessage.createdAt >= first.createdAt) {
                    Duration.between(first.createdAt, finalAssistantMessage.createdAt)
                } else {
                    null
                }
            workGroupsByInsertIndex[hiddenItems.first().index] =
                TimelineListItem.AssistantWorkGroup(
                    groupKey = hiddenItems.first().item.stableKey,
                    messages = workMessages,
                    duration = duration,
                )
        }
    if (hiddenIndexes.isEmpty()) return this
    val out = ArrayList<TimelineListItem>(size)
    forEachIndexed { index, item ->
        workGroupsByInsertIndex[index]?.let { out += it }
        if (index !in hiddenIndexes) {
            out += item
        }
    }
    return out
}

private data class IndexedTurnItem(
    val index: Int,
    val turnId: String,
    val item: TimelineListItem,
    val messages: List<CodexMessage>,
)

private fun List<IndexedTurnItem>.lastAssistantChatMessageOrNull(): CodexMessage? =
    asReversed()
        .asSequence()
        .flatMap { indexedItem ->
            when (val item = indexedItem.item) {
                is TimelineListItem.MessageChunk ->
                    if (item.isLastChunk) {
                        sequenceOf(item.message)
                    } else {
                        emptySequence()
                    }
                else -> indexedItem.messages.asSequence()
            }
        }
        .firstOrNull { message ->
            message.role == CodexMessageRole.assistant &&
                message.kind == CodexMessageKind.chat
        }

private fun TimelineListItem.timelineMessages(): List<CodexMessage> =
    when (this) {
        is TimelineListItem.Single -> listOf(message)
        is TimelineListItem.MessageChunk -> listOf(message)
        is TimelineListItem.AssistantWorkGroup -> messages
        is TimelineListItem.CommandExecutionGroup -> messages
        is TimelineListItem.FileChangeGroup -> messages
    }

private fun TimelineListItem.isAssistantWorkItem(): Boolean =
    timelineMessages().any { message ->
        message.role == CodexMessageRole.assistant || message.role == CodexMessageRole.system
    }
