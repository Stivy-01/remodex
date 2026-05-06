package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexFuzzyFileMatch
import com.remodex.mobile.core.model.CodexSkillMetadata
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.data.CodexRepository
import kotlinx.serialization.json.JsonObject

class CodexLookupService(
    private val repository: CodexRepository,
) {
    suspend fun fuzzyFileSearch(
        query: String,
        roots: List<String>,
        cancellationToken: String? = null,
    ): List<CodexFuzzyFileMatch> {
        val normalizedQuery = query.trim()
        val normalizedRoots = roots.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        if (normalizedQuery.isEmpty() || normalizedRoots.isEmpty()) return emptyList()
        val response =
            repository.sendRequest(
                "fuzzyFileSearch",
                JSONValue.Obj(
                    mapOf(
                        "query" to JSONValue.Str(normalizedQuery),
                        "roots" to JSONValue.Arr(normalizedRoots.map(JSONValue::Str)),
                        "cancellationToken" to (
                            cancellationToken?.trim()?.takeIf { it.isNotEmpty() }?.let(JSONValue::Str)
                                ?: JSONValue.Null
                        ),
                    ),
                ),
            )
        return decodeFuzzyMatches(response.result)
            ?: throw CodexServiceError.InvalidInput("fuzzyFileSearch response missing result.files")
    }

    suspend fun listSkills(
        cwds: List<String>,
        forceReload: Boolean = false,
    ): List<CodexSkillMetadata> {
        val normalizedCwds = cwds.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        val response =
            try {
                repository.sendRequest("skills/list", skillsListParams("cwds", normalizedCwds, forceReload))
            } catch (e: Throwable) {
                if (normalizedCwds.isEmpty() || !shouldRetrySkillsListWithCwdFallback(e)) throw e
                repository.sendRequest("skills/list", skillsListParams("cwd", listOf(normalizedCwds.first()), forceReload))
            }
        return decodeSkillMetadata(response.result)
            ?: throw CodexServiceError.InvalidInput("skills/list response missing result.data[].skills")
    }

    private fun skillsListParams(
        cwdKey: String,
        cwds: List<String>,
        forceReload: Boolean,
    ): JSONValue.Obj {
        val params = linkedMapOf<String, JSONValue>()
        if (cwds.isNotEmpty()) {
            if (cwdKey == "cwd") {
                params["cwd"] = JSONValue.Str(cwds.first())
            } else {
                params["cwds"] = JSONValue.Arr(cwds.map(JSONValue::Str))
            }
        }
        if (forceReload) params["forceReload"] = JSONValue.Bool(true)
        return JSONValue.Obj(params)
    }
}

internal fun decodeFuzzyMatches(result: JSONValue?): List<CodexFuzzyFileMatch>? {
    val files =
        result?.objectValue?.get("files")?.arrayValue
            ?: result?.arrayValue
            ?: return null
    return files.mapNotNull { value ->
        val obj = value.objectValue ?: return@mapNotNull null
        CodexFuzzyFileMatch.fromJsonObject(JSONValue.toJsonElement(JSONValue.Obj(obj)) as JsonObject)
    }
}

internal fun decodeSkillMetadata(result: JSONValue?): List<CodexSkillMetadata>? {
    val resultObject = result?.objectValue
    val collected = mutableListOf<CodexSkillMetadata>()
    var sawSkillContainer = false

    fun collectFrom(value: JSONValue?) {
        value?.arrayValue?.forEach { item ->
            val obj = item.objectValue ?: return@forEach
            val jsonObj = JSONValue.toJsonElement(JSONValue.Obj(obj)) as JsonObject
            runCatching { CodexSkillMetadata.fromJsonObject(jsonObj) }.getOrNull()?.let(collected::add)
        }
    }

    resultObject?.get("data")?.arrayValue?.forEach { item ->
        val obj = item.objectValue ?: return@forEach
        val skills = obj["skills"]
        if (skills != null) {
            sawSkillContainer = true
            collectFrom(skills)
        }
    }
    if (collected.isEmpty()) {
        resultObject?.get("data")?.arrayValue?.let { data ->
            collectFrom(JSONValue.Arr(data))
            if (collected.isNotEmpty()) sawSkillContainer = true
        }
    }
    resultObject?.get("skills")?.let {
        sawSkillContainer = true
        collectFrom(it)
    }
    if (collected.isEmpty()) {
        collectFrom(result)
    }
    return if (sawSkillContainer || collected.isNotEmpty()) {
        collected
            .groupBy { it.normalizedName }
            .mapNotNull { (_, bucket) -> bucket.firstOrNull { it.enabled } ?: bucket.firstOrNull() }
            .filter { it.name.trim().isNotEmpty() }
            .sortedBy { it.name.lowercase() }
    } else {
        null
    }
}

internal fun shouldRetrySkillsListWithCwdFallback(error: Throwable): Boolean {
    val rpcFailure = error as? CodexServiceError.RpcFailure ?: return false
    if (rpcFailure.rpcError.code != -32600 && rpcFailure.rpcError.code != -32602) return false
    val message = rpcFailure.rpcError.message.lowercase()
    return listOf("cwds", "cwd", "unknown field", "missing field", "invalid").any(message::contains)
}
