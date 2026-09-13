package com.vernai.document.processing

import com.vernai.core.model.Language

/**
 * Known structural limitations of optical character recognition (OCR) and text extraction
 * on Indian administrative documents.
 */
enum class DocumentLimitationCategory(
    val titleTelugu: String,
    val titleEnglish: String,
    val description: String,
    val mitigationAdvice: String
) {
    TELUGU_COMPLEX_LIGATURES(
        titleTelugu = "సంయుక్తాక్షరాలు & ఒత్తులు విభజన",
        titleEnglish = "Telugu Complex Ligatures & Subscript Dissociation",
        description = "Scanned Telugu text with conjunct consonants (సంయుక్తాక్షరాలు like 'క్ష', 'జ్ఞ', 'త్ర') often fragments into disconnected bases and floating sub-scripts during OCR.",
        mitigationAdvice = "Verify critical names and places manually against the original scan."
    ),
    TELUGU_VOWEL_SIGN_AND_DOT_SMUDGING(
        titleTelugu = "మాత్రలు & అనుస్వారాలు (సున్న) లోపాలు",
        titleEnglish = "Telugu Matra Smudging & Anusvara Confusion",
        description = "Low-DPI government photostats (xerox copies) smudge circular vowel diacritics and anusvara ('ం'), causing letter substitutions (e.g. 'క' vs 'కం').",
        mitigationAdvice = "Re-scan with 300+ DPI or adjust brightness contrast."
    ),
    TELUGU_NON_UNICODE_FONTS(
        titleTelugu = "పాత నాన్-యూనికోడ్ ఫాంట్లు (Shree-Lipi/Anu)",
        titleEnglish = "Legacy Non-Unicode Font Encoding",
        description = "Older government gazettes and taluk documents use legacy ASCII mapping fonts rather than Unicode UTF-8, yielding character scramble upon digital extraction.",
        mitigationAdvice = "Use image-based OCR mode rather than direct digital stream extraction."
    ),
    ENGLISH_LOW_DPI_CARBON_COPIES(
        titleTelugu = "తక్కువ నాణ్యత కార్బన్ కాపీలు",
        titleEnglish = "Low-DPI Carbon & Photostat Blur",
        description = "Duplicate carbon records from mandal offices suffer from ink bleeding and faintness, causing 'O'/'0', 'l'/'1', and 'c'/'e' misclassifications.",
        mitigationAdvice = "Capture scan in high-contrast black-and-white lighting."
    ),
    STAMPS_SEALS_AND_SIGNATURES(
        titleTelugu = "అధికారిక ముద్రలు & సంతకాల అడ్డంకులు",
        titleEnglish = "Official Rubber Stamps & Watermarks Overwriting Text",
        description = "Government official seals, circular stamps, and ballpoint ink signatures frequently overwrite key survey numbers, dates, and amounts, masking underlying characters.",
        mitigationAdvice = "Inspect stamp-covered regions carefully in raw preview."
    ),
    MULTI_COLUMN_TABLE_SERIALIZATION(
        titleTelugu = "పట్టికలు & కాలమ్స్ క్రమం దెబ్బతినడం",
        titleEnglish = "Table & Multi-Column Survey Deserialization",
        description = "Tabular survey schedules (pahanis, revenue registers) serialize across columns instead of rows during basic OCR, breaking number-to-header associations.",
        mitigationAdvice = "Refer to the original layout for tabular figures."
    )
}

/**
 * Evaluates extracted text quality and flags document limitations.
 */
data class DocumentQualityReport(
    val detectedLimitations: List<DocumentLimitationCategory>,
    val estimatedOcrConfidence: Float, // 0.0 to 1.0
    val requiresManualReview: Boolean,
    val advisoryTelugu: String,
    val advisoryEnglish: String
)

object DocumentLimitationsAnalyzer {

    fun analyze(
        rawText: String,
        isScanned: Boolean,
        language: Language
    ): DocumentQualityReport {
        val limitations = mutableListOf<DocumentLimitationCategory>()

        if (isScanned) {
            if (language == Language.TELUGU || containsTeluguCharacters(rawText)) {
                limitations.add(DocumentLimitationCategory.TELUGU_COMPLEX_LIGATURES)
                limitations.add(DocumentLimitationCategory.TELUGU_VOWEL_SIGN_AND_DOT_SMUDGING)
            }
            limitations.add(DocumentLimitationCategory.ENGLISH_LOW_DPI_CARBON_COPIES)
            limitations.add(DocumentLimitationCategory.STAMPS_SEALS_AND_SIGNATURES)
        }

        // Check for table indicators (e.g. lots of tabs, pipes, or numeric columns)
        if (rawText.count { it == '|' || it == '\t' } > 5 || rawText.lines().any { line -> line.split(" ").count { it.toDoubleOrNull() != null } >= 3 }) {
            limitations.add(DocumentLimitationCategory.MULTI_COLUMN_TABLE_SERIALIZATION)
        }

        // Check for font scrambling (e.g. excessive replacement characters or broken unicode)
        if (rawText.contains("") || rawText.contains("???")) {
            limitations.add(DocumentLimitationCategory.TELUGU_NON_UNICODE_FONTS)
        }

        val confidence = when {
            rawText.length < 50 -> 0.40f
            limitations.size >= 4 -> 0.70f
            limitations.size >= 2 -> 0.85f
            else -> 0.95f
        }

        val requiresReview = limitations.isNotEmpty() && isScanned

        val advisoryTelugu = if (requiresReview) {
            "ఈ పత్రం స్కాన్ చేయబడినది. ఒత్తులు, ముద్రలు లేదా సంఖ్యల ఖచ్చితత్వాన్ని మూల పత్రంతో సరిచూసుకోండి."
        } else {
            "పత్రం నుండి వచనం స్పష్టంగా సంగ్రహించబడింది."
        }

        val advisoryEnglish = if (requiresReview) {
            "Scanned document detected. Official stamps and ligatures may cause minor character discrepancies. Verify critical survey/date numbers."
        } else {
            "Digital text cleanly extracted with high confidence."
        }

        return DocumentQualityReport(
            detectedLimitations = limitations.distinct(),
            estimatedOcrConfidence = confidence,
            requiresManualReview = requiresReview,
            advisoryTelugu = advisoryTelugu,
            advisoryEnglish = advisoryEnglish
        )
    }

    private fun containsTeluguCharacters(text: String): Boolean {
        // Telugu Unicode block is U+0C00 to U+0C7F
        return text.any { it.code in 0x0C00..0x0C7F }
    }
}
