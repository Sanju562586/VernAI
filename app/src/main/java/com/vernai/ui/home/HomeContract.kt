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
                title = "Voice Assistant",
                nativeSubtitle = "குரல் உதவியாளர் (Voice Assistant)",
                description = "உங்கள் குரலில் பேசுங்கள்; வேலை உடனடியாக முடியும்.",
                destination = VernAiNavDestination.VoiceWorkspace
            ),
            FeatureCardItem(
                id = "sales",
                title = "Daily Sales Ledger",
                nativeSubtitle = "விற்பனை பதிவேடு (Sales Ledger)",
                description = "தினசரி விற்பனையை பேசுங்கள்; கணக்குகள் தானாகவே தயாராகும்.",
                destination = VernAiNavDestination.SalesLedger()
            ),
            FeatureCardItem(
                id = "complaint",
                title = "Official Letter",
                nativeSubtitle = "அதிகாரப்பூர்வ மனு (Official Letter)",
                description = "மாவட்ட ஆட்சியர் அல்லது அதிகாரிகளுக்கு விண்ணப்பம் அல்லது புகார் கடிதம்.",
                destination = VernAiNavDestination.ComplaintDrafting()
            ),
            FeatureCardItem(
                id = "doc",
                title = "Document Reader",
                nativeSubtitle = "ஆவண விளக்கம் (Document Reader)",
                description = "கடினமான ஆங்கிலப் படிவங்கள் மற்றும் அரசு ஆவணங்களை எளிய தமிழில் புரிந்து கொள்ளவும்.",
                destination = VernAiNavDestination.DocumentExplainer
            )
        )
        Language.HINDI, Language.MARATHI -> listOf(
            FeatureCardItem(
                id = "voice",
                title = "Voice Assistant",
                nativeSubtitle = "वॉयस असिस्टेंट (Voice Assistant)",
                description = "अपनी भाषा में बोलें; तुरंत काम पूरा होगा।",
                destination = VernAiNavDestination.VoiceWorkspace
            ),
            FeatureCardItem(
                id = "sales",
                title = "Daily Sales Ledger",
                nativeSubtitle = "दैनिक बिक्री खाता (Sales Ledger)",
                description = "दुकान की दैनिक बिक्री बोलें; हिसाब अपने-आप तैयार होगा।",
                destination = VernAiNavDestination.SalesLedger()
            ),
            FeatureCardItem(
                id = "complaint",
                title = "Official Letter",
                nativeSubtitle = "शिकायत / प्रार्थना पत्र (Official Letter)",
                description = "कलेक्टर या अधिकारियों के लिए औपचारिक पत्र आसानी से तैयार करें।",
                destination = VernAiNavDestination.ComplaintDrafting()
            ),
            FeatureCardItem(
                id = "doc",
                title = "Document Reader",
                nativeSubtitle = "दस्तावेज़ विवरण (Document Reader)",
                description = "कठिन सरकारी व छात्रवृत्ति फॉर्म को सरल भाषा में समझें।",
                destination = VernAiNavDestination.DocumentExplainer
            )
        )
        Language.ENGLISH -> listOf(
            FeatureCardItem(
                id = "voice",
                title = "Voice Assistant",
                nativeSubtitle = "Voice Assistant",
                description = "Speak naturally in your language to get instant assistance.",
                destination = VernAiNavDestination.VoiceWorkspace
            ),
            FeatureCardItem(
                id = "sales",
                title = "Daily Sales Ledger",
                nativeSubtitle = "Daily Sales Ledger",
                description = "Speak your shop sales to auto-calculate totals and export.",
                destination = VernAiNavDestination.SalesLedger()
            ),
            FeatureCardItem(
                id = "complaint",
                title = "Official Letter",
                nativeSubtitle = "Official Letter & Petition",
                description = "Draft formal petitions and complaints for officials in 1 minute.",
                destination = VernAiNavDestination.ComplaintDrafting()
            ),
            FeatureCardItem(
                id = "doc",
                title = "Document Explainer",
                nativeSubtitle = "Document Explainer",
                description = "Understand complex scholarship and government forms with ease.",
                destination = VernAiNavDestination.DocumentExplainer
            )
        )
        else -> listOf(
            FeatureCardItem(
                id = "voice",
                title = "Voice Assistant",
                nativeSubtitle = "వాయిస్ సహాయకుడు (Voice Assistant)",
                description = "మీ గొంతుతో మాట్లాడండి; ఏ పనైనా వెంటనే పూర్తవుతుంది.",
                destination = VernAiNavDestination.VoiceWorkspace
            ),
            FeatureCardItem(
                id = "sales",
                title = "Daily Sales Ledger",
                nativeSubtitle = "అమ్మకాల లెడ్జర్ (Sales Ledger)",
                description = "దుకాణం రోజూవారీ అమ్మకాలను మాట్లాడండి; లెక్కలు ఆటోమేటిక్‌గా సిద్ధం.",
                destination = VernAiNavDestination.SalesLedger()
            ),
            FeatureCardItem(
                id = "complaint",
                title = "Official Letter",
                nativeSubtitle = "అధికారిక లేఖ (Official Letter)",
                description = "కలెక్టర్ లేదా అధికారులకు ఫిర్యాదు లేదా దరఖాస్తు లేఖ రాయండి.",
                destination = VernAiNavDestination.ComplaintDrafting()
            ),
            FeatureCardItem(
                id = "doc",
                title = "Document Reader",
                nativeSubtitle = "పత్ర వివరణ (Document Reader)",
                description = "స్కాలర్‌షిప్ లేదా ప్రభుత్వ ఫారాలను సులభమైన తెలుగులో అర్థం చేసుకోండి.",
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
