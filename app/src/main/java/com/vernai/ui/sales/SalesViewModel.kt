package com.vernai.ui.sales

import androidx.lifecycle.viewModelScope
import com.vernai.ai.asr.AsrEngine
import com.vernai.ai.mock.MockAsrEngine
import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.ai.parser.SalesLogParser
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.Language
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import com.vernai.core.model.SalesValidationStatus
import com.vernai.document.export.DocumentExporter
import com.vernai.document.export.ExportConfig
import com.vernai.document.export.ExportFormat
import com.vernai.document.export.LocalDocumentExporter
import com.vernai.document.export.SalesLedgerExporter
import com.vernai.domain.repository.SalesLogRepository
import com.vernai.domain.usecase.ExtractSalesLogUseCase
import com.vernai.sales.processing.DuplicatePreventionEngine
import com.vernai.sales.processing.SalesArithmeticValidator
import com.vernai.sales.processing.TeluguSalesParser
import com.vernai.ui.common.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SalesViewModel(
    private val asrEngine: AsrEngine = MockAsrEngine(),
    private val salesLogRepository: SalesLogRepository? = null,
    private val exporter: DocumentExporter = LocalDocumentExporter(),
    private val ledgerExporter: SalesLedgerExporter = SalesLedgerExporter(),
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) : MviViewModel<SalesUiState, SalesUiIntent, SalesUiSideEffect>(
    SalesUiState(
        currentLog = SalesLog(
            rawSpokenText = "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు, 2 నూనె ప్యాకెట్లు 260 రూపాయలు నగదు అమ్మిన.",
            detectedLanguage = Language.TELUGU,
            items = listOf(
                SalesItem(
                    id = "1",
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    originalTerm = "టమాటా (Tomato)",
                    standardName = "Tomato",
                    quantity = 5.0,
                    unit = "కేజీ (kg)",
                    unitPrice = 40.0,
                    totalPrice = 200.0,
                    notes = "నగదు",
                    validationStatus = SalesValidationStatus.VERIFIED
                ),
                SalesItem(
                    id = "2",
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    originalTerm = "నూనె ప్యాకెట్లు (Oil)",
                    standardName = "Cooking Oil",
                    quantity = 2.0,
                    unit = "ప్యాకెట్ (packet)",
                    unitPrice = 130.0,
                    totalPrice = 260.0,
                    notes = "నగదు",
                    validationStatus = SalesValidationStatus.VERIFIED
                )
            ),
            grandTotal = 460.0
        )
    )
) {
    private val extractSalesLogUseCase = ExtractSalesLogUseCase(
        llmEngine = MockLlmInferenceEngine(),
        parser = SalesLogParser(),
        teluguParser = TeluguSalesParser(),
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
            is SalesUiIntent.UpdateItem -> handleUpdateItem(intent.item)
            is SalesUiIntent.AddNewItem -> handleAddNewItem(intent.item)
            is SalesUiIntent.DeleteItem -> handleDeleteItem(intent.itemId)
            is SalesUiIntent.SelectClarificationItem -> setState { copy(activeClarificationItem = intent.item) }
            is SalesUiIntent.ResolveClarification -> handleResolveClarification(intent)
            is SalesUiIntent.MergeDuplicate -> handleMergeDuplicate(intent.existingItemId, intent.duplicateItemId)
            is SalesUiIntent.DismissDuplicate -> handleDismissDuplicate(intent.itemId)
            is SalesUiIntent.FilterByDate -> setState { copy(filterDate = intent.date) }
            is SalesUiIntent.SaveLogToLedger -> handleSaveLogToLedger()
            is SalesUiIntent.ExportLedger -> exportLedger(intent.destinationFile, intent.format)
        }
    }

    private fun handleUpdateItem(item: SalesItem) {
        val current = uiState.value.currentLog ?: return
        val reconciled = SalesArithmeticValidator.validateAndReconcile(item).item
        val updatedItems = current.items.map { if (it.id == item.id) reconciled else it }
        val updatedLog = current.copy(items = updatedItems, grandTotal = updatedItems.sumOf { it.totalPrice })
        setState { copy(currentLog = updatedLog, activeClarificationItem = null) }
    }

    private fun handleAddNewItem(item: SalesItem) {
        val current = uiState.value.currentLog ?: SalesLog(
            rawSpokenText = "మాన్యువల్ ఎంట్రీ",
            detectedLanguage = uiState.value.selectedLanguage,
            items = emptyList(),
            grandTotal = 0.0
        )
        val reconciled = SalesArithmeticValidator.validateAndReconcile(item).item
        val dupCheck = DuplicatePreventionEngine.checkDuplicate(reconciled, current.items)
        val finalItem = if (dupCheck.isDuplicate) {
            reconciled.copy(
                validationStatus = SalesValidationStatus.DUPLICATE_WARNING,
                clarificationPrompt = dupCheck.messageInTelugu
            )
        } else {
            reconciled
        }
        val newItems = current.items + finalItem
        val updatedLog = current.copy(items = newItems, grandTotal = newItems.sumOf { it.totalPrice })
        setState { copy(currentLog = updatedLog) }
    }

    private fun handleDeleteItem(itemId: String) {
        val current = uiState.value.currentLog ?: return
        val filteredItems = current.items.filterNot { it.id == itemId }
        val updatedLog = current.copy(items = filteredItems, grandTotal = filteredItems.sumOf { it.totalPrice })
        setState {
            copy(
                currentLog = updatedLog,
                activeClarificationItem = if (activeClarificationItem?.id == itemId) null else activeClarificationItem
            )
        }
    }

    private fun handleResolveClarification(intent: SalesUiIntent.ResolveClarification) {
        val current = uiState.value.currentLog ?: return
        val item = current.items.find { it.id == intent.itemId } ?: return
        val resolved = SalesArithmeticValidator.resolveClarification(
            item = item,
            resolvedQuantity = intent.resolvedQuantity,
            resolvedUnitPrice = intent.resolvedUnitPrice,
            resolvedTotal = intent.resolvedTotal,
            resolvedNotes = intent.resolvedNotes
        )
        val updatedItems = current.items.map { if (it.id == intent.itemId) resolved else it }
        val updatedLog = current.copy(items = updatedItems, grandTotal = updatedItems.sumOf { it.totalPrice })
        setState { copy(currentLog = updatedLog, activeClarificationItem = null) }
        sendSideEffect(SalesUiSideEffect.ShowToast("వివరణ నమోదు చేయబడింది (${resolved.originalTerm})"))
    }

    private fun handleMergeDuplicate(existingItemId: String, duplicateItemId: String) {
        val current = uiState.value.currentLog ?: return
        val existing = current.items.find { it.id == existingItemId } ?: return
        val duplicate = current.items.find { it.id == duplicateItemId } ?: return
        val merged = DuplicatePreventionEngine.mergeItems(existing, duplicate)

        val updatedItems = current.items.filterNot { it.id == duplicateItemId }.map {
            if (it.id == existingItemId) merged else it
        }
        val updatedLog = current.copy(items = updatedItems, grandTotal = updatedItems.sumOf { it.totalPrice })
        setState { copy(currentLog = updatedLog) }
        sendSideEffect(SalesUiSideEffect.ShowToast("వస్తువులు విలీనం చేయబడ్డాయి (Merged: ${merged.quantity} ${merged.unit})"))
    }

    private fun handleDismissDuplicate(itemId: String) {
        val current = uiState.value.currentLog ?: return
        val updatedItems = current.items.map {
            if (it.id == itemId) it.copy(validationStatus = SalesValidationStatus.VERIFIED, clarificationPrompt = null) else it
        }
        val updatedLog = current.copy(items = updatedItems, grandTotal = updatedItems.sumOf { it.totalPrice })
        setState { copy(currentLog = updatedLog) }
    }

    private fun handleSaveLogToLedger() {
        val logToSave = uiState.value.currentLog ?: return
        viewModelScope.launch(dispatchers.io) {
            val result = salesLogRepository?.saveSalesLog(logToSave) ?: VernAiResult.Success(Unit)
            when (result) {
                is VernAiResult.Success -> {
                    sendSideEffect(SalesUiSideEffect.ShowToast("అమ్మకాల లెడ్జర్ భద్రపరచబడింది (Saved to Room DB)"))
                }
                is VernAiResult.Error -> {
                    sendSideEffect(SalesUiSideEffect.ShowToast("భద్రపరచడం విఫలమైంది: ${result.message}"))
                }
                is VernAiResult.Loading -> Unit
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
        setState { copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch(dispatchers.default) {
            when (val result = extractSalesLogUseCase(transcript, language)) {
                is VernAiResult.Success -> {
                    val log = result.data
                    // Check if any item needs clarification right away
                    val firstAmbiguous = log.items.find {
                        it.validationStatus == SalesValidationStatus.CLARIFICATION_NEEDED ||
                        it.validationStatus == SalesValidationStatus.ARITHMETIC_MISMATCH
                    }
                    setState {
                        copy(
                            isProcessing = false,
                            currentLog = log,
                            activeClarificationItem = firstAmbiguous
                        )
                    }
                }
                is VernAiResult.Error -> {
                    setState { copy(isProcessing = false, errorMessage = result.message) }
                }
                is VernAiResult.Loading -> Unit
            }
        }
    }

    private fun exportLedger(destinationFile: File, format: ExportFormat) {
        val log = uiState.value.currentLog ?: return
        viewModelScope.launch(dispatchers.io) {
            val result = when (format) {
                ExportFormat.CSV -> ledgerExporter.exportToCsv(log, destinationFile)
                ExportFormat.XLSX -> ledgerExporter.exportToXlsx(log, destinationFile)
                ExportFormat.PDF, ExportFormat.DOCX -> {
                    exporter.exportSalesLog(
                        salesLog = log,
                        destinationFile = destinationFile,
                        config = ExportConfig(format = format, targetLanguage = uiState.value.selectedLanguage)
                    )
                }
            }
            when (result) {
                is VernAiResult.Success -> sendSideEffect(SalesUiSideEffect.ExportCompleted(result.data, format))
                is VernAiResult.Error -> sendSideEffect(SalesUiSideEffect.ShowToast("ఎగుమతి విఫలమైంది: ${result.message}"))
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
