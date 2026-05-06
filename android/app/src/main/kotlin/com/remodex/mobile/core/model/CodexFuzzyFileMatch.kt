package com.remodex.mobile.core.model

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class CodexFuzzyFileMatch(
    val root: String,
    val path: String,
    val fileName: String,
    val score: Double,
    val indices: List<Int>? = null,
) {
    val id: String get() = "$root|$path"

    companion object {
        fun fromJsonObject(obj: JsonObject): CodexFuzzyFileMatch {
            val root = obj["root"]!!.jsonPrimitive.content
            val path = obj["path"]!!.jsonPrimitive.content
            val fileNameCamel = obj["fileName"]?.jsonPrimitive?.content
            val fileNameSnake = obj["file_name"]?.jsonPrimitive?.content
            val fileName =
                fileNameCamel ?: fileNameSnake ?: File(path).name
            val scoreEl = obj["score"]?.jsonPrimitive
            val score =
                when {
                    scoreEl?.doubleOrNull != null -> scoreEl.doubleOrNull!!
                    scoreEl?.intOrNull != null -> scoreEl.intOrNull!!.toDouble()
                    else -> 0.0
                }
            val indices =
                obj["indices"]?.let { el ->
                    when (val v = JSONValue.fromJsonElement(el)) {
                        is JSONValue.Arr -> v.elements.mapNotNull { it.intValue }
                        else -> null
                    }
                }
            return CodexFuzzyFileMatch(root, path, fileName, score, indices)
        }
    }
}
