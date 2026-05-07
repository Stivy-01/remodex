package com.remodex.mobile.core.model

data class CodexPluginMetadata(
    val id: String,
    val name: String,
    val marketplaceName: String,
    val marketplacePath: String? = null,
    val displayName: String? = null,
    val shortDescription: String? = null,
    val installed: Boolean = false,
    val enabled: Boolean = false,
    val installPolicy: String? = null,
) {
    val isAvailableForMention: Boolean
        get() = installed || enabled || installPolicy == "INSTALLED_BY_DEFAULT"

    val mentionPath: String
        get() = "plugin://$name@$marketplaceName"

    val displayTitle: String
        get() = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: name

    val normalizedName: String
        get() = name.trim().lowercase()

    val searchBlob: String
        get() =
            listOf(name, displayName.orEmpty(), shortDescription.orEmpty(), marketplaceName)
                .map(::normalizedDiscoveryText)
                .filter { it.isNotEmpty() }
                .joinToString("\n")

    fun matchesSearch(query: String): Boolean {
        val normalizedQuery = normalizedDiscoveryText(query)
        return normalizedQuery.isEmpty() || searchBlob.contains(normalizedQuery)
    }

    companion object {
        fun normalizedDiscoveryText(value: String): String =
            value
                .lowercase()
                .replace(Regex("""[:/_-]+"""), " ")
                .split(Regex("""\s+"""))
                .filter { it.isNotEmpty() }
                .joinToString(" ")
    }
}

