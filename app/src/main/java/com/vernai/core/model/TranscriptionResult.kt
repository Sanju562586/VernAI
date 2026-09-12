package com.vernai.core.model

/**
 * Encapsulates raw audio buffer data captured from the microphone.
 * Standardized to 16kHz, 16-bit Mono PCM.
 */
data class AudioSnippet(
    val pcmData: ByteArray,
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioSnippet
        return pcmData.contentEquals(other.pcmData) && sampleRate == other.sampleRate
    }

    override fun hashCode(): Int {
        var result = pcmData.contentHashCode()
        result = 31 * result + sampleRate
        return result
    }
}

/**
 * Result emitted by the ASR engine.
 */
data class TranscriptionResult(
    val text: String,
    val isFinal: Boolean,
    val detectedLanguage: Language,
    val confidence: Float = 1.0f,
    val processingTimeMs: Long = 0
)
