package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexThreadSyncState
import com.remodex.mobile.core.model.JSONValue
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Fetches `thread/list` pages (parity with [CodexService.fetchServerThreads] on iOS) and merges
 * active + archived results for sidebar state.
 */
internal object ThreadListSync {
    private const val PAGE_LIMIT = 40

    private val SOURCE_KINDS =
        listOf(
            "cli",
            "vscode",
            "appServer",
            "exec",
            "unknown",
        )

    suspend fun fetchMerged(repository: CodexRepository): List<CodexThread> {
        val active = fetchPages(repository, archived = false)
        val archived =
            runCatching { fetchPages(repository, archived = true) }.getOrDefault(emptyList())
        return sortThreads(mergeActiveAndArchived(active, archived))
    }

    private suspend fun fetchPages(
        repository: CodexRepository,
        archived: Boolean,
    ): List<CodexThread> {
        val out = ArrayList<CodexThread>()
        var cursor: JSONValue? = JSONValue.Null
        var guard = 0
        do {
            val fields =
                mutableMapOf(
                    "sourceKinds" to JSONValue.Arr(SOURCE_KINDS.map { JSONValue.Str(it) }),
                    "cursor" to (cursor ?: JSONValue.Null),
                    "limit" to JSONValue.NumLong(PAGE_LIMIT.toLong()),
                )
            if (archived) {
                fields["archived"] = JSONValue.Bool(true)
            }
            val response = repository.sendRequest("thread/list", JSONValue.Obj(fields))
            val result = response.result ?: throw IllegalStateException("thread/list missing result")
            val (threads, next) = parseThreadListPage(result)
            out += threads
            cursor = next.takeIf { it != JSONValue.Null }
            guard++
        } while (cursor != null && guard < 25)
        return out
    }

    private fun parseThreadListPage(result: JSONValue): Pair<List<CodexThread>, JSONValue> {
        val obj = result as? JSONValue.Obj ?: error("thread/list result must be an object")
        val m = obj.map
        val page =
            m["data"]?.arrayValue
                ?: m["items"]?.arrayValue
                ?: m["threads"]?.arrayValue
                ?: error("thread/list response missing data array")
        val threads =
            page.mapNotNull { el ->
                val jo = jsonObjectFromJsonValue(el) ?: return@mapNotNull null
                runCatching { CodexThread.fromJsonObject(jo) }.getOrNull()
            }
        val next =
            m["nextCursor"]
                ?: m["next_cursor"]
                ?: JSONValue.Null
        return threads to next
    }

    private fun jsonObjectFromJsonValue(v: JSONValue): JsonObject? {
        if (v !is JSONValue.Obj) return null
        return buildJsonObject {
            v.map.forEach { (k, child) ->
                put(k, JSONValue.toJsonElement(child))
            }
        }
    }

    private fun mergeActiveAndArchived(
        active: List<CodexThread>,
        archived: List<CodexThread>,
    ): List<CodexThread> {
        val byId = LinkedHashMap<String, CodexThread>()
        for (t in active) {
            byId[t.id] = t.copy(syncState = CodexThreadSyncState.live)
        }
        for (t in archived) {
            if (!byId.containsKey(t.id)) {
                byId[t.id] = t.copy(syncState = CodexThreadSyncState.archivedLocal)
            }
        }
        return byId.values.toList()
    }

    private fun sortThreads(value: List<CodexThread>): List<CodexThread> {
        val past = Instant.EPOCH
        return value.sortedWith { lhs, rhs ->
            val l = lhs.updatedAt ?: lhs.createdAt ?: past
            val r = rhs.updatedAt ?: rhs.createdAt ?: past
            r.compareTo(l)
        }
    }
}
