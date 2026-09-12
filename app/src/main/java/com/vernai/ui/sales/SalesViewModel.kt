package com.vernai.ui.sales

import androidx.lifecycle.viewModelScope
import com.vernai.ai.asr.AsrEngine
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.Language
import com.vernai.domain.repository.SalesLogRepository
import com.vernai.domain.usecase.ExtractSalesLogUseCase
import com.vernai.ui.common.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class SalesViewModel(
    private val asrEngine: AsrEngine,
    private val extractSalesLogUseCase: ExtractSalesLogUseCase,
    private val salesLogRepository: SalesLogRepository,
    private val dispatchers: VernAiDispatchers
) : MviViewModel<SalesUiState, SalesUiIntent, SalesUiSideEffect>(SalesUiState()) {

    private var recordingJob: Job? = null

    init {
        observeHistory()
    }

    override fun handleIntent(intent: SalesUiIntent) {
        when (intent) {
            is SalesUiIntent.ChangeLanguage -> setState { copy(selectedLanguage = intent.language) }
            is SalesUiIntent.ToggleRecording -> handleToggleRecording()
            is SalesUiIntent.SubmitManualTranscript -> processTranscript(intent.text, uiState.value.selectedLanguage)
            is SalesUiIntent.UpdateItem -> {
                val current = uiState.value.currentLog ?: return
                val updatedItems = current.items.map { if (it.id == intent.item.id) intent.item else it }
                val updatedLog = current.copy(items = updatedItems, grandTotal = updatedItems.sumOf { it.totalPrice })
                setState { copy(currentLog = updatedLog) }
            }
            is SalesUiIntent.DeleteItem -> {
                val current = uiState.value.currentLog ?: return
                val filteredItems = current.items.filterNot { it.id == intent.itemId }
                val updatedLog = current.copy(items = filteredItems, grandTotal = filteredItems.sumOf { it.totalPrice })
                setState { copy(currentLog = updatedLog) }
            }
            is SalesUiIntent.SaveLogToLedger -> {
                val logToSave = uiState.value.currentLog ?: return
                viewModelScope.launch(dispatchers.io) {
                    salesLogRepository.saveSalesLog(logToSave)
                    sendSideEffect(SalesUiSideEffect.ShowToast("లాగ్ విజయవంతంగా సేవ్ చేయబడింది (Log Saved)"))
                    setState { copy(currentLog = null, spokenTranscript = "") }
                }
            }
            is SalesUiIntent.ExportLedger -> {
                // Handled via Exporter in feature module
            }
        }
    }

    private fun handleToggleRecording() {
        if (uiState.value.isRecording) {
            // Stop recording and process
            recordingJob?.cancel()
            setState { copy(isRecording = false, isProcessing = true) }
            viewModelScope.launch(dispatchers.io) {
                val finalResult = asrEngine.stopLiveTranscription()
                when (finalResult) {
                    is VernAiResult.Success -> {
                        processTranscript(finalResult.data.text, uiState.value.selectedLanguage)
                    }
                    is VernAiResult.Error -> {
                        setState { copy(isProcessing = false, errorMessage = finalResult.message) }
                    }
                    is VernAiResult.Loading -> Unit
                }
            }
        } else {
            // Start recording
            setState { copy(isRecording = true, errorMessage = null, spokenTranscript = "") }
            recordingJob = viewModelScope.launch(dispatchers.asrInference) {
                asrEngine.startLiveTranscription(uiState.value.selectedLanguage)
                    .catch { e -> setState { copy(isRecording = false, errorMessage = e.localizedMessage) } }
                    .collect { partial ->
                        setState { copy(spokenTranscript = partial.text) }
                    }
            }
        }
    }

    private fun processTranscript(transcript: String, language: Language) {
        if (transcript.isBlank()) {
            setState { copy(isProcessing = false) }
            return
        }
        setState { copy(isProcessing = true) }
        viewModelScope.launch(dispatchers.default) {
            when (val result = extractSalesLogUseCase(transcript, language)) {
                is VernAiResult.Success -> {
                    setState { copy(isProcessing = false, currentLog = result.data) }
                }
                is VernAiResult.Error -> {
                    setState { copy(isProcessing = false, errorMessage = result.message) }
                }
                is VernAiResult.Loading -> Unit
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch(dispatchers.io) {
            salesLogRepository.getSalesLogsStream()
                .flowOn(dispatchers.io)
                .collect { history ->
                    setState { copy(historyLogs = history) }
                }
        }
    }
}
