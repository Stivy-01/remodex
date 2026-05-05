package com.remodex.mobile.data

import com.remodex.mobile.core.model.JSONValue

/**
 * Estrae `turnId` dalla risposta `turn/start` (subset di
 * [CodexService.extractTurnID](CodexMobile/CodexMobile/Services/CodexService+Incoming.swift)).
 */
internal fun extractTurnIdFromRpcResult(result: JSONValue?): String? {
    val m = (result as? JSONValue.Obj)?.map ?: return null
    m["turnId"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    m["turn_id"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    (m["turn"] as? JSONValue.Obj)?.map?.let { turn ->
        turn["id"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        turn["turnId"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        turn["turn_id"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    (m["item"] as? JSONValue.Obj)?.map?.let { item ->
        item["turnId"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        item["turn_id"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}
