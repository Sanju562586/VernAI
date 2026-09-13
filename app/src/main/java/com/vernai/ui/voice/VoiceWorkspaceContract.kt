package com.vernai.ui.voice

import com.vernai.core.model.Language
import com.vernai.ui.common.UiIntent
import com.vernai.ui.common.UiSideEffect
import com.vernai.ui.common.UiState
import com.vernai.ui.navigation.VernAiNavDestination

enum class ProcessingStage {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    REASONING_LLM,
    COMPLETED
}

enum class DetectedIntentType {
    SALES_RECORD,
    COMPLAINT_LETTER,
    GENERAL_QUERY
}

data class VoiceUiState(
    val activeLanguage: Language = Language.TELUGU,
    val stage: ProcessingStage = ProcessingStage.IDLE,
    val isRecording: Boolean = false,
    val recordingDurationSec: Int = 0,
    val liveTranscript: String = "",
    val detectedIntent: DetectedIntentType? = null,
    val extractedResultPreview: String? = null,
    val audioDecibels: Float = 0f,
    val errorMessage: String? = null
) : UiState

sealed interface VoiceUiIntent : UiIntent {
    data class ChangeLanguage(val language: Language) : VoiceUiIntent
    data object ToggleRecording : VoiceUiIntent
    data object ResetState : VoiceUiIntent
    data object ProceedToIntentAction : VoiceUiIntent
    data class SimulateSpeech(val text: String) : VoiceUiIntent
}

sealed interface VoiceUiSideEffect : UiSideEffect {
    data class NavigateTo(val destination: VernAiNavDestination) : VoiceUiSideEffect
    data class ShowToast(val message: String) : VoiceUiSideEffect
}
