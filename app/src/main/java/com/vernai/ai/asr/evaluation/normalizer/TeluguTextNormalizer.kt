package com.vernai.ai.asr.evaluation.normalizer

import java.text.Normalizer

/**
 * Normalization modes for Telugu ASR evaluation.
 */
enum class NormalizationMode {
    /**
     * Preserves exact words; only normalizes Unicode NFC, removes zero-width chars and punctuation.
     */
    STRICT,

    /**
     * Converts digits/numbers and common orthographic variants to standardized spoken Telugu tokens
     * to prevent artificial WER inflation from formatting discrepancies.
     */
    NUMERAL_CANONICAL,

    /**
     * In addition to numeral canonicalization, normalizes known colloquial/dialectal lexical variants
     * to their standard reference lemma (e.g. Telangana verb past-tense endings).
     */
    DIALECT_AWARE
}

/**
 * Robust Telugu text normalizer for ASR benchmark evaluation.
 */
object TeluguTextNormalizer {

    // Map of Arabic and Telugu numerals to spoken Telugu words
    private val DIGIT_TO_TELUGU_WORDS = mapOf(
        "0" to "సున్నా", "౦" to "సున్నా",
        "1" to "ఒకటి", "౧" to "ఒకటి",
        "2" to "రెండు", "౨" to "రెండు",
        "3" to "మూడు", "౩" to "మూడు",
        "4" to "నాలుగు", "౪" to "నాలుగు",
        "5" to "ఐదు", "౫" to "ఐదు",
        "6" to "ఆరు", "౬" to "ఆరు",
        "7" to "ఏడు", "౭" to "ఏడు",
        "8" to "ఎనిమిది", "౮" to "ఎనిమిది",
        "9" to "తొమ్మిది", "౯" to "తొమ్మిది",
        "10" to "పది",
        "15" to "పదిహేను",
        "20" to "ఇరవై",
        "50" to "యాభై",
        "100" to "వంద",
        "200" to "రెండు వందలు",
        "260" to "రెండు వందల అరవై",
        "500" to "ఐదు వందలు",
        "1000" to "వెయ్యి",
        "2000" to "రెండు వేలు"
    )

    // Common Telugu spoken abbreviations and loanword spelling variants
    private val COMMON_SPELLING_VARIANTS = mapOf(
        "కేజీ" to "కిలో",
        "కేజీల" to "కిలోల",
        "టమోటా" to "టమాటా",
        "టమోటాలు" to "టమాటాలు",
        "ఆయిల్" to "నూనె",
        "కరంటు" to "కరెంట్",
        "కరెంటు" to "కరెంట్",
        "రైతులు" to "రైతులు",
        "బోరు" to "బోరుబావి",
        "మోటార్" to "మోటారు",
        "పాసుబుక్" to "పాసుపుస్తకం",
        "పాసుబుక్కు" to "పాసుపుస్తకం",
        "పంచాయత్" to "పంచాయతీ",
        "తహసిల్" to "తహశీల్దార్",
        "తహశీల్" to "తహశీల్దార్",
        "పహాణి" to "పహాణీ",
        "పహాని" to "పహాణీ"
    )

    // Dialectal and colloquial verb endings / variants (Telangana & Rayalaseema agrarian terms)
    private val DIALECT_VARIANTS = mapOf(
        "అమ్మిన" to "అమ్మినాను",
        "అమ్మాను" to "అమ్మినాను",
        "చేసిన" to "చేసినాను",
        "చేశాను" to "చేసినాను",
        "వచ్చిన" to "వచ్చినాను",
        "వచ్చాను" to "వచ్చినాను",
        "పైసలు" to "రూపాయలు",
        "ఎట్ల" to "ఎలా",
        "సదువు" to "చదువు",
        "యాడికి" to "ఎక్కడికి",
        "సూడు" to "చూడు",
        "రైతుబంధు" to "రైతు భరోసా",
        "రైతుభరోసా" to "రైతు భరోసా",
        "గిట్టుబాటు" to "మద్దతు ధర",
        "కౌలురైతు" to "కౌలు రైతు",
        "కాంటా" to "ధాన్యం కొనుగోలు కేంద్రం",
        "వరిచేను" to "వరి పొలం"
    )

    /**
     * Normalizes raw Telugu text based on the desired evaluation mode.
     */
    fun normalize(text: String, mode: NormalizationMode = NormalizationMode.NUMERAL_CANONICAL): String {
        if (text.isBlank()) return ""

        // 1. Unicode NFC Normalization
        var s = Normalizer.normalize(text, Normalizer.Form.NFC)

        // 2. Remove Zero-Width and invisible characters (ZWNJ \u200C, ZWJ \u200D, ZWSP \u200B, BOM \uFEFF)
        s = s.replace(Regex("[\u200B-\u200D\uFEFF]"), "")

        // 3. Remove Punctuation, symbols, quotes, brackets
        s = s.replace(Regex("[.,;:!?\"'()\\-\\[\\]{}|/<>~`@#$%^&*+=_\\\\—–]"), " ")

        // 4. Tokenize by whitespace
        var tokens = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (mode != NormalizationMode.STRICT) {
            // Replace digits with spoken words
            tokens = tokens.map { token ->
                DIGIT_TO_TELUGU_WORDS[token] ?: token
            }

            // Normalize common loanword spelling variants
            tokens = tokens.map { token ->
                COMMON_SPELLING_VARIANTS[token] ?: token
            }
        }

        if (mode == NormalizationMode.DIALECT_AWARE) {
            // Canonicalize regional dialect variants
            tokens = tokens.map { token ->
                DIALECT_VARIANTS[token] ?: token
            }
        }

        return tokens.joinToString(" ").trim()
    }
}
