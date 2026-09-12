package com.vernai.ui.document

import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.Language
import com.vernai.document.processing.ExtractedDocument
import com.vernai.ui.common.UiIntent
import com.vernai.ui.common.UiSideEffect
import com.vernai.ui.common.UiState
import java.io.File

data class DocReaderUiState(
    val activeLanguage: Language = Language.TELUGU,
    val isExtracting: Boolean = false,
    val isSummarizing: Boolean = false,
    val isExporting: Boolean = false,
    val importedDocumentName: String = "పట్టాదార్ పాస్ పుస్తకం నోటీసు (Revenue Notice.pdf)",
    val extractedDocument: ExtractedDocument? = null,
    val explanationReport: ExplanationReport? = null,
    val showRawText: Boolean = false,
    val errorMessage: String? = null
) : UiState

sealed interface DocReaderUiIntent : UiIntent {
    data class ChangeLanguage(val language: Language) : DocReaderUiIntent
    data class ImportSampleDocument(val sampleType: String) : DocReaderUiIntent
    data object ToggleRawText : DocReaderUiIntent
    data object ProcessDocumentExplanation : DocReaderUiIntent
    data class ExportExplanation(val cacheDir: File) : DocReaderUiIntent
}

sealed interface DocReaderUiSideEffect : UiSideEffect {
    data class ShowToast(val message: String) : DocReaderUiSideEffect
    data class OpenExportedFile(val file: File) : DocReaderUiSideEffect
}
