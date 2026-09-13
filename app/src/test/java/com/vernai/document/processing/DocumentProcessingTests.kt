package com.vernai.document.processing

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.Language
import com.vernai.domain.repository.DocumentRepository
import com.vernai.domain.usecase.ExplainDocumentUseCase
import com.vernai.ai.mock.MockLlmInferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class DocumentProcessingTests {

    private lateinit var chunker: DocumentChunker
    private lateinit var pdfExtractor: LocalPdfExtractor
    private lateinit var docProcessor: OfflineDocumentProcessorImpl

    @Before
    fun setUp() {
        chunker = DocumentChunker(maxChunkChars = 300, overlapChars = 50)
        pdfExtractor = LocalPdfExtractor()
        docProcessor = OfflineDocumentProcessorImpl(pdfExtractor = pdfExtractor, ocrEngine = AndroidLocalOcrEngine())
    }

    // --- 1. Document Chunker Tests ---

    @Test
    fun chunkDocument_shortText_returnsSingleChunk() {
        val shortText = "రైతులకు పట్టాదార్ పాస్ పుస్తకం జారీ చేయబడింది."
        val chunks = chunker.chunkDocument(shortText)

        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].index)
        assertEquals(1, chunks[0].totalChunks)
        assertEquals(shortText, chunks[0].text)
    }

    @Test
    fun chunkDocument_longText_createsMultipleOverlappingChunks() {
        val longParagraphs = """
            మొదటి భాగం: ఈ నోటీసు రెవెన్యూ డిపార్ట్‌మెంట్ నుండి సర్వే నంబర్ 142/A కొరకు జారీ చేయబడినది. గ్రామస్తులు తమ భూమి పట్టా వివరాలను పరిశీలించుకోవలెను.
            
            రెండవ భాగం: నిర్ణీత రుసుము రూ. 150/- మీసేవ కేంద్రం ద్వారా చెల్లించి రసీదు పొందాలి. ఆధార్ కార్డు, పాత పహాణీ నకలుతో తహశీల్దార్ ఆఫీసుకు స్వయంగా హాజరుకావలెను.
            
            మూడవ భాగం: 30 రోజుల గడువు ముగిసినచో ఈ నోటీసు చట్టరీత్యా రద్దగును మరియు భూ హక్కుల మార్పిడికి ఎటువంటి అవకాశం ఉండదు.
        """.trimIndent()

        val chunks = chunker.chunkDocument(longParagraphs)

        assertTrue(chunks.size >= 2)
        assertEquals(chunks.size, chunks.first().totalChunks)
        // Verify all chunks have non-empty text and valid indices
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.index)
            assertTrue(chunk.text.isNotBlank())
            assertTrue(chunk.headingPreview.isNotBlank())
        }
    }

    @Test
    fun chunkDocument_emptyText_returnsEmptyList() {
        val chunks = chunker.chunkDocument("")
        assertTrue(chunks.isEmpty())
    }

    // --- 2. Document Limitations Analyzer Tests ---

    @Test
    fun limitationsAnalyzer_scannedTeluguDocument_identifiesTeluguOcrLimitations() {
        val teluguScannedSample = "గ్రామ పంచాయతీ కార్యాలయం శాంతినగర్. నోటీసు: ఇంటి పన్ను బకాయి రూ. 850/-. సంతకం ఉంది."
        val report = DocumentLimitationsAnalyzer.analyze(
            rawText = teluguScannedSample,
            isScanned = true,
            language = Language.TELUGU
        )

        assertTrue(report.requiresManualReview)
        assertTrue(report.detectedLimitations.contains(DocumentLimitationCategory.TELUGU_COMPLEX_LIGATURES))
        assertTrue(report.detectedLimitations.contains(DocumentLimitationCategory.TELUGU_VOWEL_SIGN_AND_DOT_SMUDGING))
        assertTrue(report.advisoryTelugu.contains("స్కాన్"))
    }

    @Test
    fun limitationsAnalyzer_digitalDocument_highConfidenceWithoutReview() {
        val digitalDoc = "GOVERNMENT OF TELANGANA. Digital land record verified cleanly."
        val report = DocumentLimitationsAnalyzer.analyze(
            rawText = digitalDoc,
            isScanned = false,
            language = Language.ENGLISH
        )

        assertFalse(report.requiresManualReview)
        assertTrue(report.estimatedOcrConfidence >= 0.90f)
    }

    // --- 3. Local PDF Extractor Tests ---

    @Test
    fun localPdfExtractor_validMinimalPdf_extractsCleanText() {
        // Construct a valid minimal PDF byte stream
        val minimalPdf = """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >> endobj
            4 0 obj << /Length 55 >>
            stream
            BT
            /F1 12 Tf
            (TSSPDCL Electricity Disconnection Notice) Tj
            ET
            endstream
            endobj
            xref
            0 5
            trailer << /Root 1 0 R >>
            %%EOF
        """.trimIndent()

        val stream = ByteArrayInputStream(minimalPdf.toByteArray(StandardCharsets.ISO_8859_1))
        val result = pdfExtractor.extract(stream)

        assertNotNull(result)
        assertEquals(1, result.pageCount)
        assertTrue(result.extractedText.contains("TSSPDCL Electricity Disconnection Notice"))
    }

    @Test
    fun localPdfExtractor_corruptedPdf_throwsCorruptedPdfException() {
        val corruptedBytes = "THIS IS NOT A VALID PDF FILE AT ALL".toByteArray()
        try {
            pdfExtractor.extract(ByteArrayInputStream(corruptedBytes))
            org.junit.Assert.fail("Expected PdfExtractionException.CorruptedPdf")
        } catch (e: PdfExtractionException.CorruptedPdf) {
            assertTrue(e.message!!.contains("%PDF-"))
        }
    }

    @Test
    fun localPdfExtractor_encryptedPdf_throwsPasswordProtectedException() {
        val encryptedPdf = """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            trailer << /Root 1 0 R /Encrypt 5 0 R >>
            %%EOF
        """.trimIndent()

        try {
            pdfExtractor.extract(ByteArrayInputStream(encryptedPdf.toByteArray(StandardCharsets.ISO_8859_1)))
            org.junit.Assert.fail("Expected PdfExtractionException.PasswordProtected")
        } catch (_: PdfExtractionException.PasswordProtected) {
            // Expected
        }
    }

    // --- 4. Offline Document Processor Integration & Fallback Tests ---

    @Test
    fun docProcessor_extractFromBytes_handlesCorruptedFileGracefully() = runTest {
        val result = docProcessor.extractFromBytes(
            fileName = "corrupt.pdf",
            mimeType = "application/pdf",
            bytes = "NOT_A_PDF".toByteArray(),
            languageHint = Language.TELUGU
        )

        assertTrue(result is VernAiResult.Error)
        val msg = (result as VernAiResult.Error).message ?: ""
        assertTrue(msg.contains("పాడైంది") || msg.contains("Corrupted"))
    }

    @Test
    fun docProcessor_scannedPdfFallback_triggersOcrEngine() = runTest {
        // PDF with page count but zero text stream
        val scannedPdf = """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >> endobj
            trailer << /Root 1 0 R >>
            %%EOF
        """.trimIndent()

        val result = docProcessor.extractFromBytes(
            fileName = "scanned.pdf",
            mimeType = "application/pdf",
            bytes = scannedPdf.toByteArray(StandardCharsets.ISO_8859_1),
            languageHint = Language.TELUGU
        )

        assertTrue(result is VernAiResult.Success)
        val doc = (result as VernAiResult.Success).data
        assertTrue(doc.isScannedImage) // Verified OCR fallback was triggered
        assertTrue(doc.rawText.contains("పట్టాదార్"))
    }

    // --- 5. Explain Document Use Case Tests ---

    private class InMemoryDocumentRepository : DocumentRepository {
        val saved = mutableListOf<ExplanationReport>()
        override fun getExplanationsStream(): Flow<List<ExplanationReport>> = flowOf(saved)
        override suspend fun getExplanationById(id: String): ExplanationReport? = saved.find { it.id == id }
        override suspend fun saveExplanation(report: ExplanationReport): VernAiResult<Unit> {
            saved.add(report)
            return VernAiResult.Success(Unit)
        }
        override suspend fun deleteExplanation(id: String): VernAiResult<Unit> {
            saved.removeAll { it.id == id }
            return VernAiResult.Success(Unit)
        }
    }

    @Test
    fun explainDocumentUseCase_summarize_returnsStructuredReportAndPersists() = runTest {
        val mockRepo = InMemoryDocumentRepository()
        val mockLlm = MockLlmInferenceEngine()
        val useCase = ExplainDocumentUseCase(llmEngine = mockLlm, repository = mockRepo)

        val docText = TestDocuments.getSampleDocumentText(TestDocumentType.REVENUE_PATTADAR_NOTICE)
        val result = useCase.summarizeDocument(
            documentTitle = "Revenue_Notice_142A.pdf",
            documentText = docText,
            targetLanguage = Language.TELUGU
        )

        assertTrue(result is VernAiResult.Success)
        val report = (result as VernAiResult.Success).data

        assertNotNull(report.summaryInVernacular)
        assertTrue(report.summaryInVernacular.contains("పట్టా"))
        assertTrue(report.keyActionPoints.isNotEmpty())
        assertTrue(report.legalDeadlines.isNotEmpty())
        assertEquals(1, mockRepo.saved.size)
    }

    @Test
    fun explainDocumentUseCase_explainSelectedSnippet_returnsConversationalExplanation() = runTest {
        val mockLlm = MockLlmInferenceEngine()
        val useCase = ExplainDocumentUseCase(llmEngine = mockLlm)

        val snippet = "ఈ నోటీసు అందిన 30 రోజులలోపు సంబంధిత తహశీల్దార్ కార్యాలయంలో ఆధార్ కార్డుతో హాజరుకావలెను."
        val result = useCase.explainSelectedText(snippet, Language.TELUGU)

        assertTrue(result is VernAiResult.Success)
        val explanation = (result as VernAiResult.Success).data
        assertTrue(explanation.isNotBlank())
        assertTrue(explanation.contains("తహశీల్దార్") || explanation.contains("వివరణ"))
    }
}
