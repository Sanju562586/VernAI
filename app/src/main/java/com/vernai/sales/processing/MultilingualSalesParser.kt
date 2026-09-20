package com.vernai.sales.processing

import com.vernai.core.model.Language
import com.vernai.core.model.SalesItem

/**
 * Unified Multilingual Sales Parser supporting Telugu, Tamil, Hindi, and English.
 */
class MultilingualSalesParser(
    private val teluguParser: TeluguSalesParser = TeluguSalesParser(),
    private val tamilParser: TamilSalesParser = TamilSalesParser(),
    private val hindiParser: HindiSalesParser = HindiSalesParser(),
    private val englishParser: EnglishSalesParser = EnglishSalesParser()
) {

    fun parseTranscript(transcript: String, language: Language = Language.detectFromText(transcript)): List<SalesItem> {
        val targetLang = if (language == Language.ENGLISH) {
            Language.detectFromText(transcript)
        } else {
            language
        }

        return when (targetLang) {
            Language.TELUGU -> teluguParser.parseTranscript(transcript)
            Language.TAMIL -> tamilParser.parseTranscript(transcript)
            Language.HINDI, Language.MARATHI -> hindiParser.parseTranscript(transcript)
            else -> {
                // Try English parser; if it yields 0 items and non-English chars present, try native detection
                val engItems = englishParser.parseTranscript(transcript)
                if (engItems.isNotEmpty() && engItems.any { it.quantity > 0 || it.totalPrice > 0 }) {
                    engItems
                } else {
                    when (Language.detectFromText(transcript)) {
                        Language.TELUGU -> teluguParser.parseTranscript(transcript)
                        Language.TAMIL -> tamilParser.parseTranscript(transcript)
                        Language.HINDI, Language.MARATHI -> hindiParser.parseTranscript(transcript)
                        else -> engItems
                    }
                }
            }
        }
    }
}
