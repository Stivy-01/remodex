package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageDeliveryState
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole

/**
 * Merges [thread/read] snapshots into the live timeline without duplicating stable rows.
 * Simplified vs iOS [CodexService.mergeHistoryMessages] but same keying idea.
 */
internal object HistoryMessageMerge {
    fun merge(
        existing: List<CodexMessage>,
        incoming: List<CodexMessage>,
    ): List<CodexMessage> {
        if (incoming.isEmpty()) return existing
        if (existing.isEmpty()) {
            return incoming
                .sortedBy { it.createdAt }
        }
        val merged = existing.toMutableList()
        val keys = merged.map { historyKey(it) }.toMutableSet()
        val additions = ArrayList<CodexMessage>()
        for (m in incoming.sortedBy { it.createdAt }) {
            if (m.role == CodexMessageRole.system &&
                m.kind == CodexMessageKind.thinking &&
                !m.turnId.isNullOrBlank()
            ) {
                val thinkingIndex =
                    merged.indexOfLast { existingMessage ->
                        existingMessage.role == CodexMessageRole.system &&
                            existingMessage.kind == CodexMessageKind.thinking &&
                            existingMessage.turnId == m.turnId
                    }
                if (thinkingIndex >= 0) {
                    val current = merged[thinkingIndex]
                    keys.remove(historyKey(current))
                    val next = mergeSystemItemDuplicate(current, m)
                    merged[thinkingIndex] = next
                    keys.add(historyKey(next))
                    continue
                }
            }

            if (m.role == CodexMessageRole.system &&
                m.kind == CodexMessageKind.fileChange &&
                !m.turnId.isNullOrBlank()
            ) {
                val mergeIndex =
                    merged.indexOfLast { existingMessage ->
                        existingMessage.role == CodexMessageRole.system &&
                            existingMessage.kind == CodexMessageKind.fileChange &&
                            existingMessage.turnId == m.turnId &&
                            shouldMergeAdjacentFileChangeRows(existingMessage, m)
                    }
                if (mergeIndex >= 0) {
                    val current = merged[mergeIndex]
                    keys.remove(historyKey(current))
                    val next = mergeFileChangeDuplicate(current, m)
                    merged[mergeIndex] = next
                    keys.add(historyKey(next))
                    continue
                }
            }

            if (m.role == CodexMessageRole.system &&
                m.kind == CodexMessageKind.commandExecution &&
                !m.turnId.isNullOrBlank()
            ) {
                val incomingCommandKey = normalizedCommandExecutionPreviewKey(m.text)
                val commandIndexByPreview =
                    incomingCommandKey?.let { key ->
                        merged.indexOfLast { existingMessage ->
                            existingMessage.role == CodexMessageRole.system &&
                                existingMessage.kind == CodexMessageKind.commandExecution &&
                                existingMessage.turnId == m.turnId &&
                                normalizedCommandExecutionPreviewKey(existingMessage.text) == key
                        }
                    } ?: -1
                val commandIndex =
                    if (commandIndexByPreview >= 0) {
                        commandIndexByPreview
                    } else {
                        merged.indexOfLast { existingMessage ->
                            existingMessage.role == CodexMessageRole.system &&
                                existingMessage.kind == CodexMessageKind.commandExecution &&
                                existingMessage.turnId == m.turnId &&
                                shouldReconcileCommandByTurn(existingMessage, m)
                        }
                    }
                if (commandIndex >= 0) {
                    val current = merged[commandIndex]
                    keys.remove(historyKey(current))
                    val next = mergeSystemItemDuplicate(current, m)
                    merged[commandIndex] = next
                    keys.add(historyKey(next))
                    continue
                }
            }

            val duplicateFileChangeIndex = merged.indexOfLast { existingMessage ->
                isCompatibleFileChangeDuplicate(existingMessage, m)
            }
            if (duplicateFileChangeIndex >= 0) {
                val current = merged[duplicateFileChangeIndex]
                keys.remove(historyKey(current))
                val next = mergeFileChangeDuplicate(current, m)
                merged[duplicateFileChangeIndex] = next
                keys.add(historyKey(next))
                continue
            }

            val duplicateUserIndex = merged.indexOfLast { existingMessage ->
                isCompatibleUserChatDuplicate(existingMessage, m)
            }
            if (duplicateUserIndex >= 0) {
                val current = merged[duplicateUserIndex]
                keys.remove(historyKey(current))
                val next = mergeUserChatDuplicate(current, m)
                merged[duplicateUserIndex] = next
                keys.add(historyKey(next))
                continue
            }

            val k = historyKey(m)
            if (k !in keys) {
                keys.add(k)
                additions.add(m)
            }
        }
        if (additions.isEmpty() && merged == existing) return existing
        return (merged + additions).dedupeCompatibleUserChats()
    }

