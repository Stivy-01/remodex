package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexProjectDirectoryEntry
import com.remodex.mobile.core.model.CodexProjectDirectoryListing
import com.remodex.mobile.core.model.CodexProjectLocation
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCObject
import com.remodex.mobile.data.CodexRepository

class ProjectFolderService(
    private val repository: CodexRepository,
) {
    suspend fun quickLocations(): List<CodexProjectLocation> {
        val response =
            runCatching { repository.sendRequest("project/quickLocations", JSONValue.Obj(emptyMap())) }
                .getOrElse { error ->
                    if (error.isUnsupportedProjectMethod()) return emptyList()
                    throw error
                }
        val raw = response.result?.objectValue?.get("locations")?.arrayValue
            ?: throw CodexServiceError.InvalidInput("project/quickLocations response missing locations")
        return raw.mapNotNull { it.objectValue?.let(CodexProjectLocation::fromOrNull) }
    }

    suspend fun listDirectory(path: String): CodexProjectDirectoryListing {
        val response =
            runCatching {
                repository.sendRequest(
                    "project/listDirectory",
                    JSONValue.Obj(
                        mapOf(
                            "path" to JSONValue.Str(path),
                            "limit" to JSONValue.NumLong(200),
                        ),
                    ),
                )
            }.getOrElse { error ->
                if (error.isUnsupportedProjectMethod()) return listDirectoryWithFsRpc(path)
                throw error
            }
        val obj = response.result?.objectValue
            ?: throw CodexServiceError.InvalidInput("project/listDirectory response missing entries")
        return CodexProjectDirectoryListing.fromJson(obj)
    }

    suspend fun searchDirectories(
        rootPath: String,
        query: String,
    ): List<CodexProjectDirectoryEntry> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        val response =
            runCatching {
                repository.sendRequest(
                    "project/searchDirectories",
                    JSONValue.Obj(
                        mapOf(
                            "path" to JSONValue.Str(rootPath),
                            "query" to JSONValue.Str(normalizedQuery),
                            "limit" to JSONValue.NumLong(80),
                        ),
                    ),
                )
            }.getOrElse { error ->
                if (error.isUnsupportedProjectMethod()) return searchDirectoriesWithFsRpc(rootPath, normalizedQuery)
                throw error
            }
        val raw = response.result?.objectValue?.get("entries")?.arrayValue
            ?: throw CodexServiceError.InvalidInput("project/searchDirectories response missing entries")
        return raw.mapNotNull { it.objectValue?.let(CodexProjectDirectoryEntry::fromOrNull) }
    }

    suspend fun createDirectory(
        parentPath: String,
        name: String,
    ): String {
        val response =
            runCatching {
                repository.sendRequest(
                    "project/createDirectory",
                    JSONValue.Obj(
                        mapOf(
                            "parentPath" to JSONValue.Str(parentPath),
                            "name" to JSONValue.Str(name),
                        ),
                    ),
                )
            }.getOrElse { error ->
                if (error.isUnsupportedProjectMethod()) return createDirectoryWithFsRpc(parentPath, name)
                throw error
            }
        return response.result?.objectValue?.get("path")?.stringValue?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw CodexServiceError.InvalidInput("project/createDirectory response missing path")
    }

    private suspend fun listDirectoryWithFsRpc(path: String): CodexProjectDirectoryListing {
        val normalizedPath = path.trim()
        val response =
            repository.sendRequest(
                "fs/readDirectory",
                JSONValue.Obj(mapOf("path" to JSONValue.Str(normalizedPath))),
            )
        val entries =
            response.result?.objectValue?.get("entries")?.arrayValue
                ?.mapNotNull { it.objectValue?.toProjectEntry(parentPath = normalizedPath) }
                ?.filter { entry -> entry.name.isNotBlank() }
                ?.sortedWith(compareBy<CodexProjectDirectoryEntry> { it.name.lowercase() }.thenBy { it.path })
                ?: throw CodexServiceError.InvalidInput("fs/readDirectory response missing entries")
        return CodexProjectDirectoryListing(
            path = normalizedPath,
            parentPath = parentPathOf(normalizedPath),
            entries = entries,
        )
    }

    private suspend fun searchDirectoriesWithFsRpc(
        rootPath: String,
        query: String,
    ): List<CodexProjectDirectoryEntry> {
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf<String>()
        val results = mutableListOf<CodexProjectDirectoryEntry>()
        queue.add(rootPath.trim() to 0)

        while (queue.isNotEmpty() && results.size < 80 && visited.size < 5000) {
            val (path, depth) = queue.removeFirst()
            if (!visited.add(path)) continue
            val listing = runCatching { listDirectoryWithFsRpc(path) }.getOrNull() ?: continue
            for (entry in listing.entries) {
                if (tokens.all { token -> entry.name.lowercase().contains(token) }) {
                    results.add(entry)
                    if (results.size >= 80) break
                }
                if (depth < 8) {
                    queue.add(entry.path to depth + 1)
                }
            }
        }

        return results.sortedWith(compareBy<CodexProjectDirectoryEntry> { it.name.lowercase() }.thenBy { it.path })
    }

    private suspend fun createDirectoryWithFsRpc(
        parentPath: String,
        name: String,
    ): String {
        val childPath = joinChildPath(parentPath.trim(), name.trim())
        repository.sendRequest(
            "fs/createDirectory",
            JSONValue.Obj(
                mapOf(
                    "path" to JSONValue.Str(childPath),
                    "recursive" to JSONValue.Bool(false),
                ),
            ),
        )
        return childPath
    }

    private fun RPCObject.toProjectEntry(parentPath: String): CodexProjectDirectoryEntry? {
        val isDirectory = this["isDirectory"]?.boolValue ?: false
        if (!isDirectory) return null
        val name =
            this["name"]?.stringValue?.trim()
                ?: this["fileName"]?.stringValue?.trim()
                ?: this["filename"]?.stringValue?.trim()
                ?: return null
        val path = this["path"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() } ?: joinChildPath(parentPath, name)
        return CodexProjectDirectoryEntry(
            name = name,
            path = path,
            isSymlink = this["isSymlink"]?.boolValue ?: false,
        )
    }

    private fun Throwable.isUnsupportedProjectMethod(): Boolean {
        val rpcFailure = this as? CodexServiceError.RpcFailure ?: return false
        val message = rpcFailure.rpcError.message.lowercase()
        return message.contains("unknown variant")
            && message.contains("project/")
            && message.contains("fs/")
    }

    private fun parentPathOf(path: String): String? {
        val trimmed = path.trim().trimEnd('/', '\\')
        if (trimmed.isEmpty()) return null
        val index = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        if (index <= 0) return null
        if (index == 2 && trimmed.getOrNull(1) == ':') return null
        return trimmed.substring(0, index)
    }

    private fun joinChildPath(
        parentPath: String,
        name: String,
    ): String {
        val separator = if (parentPath.contains('\\') && !parentPath.contains('/')) "\\" else "/"
        return parentPath.trimEnd('/', '\\') + separator + name
    }
}
