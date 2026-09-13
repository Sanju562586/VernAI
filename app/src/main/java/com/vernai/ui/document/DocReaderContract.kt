package com.vernai.ui.document

import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.Language
import com.vernai.document.processing.DocumentChunk
import com.vernai.document.processing.DocumentQualityReport
import com.vernai.document.processing.ExtractedDocument
import com.vernai.document.processing.TestDocumentType
import com.vernai.ui.common.UiIntent
import com.vernai.ui.common.UiSideEffect
import com.vernai.ui.common.UiState
import java.io.File

data class DocReaderUiState(
    val activeLanguage: Language = Language.TELUGU,
    val isExtracting: Boolean = false,
    val isSummarizing: Boolean = false,
    val isExplainingSnippet: Boolean = false,
    val isExporting: Boolean = false,
    val importedDocumentName: String = "పట్టాదార్ పాస్ పుస్తకం నోటీసు (Revenue Notice.pdf)",
    val extractedDocument: ExtractedDocument? = null,
    val chunks: List<DocumentChunk> = emptyList(),
    val selectedChunk: DocumentChunk? = null,
    val selectedSnippetExplanation: String? = null,
    val explanationReport: ExplanationReport? = null,
    val qualityReport: DocumentQualityReport? = null,
    val showRawText: Boolean = false,
    val extractionError: String? = null
) : UiState

sealed interface DocReaderUiIntent : UiIntent {
    data class ChangeLanguage(val language: Language) : DocReaderUiIntent
    data class PickDocumentFile(val fileName: String, val mimeType: String, val bytes: ByteArray) : DocReaderUiIntent
    data class ImportTestDocument(val type: TestDocumentType) : DocReaderUiIntent
    data class SelectChunk(val chunk: DocumentChunk) : DocReaderUiIntent
    data class ExplainSelectedChunk(val chunk: DocumentChunk) : DocReaderUiIntent
    data object ClearSelectedSnippet : DocReaderUiIntent
    data object ToggleRawText : DocReaderUiIntent
    data object ProcessDocumentExplanation : DocReaderUiIntent
    data class ExportExplanation(val cacheDir: File) : DocReaderUiIntent
    data object ClearError : DocReaderUiIntent
}

sealed interface DocReaderUiSideEffect : UiSideEffect {
    data class ShowToast(val message: String) : DocReaderUiSideEffect
    data class OpenExportedFile(val file: File) : DocReaderUiSideEffect
}
