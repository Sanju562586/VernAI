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
                val currentTranscript = uiState.value.liveTranscript.trim()
                when (uiState.value.detectedIntent) {
                    DetectedIntentType.SALES_RECORD -> {
                        sendSideEffect(VoiceUiSideEffect.NavigateTo(VernAiNavDestination.SalesLedger(currentTranscript.ifBlank { null })))
                    }
                    DetectedIntentType.COMPLAINT_LETTER -> {
                        sendSideEffect(VoiceUiSideEffect.NavigateTo(VernAiNavDestination.ComplaintDrafting(currentTranscript.ifBlank { null })))
                    }
                    else -> sendSideEffect(VoiceUiSideEffect.ShowToast("ఫలితం విజయవంతంగా రూపొందించబడింది (Result processed)"))
                }
            }
            is VoiceUiIntent.SimulateSpeech -> {
                recordingJob?.cancel()
                timerJob?.cancel()
                val spokenText = intent.text.trim()
                val detected = if (isSalesIntent(spokenText)) {
                    DetectedIntentType.SALES_RECORD
                } else {
                    DetectedIntentType.COMPLAINT_LETTER
                }

                setState {
                    copy(
                        isRecording = false,
                        stage = ProcessingStage.REASONING_LLM,
                        liveTranscript = spokenText,
                        detectedIntent = detected,
                        errorMessage = null
                    )
                }

                viewModelScope.launch(dispatchers.default) {
                    val llmPrompt = if (detected == DetectedIntentType.SALES_RECORD) {
                        "Extract Sales items from: $spokenText in ${uiState.value.activeLanguage.englishName}"
                    } else {
                        "Draft formal grievance complaint from: $spokenText in ${uiState.value.activeLanguage.englishName}"
                    }
                    val llmResult = llmEngine.generateCompleteText(llmPrompt)

                    val preview = if (llmResult is VernAiResult.Success && llmResult.data.isNotBlank()) {
                        llmResult.data
                    } else {
                        buildLocalizedPreview(detected, spokenText, uiState.value.activeLanguage)
                    }

                    setState {
                        copy(
                            stage = ProcessingStage.COMPLETED,
                            extractedResultPreview = preview
                        )
                    }
                }
            }
        }
    }

    private fun isSalesIntent(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("అమ్మిన") || lower.contains("రూపాయలు") || lower.contains("కేజీ") ||
               lower.contains("விற்ற") || lower.contains("ரூபாய்") || lower.contains("கிலோ") || lower.contains("லிட்டர்") || lower.contains("ரொக்கம்") ||
               lower.contains("बेचा") || lower.contains("रुपये") || lower.contains("किलो") || lower.contains("लीटर") || lower.contains("नकद") ||
               lower.contains("sold") || lower.contains("rupee") || lower.contains("kg") || lower.contains("liter") || lower.contains("cash") || lower.contains("packet")
    }

    private fun buildLocalizedPreview(detected: DetectedIntentType, transcript: String, language: Language): String {
        return if (detected == DetectedIntentType.SALES_RECORD) {
            when (language) {
                Language.TAMIL -> "கண்டறியப்பட்ட விற்பனை (Sales Detected):\n$transcript\n\nபதிவேட்டில் சேர்க்க தயாராக உள்ளது (Ready for Ledger)."
                Language.HINDI, Language.MARATHI -> "पहचानी गई बिक्री (Sales Detected):\n$transcript\n\nबिक्री खाते में जोड़ने के लिए तैयार है।"
                Language.ENGLISH -> "Detected Sales Record:\n$transcript\n\nReady for Sales Ledger entry."
                else -> "గుర్తించిన అమ్మకాలు (Sales Spoken):\n$transcript\n\nలెడ్జర్ నమోదు కోసం సిద్ధంగా ఉంది."
            }
        } else {
            when (language) {
                Language.TAMIL -> "முறையான புகார் மனு (Complaint Draft):\n$transcript\n\nஅதிகாரப்பூர்வ மனுவாக மாற்ற தயாராக உள்ளது."
                Language.HINDI, Language.MARATHI -> "शिकायत पत्र प्रारूप (Complaint Draft):\n$transcript\n\nऔपचारिक पत्र में बदलने के लिए तैयार है।"
                Language.ENGLISH -> "Formal Grievance Complaint:\n$transcript\n\nReady for administrative petition generation."
                else -> "ఫిర్యాదు ముసాయిదా (Complaint Spoken):\n$transcript\n\nస్థానిక AI ద్వారా అధికారిక వినతిపత్రంగా రూపొందించడానికి సిద్ధంగా ఉంది."
            }
        }
    }

    private fun handleToggleRecording() {
        if (uiState.value.isRecording) {
            // Stop recording -> Transition to LLM reasoning
            recordingJob?.cancel()
            timerJob?.cancel()
            val currentText = uiState.value.liveTranscript.trim()
            val immediateDetected = if (isSalesIntent(currentText)) {
                DetectedIntentType.SALES_RECORD
            } else {
                DetectedIntentType.COMPLAINT_LETTER
            }
            setState { copy(isRecording = false, stage = ProcessingStage.REASONING_LLM, detectedIntent = immediateDetected) }

            viewModelScope.launch(dispatchers.default) {
                // Ensure remaining buffer is flushed from ASR
                val stopResult = asrEngine.stopLiveTranscription()
                val finalPrompt = if (stopResult is VernAiResult.Success && stopResult.data.text.isNotBlank()) {
                    stopResult.data.text
                } else {
                    currentText
                }

                if (finalPrompt.isBlank()) {
                    val emptyMsg = when (uiState.value.activeLanguage) {
                        Language.TAMIL -> "குரல் கேட்கவில்லை. தயவுசெய்து மைக்கில் தெளிவாக பேசவும் (No speech recognized. Please speak clearly)."
                        Language.HINDI, Language.MARATHI -> "आवाज स्पष्ट सुनाई नहीं दी। कृपया माइक में स्पष्ट बोलें (No speech recognized. Please speak clearly)."
                        Language.ENGLISH -> "No speech recognized. Please speak clearly into the microphone."
                        else -> "ధ్వని గుర్తించబడలేదు. దయచేసి మైక్రోఫోన్ వద్ద స్పష్టంగా మాట్లాడండి (No speech recognized. Please speak clearly)."
                    }
                    setState {
                        copy(
                            isRecording = false,
                            stage = ProcessingStage.IDLE,
                            errorMessage = emptyMsg
                        )
                    }
                    return@launch
                }

                val detected = if (isSalesIntent(finalPrompt)) {
                    DetectedIntentType.SALES_RECORD
                } else {
                    DetectedIntentType.COMPLAINT_LETTER
                }

                val llmPrompt = if (detected == DetectedIntentType.SALES_RECORD) {
                    "Extract Sales items from: $finalPrompt in ${uiState.value.activeLanguage.englishName}"
                } else {
                    "Draft formal grievance complaint from: $finalPrompt in ${uiState.value.activeLanguage.englishName}"
                }
                val llmResult = llmEngine.generateCompleteText(llmPrompt)

                val preview = if (llmResult is VernAiResult.Success && llmResult.data.isNotBlank()) {
                    llmResult.data
                } else {
                    buildLocalizedPreview(detected, finalPrompt, uiState.value.activeLanguage)
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
                    if (!uiState.value.isRecording) break
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
                        val currentText = partial.text
                        if (partial.isFinal) {
                            timerJob?.cancel()
                            val text = currentText.trim()
                            if (text.isBlank()) return@collect
                            val detected = if (isSalesIntent(text)) {
                                DetectedIntentType.SALES_RECORD
                            } else {
                                DetectedIntentType.COMPLAINT_LETTER
                            }
                            setState {
                                copy(
                                    liveTranscript = text,
                                    isRecording = false,
                                    stage = ProcessingStage.REASONING_LLM,
                                    detectedIntent = detected
                                )
                            }
                        } else {
                            setState {
                                copy(
                                    liveTranscript = currentText
                                )
                            }
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
