package com.remodex.mobile.core.model

data class ThreadHistoryPaginationState(
    val olderCursor: JSONValue? = null,
    val exhaustedOlderCursor: JSONValue? = null,
    val hasAuthoritativeLocalHistoryStart: Boolean = false,
    val initialTurnsLoaded: Boolean = false,
) {
    val canLoadOlder: Boolean
        get() = olderCursor != null && olderCursor != JSONValue.Null && !hasAuthoritativeLocalHistoryStart
}
