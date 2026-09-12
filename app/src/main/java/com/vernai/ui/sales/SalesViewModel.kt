package com.vernai.ui.sales

import androidx.lifecycle.viewModelScope
import com.vernai.ai.asr.AsrEngine
import com.vernai.ai.mock.MockAsrEngine
import com.vernai.ai.mock.MockDocumentExporter
import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.ai.parser.SalesLogParser
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.Language
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import com.vernai.document.export.DocumentExporter
import com.vernai.document.export.ExportConfig
import com.vernai.document.export.ExportFormat
import com.vernai.domain.repository.SalesLogRepository
import com.vernai.domain.usecase.ExtractSalesLogUseCase
import com.vernai.ui.common.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class SalesViewModel(
    private val asrEngine: AsrEngine = MockAsrEngine(),
    private val salesLogRepository: SalesLogRepository? = null,
    private val exporter: DocumentExporter = MockDocumentExporter(),
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) : MviViewModel<SalesUiState, SalesUiIntent, SalesUiSideEffect>(
    SalesUiState(
        currentLog = SalesLog(
            rawSpokenText = "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు, 2 నూనె ప్యాకెట్లు 260 రూపాయలు అమ్మిన.",
            detectedLanguage = Language.TELUGU,
            items = listOf(
                SalesItem(id = "1", originalTerm = "టమాటా (Tomato)", standardName = "Tomato", quantity = 5.0, unit = "kg", unitPrice = 40.0, totalPrice = 200.0),
                SalesItem(id = "2", originalTerm = "నూనె ప్యాకెట్లు (Oil)", standardName = "Cooking Oil", quantity = 2.0, unit = "packet", unitPrice = 130.0, totalPrice = 260.0)
            ),
            grandTotal = 460.0
        )
    )
) {
    private val extractSalesLogUseCase = ExtractSalesLogUseCase(
        llmEngine = MockLlmInferenceEngine(),
        parser = SalesLogParser(),
        repository = salesLogRepository ?: object : SalesLogRepository {
            override fun getSalesLogsStream() = kotlinx.coroutines.flow.flowOf(emptyList<SalesLog>())
            override suspend fun getSalesLogById(id: String) = null
            override suspend fun saveSalesLog(salesLog: SalesLog) = VernAiResult.Success(Unit)
            override suspend fun deleteSalesLog(id: String) = VernAiResult.Success(Unit)
        },
        inferenceLock = InferenceLock(),
        dispatchers = dispatchers
    )

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
                    salesLogRepository?.saveSalesLog(logToSave)
                    sendSideEffect(SalesUiSideEffect.ShowToast("అమ్మకాల లెడ్జర్ భద్రపరచబడింది (Saved to Room DB)"))
                }
            }
            is SalesUiIntent.ExportLedger -> {
                exportLedger(intent.destinationFile)
            }
        }
    }

    private fun handleToggleRecording() {
        if (uiState.value.isRecording) {
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

    private fun exportLedger(destinationFile: File) {
        val log = uiState.value.currentLog ?: return
        viewModelScope.launch(dispatchers.io) {
            val result = exporter.exportSalesLog(
                salesLog = log,
                destinationFile = destinationFile,
                config = ExportConfig(format = ExportFormat.PDF, targetLanguage = uiState.value.selectedLanguage)
            )
            when (result) {
                is VernAiResult.Success -> sendSideEffect(SalesUiSideEffect.ExportCompleted(result.data))
                is VernAiResult.Error -> sendSideEffect(SalesUiSideEffect.ShowToast("Export Error: ${result.message}"))
                is VernAiResult.Loading -> Unit
            }
        }
    }

    private fun observeHistory() {
        val repo = salesLogRepository ?: return
        viewModelScope.launch(dispatchers.io) {
            repo.getSalesLogsStream()
                .flowOn(dispatchers.io)
                .collect { history ->
                    setState { copy(historyLogs = history) }
                }
        }
    }
}
