package com.vernai.core.model

/**
 * Supported Indic and Bridge languages in VernAI.
 */
enum class Language(
    val isoCode: String,
    val nativeName: String,
    val englishName: String,
    val scriptUnicodeRange: LongRange
) {
    TELUGU("te", "తెలుగు", "Telugu", 0x0C00L..0x0C7FL),
    HINDI("hi", "हिन्दी", "Hindi", 0x0900L..0x097FL),
    TAMIL("ta", "தமிழ்", "Tamil", 0x0B80L..0x0BFFL),
    KANNADA("kn", "ಕನ್ನಡ", "Kannada", 0x0C80L..0x0CFFL),
    MALAYALAM("ml", "മലയാളം", "Malayalam", 0x0D00L..0x0D7FL),
    BENGALI("bn", "বাংলা", "Bengali", 0x0980L..0x09FFL),
    MARATHI("mr", "मराठी", "Marathi", 0x0900L..0x097FL),
    GUJARATI("gu", "ગુજરાતી", "Gujarati", 0x0A80L..0x0AFFL),
    ENGLISH("en", "English", "English", 0x0020L..0x007FL);

    companion object {
        fun fromIso(code: String): Language {
            return entries.firstOrNull { it.isoCode.equals(code, ignoreCase = true) } ?: TELUGU
        }

        /**
         * Detects language family by examining character code points in the text.
         */
        fun detectFromText(text: String): Language {
            val counts = mutableMapOf<Language, Int>()
            for (char in text) {
                val code = char.code.toLong()
                for (lang in entries) {
                    if (code in lang.scriptUnicodeRange && lang != ENGLISH) {
                        counts[lang] = (counts[lang] ?: 0) + 1
                    }
                }
            }
            return counts.maxByOrNull { it.value }?.key ?: ENGLISH
        }
    }
}
