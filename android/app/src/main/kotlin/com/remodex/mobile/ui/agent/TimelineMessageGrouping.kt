package com.remodex.mobile.ui.agent

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole

/** More than this many identical tool rows in a row → one collapsible group. */
internal const val TIMELINE_TOOL_GROUP_THRESHOLD = 3

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
internal fun List<CodexMessage>.toTimelineListItems(): List<TimelineListItem> {
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
            out += TimelineListItem.Single(msg)
            i++
        }
    }
    return out
}
