package com.remodex.mobile.core.model

data class WorkspaceImageReadResult(
    val path: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val byteLength: Long = 0L,
    val mtimeMs: Double = 0.0,
    val previewMaxPixelDimension: Int? = null,
    val dataByteLength: Long? = null,
    val dataBase64: String? = null,
    val notModified: Boolean = false,
) {
    companion object {
        fun fromJson(json: RPCObject): WorkspaceImageReadResult =
            WorkspaceImageReadResult(
                path = json["path"]?.stringValue?.trim().orEmpty(),
                fileName = json["fileName"]?.stringValue?.trim().orEmpty(),
                mimeType = json["mimeType"]?.stringValue?.trim().orEmpty(),
                byteLength = json["byteLength"]?.longValue ?: 0L,
                mtimeMs = json["mtimeMs"]?.doubleValue ?: 0.0,
                previewMaxPixelDimension = json["previewMaxPixelDimension"]?.intValue,
                dataByteLength = json["dataByteLength"]?.longValue,
                dataBase64 = json["dataBase64"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                notModified = json["notModified"]?.boolValue ?: false,
            )
    }
}
