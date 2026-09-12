package com.vernai.ai.asr.evaluation

import com.vernai.ai.asr.evaluation.dataset.EvaluationDomain
import com.vernai.ai.asr.evaluation.dataset.TeluguEvaluationDataset
import com.vernai.ai.asr.evaluation.metrics.AsrMetricsCalculator
import com.vernai.ai.asr.evaluation.metrics.EditDistance
import com.vernai.ai.asr.evaluation.metrics.EditOp
import com.vernai.ai.asr.evaluation.normalizer.NormalizationMode
import com.vernai.ai.asr.evaluation.normalizer.TeluguTextNormalizer
import com.vernai.ai.asr.evaluation.pipeline.TeluguAsrEvaluationPipeline
import com.vernai.ai.asr.evaluation.report.EvaluationReportGenerator
import com.vernai.ai.mock.MockAsrEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TeluguAsrEvaluationPipelineTest {

    @Test
    fun testLevenshteinAlignmentOperations() {
        val ref = listOf("ఈరోజు", "5", "కేజీల", "టమాటా")
        val hyp = listOf("ఈరోజు", "ఐదు", "కేజీల", "టమాట", "అమ్మిన")

        val alignment = EditDistance.align(ref, hyp)

        assertEquals(2, alignment.matches) // "ఈరోజు", "కేజీల"
        assertEquals(2, alignment.substitutions) // "5"->"ఐదు", "టమాటా"->"టమాట"
        assertEquals(0, alignment.deletions)
        assertEquals(1, alignment.insertions) // "అమ్మిన"
        assertEquals(4, alignment.referenceLength)
        assertEquals(5, alignment.hypothesisLength)

        // WER = (2 Sub + 0 Del + 1 Ins) / 4 = 3 / 4 = 0.75 (75%)
        assertEquals(0.75f, alignment.errorRate, 0.001f)
    }

    @Test
    fun testWerAndCerCalculation() {
        val ref = "వరి పంటకు ఎరువులు కావాలి"
        val hyp = "వరి పంటకు ఎరువు కావాలి"

        val score = AsrMetricsCalculator.evaluate(ref, hyp)

        // 4 words: 1 substitution ("ఎరువులు" -> "ఎరువు") -> WER = 1/4 = 0.25 (25%)
        assertEquals(0.25f, score.wer, 0.01f)
        assertTrue("CER should be lower than WER for single-letter suffix discrepancy", score.cer < score.wer)
        assertTrue("CER should be positive", score.cer > 0.0f)
    }

    @Test
    fun testTeluguNormalizerPunctuationAndUnicode() {
        val raw = "  వరి పంటకు , ఎరువులు ; కావాలి !  "
        val normalized = TeluguTextNormalizer.normalize(raw, NormalizationMode.STRICT)
        assertEquals("వరి పంటకు ఎరువులు కావాలి", normalized)
    }

    @Test
    fun testTeluguNormalizerNumeralCanonicalization() {
        val rawWithDigits = "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు"
        val normalized = TeluguTextNormalizer.normalize(rawWithDigits, NormalizationMode.NUMERAL_CANONICAL)
        // 5 -> ఐదు, 200 -> రెండు వందలు, కేజీల -> కిలోల
        assertTrue("Should contain spoken number ఐదు", normalized.contains("ఐదు"))
        assertTrue("Should contain spoken number రెండు వందలు", normalized.contains("రెండు వందలు"))
    }

    @Test
    fun testTeluguNormalizerDialectHarmonization() {
        val dialectal = "సరుకులన్నీ అమ్మిన పైసలు ఎట్ల వచ్చిన"
        val harmonized = TeluguTextNormalizer.normalize(dialectal, NormalizationMode.DIALECT_AWARE)
        // అమ్మిన -> అమ్మినాను, పైసలు -> రూపాయలు, ఎట్ల -> ఎలా, వచ్చిన -> వచ్చినాను
        assertTrue(harmonized.contains("అమ్మినాను"))
        assertTrue(harmonized.contains("రూపాయలు"))
        assertTrue(harmonized.contains("ఎలా"))
        assertTrue(harmonized.contains("వచ్చినాను"))
    }

    @Test
    fun testDatasetIntegrityAndDomainCoverage() {
        val dataset = TeluguEvaluationDataset.utterances
        assertEquals(16, dataset.size)

        // Verify all 8 domain categories have test samples
        val coveredDomains = dataset.map { it.domain }.toSet()
        assertEquals(EvaluationDomain.entries.size, coveredDomains.size)

        for (item in dataset) {
            assertTrue("ID should start with TEL_", item.id.startsWith("TEL_"))
            assertTrue("Reference text must not be blank", item.referenceText.isNotBlank())
            assertTrue("Duration should be positive", item.durationSec > 0.0)
            assertTrue("Primary challenge should be stated", item.primaryChallenge.isNotBlank())
            assertTrue("Keywords should be provided", item.keyPhonemesOrKeywords.isNotEmpty())
        }
    }

    @Test
    fun testFullOfflineEvaluationPipelineExecution() = runTest {
        val engine = MockAsrEngine()
        val pipeline = TeluguAsrEvaluationPipeline(asrEngine = engine)

        val result = pipeline.runEvaluation()

        assertNotNull(result)
        assertEquals(16, result.totalSamples)
        assertTrue(result.totalAudioDurationSec > 50.0)
        assertTrue("Mean RTF should be faster than real-time (< 1.0)", result.overallMeanRtf < 1.0f)
        assertTrue("P95 latency should be measurable", result.p95LatencyMs >= 0)

        // Generate Markdown report
        val markdownReport = EvaluationReportGenerator.generateMarkdownReport(result)
        assertNotNull(markdownReport)
        assertTrue(markdownReport.contains("VernAI — On-Device Telugu ASR Benchmark"))
        assertTrue(markdownReport.contains("Executive Performance Metrics"))
        assertTrue(markdownReport.contains("Linguistic Error Taxonomy"))

        // Persist report artifact for review
        val reportFile = File("evaluation_report_telugu_asr.md")
        reportFile.writeText(markdownReport)
        assertTrue("Report file should be created", reportFile.exists())
    }
}
