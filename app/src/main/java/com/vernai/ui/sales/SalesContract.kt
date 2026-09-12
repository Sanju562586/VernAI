package com.vernai.ui.sales

import com.vernai.core.model.Language
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
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
    val errorMessage: String? = null
) : UiState

sealed interface SalesUiIntent : UiIntent {
    data class ChangeLanguage(val language: Language) : SalesUiIntent
    data object ToggleRecording : SalesUiIntent
    data class SubmitManualTranscript(val text: String) : SalesUiIntent
    data class UpdateItem(val item: SalesItem) : SalesUiIntent
    data class DeleteItem(val itemId: String) : SalesUiIntent
    data object SaveLogToLedger : SalesUiIntent
    data class ExportLedger(val destinationFile: File) : SalesUiIntent
}

sealed interface SalesUiSideEffect : UiSideEffect {
    data class ShowToast(val message: String) : SalesUiSideEffect
    data class ExportCompleted(val exportedFile: File) : SalesUiSideEffect
    data class RequestAudioPermission(val permission: String) : SalesUiSideEffect
}
