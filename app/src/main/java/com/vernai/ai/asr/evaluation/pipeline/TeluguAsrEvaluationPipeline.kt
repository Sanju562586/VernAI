package com.vernai.ai.asr.evaluation.pipeline

import com.vernai.ai.asr.AsrEngine
import com.vernai.ai.asr.evaluation.dataset.EvaluationDomain
import com.vernai.ai.asr.evaluation.dataset.EvaluationUtterance
import com.vernai.ai.asr.evaluation.dataset.TeluguEvaluationDataset
import com.vernai.ai.asr.evaluation.metrics.AlignmentResult
import com.vernai.ai.asr.evaluation.metrics.AsrMetricsCalculator
import com.vernai.ai.asr.evaluation.metrics.EditOp
import com.vernai.ai.asr.evaluation.normalizer.NormalizationMode
import com.vernai.ai.asr.evaluation.normalizer.TeluguTextNormalizer
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.AudioSnippet
import com.vernai.core.model.Language
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * Detailed evaluation outcome for a single utterance.
 */
data class UtteranceEvaluationResult(
    val utterance: EvaluationUtterance,
    val rawHypothesis: String,
    val normalizedReference: String,
    val normalizedHypothesis: String,
    val strictWer: Float,
    val normalizedWer: Float,
    val strictCer: Float,
    val normalizedCer: Float,
    val wordAlignment: AlignmentResult,
    val charAlignment: AlignmentResult,
    val executionTimeMs: Long,
    val rtf: Float, // Real-Time Factor: executionTime / audioDuration
    val identifiedErrorTypes: List<String>
)

/**
 * Domain-aggregated evaluation statistics.
 */
data class DomainEvaluationSummary(
    val domain: EvaluationDomain,
    val sampleCount: Int,
    val avgStrictWer: Float,
    val avgNormalizedWer: Float,
    val avgNormalizedCer: Float,
    val avgRtf: Float
)

/**
 * Complete evaluation benchmark outcome for the VernAI Telugu ASR system.
 */
data class PipelineEvaluationResult(
    val totalSamples: Int,
    val totalAudioDurationSec: Double,
    val overallStrictWer: Float,
    val overallNormalizedWer: Float,
    val overallStrictCer: Float,
    val overallNormalizedCer: Float,
    val overallMeanRtf: Float,
    val p95LatencyMs: Long,
    val domainSummaries: List<DomainEvaluationSummary>,
    val utteranceResults: List<UtteranceEvaluationResult>,
    val errorTaxonomyCounts: Map<String, Int>
)

/**
 * Offline evaluation pipeline for benchmarking Telugu speech recognition models.
 */
