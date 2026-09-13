package com.vernai.ui.sales

import com.vernai.core.model.Language
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import com.vernai.document.export.ExportFormat
import com.vernai.ui.common.UiIntent
import com.vernai.ui.common.UiSideEffect
import com.vernai.ui.common.UiState
import java.io.File

data class SalesUiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val spokenTranscript: String = "",
    val selectedLanguage: Language = Language.TELUGU,
    val currentLog: SalesLog? = null,
    val historyLogs: List<SalesLog> = emptyList(),
    val filterDate: String? = null,
    val activeClarificationItem: SalesItem? = null,
    val duplicateWarningItem: SalesItem? = null,
    val errorMessage: String? = null
) : UiState

sealed interface SalesUiIntent : UiIntent {
    data class ChangeLanguage(val language: Language) : SalesUiIntent
    data object ToggleRecording : SalesUiIntent
    data class SubmitManualTranscript(val text: String) : SalesUiIntent
    data class UpdateItem(val item: SalesItem) : SalesUiIntent
    data class AddNewItem(val item: SalesItem) : SalesUiIntent
    data class DeleteItem(val itemId: String) : SalesUiIntent
    data class SelectClarificationItem(val item: SalesItem?) : SalesUiIntent
    data class ResolveClarification(
        val itemId: String,
        val resolvedQuantity: Double?,
        val resolvedUnitPrice: Double?,
        val resolvedTotal: Double?,
        val resolvedNotes: String?
    ) : SalesUiIntent
    data class MergeDuplicate(val existingItemId: String, val duplicateItemId: String) : SalesUiIntent
    data class DismissDuplicate(val itemId: String) : SalesUiIntent
    data class FilterByDate(val date: String?) : SalesUiIntent
    data object SaveLogToLedger : SalesUiIntent
    data class ExportLedger(val destinationFile: File, val format: ExportFormat = ExportFormat.CSV) : SalesUiIntent
}

sealed interface SalesUiSideEffect : UiSideEffect {
    data class ShowToast(val message: String) : SalesUiSideEffect
    data class ExportCompleted(val exportedFile: File, val format: ExportFormat) : SalesUiSideEffect
    data class RequestAudioPermission(val permission: String) : SalesUiSideEffect
}
