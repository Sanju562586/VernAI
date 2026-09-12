package com.vernai.ui.document

import androidx.lifecycle.viewModelScope
import com.vernai.ai.mock.MockDocumentExporter
import com.vernai.ai.mock.MockDocumentProcessor
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.Language
import com.vernai.document.export.DocumentExporter
import com.vernai.document.export.ExportConfig
import com.vernai.document.export.ExportFormat
import com.vernai.document.processing.DocumentProcessor
import com.vernai.domain.repository.DocumentRepository
import com.vernai.ui.common.MviViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class DocReaderViewModel(
    private val processor: DocumentProcessor = MockDocumentProcessor(),
    private val exporter: DocumentExporter = MockDocumentExporter(),
    private val repository: DocumentRepository? = null,
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) : MviViewModel<DocReaderUiState, DocReaderUiIntent, DocReaderUiSideEffect>(DocReaderUiState()) {

    init {
        // Load initial default sample
        handleIntent(DocReaderUiIntent.ImportSampleDocument("revenue_notice"))
    }

    override fun handleIntent(intent: DocReaderUiIntent) {
        when (intent) {
            is DocReaderUiIntent.ChangeLanguage -> setState { copy(activeLanguage = intent.language) }
            is DocReaderUiIntent.ToggleRawText -> setState { copy(showRawText = !showRawText) }
            is DocReaderUiIntent.ImportSampleDocument -> importSample(intent.sampleType)
            is DocReaderUiIntent.ProcessDocumentExplanation -> summarizeDocument()
            is DocReaderUiIntent.ExportExplanation -> exportExplanation(intent.cacheDir)
        }
    }

    private fun importSample(type: String) {
        setState { copy(isExtracting = true, explanationReport = null) }
        viewModelScope.launch(dispatchers.io) {
            val fileName = when (type) {
                "electricity" -> "విద్యుత్ బోర్డు నోటీసు (TSSPDCL Notice.pdf)"
                else -> "పట్టాదార్ పాస్ పుస్తకం నోటీసు (Revenue Notice.pdf)"
            }
            val result = processor.extractText(File(fileName), uiState.value.activeLanguage)
            when (result) {
                is VernAiResult.Success -> {
                    setState {
                        copy(
                            isExtracting = false,
                            importedDocumentName = fileName,
                            extractedDocument = result.data
                        )
                    }
                    summarizeDocument()
                }
                is VernAiResult.Error -> {
                    setState { copy(isExtracting = false, errorMessage = result.message) }
                }
                is VernAiResult.Loading -> Unit
            }
        }
    }

    private fun summarizeDocument() {
        val extracted = uiState.value.extractedDocument ?: return
        setState { copy(isSummarizing = true) }
        viewModelScope.launch(dispatchers.default) {
            delay(700) // Simulated local LLM explanation
            val report = ExplanationReport(
                id = UUID.randomUUID().toString(),
                sourceDocumentName = uiState.value.importedDocumentName,
                extractedCharacterCount = extracted.rawText.length,
                summaryInVernacular = "ఈ పత్రం రెవెన్యూ శాఖ నుండి వచ్చిన అధికారిక నోటీసు. సర్వే నంబర్ 142/A లోని 2.50 ఎకరాల భూమి పట్టా వివరాల ధృవీకరణ కొరకు జారీ చేయబడినది.",
                keyActionPoints = listOf(
                    "మీ ఆధార్ కార్డు మరియు పహాణీ నకలుతో తహశీల్దార్ ఆఫీసుకు వెళ్లాలి.",
                    "సర్వీస్ ఛార్జీగా రూ. 150/- రసీదు పొందాలి.",
                    "భూమి సరిహద్దు కొలతల దరఖాస్తును పూర్తి చేయాలి."
                ),
                legalDeadlines = listOf(
                    "గడువు తేదీ: నోటీసు అందిన 30 రోజులలోపు తప్పనిసరిగా హాజరుకావలెను.",
                    "గడువు ముగిసినచో నోటీసు చట్టరీత్యా రద్దగును."
                ),
                targetLanguage = uiState.value.activeLanguage
            )

            // Save to room if repo available
            repository?.saveExplanation(report)

            setState {
                copy(
                    isSummarizing = false,
                    explanationReport = report
                )
            }
        }
    }

    private fun exportExplanation(cacheDir: File) {
        val report = uiState.value.explanationReport ?: return
        setState { copy(isExporting = true) }
        viewModelScope.launch(dispatchers.io) {
            val destination = File(cacheDir, "Explanation_${System.currentTimeMillis()}.pdf")
            val result = exporter.exportExplanationReport(
                report = report,
                destinationFile = destination,
                config = ExportConfig(format = ExportFormat.PDF, targetLanguage = uiState.value.activeLanguage)
            )
            when (result) {
                is VernAiResult.Success -> {
                    setState { copy(isExporting = false) }
                    sendSideEffect(DocReaderUiSideEffect.OpenExportedFile(result.data))
                }
                is VernAiResult.Error -> {
                    setState { copy(isExporting = false) }
                    sendSideEffect(DocReaderUiSideEffect.ShowToast("Export Error: ${result.message}"))
                }
                is VernAiResult.Loading -> Unit
            }
        }
    }
}
