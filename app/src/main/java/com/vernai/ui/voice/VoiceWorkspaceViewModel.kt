package com.vernai.ui.voice

import androidx.lifecycle.viewModelScope
import com.vernai.ai.asr.AsrEngine
import com.vernai.ai.asr.AsrState
import com.vernai.ai.llm.GenerationParameters
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.ai.mock.MockAsrEngine
import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.result.VernAiResult
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
    private var decibelMonitorJob: Job? = null

    init {
        viewModelScope.launch(dispatchers.asrInference) {
            asrEngine.initialize(uiState.value.activeLanguage)
        }

        decibelMonitorJob = viewModelScope.launch(dispatchers.main) {
            asrEngine.state.collect { asrState ->
                if (asrState is AsrState.Recording) {
                    setState { copy(audioDecibels = asrState.decibels) }
                }
            }
        }
    }

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
                // Ensure remaining buffer is flushed from ASR
                val stopResult = asrEngine.stopLiveTranscription()
                val finalPrompt = if (stopResult is VernAiResult.Success && stopResult.data.text.isNotBlank()) {
                    stopResult.data.text
                } else {
                    uiState.value.liveTranscript
                }

                val detected = if (finalPrompt.contains("అమ్మిన") || finalPrompt.contains("రూపాయలు") || finalPrompt.contains("కేజీ")) {
                    DetectedIntentType.SALES_RECORD
                } else {
                    DetectedIntentType.COMPLAINT_LETTER
                }

                val llmPrompt = if (detected == DetectedIntentType.SALES_RECORD) {
                    "విశ్లేషించండి (Extract Sales items): $finalPrompt"
                } else {
                    "వినతిపత్రం (Draft Complaint): $finalPrompt"
                }
                val llmResult = llmEngine.generateCompleteText(llmPrompt)

                val preview = if (llmResult is VernAiResult.Success && llmResult.data.isNotBlank()) {
                    llmResult.data
                } else {
                    when (detected) {
                        DetectedIntentType.SALES_RECORD ->
                            "గుర్తించిన అమ్మకాలు (Sales Detected):\n• టమాటా: 5 kg = ₹200\n• నూనె ప్యాకెట్లు: 2 = ₹260\nమొత్తం: ₹460"
                        DetectedIntentType.COMPLAINT_LETTER ->
                            "ఫిర్యాదు ముసాయిదా (Complaint Draft):\nశాంతినగర్ పరిధిలో వీధి దీపాల సమస్య పరిష్కారం కోసం వినతిపత్రం."
                        else -> "ఆడియో విశ్లేషణ పూర్తయింది."
                    }
                }

                setState {
                    copy(
                        liveTranscript = finalPrompt,
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
                    .catch { e ->
                        val friendlyMessage = if (e is SecurityException) {
                            "మైక్రోఫోన్ అనుమతి నిరాకరించబడింది (Microphone permission denied)"
                        } else {
                            e.message ?: "ఆడియో రికార్డింగ్ విఫలమైంది (Audio recording failed)"
                        }
                        setState { copy(errorMessage = friendlyMessage, isRecording = false, stage = ProcessingStage.IDLE) }
                    }
                    .collect { partial ->
                        setState {
                            copy(
                                liveTranscript = partial.text
                            )
                        }
                    }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        timerJob?.cancel()
        decibelMonitorJob?.cancel()
    }
}