    private fun historyKey(m: CodexMessage): String {
        val item = m.itemId?.trim()?.takeIf { it.isNotEmpty() }
        if (item != null) {
            return "item:${m.role}:${m.kind}:$item"
        }
        val normalizedText = normalizedMessageText(m.text)
        return "${m.role}|${m.kind}|${m.turnId}|${m.createdAt}|${normalizedText.length}:${normalizedText.hashCode()}"
    }

    private fun isCompatibleUserChatDuplicate(
        existing: CodexMessage,
        incoming: CodexMessage,
    ): Boolean {
        if (existing.role != CodexMessageRole.user || incoming.role != CodexMessageRole.user) return false
        if (existing.kind != CodexMessageKind.chat || incoming.kind != CodexMessageKind.chat) return false
        if (normalizedMessageText(existing.text) != normalizedMessageText(incoming.text)) return false
        if (!isUserChatMergeEligible(existing, incoming)) return false
        return compatibleAttachments(existing, incoming)
    }

    private fun isUserChatMergeEligible(
        existing: CodexMessage,
        incoming: CodexMessage,
    ): Boolean {
        val existingItem = existing.itemId?.trim()?.takeIf { it.isNotEmpty() }
        val incomingItem = incoming.itemId?.trim()?.takeIf { it.isNotEmpty() }
        if (existingItem != null && incomingItem != null) return existingItem == incomingItem

        val existingTurn = existing.turnId?.trim()?.takeIf { it.isNotEmpty() }
        val incomingTurn = incoming.turnId?.trim()?.takeIf { it.isNotEmpty() }
        if (existingTurn != null && incomingTurn != null) return existingTurn == incomingTurn

        val hasPendingLocal =
            existing.deliveryState == CodexMessageDeliveryState.pending ||
                incoming.deliveryState == CodexMessageDeliveryState.pending
        return hasPendingLocal && compatibleTurnIds(existing.turnId, incoming.turnId)
    }

    private fun mergeUserChatDuplicate(
        existing: CodexMessage,
        incoming: CodexMessage,
    ): CodexMessage =
        existing.copy(
            text = incoming.text.ifBlank { existing.text },
            turnId = incoming.turnId ?: existing.turnId,
            itemId = incoming.itemId ?: existing.itemId,
            isStreaming = false,
            deliveryState = CodexMessageDeliveryState.confirmed,
            attachments =
                when {
                    existing.attachments.isNotEmpty() -> existing.attachments
                    incoming.attachments.isNotEmpty() -> incoming.attachments
                    else -> emptyList()
                },
        )

    private fun mergeSystemItemDuplicate(
        existing: CodexMessage,
        incoming: CodexMessage,
    ): CodexMessage =
        existing.copy(
            text = mergeSnapshot(existing.text, incoming.text),
            turnId = incoming.turnId ?: existing.turnId,
            itemId = incoming.itemId ?: existing.itemId,
            isStreaming = false,
            deliveryState = CodexMessageDeliveryState.confirmed,
        )

    private fun isCompatibleFileChangeDuplicate(
        existing: CodexMessage,
        incoming: CodexMessage,
    ): Boolean {
        if (existing.role != CodexMessageRole.system || incoming.role != CodexMessageRole.system) return false
        if (existing.kind != CodexMessageKind.fileChange || incoming.kind != CodexMessageKind.fileChange) return false
        val existingItem = existing.itemId?.trim()?.takeIf { it.isNotEmpty() }
        val incomingItem = incoming.itemId?.trim()?.takeIf { it.isNotEmpty() }
        if (existingItem != null && incomingItem != null) return existingItem == incomingItem
        return compatibleTurnIds(existing.turnId, incoming.turnId) &&
            (isFileChangePlaceholder(existing.text) || isFileChangePlaceholder(incoming.text))
    }

    private fun shouldMergeAdjacentFileChangeRows(
        existing: CodexMessage,
        incoming: CodexMessage,
    ): Boolean =
        existing.isStreaming ||
            incoming.isStreaming ||
            isFileChangePlaceholder(existing.text) ||
            isFileChangePlaceholder(incoming.text)

