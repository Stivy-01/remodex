package com.remodex.mobile.core.model

import java.util.concurrent.atomic.AtomicInteger

/**
 * Monotonic order index for [CodexMessage], matching the iOS global counter semantics.
 */
object CodexMessageOrderCounter {
    private val counter = AtomicInteger(0)

    fun next(): Int = counter.getAndIncrement()

    fun seedFrom(allMessages: Map<String, List<CodexMessage>>) {
        val maxExisting =
            allMessages.values
                .flatten()
                .maxOfOrNull { it.orderIndex }
                ?: -1
        counter.updateAndGet { cur -> if (maxExisting >= cur) maxExisting + 1 else cur }
    }
}
