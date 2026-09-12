package com.vernai.ai.asr

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.AudioSnippet
import com.vernai.core.model.Language
import com.vernai.core.model.TranscriptionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface AsrState {
    data object Idle : AsrState
    data object Initializing : AsrState
    data object Ready : AsrState
    data class Recording(val decibels: Float) : AsrState
    data class Transcribing(val partialText: String) : AsrState
    data class Error(val message: String) : AsrState
}

/**
 * Pluggable on-device Speech-To-Text interface.
 * Implementations can wrap Sherpa-ONNX, whisper.cpp, or TFLite IndicConformer.
 */
interface AsrEngine : AutoCloseable {
    val state: StateFlow<AsrState>

    /**
     * Initializes engine with weights from local storage.
     */
    suspend fun initialize(targetLanguageHint: Language? = null): VernAiResult<Unit>

    /**
     * Starts continuous audio capture from microphone and emits real-time transcription tokens.
     */
    fun startLiveTranscription(languageHint: Language? = null): Flow<TranscriptionResult>

    /**
     * Halts recording and finalizes transcription of any remaining audio buffer.
     */
    suspend fun stopLiveTranscription(): VernAiResult<TranscriptionResult>

    /**
     * Transcribes an existing offline audio snippet.
     */
    suspend fun transcribeSnippet(snippet: AudioSnippet, languageHint: Language? = null): VernAiResult<TranscriptionResult>

    /**
     * Checks if the engine is currently loaded into RAM.
     */
    fun isReady(): Boolean
}
