package com.remodex.mobile.core.voice

/**
 * ChatGPT `/backend-api/transcribe` failures (recording errors live elsewhere on iOS).
 * Mirrors [GPTVoiceTranscriptionError](CodexMobile/CodexMobile/Services/GPTVoiceTranscriptionManager.swift) subset used for HTTP.
 */
sealed class GptVoiceTranscriptionError(
    message: String,
) : Exception(message) {
    data object AuthExpired :
        GptVoiceTranscriptionError("Your ChatGPT login has expired. Sign in again.")

    class TranscriptionFailed(
        reason: String,
    ) : GptVoiceTranscriptionError(reason)
}
