package com.vernai.ui.letter

import com.vernai.core.model.Language
import com.vernai.document.export.ExportFormat
import com.vernai.domain.model.letter.LetterRecipient
import com.vernai.domain.model.letter.LetterType
import com.vernai.domain.model.letter.StructuredLetter
import com.vernai.ui.common.UiIntent
import com.vernai.ui.common.UiSideEffect
import com.vernai.ui.common.UiState
import java.io.File

data class LetterUiState(
    val selectedLanguage: Language = Language.TELUGU,
    val letterType: LetterType = LetterType.COMPLAINT,
    val voiceTranscript: String = "",
    val recipientDesignation: String = "",
    val recipientDepartment: String = "",
    val recipientAddress: String = "",
    val userFactsText: String = "",
    val location: String = "",
    val date: String = "",
    val applicantName: String = "",

    // Output & Structured Preview fields
    val structuredLetter: StructuredLetter? = null,
    val subject: String = "",
    val salutation: String = "",
    val vernacularBody: String = "",
    val closing: String = "",
    val signaturePlaceholder: String = "",
    val englishTranslation: String = "",
    val preservedFacts: List<String> = emptyList(),
    val isGenerating: Boolean = false,
    val isRegenerating: Boolean = false,
    val isSaving: Boolean = false,
    val isExporting: Boolean = false,
    val exportedFile: File? = null,
    val activeTab: Int = 0, // 0 = వివరాలు (Inputs & Facts), 1 = తెలుగు లేఖ (Telugu Draft), 2 = English Copy

    // Privacy & Anti-Hallucination Guardrails
    val isDraft: Boolean = true,
    val isUserReviewed: Boolean = false,
    val showReviewDialog: Boolean = false,
    val pendingExportFormat: ExportFormat? = null,
    val errorMessage: String? = null
) : UiState

sealed interface LetterUiIntent : UiIntent {
    data class InitializeWithTranscript(val transcript: String) : LetterUiIntent
    data class UpdateVoiceTranscript(val transcript: String) : LetterUiIntent
    data class UpdateLetterType(val letterType: LetterType) : LetterUiIntent
    data class UpdateRecipientDesignation(val designation: String) : LetterUiIntent
    data class UpdateRecipientDepartment(val department: String) : LetterUiIntent
    data class UpdateRecipientAddress(val address: String) : LetterUiIntent
    data class UpdateUserFacts(val factsText: String) : LetterUiIntent
    data class UpdateLocation(val location: String) : LetterUiIntent
    data class UpdateDate(val date: String) : LetterUiIntent
    data class UpdateApplicantName(val name: String) : LetterUiIntent

    data object GenerateLetter : LetterUiIntent
    data object RegenerateLetter : LetterUiIntent
    data object CancelGeneration : LetterUiIntent

    data class UpdateSubject(val subject: String) : LetterUiIntent
    data class UpdateSalutation(val salutation: String) : LetterUiIntent
    data class UpdateVernacularBody(val body: String) : LetterUiIntent
    data class UpdateClosing(val closing: String) : LetterUiIntent
    data class UpdateSignaturePlaceholder(val signature: String) : LetterUiIntent
    data class UpdateEnglishTranslation(val translation: String) : LetterUiIntent

    data class SwitchTab(val tabIndex: Int) : LetterUiIntent
    data object SaveLetterDraft : LetterUiIntent
    data class ExportDocument(val format: ExportFormat, val cacheDir: File) : LetterUiIntent
    data class RequestExport(val format: ExportFormat, val cacheDir: File) : LetterUiIntent
    data class SetUserReviewed(val isReviewed: Boolean) : LetterUiIntent
    data object ConfirmReviewAndExport : LetterUiIntent
    data object DismissReviewDialog : LetterUiIntent
    data object LoadSampleFacts : LetterUiIntent
}

sealed interface LetterUiSideEffect : UiSideEffect {
    data class ShowToast(val message: String) : LetterUiSideEffect
    data class ShareExportedFile(val file: File, val mimeType: String) : LetterUiSideEffect
}
