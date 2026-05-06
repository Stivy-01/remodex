package com.remodex.mobile.data

import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingStructuredInputOption
import com.remodex.mobile.core.model.PendingStructuredInputQuestion

internal fun isStructuredInputServerRequestMethod(method: String): Boolean =
    method == "item/tool/requestUserInput"

internal fun isApprovalServerRequestMethod(normalizedMethod: String): Boolean =
    normalizedMethod == "item/commandexecution/requestapproval" ||
        normalizedMethod == "item/filechange/requestapproval" ||
        normalizedMethod.endsWith("requestapproval")

internal fun parseStructuredInputQuestions(
    payload: Map<String, JSONValue>?,
): List<PendingStructuredInputQuestion> =
    payload?.get("questions")?.arrayValue?.mapNotNull { value ->
        val questionObject = value.objectValue ?: return@mapNotNull null
        val id = questionObject["id"]?.stringValue?.trim().orEmpty()
        if (id.isEmpty()) return@mapNotNull null
        PendingStructuredInputQuestion(
            id = id,
            header = questionObject["header"]?.stringValue?.trim().orEmpty(),
            question = questionObject["question"]?.stringValue?.trim().orEmpty(),
            options =
                questionObject["options"]?.arrayValue?.mapNotNull { optionValue ->
                    val optionObject = optionValue.objectValue ?: return@mapNotNull null
                    val label = optionObject["label"]?.stringValue?.trim().orEmpty()
                    if (label.isEmpty()) return@mapNotNull null
                    PendingStructuredInputOption(
                        label = label,
                        description =
                            optionObject["description"]?.stringValue
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() },
                    )
                }.orEmpty(),
        )
    }.orEmpty()
