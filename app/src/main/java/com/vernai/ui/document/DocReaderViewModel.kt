package com.vernai.ui.document

import androidx.lifecycle.viewModelScope
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.ai.mock.MockDocumentExporter
import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.Language
import com.vernai.document.export.DocumentExporter
import com.vernai.document.export.ExportConfig
import com.vernai.document.export.ExportFormat
import com.vernai.document.processing.DocumentChunk
import com.vernai.document.processing.DocumentChunker
import com.vernai.document.processing.DocumentLimitationsAnalyzer
import com.vernai.document.processing.ExtractedDocument
import com.vernai.document.processing.OfflineDocumentProcessorImpl
import com.vernai.document.processing.TestDocumentType
import com.vernai.document.processing.TestDocuments
import com.vernai.domain.repository.DocumentRepository
import com.vernai.domain.usecase.ExplainDocumentUseCase
import com.vernai.domain.usecase.FormFillingGuidance
import com.vernai.ui.common.MviViewModel
import kotlinx.coroutines.launch
import java.io.File

class DocReaderViewModel(
    private val processor: OfflineDocumentProcessorImpl = OfflineDocumentProcessorImpl(),
    private val chunker: DocumentChunker = DocumentChunker(),
    private val llmEngine: LlmInferenceEngine = MockLlmInferenceEngine(),
    private val exporter: DocumentExporter = MockDocumentExporter(),
    private val repository: DocumentRepository? = null,
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) : MviViewModel<DocReaderUiState, DocReaderUiIntent, DocReaderUiSideEffect>(DocReaderUiState()) {

    private val explainUseCase = ExplainDocumentUseCase(
        llmEngine = llmEngine,
        repository = repository,
        dispatchers = dispatchers
    )

    init {
        // Load scholarship guidelines by default to demonstrate student form explainer
        handleIntent(DocReaderUiIntent.ImportTestDocument(TestDocumentType.SCHOLARSHIP_APPLICATION_FORM))
    }

    override fun handleIntent(intent: DocReaderUiIntent) {
        when (intent) {
            is DocReaderUiIntent.ChangeLanguage -> {
                val extracted = uiState.value.extractedDocument
                val guide = extracted?.let { explainUseCase.extractFormFillingGuide(it.rawText, intent.language) }
                setState {
                    copy(
                        activeLanguage = intent.language,
                        formFillingGuidance = guide
                    )
                }
                summarizeCurrentDocument()
            }
            is DocReaderUiIntent.ToggleRawText -> setState { copy(showRawText = !showRawText) }
            is DocReaderUiIntent.ImportTestDocument -> importTestDocument(intent.type)
            is DocReaderUiIntent.PickDocumentFile -> processPickedBytes(intent.fileName, intent.mimeType, intent.bytes)
            is DocReaderUiIntent.SelectChunk -> setState { copy(selectedChunk = intent.chunk, selectedSnippetExplanation = null) }
            is DocReaderUiIntent.ExplainSelectedChunk -> explainChunk(intent.chunk)
            is DocReaderUiIntent.ClearSelectedSnippet -> setState { copy(selectedSnippetExplanation = null, selectedChunk = null) }
            is DocReaderUiIntent.ProcessDocumentExplanation -> summarizeCurrentDocument()
            is DocReaderUiIntent.ExportExplanation -> exportExplanation(intent.cacheDir)
            is DocReaderUiIntent.ClearError -> setState { copy(extractionError = null) }
            is DocReaderUiIntent.ToggleSpeech -> toggleSpeech()
            is DocReaderUiIntent.SetSpeakingState -> setState { copy(isSpeaking = intent.isSpeaking) }
        }
    }

    private fun processPickedBytes(fileName: String, mimeType: String, bytes: ByteArray) {
        setState {
            copy(
                isExtracting = true,
                extractionError = null,
                explanationReport = null,
                formFillingGuidance = null,
                selectedChunk = null,
                selectedSnippetExplanation = null,
                importedDocumentName = fileName
            )
        }

        viewModelScope.launch(dispatchers.io) {
            val result = processor.extractFromBytes(fileName, mimeType, bytes, uiState.value.activeLanguage)
            when (result) {
                is VernAiResult.Success -> {
                    val extracted = result.data
                    val chunks = chunker.chunkDocument(extracted.rawText)
                    val quality = DocumentLimitationsAnalyzer.analyze(
                        rawText = extracted.rawText,
                        isScanned = extracted.isScannedImage,
                        language = extracted.detectedLanguage
                    )
                    val guide = explainUseCase.extractFormFillingGuide(extracted.rawText, uiState.value.activeLanguage)

                    setState {
                        copy(
                            isExtracting = false,
                            extractedDocument = extracted,
                            chunks = chunks,
                            qualityReport = quality,
                            formFillingGuidance = guide
                        )
                    }
                    summarizeCurrentDocument()
                }
                is VernAiResult.Error -> {
                    setState {
                        copy(
                            isExtracting = false,
                            extractionError = result.message,
                            extractedDocument = null,
                            chunks = emptyList()
                        )
                    }
                    sendSideEffect(DocReaderUiSideEffect.ShowToast("Extraction failed: ${result.message}"))
                }
                is VernAiResult.Loading -> Unit
            }
        }
    }

    private fun importTestDocument(type: TestDocumentType) {
        if (type == TestDocumentType.CORRUPTED_MALFORMED_FILE) {
            setState {
                copy(
                    isExtracting = false,
                    importedDocumentName = type.fileName,
                    extractedDocument = null,
                    chunks = emptyList(),
                    explanationReport = null,
                    formFillingGuidance = null,
                    qualityReport = null,
                    selectedChunk = null,
                    selectedSnippetExplanation = null,
                    extractionError = "Corrupted PDF: 0 readable bytes found in file buffer."
                )
            }
            sendSideEffect(DocReaderUiSideEffect.ShowToast("Corrupted file handled safely."))
            return
        }

        setState {
            copy(
                isExtracting = true,
                extractionError = null,
                explanationReport = null,
                formFillingGuidance = null,
                selectedChunk = null,
                selectedSnippetExplanation = null,
                importedDocumentName = "${type.titleEnglish} (${type.fileName})"
            )
        }

        viewModelScope.launch(dispatchers.default) {
            val rawText = TestDocuments.getSampleDocumentText(type)
            val extracted = ExtractedDocument(
                title = type.fileName,
                rawText = rawText,
                detectedLanguage = uiState.value.activeLanguage,
                pageCount = if (type.isScanned) 2 else 1,
                isScannedImage = type.isScanned
            )
            val chunks = chunker.chunkDocument(rawText)
            val quality = DocumentLimitationsAnalyzer.analyze(
                rawText = rawText,
                isScanned = type.isScanned,
                language = uiState.value.activeLanguage
            )
            val guide = explainUseCase.extractFormFillingGuide(rawText, uiState.value.activeLanguage)

            setState {
                copy(
                    isExtracting = false,
                    extractedDocument = extracted,
                    chunks = chunks,
                    qualityReport = quality,
                    formFillingGuidance = guide
                )
            }

            summarizeCurrentDocument()
        }
    }

    private fun explainChunk(chunk: DocumentChunk) {
        setState { copy(isExplainingSnippet = true, selectedChunk = chunk) }
        viewModelScope.launch(dispatchers.default) {
            val result = explainUseCase.explainSelectedText(chunk.text, uiState.value.activeLanguage)
            when (result) {
                is VernAiResult.Success -> {
                    setState {
                        copy(
                            isExplainingSnippet = false,
                            selectedSnippetExplanation = result.data
                        )
                    }
                }
                is VernAiResult.Error -> {
                    setState { copy(isExplainingSnippet = false) }
                    sendSideEffect(DocReaderUiSideEffect.ShowToast("Explanation error: ${result.message}"))
                }
                is VernAiResult.Loading -> Unit
            }
        }
    }

    private fun summarizeCurrentDocument() {
        val extracted = uiState.value.extractedDocument ?: return
        setState { copy(isSummarizing = true) }
        viewModelScope.launch(dispatchers.default) {
            val result = explainUseCase.summarizeDocument(
                documentTitle = uiState.value.importedDocumentName,
                documentText = extracted.rawText,
                targetLanguage = uiState.value.activeLanguage
            )
            when (result) {
                is VernAiResult.Success -> {
                    setState {
                        copy(
                            isSummarizing = false,
                            explanationReport = result.data
                        )
                    }
                }
                is VernAiResult.Error -> {
                    setState { copy(isSummarizing = false) }
                    sendSideEffect(DocReaderUiSideEffect.ShowToast("Summary error: ${result.message}"))
                }
                is VernAiResult.Loading -> Unit
            }
        }
    }

    private fun exportExplanation(cacheDir: File) {
        val report = uiState.value.explanationReport ?: return
        setState { copy(isExporting = true) }
        viewModelScope.launch(dispatchers.io) {
            val destination = File(cacheDir, "DocumentExplanation_${System.currentTimeMillis()}.pdf")
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

    private fun toggleSpeech() {
        if (uiState.value.isSpeaking) {
            setState { copy(isSpeaking = false) }
            sendSideEffect(DocReaderUiSideEffect.StopSpeaking)
        } else {
            val report = uiState.value.explanationReport
            val textToSpeak = buildString {
                if (report != null) {
                    append(report.summaryInVernacular)
                    if (report.legalDeadlines.isNotEmpty()) {
                        append(". ముఖ్యమైన గడువులు: ")
                        append(report.legalDeadlines.joinToString(", "))
                    }
                    if (report.keyActionPoints.isNotEmpty()) {
                        append(". ముఖ్యమైన అంశాలు: ")
                        append(report.keyActionPoints.joinToString(", "))
                    }
                } else if (uiState.value.selectedSnippetExplanation != null) {
                    append(uiState.value.selectedSnippetExplanation)
                } else if (uiState.value.extractedDocument != null) {
                    append(uiState.value.extractedDocument?.rawText?.take(500))
                }
            }

            if (textToSpeak.isBlank()) {
                sendSideEffect(DocReaderUiSideEffect.ShowToast("చదవడానికి ఎటువంటి వివరణ అందుబాటులో లేదు (No explanation to read)"))
                return
            }

            setState { copy(isSpeaking = true) }
            sendSideEffect(DocReaderUiSideEffect.SpeakText(textToSpeak, uiState.value.activeLanguage))
        }
    }
}
