package com.remodex.mobile.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CodexAccessMode {
    @SerialName("on-request")
    onRequest,

    @SerialName("full-access")
    fullAccess;

    val displayName: String
        get() =
            when (this) {
                onRequest -> "Ask"
                fullAccess -> "Full"
            }

    val menuTitle: String
        get() =
            when (this) {
                onRequest -> "On-Request"
                fullAccess -> "Full Access"
            }

    val approvalPolicyCandidates: List<String>
        get() =
            when (this) {
                onRequest -> listOf("on-request", "onRequest")
                fullAccess -> listOf("never")
            }

    val sandboxLegacyValue: String
        get() =
            when (this) {
                onRequest -> "workspace-write"
                fullAccess -> "danger-full-access"
            }
}
