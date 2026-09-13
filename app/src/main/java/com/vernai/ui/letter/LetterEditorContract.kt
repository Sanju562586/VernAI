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
    val voiceTranscript: String = "మా గ్రామం శాంతినగర్‌లో గత 10 రోజులుగా వీధి దీపాలు వెలగడం లేదు మరియు మెయిన్ పైప్‌లైన్ పగిలిపోయి 4 రోజులుగా మంచినీటి సరఫరా నిలిచిపోయింది.",
    val recipientDesignation: String = "పంచాయతీ కార్యదర్శి / సర్పంచ్ గారు",
    val recipientDepartment: String = "గ్రామ పంచాయతీ కార్యాలయం",
    val recipientAddress: String = "మండల పరిషత్, ఖమ్మం జిల్లా",
    val userFactsText: String = "1. గత 10 రోజులుగా వీధి దీపాలు పనిచేయడం లేదు.\n2. పైప్‌లైన్ లీకేజీ వల్ల 4 రోజులుగా నీటి సరఫరా నిలిచిపోయింది.\n3. దాదాపు 150 కుటుంబాలు తీవ్ర ఇబ్బందులు పడుతున్నాయి.",
    val location: String = "శాంతినగర్",
    val date: String = "13-09-2026",
    val applicantName: String = "శాంతినగర్ కాలనీ గ్రామస్తులు",

    // Output & Structured Preview fields
    val structuredLetter: StructuredLetter? = null,
    val subject: String = "విషయము: గ్రామ పరిధిలో తాగునీటి ఎద్దడి మరియు వీధి దీపాల మరమ్మత్తులు చేపట్టవలసిందిగా వినతి.",
    val salutation: String = "గౌరవనీయులైన పంచాయతీ కార్యదర్శి / సర్పంచ్ గారికి,",
    val vernacularBody: String = """
స్థలం: శాంతినగర్
తేదీ: 13-09-2026

స్వీకర్త:
గౌరవనీయులైన పంచాయతీ కార్యదర్శి / సర్పంచ్ గారికి,
గ్రామ పంచాయతీ కార్యాలయం,
మండల పరిషత్, ఖమ్మం జిల్లా.

విషయము: గ్రామ పరిధిలో తాగునీటి ఎద్దడి మరియు వీధి దీపాల మరమ్మత్తులు చేపట్టవలసిందిగా వినతి.

ఆర్యా,

విన్నవించునది ఏమనగా, మేము శాంతినగర్ కాలనీ నివాసితులము. మా ప్రాంతంలో ఎదురవుతున్న తీవ్రమైన మౌలిక వసతుల సమస్యలను మీ అమూల్యమైన దృష్టికి తీసుకువచ్చి సత్వర పరిష్కారం కోరడానికి ఈ వినతిపత్రం సమర్పిస్తున్నాము.

మా కాలనీలో గత 10 రోజులుగా వీధి దీపాలు వెలగకపోవడం వలన రాత్రి వేళల్లో రాకపోకలకు తీవ్ర అసౌకర్యం కలుగుతున్నది. అంతేగాక ప్రధాన పైప్‌లైన్ లీకేజీ కారణంగా గత 4 రోజులుగా తాగునీటి సరఫరా పూర్తిగా నిలిచిపోయింది. దీనివల్ల కాలనీలోని దాదాపు 150 కుటుంబాలు తీవ్ర ఇబ్బందులు ఎదుర్కొంటున్నాయి.

కావున దయచేసి మా సమస్యల తీవ్రతను గుర్తించి, సంబంధిత అధికారులను తక్షణమే క్షేత్రస్థాయి పరిశీలనకు ఆదేశించి, తాగునీటి సరఫరా పునరుద్ధరణ మరియు వీధి దీపాల మరమ్మత్తులు వెంటనే పూర్తి చేయించగలరని వినయపూర్వకంగా వేడుకొనుచున్నాము.

ధన్యవాదములతో,

ఇట్లు,
భవదీయులు,
[దరఖాస్తుదారుడి సంతకం]
(శాంతినగర్ కాలనీ గ్రామస్తులు)
చిరునామా: శాంతినగర్
[ఫోన్ నంబర్ / చిరునామా]
    """.trimIndent(),
    val closing: String = "ఇట్లు,\nభవదీయులు,",
    val signaturePlaceholder: String = "[దరఖాస్తుదారుడి సంతకం]\n(శాంతినగర్ కాలనీ గ్రామస్తులు)",
    val englishTranslation: String = """
To
The Panchayat Secretary / Sarpanch,
Grama Panchayat Office,
Mandal Parishad, Khammam District.

Subject: Representation regarding urgent restoration of drinking water supply and street lights.

Respected Sir/Madam,

We the residents of Shantinagar Colony bring to your urgent attention the severe civic issues prevailing in our locality. The street lights have been dysfunctional for the last 10 days, causing safety concerns at night. Furthermore, due to a major water pipeline breach, potable water supply has been disrupted for the past 4 days, affecting over 150 households.

We earnestly request your esteemed office to inspect the location and direct the technical team to restore water supply and repair street lights on priority.

Yours faithfully,
Residents of Shantinagar
Place: Shantinagar
Date: 13-09-2026
    """.trimIndent(),
    val preservedFacts: List<String> = listOf(
        "గత 10 రోజులుగా వీధి దీపాలు పనిచేయడం లేదు",
        "పైప్‌లైన్ లీకేజీ వల్ల 4 రోజులుగా నీటి సరఫరా నిలిచిపోయింది",
        "దాదాపు 150 కుటుంబాలు తీవ్ర ఇబ్బందులు పడుతున్నాయి"
    ),
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
    val pendingExportFormat: ExportFormat? = null
) : UiState

sealed interface LetterUiIntent : UiIntent {
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