    private fun mergeFileChangeDuplicate(
        existing: CodexMessage,
        incoming: CodexMessage,
    ): CodexMessage {
        val existingStructured = isStructuredFileChange(existing.text)
        val incomingStructured = isStructuredFileChange(incoming.text)
        val text =
            when {
                existingStructured && isFileChangePlaceholder(incoming.text) -> existing.text
                incomingStructured && isFileChangePlaceholder(existing.text) -> incoming.text
                incomingStructured && !existingStructured -> incoming.text
                existingStructured && !incomingStructured -> existing.text
                incoming.text.isNotBlank() -> incoming.text
                else -> existing.text
            }
        return existing.copy(
            text = text,
            turnId = incoming.turnId ?: existing.turnId,
            itemId = incoming.itemId ?: existing.itemId,
            isStreaming = false,
        )
    }

    private fun List<CodexMessage>.dedupeCompatibleUserChats(): List<CodexMessage> {
        if (size < 2) return this
        val result = mutableListOf<CodexMessage>()
        for (message in this) {
            val duplicateIndex =
                result.indexOfLast { candidate ->
                    isCompatibleUserChatDuplicate(candidate, message)
                }
            if (duplicateIndex >= 0) {
                result[duplicateIndex] = mergeUserChatDuplicate(result[duplicateIndex], message)
            } else {
                result.add(message)
            }
        }
        return result
    }

    private fun compatibleTurnIds(
        existing: String?,
        incoming: String?,
    ): Boolean {
        val existingTurn = existing?.trim()?.takeIf { it.isNotEmpty() }
        val incomingTurn = incoming?.trim()?.takeIf { it.isNotEmpty() }
        return existingTurn == null || incomingTurn == null || existingTurn == incomingTurn
    }

    private fun compatibleAttachments(
        existing: CodexMessage,
        incoming: CodexMessage,
    ): Boolean =
        existing.attachments == incoming.attachments ||
            existing.attachments.isEmpty() ||
            incoming.attachments.isEmpty()

    private fun normalizedMessageText(text: String): String =
        text.trim().replace("\\s+".toRegex(), " ")

    private fun isFileChangePlaceholder(text: String): Boolean =
        text.trim().equals("[file change]", ignoreCase = true) ||
            text.trim().equals("file change", ignoreCase = true)

    private fun isStructuredFileChange(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.contains("\nPath:", ignoreCase = true) || t.startsWith("Path:", ignoreCase = true)) return true
        if (t.contains("\nTotals:", ignoreCase = true) || t.startsWith("Totals:", ignoreCase = true)) return true
        if (t.contains("```diff", ignoreCase = true)) return true
        if (t.contains("diff --git")) return true
        return false
    }

    private fun shouldReconcileCommandByTurn(
        existing: CodexMessage,
        incoming: CodexMessage,
    ): Boolean {
        val existingItem = existing.itemId?.trim()?.takeIf { it.isNotEmpty() }
        val incomingItem = incoming.itemId?.trim()?.takeIf { it.isNotEmpty() }
        return existing.isStreaming ||
            incoming.isStreaming ||
            existingItem == null ||
            incomingItem == null ||
            isSyntheticTurnScopedItemId(existingItem) ||
            isSyntheticTurnScopedItemId(incomingItem)
    }

    private fun isSyntheticTurnScopedItemId(itemId: String?): Boolean =
        itemId?.startsWith("turn:", ignoreCase = true) == true &&
            itemId.contains("|kind:", ignoreCase = true)

    private fun normalizedCommandExecutionPreviewKey(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val withoutPhase =
            trimmed.replaceFirst(
                Regex("""^(running|completed|failed|stopped)\s*>?\s*""", RegexOption.IGNORE_CASE),
                "",
            )
        val commandTokens = withoutPhase.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val normalized =
            commandTokens
                .joinToString(" ") { token -> token.trim().trim('"', '\'') }
                .replace("\\s+".toRegex(), " ")
                .lowercase()
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun mergeSnapshot(
        existing: String,
        incoming: String,
    ): String {
        if (existing.isEmpty()) return incoming
        if (incoming.isEmpty()) return existing
        if (incoming == existing) return existing
        if (existing.endsWith(incoming)) return existing
        if (incoming.length > existing.length && incoming.startsWith(existing)) return incoming
        if (existing.length > incoming.length && existing.startsWith(incoming)) return existing
        return incoming
    }
}
