package com.remodex.mobile.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Matches [remodexBuildPullRequestURL] in TurnViewModel.swift (GitHub compare quick pull). */
fun remodexBuildPullRequestUrl(
    ownerRepo: String,
    branch: String,
    base: String,
    title: String,
    body: String,
): String {
    val encodedBranch = urlEncodePath(branch)
    val encodedBase = urlEncodePath(base)
    val trimmedTitle = title.trim()
    val trimmedBody = body.trim()
    if (trimmedTitle.isEmpty() && trimmedBody.isEmpty()) {
        return "https://github.com/$ownerRepo/compare/$encodedBase...$encodedBranch?expand=1"
    }
    val t = urlEncodeQuery(trimmedTitle)
    val b = urlEncodeQuery(trimmedBody)
    return "https://github.com/$ownerRepo/compare/$encodedBase...$encodedBranch?quick_pull=1&title=$t&body=$b"
}

private fun urlEncodeQuery(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun urlEncodePath(value: String): String =
    value.split("/").joinToString("/") { segment ->
        urlEncodeQuery(segment)
    }
