package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.TimelineBoundedCache
import com.remodex.mobile.core.model.TurnTimelineCacheKey

internal object TurnTimelineRichContentCache {
    private val mermaidMarkdown =
        TimelineBoundedCache<String, List<TurnMarkdownSegment>?>(maxEntries = 256)
    private val fileChanges =
        TimelineBoundedCache<String, TurnFileChangePresentation>(maxEntries = 256)
    private val unifiedPatches =
        TimelineBoundedCache<String, String>(maxEntries = 256)
    private val commands =
        TimelineBoundedCache<String, TurnCommandExecutionPresentation>(maxEntries = 256)
    private val subagents =
        TimelineBoundedCache<String, TurnSubagentPresentation>(maxEntries = 256)

    fun parseMermaidMarkdown(markdown: String): List<TurnMarkdownSegment>? =
        mermaidMarkdown.getOrPut(
            TurnTimelineCacheKey.textKey("mermaid-markdown", markdown),
        ) {
            TurnTimelineRichContentParser.parseMermaidMarkdown(markdown)
        }

    fun parseFileChange(message: CodexMessage): TurnFileChangePresentation =
        fileChanges.getOrPut(message.cacheKey("file-change")) {
            TurnTimelineRichContentParser.parseFileChange(message)
        }

    fun unifiedPatchForFileChangeMessage(message: CodexMessage): String =
        unifiedPatches.getOrPut(message.cacheKey("unified-patch")) {
            TurnTimelineRichContentParser.unifiedPatchForFileChangeMessage(message)
        }

    fun parseCommandExecution(message: CodexMessage): TurnCommandExecutionPresentation =
        commands.getOrPut(
            message.cacheKey("command-execution", variant = "streaming=${message.isStreaming}"),
        ) {
            TurnTimelineRichContentParser.parseCommandExecution(message)
        }

    fun parseSubagent(message: CodexMessage): TurnSubagentPresentation =
        subagents.getOrPut(
            message.cacheKey(
                "subagent",
                variant = "action=${message.subagentAction?.hashCode() ?: 0}",
            ),
        ) {
            TurnTimelineRichContentParser.parseSubagent(message)
        }

    fun clear() {
        mermaidMarkdown.clear()
        fileChanges.clear()
        unifiedPatches.clear()
        commands.clear()
        subagents.clear()
    }

    internal fun totalSizeForTests(): Int =
        mermaidMarkdown.size() +
            fileChanges.size() +
            unifiedPatches.size() +
            commands.size() +
            subagents.size()

    private fun CodexMessage.cacheKey(
        kind: String,
        variant: String? = null,
    ): String =
        TurnTimelineCacheKey.key(
            messageId = id,
            kind = "$kind:${this.kind.name}",
            text = text,
            variant = variant,
        )
}
