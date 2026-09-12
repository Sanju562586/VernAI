package com.vernai.ui.letter

import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.Language
import com.vernai.document.export.ExportFormat
import com.vernai.ui.common.UiIntent
import com.vernai.ui.common.UiSideEffect
import com.vernai.ui.common.UiState
import java.io.File

data class LetterUiState(
    val selectedLanguage: Language = Language.TELUGU,
    val department: String = "గ్రామ పంచాయతీ కార్యాలయం (Gram Panchayat)",
    val subject: String = "వీధి దీపాలు మరియు తాగునీటి సరఫరా పరిష్కారం కొరకు",
    val vernacularBody: String = """
గౌరవనీయులైన సర్పంచ్ / పంచాయతీ కార్యదర్శి గారికి,

మా గ్రామమైన శాంతినగర్ కాలనీలో గత వారం రోజులుగా వీధి దీపాలు పనిచేయడం లేదు. రాత్రి వేళల్లో ప్రజలు, ముఖ్యంగా వృద్ధులు మరియు మహిళలు తీవ్ర ఇబ్బందులు పడుతున్నారు. అలాగే మంచినీటి సరఫరా కూడా సక్రమంగా జరగడం లేదు.

కావున దయచేసి తక్షణమే సంబంధిత సిబ్బందిని ఆదేశించి వీధి దీపాలు సరిచేయించి, తాగునీరు అందించాల్సిందిగా కోరుతున్నాము.

ఇట్లు,
శాంతినగర్ గ్రామస్తులు.
    """.trimIndent(),
    val englishTranslation: String = """
To
The Panchayat Secretary / Sarpanch,
Grama Panchayat Office.

Subject: Request for restoration of street lights and regular drinking water supply.

Respected Sir/Madam,
We bring to your kind notice that street lights in Shantinagar colony have not been functioning for the past week, causing immense hardship to residents at night. In addition, the drinking water supply remains irregular.

We earnestly request you to intervene promptly and resolve these essential civic amenities at the earliest.

Yours faithfully,
Residents of Shantinagar.
    """.trimIndent(),
    val isRegenerating: Boolean = false,
    val isExporting: Boolean = false,
    val exportedFile: File? = null,
    val activeTab: Int = 0 // 0 = Telugu/Vernacular, 1 = English Translation
) : UiState

sealed interface LetterUiIntent : UiIntent {
    data class UpdateSubject(val subject: String) : LetterUiIntent
    data class UpdateDepartment(val department: String) : LetterUiIntent
    data class UpdateVernacularBody(val body: String) : LetterUiIntent
    data class UpdateEnglishTranslation(val translation: String) : LetterUiIntent
    data class SwitchTab(val tabIndex: Int) : LetterUiIntent
    data object RegenerateLetter : LetterUiIntent
    data object SaveLetterDraft : LetterUiIntent
    data class ExportDocument(val format: ExportFormat, val cacheDir: File) : LetterUiIntent
}

sealed interface LetterUiSideEffect : UiSideEffect {
    data class ShowToast(val message: String) : LetterUiSideEffect
    data class ShareExportedFile(val file: File, val mimeType: String) : LetterUiSideEffect
}
