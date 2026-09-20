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
    val features: List<FeatureCardItem> = getFeaturesForLanguage(Language.TELUGU)
) : UiState

fun getFeaturesForLanguage(language: Language): List<FeatureCardItem> {
    return when (language) {
        Language.TAMIL -> listOf(
            FeatureCardItem(
                id = "voice",
                title = "Voice Workspace & Live ASR",
                nativeSubtitle = "குரல் உதவியாளர் (Voice Assistant)",
                description = "உங்கள் மொழியில் பேசுங்கள்; உடனடி ஆஃப்லைன் எழுத்து வடிவம்.",
                destination = VernAiNavDestination.VoiceWorkspace
            ),
            FeatureCardItem(
                id = "sales",
                title = "Structured Sales Ledger",
                nativeSubtitle = "விற்பனை பதிவேடு (Sales Ledger)",
                description = "தினசரி விற்பனையை தமிழில் பேசுங்கள்; எக்செல்/அட்டவணையாக மாற்றவும்.",
                destination = VernAiNavDestination.SalesLedger()
            ),
            FeatureCardItem(
                id = "complaint",
                title = "Formal Complaint Drafting",
                nativeSubtitle = "முறையான புகார் மனு (Complaint Letter)",
                description = "மாவட்ட ஆட்சியர் அல்லது அதிகாரிகளுக்கு முறையான மனு தயாரிக்கவும்.",
                destination = VernAiNavDestination.ComplaintDrafting()
            ),
            FeatureCardItem(
                id = "doc",
                title = "Local Document Explainer",
                nativeSubtitle = "ஆவண விளக்கம் (Document Reader)",
                description = "படிவங்கள் மற்றும் கடினமான அரசு ஆவணங்களை எளிய தமிழில் புரிந்து கொள்ளவும்.",
                destination = VernAiNavDestination.DocumentExplainer
            )
        )
        Language.HINDI, Language.MARATHI -> listOf(
            FeatureCardItem(
                id = "voice",
                title = "Voice Workspace & Live ASR",
                nativeSubtitle = "वॉयस असिस्टेंट (Voice Assistant)",
                description = "अपनी भाषा में बोलें; स्वचालित रूप से टेक्स्ट में बदलें।",
                destination = VernAiNavDestination.VoiceWorkspace
            ),
            FeatureCardItem(
                id = "sales",
                title = "Structured Sales Ledger",
                nativeSubtitle = "दैनिक बिक्री खाता (Sales Ledger)",
                description = "दैनिक बिक्री बोलें; सीधे स्प्रेडशीट/एक्सेल में निर्यात करें।",
                destination = VernAiNavDestination.SalesLedger()
            ),
            FeatureCardItem(
                id = "complaint",
                title = "Formal Complaint Drafting",
                nativeSubtitle = "शिकायत पत्र (Complaint Letter)",
                description = "जिला कलेक्टर या अधिकारियों के लिए औपचारिक शिकायत पत्र तैयार करें।",
                destination = VernAiNavDestination.ComplaintDrafting()
            ),
            FeatureCardItem(
                id = "doc",
                title = "Local Document Explainer",
                nativeSubtitle = "दस्तावेज़ विवरण (Document Reader)",
                description = "कठिन सरकारी व छात्रवृत्ति फॉर्म को सरल भाषा में समझें।",
                destination = VernAiNavDestination.DocumentExplainer
            )
        )
        Language.ENGLISH -> listOf(
            FeatureCardItem(
                id = "voice",
                title = "Voice Workspace & Live ASR",
                nativeSubtitle = "Voice Assistant & Live ASR",
                description = "Speak freely in Indic/English; transcribe live and extract automated actions.",
                destination = VernAiNavDestination.VoiceWorkspace
            ),
            FeatureCardItem(
                id = "sales",
                title = "Structured Sales Ledger",
                nativeSubtitle = "Sales Ledger (Voice-to-Sheets)",
                description = "Speak daily transactions; auto-extract to typed ledger & export to Sheets/Excel.",
                destination = VernAiNavDestination.SalesLedger()
            ),
            FeatureCardItem(
                id = "complaint",
                title = "Formal Complaint Drafting",
                nativeSubtitle = "Complaint & Petition Drafting",
                description = "Draft administrative complaints in regional language with English copy.",
                destination = VernAiNavDestination.ComplaintDrafting()
            ),
            FeatureCardItem(
                id = "doc",
                title = "Local Document Explainer",
                nativeSubtitle = "Document Reader & Guidance",
                description = "Zero-hallucination explainer and guidance for dense official PDFs and forms.",
                destination = VernAiNavDestination.DocumentExplainer
            )
        )
        else -> listOf(
            FeatureCardItem(
                id = "voice",
                title = "Voice Workspace & Live ASR",
                nativeSubtitle = "వాయిస్ వర్క్‌స్పేస్ (Voice Assistant)",
                description = "మీ భాషలో మాట్లాడండి; ప్రత్యక్ష వచన రూపం మరియు ఆటోమేటెడ్ పనులు.",
                destination = VernAiNavDestination.VoiceWorkspace
            ),
            FeatureCardItem(
                id = "sales",
                title = "Structured Sales Ledger",
                nativeSubtitle = "అమ్మకాల లాగ్ (Sales Log)",
                description = "రోజూవారీ అమ్మకాలను మాట్లాడండి; టైప్ చేసిన లెడ్జర్‌గా మార్చి ఎగుమతి చేయండి.",
                destination = VernAiNavDestination.SalesLedger()
            ),
            FeatureCardItem(
                id = "complaint",
                title = "Formal Complaint Drafting",
                nativeSubtitle = "ఫిర్యాదు పత్రం (Complaint Letter)",
                description = "జిల్లా కలెక్టర్ లేదా అధికారులకు అధికారిక ఫిర్యాదు పత్రం రూపొందించండి.",
                destination = VernAiNavDestination.ComplaintDrafting()
            ),
            FeatureCardItem(
                id = "doc",
                title = "Local Document Explainer",
                nativeSubtitle = "పత్ర వివరణ (Document Reader)",
                description = "ఆఫ్‌లైన్ PDFలు/స్కాన్‌ల నుండి ముఖ్యమైన అంశాలను సులభంగా అర్థం చేసుకోండి.",
                destination = VernAiNavDestination.DocumentExplainer
            )
        )
    }
}

sealed interface HomeUiIntent : UiIntent {
    data class SelectLanguage(val language: Language) : HomeUiIntent
    data class NavigateTo(val destination: VernAiNavDestination) : HomeUiIntent
    data object RefreshStats : HomeUiIntent
}

sealed interface HomeUiSideEffect : UiSideEffect {
    data class Navigate(val destination: VernAiNavDestination) : HomeUiSideEffect
}
