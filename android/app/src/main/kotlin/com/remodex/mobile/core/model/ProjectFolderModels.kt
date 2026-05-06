package com.remodex.mobile.core.model

data class CodexProjectLocation(
    val id: String,
    val label: String,
    val path: String,
) {
    companion object {
        fun fromOrNull(json: RPCObject): CodexProjectLocation? {
            val id = json["id"]?.stringValue?.trim().orEmpty()
            val label = json["label"]?.stringValue?.trim().orEmpty()
            val path = json["path"]?.stringValue?.trim().orEmpty()
            if (id.isEmpty() || label.isEmpty() || path.isEmpty()) return null
            return CodexProjectLocation(id = id, label = label, path = path)
        }
    }
}

data class CodexProjectDirectoryEntry(
    val name: String,
    val path: String,
    val isSymlink: Boolean = false,
) {
    val id: String get() = path

    companion object {
        fun fromOrNull(json: RPCObject): CodexProjectDirectoryEntry? {
            val name = json["name"]?.stringValue?.trim().orEmpty()
            val path = json["path"]?.stringValue?.trim().orEmpty()
            if (name.isEmpty() || path.isEmpty()) return null
            return CodexProjectDirectoryEntry(
                name = name,
                path = path,
                isSymlink = json["isSymlink"]?.boolValue ?: false,
            )
        }
    }
}

data class CodexProjectDirectoryListing(
    val path: String,
    val parentPath: String?,
    val entries: List<CodexProjectDirectoryEntry>,
) {
    companion object {
        fun fromJson(json: RPCObject): CodexProjectDirectoryListing {
            val path = json["path"]?.stringValue?.trim().orEmpty()
            val entries =
                json["entries"]?.arrayValue
                    ?.mapNotNull { it.objectValue?.let(CodexProjectDirectoryEntry::fromOrNull) }
                    ?: emptyList()
            return CodexProjectDirectoryListing(
                path = path,
                parentPath = json["parentPath"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                entries = entries,
            )
        }
    }
}
