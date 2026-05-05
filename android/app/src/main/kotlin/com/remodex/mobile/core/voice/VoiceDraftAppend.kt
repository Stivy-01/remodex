package com.remodex.mobile.core.voice

/**
 * Pure helper: merge a transcription into the composer draft (leading/trailing whitespace normalized).
 */
object VoiceDraftAppend {
    fun append(
        existingDraft: String,
        transcript: String,
    ): String {
        val t = transcript.trim()
        if (t.isEmpty()) return existingDraft
        val base = existingDraft.trimEnd()
        return if (base.isEmpty()) {
            t
        } else {
            "$base $t"
        }
    }
}
