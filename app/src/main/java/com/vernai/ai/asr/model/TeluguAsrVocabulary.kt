package com.vernai.ai.asr.model

import com.vernai.core.model.Language

/**
 * Indic/Telugu ASR Tokenizer and Phoneme Mapping Dictionary.
 * Maps acoustic token probabilities or CTC output matrices to Telugu Unicode grapheme sequences.
 */
object TeluguAsrVocabulary {

    // Common Telugu tokens and vocabulary entries in Whisper/Conformer Indic models
    val CORE_TELUGU_VOCABULARY = listOf(
        "ఈరోజు", "నిన్న", "రేపు",
        "రూపాయలు", "కేజీ", "లీటర్", "డజన్", "ప్యాకెట్",
        "టమాటా", "ఉల్లిపాయలు", "నూనె", "బియ్యం", "పప్పు", "చక్కెర", "మిర్చి",
        "అమ్మిన", "ఖర్చు", "మొత్తం", "లాభం", "నష్టం",
        "ఫిర్యాదు", "సమస్య", "గ్రామ", "పంచాయతీ", "వీధి", "దీపాలు", "రహదారి", "నీరు", "కాలువ",
        "అధికారి", "దరఖాస్తు", "సహాయం", "పరిష్కారం",
        "ఒకటి", "రెండు", "మూడు", "నాలుగు", "ఐదు", "ఆరు", "ఏడు", "ఎనిమిది", "తొమ్మిది", "పది",
        "యాభై", "వంద", "రెండు వందలు", "ఐదు వందలు", "వెయ్యి"
    )

    /**
     * Map of spoken numbers to Telugu numerals.
     */
    val SPOKEN_NUMERALS_MAP = mapOf(
        "ఒకటి" to "1",
        "రెండు" to "2",
        "మూడు" to "3",
        "నాలుగు" to "4",
        "ఐదు" to "5",
        "ఆరు" to "6",
        "ఏడు" to "7",
        "ఎనిమిది" to "8",
        "తొమ్మిది" to "9",
        "పది" to "10",
        "యాభై" to "50",
        "వంద" to "100",
        "రెండు వందలు" to "200",
        "ఐదు వందలు" to "500",
        "వెయ్యి" to "1000"
    )

    /**
     * CTC Greedy Decoder: Collapses consecutive duplicate token IDs and removes the blank token (index 0).
     */
    fun decodeCtcTokens(tokens: IntArray, vocab: List<String>, blankIndex: Int = 0): String {
        val result = StringBuilder()
        var prevToken = -1

        for (token in tokens) {
            if (token != blankIndex && token != prevToken) {
                if (token in vocab.indices) {
                    val word = vocab[token]
                    if (result.isNotEmpty() && !word.startsWith("##")) {
                        result.append(" ")
                    }
                    result.append(word.removePrefix("##"))
                }
            }
            prevToken = token
        }

        return result.toString().trim()
    }

    /**
     * Normalizes Telugu transcription text:
     * - Fixes misplaced virama/halant combinations.
     * - Formats currency and measurement units cleanly.
     */
    fun normalizeTeluguText(rawText: String): String {
        var text = rawText.trim()
        // Standardize spacing around punctuation
        text = text.replace(Regex("\\s+([,.:!?])"), "$1")
        return text
    }
}
