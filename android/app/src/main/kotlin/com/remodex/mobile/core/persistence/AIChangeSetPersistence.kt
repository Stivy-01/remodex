package com.remodex.mobile.core.persistence

import android.content.Context
import com.remodex.mobile.core.model.AIChangeSet
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Plain JSON ledger for assistant change sets (same filename/layout as iOS).
 * Mirrors [AIChangeSetPersistence.swift](CodexMobile/CodexMobile/Services/AIChangeSetPersistence.swift).
 */
class AIChangeSetPersistence(
    private val context: Context,
) {
    private val fileName = "codex-ai-change-sets-v1.json"

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val listSerializer = ListSerializer(AIChangeSet.serializer())

    fun load(): List<AIChangeSet> {
        val file = storeFile()
        if (!file.exists() || file.length() == 0L) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString(listSerializer, text) }.getOrDefault(emptyList())
    }

    fun save(value: List<AIChangeSet>) {
        val data =
            runCatching { json.encodeToString(listSerializer, value) }
                .getOrNull()
                ?: return
        val file = storeFile()
        file.parentFile?.mkdirs()
        file.writeText(data)
    }

    private fun storeFile(): File {
        val dir = File(context.filesDir, context.packageName).apply { mkdirs() }
        return File(dir, fileName)
    }
}
