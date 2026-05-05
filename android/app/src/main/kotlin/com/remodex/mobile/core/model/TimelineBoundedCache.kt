package com.remodex.mobile.core.model

/**
 * Small LRU cache for pure timeline parse/render models. Values must not hold Compose state.
 */
internal class TimelineBoundedCache<K, V>(
    private val maxEntries: Int,
) {
    private val entries =
        object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                size > maxEntries
        }

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    @Synchronized
    fun getOrPut(
        key: K,
        producer: () -> V,
    ): V {
        entries[key]?.let { return it }
        return producer().also { entries[key] = it }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}

internal object TurnTimelineCacheKey {
    fun key(
        messageId: String,
        kind: String,
        text: String,
        variant: String? = null,
    ): String =
        buildString {
            append(messageId)
            append('|')
            append(kind)
            append('|')
            append(text.length)
            append(':')
            append(text.hashCode())
            variant?.let {
                append('|')
                append(it)
            }
        }

    fun textKey(
        kind: String,
        text: String,
        variant: String? = null,
    ): String =
        buildString {
            append(kind)
            append('|')
            append(text.length)
            append(':')
            append(text.hashCode())
            variant?.let {
                append('|')
                append(it)
            }
        }
}