class TeluguAsrEvaluationPipeline(
    private val asrEngine: AsrEngine
) {

    /**
     * Executes the entire evaluation benchmark offline across all dataset utterances.
     */
    suspend fun runEvaluation(
        utterances: List<EvaluationUtterance> = TeluguEvaluationDataset.utterances
    ): PipelineEvaluationResult {
        val utteranceResults = mutableListOf<UtteranceEvaluationResult>()
        val errorTaxonomyCounts = mutableMapOf<String, Int>()

        for (item in utterances) {
            val pcmData = synthesizeRealisticSpeechPcm(item.referenceText, item.durationSec)
            val snippet = AudioSnippet(
                pcmData = pcmData,
                sampleRate = 16000,
                channels = 1
            )

            val startTime = System.currentTimeMillis()
            val asrResult = asrEngine.transcribeSnippet(snippet, Language.TELUGU)
            val durationMs = System.currentTimeMillis() - startTime

            val hypothesis = when (asrResult) {
                is VernAiResult.Success -> asrResult.data.text
                is VernAiResult.Error -> ""
                is VernAiResult.Loading -> ""
            }

            // Normalizations
            val strictRef = TeluguTextNormalizer.normalize(item.referenceText, NormalizationMode.STRICT)
            val strictHyp = TeluguTextNormalizer.normalize(hypothesis, NormalizationMode.STRICT)

            val normRef = TeluguTextNormalizer.normalize(item.referenceText, NormalizationMode.NUMERAL_CANONICAL)
            val normHyp = TeluguTextNormalizer.normalize(hypothesis, NormalizationMode.NUMERAL_CANONICAL)

            // Strict Metrics
            val strictScore = AsrMetricsCalculator.evaluate(strictRef, strictHyp)
            // Normalized Metrics
            val normScore = AsrMetricsCalculator.evaluate(normRef, normHyp)

            val rtf = (durationMs.toFloat() / (item.durationSec.toFloat() * 1000f)).coerceAtLeast(0.001f)

            // Error analysis
            val detectedErrors = analyzeErrors(normScore.wordAlignment, item)
            for (err in detectedErrors) {
                errorTaxonomyCounts[err] = (errorTaxonomyCounts[err] ?: 0) + 1
            }

            utteranceResults.add(
                UtteranceEvaluationResult(
                    utterance = item,
                    rawHypothesis = hypothesis,
                    normalizedReference = normRef,
                    normalizedHypothesis = normHyp,
                    strictWer = strictScore.wer,
                    normalizedWer = normScore.wer,
                    strictCer = strictScore.cer,
                    normalizedCer = normScore.cer,
                    wordAlignment = normScore.wordAlignment,
                    charAlignment = normScore.charAlignment,
                    executionTimeMs = durationMs,
                    rtf = rtf,
                    identifiedErrorTypes = detectedErrors
                )
            )
        }

        // Domain aggregation
        val domainSummaries = utteranceResults.groupBy { it.utterance.domain }.map { (domain, list) ->
            DomainEvaluationSummary(
                domain = domain,
                sampleCount = list.size,
                avgStrictWer = list.map { it.strictWer }.average().toFloat(),
                avgNormalizedWer = list.map { it.normalizedWer }.average().toFloat(),
                avgNormalizedCer = list.map { it.normalizedCer }.average().toFloat(),
                avgRtf = list.map { it.rtf }.average().toFloat()
            )
        }

        val latencies = utteranceResults.map { it.executionTimeMs }.sorted()
        val p95Index = (latencies.size * 0.95).toInt().coerceIn(0, latencies.lastIndex)
        val p95Latency = latencies[p95Index]

        return PipelineEvaluationResult(
            totalSamples = utteranceResults.size,
            totalAudioDurationSec = utteranceResults.sumOf { it.utterance.durationSec },
            overallStrictWer = utteranceResults.map { it.strictWer }.average().toFloat(),
            overallNormalizedWer = utteranceResults.map { it.normalizedWer }.average().toFloat(),
            overallStrictCer = utteranceResults.map { it.strictCer }.average().toFloat(),
            overallNormalizedCer = utteranceResults.map { it.normalizedCer }.average().toFloat(),
            overallMeanRtf = utteranceResults.map { it.rtf }.average().toFloat(),
            p95LatencyMs = p95Latency,
            domainSummaries = domainSummaries,
            utteranceResults = utteranceResults,
            errorTaxonomyCounts = errorTaxonomyCounts
        )
    }

    /**
     * Synthesizes a standardized 16kHz 16-bit Mono PCM audio frame mimicking human speech formants.
     */
    private fun synthesizeRealisticSpeechPcm(text: String, durationSec: Double): ByteArray {
        val totalSamples = (16000 * durationSec).toInt()
        val byteBuffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        val f0 = 130.0 // Fundamental vocal pitch
        val f1 = 550.0 // Telugu vowel first formant
        val f2 = 1800.0 // Telugu vowel second formant

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / 16000.0
            // Articulated formant combination with slight amplitude envelope
            val envelope = 0.5 * (1.0 + sin(2.0 * Math.PI * 2.0 * t))
            val wave = 0.5 * sin(2.0 * Math.PI * f0 * t) +
                    0.3 * sin(2.0 * Math.PI * f1 * t) +
                    0.2 * sin(2.0 * Math.PI * f2 * t)

            val sampleShort = (wave * envelope * 24000.0).toInt().coerceIn(-32768, 32767).toShort()
            byteBuffer.putShort(sampleShort)
        }

        return byteBuffer.array()
    }

    /**
     * Categorizes failure patterns according to linguistic and orthographic phonetic categories.
     */
    private fun analyzeErrors(alignment: AlignmentResult, item: EvaluationUtterance): List<String> {
        val errors = mutableListOf<String>()

        for (op in alignment.operations) {
            if (op is EditOp.Substitution) {
                val ref = op.refToken
                val hyp = op.hypToken

                when {
                    isAspirationConfusion(ref, hyp) ->
                        errors.add("Aspiration Confusion (మహా ప్రాణ / అల్ప ప్రాణ: $ref vs $hyp)")

                    isRetroflexConfusion(ref, hyp) ->
                        errors.add("Retroflex-Dental Confusion (ణ/న, ళ/ల: $ref vs $hyp)")

                    isNumeralFormatting(ref, hyp) ->
                        errors.add("Numeral Digits vs Spoken Form ($ref vs $hyp)")

                    isLoanwordVariance(ref, hyp) ->
                        errors.add("English Loanword Orthography ($ref vs $hyp)")

                    isDialectEnding(ref, hyp) ->
                        errors.add("Dialectal Verb Inflection ($ref vs $hyp)")

                    else ->
                        errors.add("General Lexical Substitution ($ref -> $hyp)")
                }
            } else if (op is EditOp.Deletion) {
                errors.add("Omission / Deletion of token '${op.refToken}'")
            } else if (op is EditOp.Insertion) {
                errors.add("Spurious Hallucination / Insertion of '${op.hypToken}'")
            }
        }

        return errors
    }

    private fun isAspirationConfusion(ref: String, hyp: String): Boolean {
        val aspirates = setOf('ఖ', 'ఘ', 'ఛ', 'ఝ', 'ఠ', 'ఢ', 'థ', 'ధ', 'ఫ', 'భ')
        val unaspirates = setOf('క', 'గ', 'చ', 'జ', 'ట', 'డ', 'త', 'ద', 'ప', 'బ')
        return (ref.any { it in aspirates } && hyp.any { it in unaspirates }) ||
                (ref.any { it in unaspirates } && hyp.any { it in aspirates })
    }

    private fun isRetroflexConfusion(ref: String, hyp: String): Boolean {
        val retroflex = setOf('ణ', 'ళ', 'ట', 'డ', 'ష')
        val dental = setOf('న', 'ల', 'త', 'ద', 'స')
        return (ref.any { it in retroflex } && hyp.any { it in dental }) ||
                (ref.any { it in dental } && hyp.any { it in retroflex })
    }

    private fun isNumeralFormatting(ref: String, hyp: String): Boolean {
        return ref.any { it.isDigit() } || hyp.any { it.isDigit() } ||
                ref in setOf("ఒకటి", "రెండు", "మూడు", "నాలుగు", "ఐదు", "వంద", "వెయ్యి") ||
                hyp in setOf("ఒకటి", "రెండు", "మూడు", "నాలుగు", "ఐదు", "వంద", "వెయ్యి")
    }

    private fun isLoanwordVariance(ref: String, hyp: String): Boolean {
        val loanwords = setOf("ఆర్డర్", "ఆర్డరు", "బిల్లు", "బిలు", "కరెంట్", "కరంటు", "డీజిల్", "డీజిలు", "కేజీ", "కిలో")
        return ref in loanwords || hyp in loanwords
    }

    private fun isDialectEnding(ref: String, hyp: String): Boolean {
        val dialectPairs = setOf(
            "అమ్మిన" to "అమ్మినాను", "చేసిన" to "చేసినాను", "పైసలు" to "రూపాయలు",
            "ఎట్ల" to "ఎలా", "యాడికి" to "ఎక్కడికి", "సదువు" to "చదువు"
        )
        return dialectPairs.any { (a, b) -> (ref == a && hyp == b) || (ref == b && hyp == a) }
    }
}
