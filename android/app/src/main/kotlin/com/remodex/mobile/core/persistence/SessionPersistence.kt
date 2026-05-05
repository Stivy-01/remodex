package com.remodex.mobile.core.persistence

import android.content.Context
import com.remodex.mobile.core.model.CODEX_SECURE_PROTOCOL_VERSION
import com.remodex.mobile.core.model.CodexPairingQRPayload
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexThreadSyncState
import com.remodex.mobile.core.security.CodexSecureKeys
import com.remodex.mobile.core.security.SecureStore
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Aggregates saved relay pairing and lightweight UI session hints (e.g. last open thread).
 * Relay fields mirror [CodexService.swift](CodexMobile/CodexMobile/Services/CodexService.swift) SecureStore usage;
 * iOS does not persist activeThreadId in Keychain; we keep it in private prefs for Android UX only.
 */
class SessionPersistence(
    private val secureStore: SecureStore,
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "remodex_session_state",
            Context.MODE_PRIVATE,
        )

    fun loadRelaySnapshot(): RelaySessionSnapshot =
        RelaySessionSnapshot(
            relaySessionId = secureStore.readString(CodexSecureKeys.relaySessionId),
            relayUrl = secureStore.readString(CodexSecureKeys.relayUrl),
            relayMacDeviceId = secureStore.readString(CodexSecureKeys.relayMacDeviceId),
            relayMacIdentityPublicKey = secureStore.readString(CodexSecureKeys.relayMacIdentityPublicKey),
            relayProtocolVersion = secureStore.readString(CodexSecureKeys.relayProtocolVersion),
            relayLastAppliedBridgeOutboundSeq =
                secureStore.readString(CodexSecureKeys.relayLastAppliedBridgeOutboundSeq),
            lastTrustedMacDeviceId = secureStore.readString(CodexSecureKeys.lastTrustedMacDeviceId),
        )

    fun saveRelaySnapshot(snapshot: RelaySessionSnapshot) {
        writeOrRemove(CodexSecureKeys.relaySessionId, snapshot.relaySessionId)
        writeOrRemove(CodexSecureKeys.relayUrl, snapshot.relayUrl)
        writeOrRemove(CodexSecureKeys.relayMacDeviceId, snapshot.relayMacDeviceId)
        writeOrRemove(CodexSecureKeys.relayMacIdentityPublicKey, snapshot.relayMacIdentityPublicKey)
        writeOrRemove(CodexSecureKeys.relayProtocolVersion, snapshot.relayProtocolVersion)
        writeOrRemove(
            CodexSecureKeys.relayLastAppliedBridgeOutboundSeq,
            snapshot.relayLastAppliedBridgeOutboundSeq,
        )
        writeOrRemove(CodexSecureKeys.lastTrustedMacDeviceId, snapshot.lastTrustedMacDeviceId)
    }

    fun clearRelaySession() {
        secureStore.deleteValue(CodexSecureKeys.relaySessionId)
        secureStore.deleteValue(CodexSecureKeys.relayUrl)
        secureStore.deleteValue(CodexSecureKeys.relayMacDeviceId)
        secureStore.deleteValue(CodexSecureKeys.relayMacIdentityPublicKey)
        secureStore.deleteValue(CodexSecureKeys.relayProtocolVersion)
        secureStore.deleteValue(CodexSecureKeys.relayLastAppliedBridgeOutboundSeq)
        setForceQrBootstrapOnNextHandshake(false)
    }

    /**
     * After a fresh QR scan, the next handshake must use QR bootstrap (parity with iOS
     * `shouldForceQRBootstrapOnNextHandshake`).
     */
    fun shouldForceQrBootstrapOnNextHandshake(): Boolean =
        prefs.getBoolean(KEY_FORCE_QR_BOOTSTRAP, false)

    fun setForceQrBootstrapOnNextHandshake(value: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_QR_BOOTSTRAP, value).apply()
    }

    /** Applies QR payload into the encrypted store and flags the next secure handshake as QR bootstrap. */
    fun applyPairingPayload(
        payload: CodexPairingQRPayload,
        secureStore: SecureStore,
    ) {
        secureStore.writeString(CodexSecureKeys.relaySessionId, payload.sessionId)
        secureStore.writeString(CodexSecureKeys.relayUrl, payload.relay)
        secureStore.writeString(CodexSecureKeys.relayMacDeviceId, payload.macDeviceId)
        secureStore.writeString(CodexSecureKeys.relayMacIdentityPublicKey, payload.macIdentityPublicKey)
        secureStore.writeString(
            CodexSecureKeys.relayProtocolVersion,
            CODEX_SECURE_PROTOCOL_VERSION.toString(),
        )
        secureStore.writeString(CodexSecureKeys.relayLastAppliedBridgeOutboundSeq, "0")
        setForceQrBootstrapOnNextHandshake(true)
    }

    fun loadLastActiveThreadId(): String? =
        prefs.getString(KEY_LAST_ACTIVE_THREAD, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun loadLocalRelayHostOverride(): String? =
        prefs.getString(KEY_LOCAL_RELAY_HOST_OVERRIDE, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun saveLocalRelayHostOverride(value: String?) {
        prefs.edit().putOrRemove(KEY_LOCAL_RELAY_HOST_OVERRIDE, value?.trim()?.takeIf { it.isNotEmpty() }).apply()
    }

    fun saveLastActiveThreadId(threadId: String?) {
        if (threadId.isNullOrBlank()) {
            prefs.edit().remove(KEY_LAST_ACTIVE_THREAD).apply()
        } else {
            prefs.edit().putString(KEY_LAST_ACTIVE_THREAD, threadId.trim()).apply()
        }
    }

    fun loadAssociatedManagedWorktreePaths(): Map<String, String> =
        prefs.getStringSet(KEY_ASSOCIATED_MANAGED_WORKTREES, emptySet()).orEmpty()
            .mapNotNull(::decodeManagedWorktreeEntry)
            .toMap()

    fun saveAssociatedManagedWorktreePath(
        threadId: String,
        path: String,
    ) {
        val tid = threadId.trim()
        val normalizedPath = path.trim()
        if (tid.isEmpty() || normalizedPath.isEmpty()) return
        val existing = loadAssociatedManagedWorktreePaths().toMutableMap()
        existing[tid] = normalizedPath
        prefs.edit()
            .putStringSet(KEY_ASSOCIATED_MANAGED_WORKTREES, existing.map(::encodeManagedWorktreeEntry).toSet())
            .apply()
    }

    fun removeAssociatedManagedWorktreePath(threadId: String) {
        val tid = threadId.trim()
        if (tid.isEmpty()) return
        val existing = loadAssociatedManagedWorktreePaths().toMutableMap()
        if (existing.remove(tid) != null) {
            prefs.edit()
                .putStringSet(KEY_ASSOCIATED_MANAGED_WORKTREES, existing.map(::encodeManagedWorktreeEntry).toSet())
                .apply()
        }
    }

    fun loadRuntimeSelection(): RuntimeSelectionSnapshot =
        RuntimeSelectionSnapshot(
            selectedModelId = prefs.getString(KEY_RUNTIME_MODEL_ID, null)?.trim()?.takeIf { it.isNotEmpty() },
            selectedReasoningEffort =
                prefs.getString(KEY_RUNTIME_REASONING_EFFORT, null)?.trim()?.takeIf { it.isNotEmpty() },
            selectedAccessMode =
                prefs.getString(KEY_RUNTIME_ACCESS_MODE, null)?.trim()?.takeIf { it.isNotEmpty() },
            selectedServiceTier =
                prefs.getString(KEY_RUNTIME_SERVICE_TIER, null)?.trim()?.takeIf { it.isNotEmpty() },
        )

    fun saveRuntimeSelection(snapshot: RuntimeSelectionSnapshot) {
        prefs.edit()
            .putOrRemove(KEY_RUNTIME_MODEL_ID, snapshot.selectedModelId)
            .putOrRemove(KEY_RUNTIME_REASONING_EFFORT, snapshot.selectedReasoningEffort)
            .putOrRemove(KEY_RUNTIME_ACCESS_MODE, snapshot.selectedAccessMode)
            .putOrRemove(KEY_RUNTIME_SERVICE_TIER, snapshot.selectedServiceTier)
            .apply()
    }

    fun loadThreadRenames(): Map<String, String> =
        prefs.getStringSet(KEY_THREAD_RENAMES, emptySet()).orEmpty()
            .mapNotNull(::decodeThreadRenameEntry)
            .toMap()

    fun saveThreadRename(
        threadId: String,
        name: String,
    ) {
        val tid = threadId.trim()
        val trimmedName = name.trim()
        if (tid.isEmpty() || trimmedName.isEmpty()) return
        val existing = loadThreadRenames().toMutableMap()
        existing[tid] = trimmedName
        prefs.edit()
            .putStringSet(KEY_THREAD_RENAMES, existing.map(::encodeThreadRenameEntry).toSet())
            .apply()
    }

    fun removeThreadRename(threadId: String) {
        val tid = threadId.trim()
        if (tid.isEmpty()) return
        val existing = loadThreadRenames().toMutableMap()
        if (existing.remove(tid) != null) {
            prefs.edit()
                .putStringSet(KEY_THREAD_RENAMES, existing.map(::encodeThreadRenameEntry).toSet())
                .apply()
        }
    }

    fun loadLocallyDeletedThreadIds(): Set<String> =
        prefs.getStringSet(KEY_LOCALLY_DELETED_THREAD_IDS, emptySet()).orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun addLocallyDeletedThreadId(threadId: String) {
        val tid = threadId.trim()
        if (tid.isEmpty()) return
        val existing = loadLocallyDeletedThreadIds().toMutableSet()
        if (existing.add(tid)) {
            prefs.edit().putStringSet(KEY_LOCALLY_DELETED_THREAD_IDS, existing).apply()
        }
    }

    fun loadLocallyArchivedThreadIds(): Set<String> =
        prefs.getStringSet(KEY_LOCALLY_ARCHIVED_THREAD_IDS, emptySet()).orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun addLocallyArchivedThreadId(threadId: String) {
        val tid = threadId.trim()
        if (tid.isEmpty()) return
        val existing = loadLocallyArchivedThreadIds().toMutableSet()
        if (existing.add(tid)) {
            prefs.edit().putStringSet(KEY_LOCALLY_ARCHIVED_THREAD_IDS, existing).apply()
        }
    }

    fun removeLocallyArchivedThreadId(threadId: String) {
        val tid = threadId.trim()
        if (tid.isEmpty()) return
        val existing = loadLocallyArchivedThreadIds().toMutableSet()
        if (existing.remove(tid)) {
            prefs.edit().putStringSet(KEY_LOCALLY_ARCHIVED_THREAD_IDS, existing).apply()
        }
    }

    fun loadCachedThreads(): List<CodexThread> =
        prefs.getStringSet(KEY_CACHED_THREADS, emptySet()).orEmpty()
            .mapNotNull(::decodeCachedThreadEntry)
            .sortedWith(
                compareByDescending<CachedThreadEntry> { it.sortInstant ?: Instant.EPOCH }
                    .thenBy { it.index },
            )
            .map { it.thread }

    fun saveCachedThreads(threads: List<CodexThread>) {
        val entries =
            threads
                .take(MAX_CACHED_THREADS)
                .mapIndexedNotNull(::encodeCachedThreadEntry)
                .toSet()
        prefs.edit().putStringSet(KEY_CACHED_THREADS, entries).apply()
    }

    private fun writeOrRemove(
        key: String,
        value: String?,
    ) {
        if (value.isNullOrBlank()) {
            secureStore.deleteValue(key)
        } else {
            secureStore.writeString(key, value)
        }
    }

    private companion object {
        const val KEY_LAST_ACTIVE_THREAD = "codex.ui.lastActiveThreadId"
        const val KEY_FORCE_QR_BOOTSTRAP = "remodex.secure.forceQrBootstrap"
        const val KEY_LOCAL_RELAY_HOST_OVERRIDE = "remodex.localRelayHostOverride"
        const val KEY_RUNTIME_MODEL_ID = "codex.runtime.selectedModelId"
        const val KEY_RUNTIME_REASONING_EFFORT = "codex.runtime.selectedReasoningEffort"
        const val KEY_RUNTIME_ACCESS_MODE = "codex.runtime.selectedAccessMode"
        const val KEY_RUNTIME_SERVICE_TIER = "codex.runtime.selectedServiceTier"
        const val KEY_ASSOCIATED_MANAGED_WORKTREES = "codex.thread.associatedManagedWorktrees"
        const val KEY_THREAD_RENAMES = "codex.thread.renamedThreadNames"
        const val KEY_CACHED_THREADS = "codex.thread.cachedThreadSnapshot.v1"
        const val KEY_LOCALLY_DELETED_THREAD_IDS = "codex.locallyDeletedThreadIDs"
        const val KEY_LOCALLY_ARCHIVED_THREAD_IDS = "codex.locallyArchivedThreadIDs"
        const val MANAGED_WORKTREE_ENTRY_SEPARATOR = "\t"
        const val MAX_CACHED_THREADS = 100
    }

    private fun encodeManagedWorktreeEntry(entry: Map.Entry<String, String>): String =
        urlEncode(entry.key) + MANAGED_WORKTREE_ENTRY_SEPARATOR + urlEncode(entry.value)

    private fun decodeManagedWorktreeEntry(raw: String): Pair<String, String>? {
        val parts = raw.split(MANAGED_WORKTREE_ENTRY_SEPARATOR, limit = 2)
        if (parts.size != 2) return null
        val threadId = runCatching { urlDecode(parts[0]) }.getOrNull()?.trim().orEmpty()
        val path = runCatching { urlDecode(parts[1]) }.getOrNull()?.trim().orEmpty()
        if (threadId.isEmpty() || path.isEmpty()) return null
        return threadId to path
    }

    private fun encodeThreadRenameEntry(entry: Map.Entry<String, String>): String =
        urlEncode(entry.key) + MANAGED_WORKTREE_ENTRY_SEPARATOR + urlEncode(entry.value)

    private fun decodeThreadRenameEntry(raw: String): Pair<String, String>? {
        val parts = raw.split(MANAGED_WORKTREE_ENTRY_SEPARATOR, limit = 2)
        if (parts.size != 2) return null
        val threadId = runCatching { urlDecode(parts[0]) }.getOrNull()?.trim().orEmpty()
        val name = runCatching { urlDecode(parts[1]) }.getOrNull()?.trim().orEmpty()
        if (threadId.isEmpty() || name.isEmpty()) return null
        return threadId to name
    }

    private fun encodeCachedThreadEntry(
        index: Int,
        thread: CodexThread,
    ): String? {
        val id = thread.id.trim().takeIf { it.isNotEmpty() } ?: return null
        val fields =
            listOf(
                index.toString(),
                id,
                thread.title.orEmpty(),
                thread.name.orEmpty(),
                thread.preview.orEmpty(),
                thread.createdAt?.toString().orEmpty(),
                thread.updatedAt?.toString().orEmpty(),
                thread.cwd.orEmpty(),
                thread.syncState.name,
                thread.forkedFromThreadId.orEmpty(),
                thread.parentThreadId.orEmpty(),
                thread.agentId.orEmpty(),
                thread.agentNickname.orEmpty(),
                thread.agentRole.orEmpty(),
                thread.model.orEmpty(),
                thread.modelProvider.orEmpty(),
            )
        return fields.joinToString(MANAGED_WORKTREE_ENTRY_SEPARATOR) { urlEncode(it) }
    }

    private fun decodeCachedThreadEntry(raw: String): CachedThreadEntry? {
        val parts =
            raw.split(MANAGED_WORKTREE_ENTRY_SEPARATOR)
                .map { runCatching { urlDecode(it) }.getOrNull() ?: return null }
        if (parts.size < 9) return null
        val id = parts[1].trim().takeIf { it.isNotEmpty() } ?: return null
        val createdAt = parts.getOrNull(5).parseInstantOrNull()
        val updatedAt = parts.getOrNull(6).parseInstantOrNull()
        val syncState =
            parts.getOrNull(8)
                ?.let { runCatching { CodexThreadSyncState.valueOf(it) }.getOrNull() }
                ?: CodexThreadSyncState.live
        val thread =
            CodexThread(
                id = id,
                title = parts.getOrNull(2).nonBlankOrNull(),
                name = parts.getOrNull(3).nonBlankOrNull(),
                preview = parts.getOrNull(4).nonBlankOrNull(),
                createdAt = createdAt,
                updatedAt = updatedAt,
                cwd = CodexThread.normalizeProjectPath(parts.getOrNull(7)),
                syncState = syncState,
                forkedFromThreadId = CodexThread.normalizeIdentifier(parts.getOrNull(9)),
                parentThreadId = CodexThread.normalizeIdentifier(parts.getOrNull(10)),
                agentId = CodexThread.normalizeIdentifier(parts.getOrNull(11)),
                agentNickname = CodexThread.normalizeIdentifier(parts.getOrNull(12)),
                agentRole = CodexThread.normalizeIdentifier(parts.getOrNull(13)),
                model = CodexThread.normalizeIdentifier(parts.getOrNull(14)),
                modelProvider = CodexThread.normalizeIdentifier(parts.getOrNull(15)),
            )
        return CachedThreadEntry(
            index = parts[0].toIntOrNull() ?: Int.MAX_VALUE,
            sortInstant = updatedAt ?: createdAt,
            thread = thread,
        )
    }

    private data class CachedThreadEntry(
        val index: Int,
        val sortInstant: Instant?,
        val thread: CodexThread,
    )

    private fun String?.nonBlankOrNull(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.parseInstantOrNull(): Instant? =
        this?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching { Instant.parse(raw) }.getOrNull()
        }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun urlDecode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}

private fun android.content.SharedPreferences.Editor.putOrRemove(
    key: String,
    value: String?,
): android.content.SharedPreferences.Editor {
    if (value.isNullOrBlank()) {
        remove(key)
    } else {
        putString(key, value.trim())
    }
    return this
}

data class RuntimeSelectionSnapshot(
    val selectedModelId: String? = null,
    val selectedReasoningEffort: String? = null,
    val selectedAccessMode: String? = null,
    val selectedServiceTier: String? = null,
)

data class RelaySessionSnapshot(
    val relaySessionId: String? = null,
    val relayUrl: String? = null,
    val relayMacDeviceId: String? = null,
    val relayMacIdentityPublicKey: String? = null,
    val relayProtocolVersion: String? = null,
    val relayLastAppliedBridgeOutboundSeq: String? = null,
    val lastTrustedMacDeviceId: String? = null,
)
