package com.vernai.ui.home

import com.vernai.core.model.Language
import com.vernai.ui.common.UiIntent
import com.vernai.ui.common.UiSideEffect
import com.vernai.ui.common.UiState
import com.vernai.ui.navigation.VernAiNavDestination

data class FeatureCardItem(
    val id: String,
    val title: String,
    val nativeSubtitle: String,
    val description: String,
    val destination: VernAiNavDestination
)

data class HomeUiState(
    val activeLanguage: Language = Language.TELUGU,
    val isOfflineReady: Boolean = true,
    val availableMemoryMb: Long = 3420L,
    val hardwarePlatform: String = "Qualcomm Snapdragon (Adreno / Kryo)",
    val totalSalesRecorded: Int = 0,
    val totalComplaintsDrafted: Int = 0,
    val totalDocumentsRead: Int = 0,
    val features: List<FeatureCardItem> = listOf(
        FeatureCardItem(
            id = "sales",
            title = "Structured Sales Ledger",
            nativeSubtitle = "అమ్మకాల లాగ్ (Sales Log)",
            description = "Speak daily transactions; auto-extract to typed ledger & export to PDF/DOCX.",
            destination = VernAiNavDestination.SalesLedger
        ),
        FeatureCardItem(
            id = "complaint",
            title = "Formal Complaint Drafting",
            nativeSubtitle = "ఫిర్యాదు పత్రం (Complaint Letter)",
            description = "Draft administrative complaints in regional language with English translation.",
            destination = VernAiNavDestination.ComplaintDrafting
        ),
        FeatureCardItem(
            id = "doc",
            title = "Local Document Explainer",
            nativeSubtitle = "పత్ర వివరణ (Document Reader)",
            description = "Extract text from offline PDFs/scans and explain key takeaways in Telugu/Indic.",
            destination = VernAiNavDestination.DocumentExplainer
        )
    )
) : UiState

sealed interface HomeUiIntent : UiIntent {
    data class SelectLanguage(val language: Language) : HomeUiIntent
    data class NavigateTo(val destination: VernAiNavDestination) : HomeUiIntent
    data object RefreshStats : HomeUiIntent
}

sealed interface HomeUiSideEffect : UiSideEffect {
    data class Navigate(val destination: VernAiNavDestination) : HomeUiSideEffect
}
