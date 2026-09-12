package com.vernai.ai.asr.evaluation.metrics

/**
 * Encapsulates full evaluation scores for a reference-hypothesis pair.
 */
data class AsrMetricScore(
    val wer: Float,
    val cer: Float,
    val wordAlignment: AlignmentResult,
    val charAlignment: AlignmentResult
)

/**
 * Computes Word Error Rate (WER) and Character Error Rate (CER) for ASR evaluation.
 */
object AsrMetricsCalculator {

    /**
     * Computes Word Error Rate (WER) = (Substitutions + Deletions + Insertions) / Reference Word Count.
     */
    fun computeWer(reference: String, hypothesis: String): AlignmentResult {
        val refWords = tokenizeWords(reference)
        val hypWords = tokenizeWords(hypothesis)
        return EditDistance.align(refWords, hypWords)
    }

    /**
     * Computes Character Error Rate (CER) = (Substitutions + Deletions + Insertions) / Reference Char Count.
     * Character tokens exclude spaces to measure pure orthographic accuracy.
     */
    fun computeCer(reference: String, hypothesis: String): AlignmentResult {
        val refChars = tokenizeChars(reference)
        val hypChars = tokenizeChars(hypothesis)
        return EditDistance.align(refChars, hypChars)
    }

    /**
     * Computes both WER and CER in a single call.
     */
    fun evaluate(reference: String, hypothesis: String): AsrMetricScore {
        val wordResult = computeWer(reference, hypothesis)
        val charResult = computeCer(reference, hypothesis)

        return AsrMetricScore(
            wer = wordResult.errorRate,
            cer = charResult.errorRate,
            wordAlignment = wordResult,
            charAlignment = charResult
        )
    }

    private fun tokenizeWords(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        return trimmed.split(Regex("\\s+"))
    }

    private fun tokenizeChars(text: String): List<String> {
        // Exclude whitespace when evaluating character-level orthographic errors
        val noSpace = text.replace(Regex("\\s+"), "")
        if (noSpace.isEmpty()) return emptyList()
        val tokens = ArrayList<String>(noSpace.length)
        var i = 0
        while (i < noSpace.length) {
            val codePoint = noSpace.codePointAt(i)
            tokens.add(String(Character.toChars(codePoint)))
            i += Character.charCount(codePoint)
        }
        return tokens
    }
}
