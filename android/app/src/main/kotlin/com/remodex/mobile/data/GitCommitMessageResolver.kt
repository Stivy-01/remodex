package com.remodex.mobile.data

internal suspend fun remodexResolveCommitMessage(
    rawMessage: String,
    generateDraft: suspend () -> String,
): String? {
    val trimmed = rawMessage.trim()
    if (trimmed.isNotEmpty()) return trimmed
    return generateDraft().trim().takeIf { it.isNotEmpty() }
}

