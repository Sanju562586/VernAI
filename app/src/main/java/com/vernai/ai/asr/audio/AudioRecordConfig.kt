package com.vernai.ai.asr.audio

import android.media.AudioFormat

/**
 * Audio capture parameters standardized for on-device Indic ASR models.
 * Standard format: 16kHz, 16-bit signed Little-Endian, Mono PCM.
 */
data class AudioRecordConfig(
    val sampleRate: Int = 16000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val chunkDurationMs: Int = 100, // 100ms per frame = 1600 samples at 16kHz
    val speechSilenceThresholdDb: Float = 36.0f
) {
    val samplesPerChunk: Int = (sampleRate * chunkDurationMs) / 1000
    val bytesPerChunk: Int = samplesPerChunk * 2 // 16-bit = 2 bytes per sample
}

/**
 * Encapsulates a slice of processed audio data emitted by AudioRecordManager.
 */
data class AudioFrame(
    val pcmBytes: ByteArray,
    val normalizedSamples: FloatArray,
    val decibels: Float,
    val isSpeech: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioFrame
        return pcmBytes.contentEquals(other.pcmBytes) &&
                normalizedSamples.contentEquals(other.normalizedSamples) &&
                decibels == other.decibels &&
                isSpeech == other.isSpeech
    }

    override fun hashCode(): Int {
        var result = pcmBytes.contentHashCode()
        result = 31 * result + normalizedSamples.contentHashCode()
        result = 31 * result + decibels.hashCode()
        result = 31 * result + isSpeech.hashCode()
        return result
    }
}
