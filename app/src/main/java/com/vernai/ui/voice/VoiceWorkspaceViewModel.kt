package com.vernai.ui.voice

import androidx.lifecycle.viewModelScope
import com.vernai.ai.asr.AsrEngine
import com.vernai.ai.llm.GenerationParameters
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.ai.mock.MockAsrEngine
import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.model.Language
import com.vernai.ui.common.MviViewModel
import com.vernai.ui.navigation.VernAiNavDestination
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class VoiceWorkspaceViewModel(
    private val asrEngine: AsrEngine = MockAsrEngine(),
    private val llmEngine: LlmInferenceEngine = MockLlmInferenceEngine(),
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) : MviViewModel<VoiceUiState, VoiceUiIntent, VoiceUiSideEffect>(VoiceUiState()) {

    private var recordingJob: Job? = null
    private var timerJob: Job? = null

    override fun handleIntent(intent: VoiceUiIntent) {
        when (intent) {
            is VoiceUiIntent.ChangeLanguage -> setState { copy(activeLanguage = intent.language) }
            is VoiceUiIntent.ToggleRecording -> handleToggleRecording()
            is VoiceUiIntent.ResetState -> {
                recordingJob?.cancel()
                timerJob?.cancel()
                setState {
                    copy(
                        stage = ProcessingStage.IDLE,
                        isRecording = false,
                        recordingDurationSec = 0,
                        liveTranscript = "",
                        detectedIntent = null,
                        extractedResultPreview = null,
                        errorMessage = null
                    )
                }
            }
            is VoiceUiIntent.ProceedToIntentAction -> {
                when (uiState.value.detectedIntent) {
                    DetectedIntentType.SALES_RECORD -> sendSideEffect(VoiceUiSideEffect.NavigateTo(VernAiNavDestination.SalesLedger))
                    DetectedIntentType.COMPLAINT_LETTER -> sendSideEffect(VoiceUiSideEffect.NavigateTo(VernAiNavDestination.ComplaintDrafting))
                    else -> sendSideEffect(VoiceUiSideEffect.ShowToast("ఫలితం విజయవంతంగా రూపొందించబడింది (Result processed)"))
                }
            }
        }
    }

    private fun handleToggleRecording() {
        if (uiState.value.isRecording) {
            // Stop recording -> Transition to LLM reasoning
            recordingJob?.cancel()
            timerJob?.cancel()
            setState { copy(isRecording = false, stage = ProcessingStage.REASONING_LLM) }

            viewModelScope.launch(dispatchers.default) {
                // Simulate local LLM categorization & intent extraction
                val prompt = uiState.value.liveTranscript
                val detected = if (prompt.contains("అమ్మిన") || prompt.contains("రూపాయలు") || prompt.contains("కేజీ")) {
                    DetectedIntentType.SALES_RECORD
                } else {
                    DetectedIntentType.COMPLAINT_LETTER
                }

                val preview = when (detected) {
                    DetectedIntentType.SALES_RECORD ->
                        "గుర్తించిన అమ్మకాలు (Sales Detected):\n• టమాటా: 5 kg = ₹200\n• నూనె ప్యాకెట్లు: 2 = ₹260\nమొత్తం: ₹460"
                    DetectedIntentType.COMPLAINT_LETTER ->
                        "ఫిర్యాదు ముసాయిదా (Complaint Draft):\nశాంతినగర్ పరిధిలో వీధి దీపాల సమస్య పరిష్కారం కోసం వినతిపత్రం."
                    else -> "ఆడియో విశ్లేషణ పూర్తయింది."
                }

                delay(600) // Realistic local model reasoning pause
                setState {
                    copy(
                        stage = ProcessingStage.COMPLETED,
                        detectedIntent = detected,
                        extractedResultPreview = preview
                    )
                }
            }
        } else {
            // Start recording
            setState {
                copy(
                    isRecording = true,
                    stage = ProcessingStage.RECORDING,
                    recordingDurationSec = 0,
                    liveTranscript = "",
                    detectedIntent = null,
                    extractedResultPreview = null,
                    errorMessage = null
                )
            }

            // Start duration ticker
            timerJob = viewModelScope.launch(dispatchers.main) {
                while (true) {
                    delay(1000)
                    setState { copy(recordingDurationSec = recordingDurationSec + 1) }
                }
            }

            // Start ASR stream
            recordingJob = viewModelScope.launch(dispatchers.asrInference) {
                asrEngine.startLiveTranscription(uiState.value.activeLanguage)
                    .catch { e -> setState { copy(errorMessage = e.message, isRecording = false, stage = ProcessingStage.IDLE) } }
                    .collect { partial ->
                        setState {
                            copy(
                                liveTranscript = partial.text,
                                audioDecibels = (50..85).random().toFloat()
                            )
                        }
                    }
            }
        }
    }
}
