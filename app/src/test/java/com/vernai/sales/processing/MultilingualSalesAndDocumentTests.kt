package com.vernai.sales.processing

import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.ai.parser.SalesLogParser
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.Language
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import com.vernai.core.model.SalesValidationStatus
import com.vernai.document.export.SalesLedgerExporter
import com.vernai.document.processing.TestDocuments
import com.vernai.document.processing.TestDocumentType
import com.vernai.domain.repository.SalesLogRepository
import com.vernai.domain.usecase.ExplainDocumentUseCase
import com.vernai.domain.usecase.ExtractSalesLogUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MultilingualSalesAndDocumentTests {

    private lateinit var tamilParser: TamilSalesParser
    private lateinit var hindiParser: HindiSalesParser
    private lateinit var englishParser: EnglishSalesParser
    private lateinit var multilingualParser: MultilingualSalesParser
    private lateinit var validator: SalesArithmeticValidator
    private lateinit var exporter: SalesLedgerExporter
    private lateinit var explainUseCase: ExplainDocumentUseCase

    private val testDispatchers = object : VernAiDispatchers {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val asrInference: CoroutineDispatcher = Dispatchers.Unconfined
        override val llmInference: CoroutineDispatcher = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        tamilParser = TamilSalesParser()
        hindiParser = HindiSalesParser()
        englishParser = EnglishSalesParser()
        multilingualParser = MultilingualSalesParser()
        validator = SalesArithmeticValidator
        exporter = SalesLedgerExporter()
        explainUseCase = ExplainDocumentUseCase(
            llmEngine = MockLlmInferenceEngine(),
            dispatchers = testDispatchers
        )
    }

    // =========================================================================
    // 1. TAMIL SALES PARSING (Scenario 3: Chennai Shop Owner)
    // =========================================================================

    @Test
    fun `test tamil digit and word number conversions`() {
        assertEquals(5.0, tamilParser.parseNumber("5")!!, 0.001)
        assertEquals(5.0, tamilParser.parseNumber("ஐந்து")!!, 0.001)
        assertEquals(10.0, tamilParser.parseNumber("பத்து")!!, 0.001)
        assertEquals(20.0, tamilParser.parseNumber("இருபது")!!, 0.001)
        assertEquals(100.0, tamilParser.parseNumber("நூறு")!!, 0.001)
        assertEquals(200.0, tamilParser.parseNumber("இருநூறு")!!, 0.001)
        assertEquals(1000.0, tamilParser.parseNumber("ஆயிரம்")!!, 0.001)
    }

    @Test
    fun `test tamil single item dictation extraction`() {
        val transcript = "5 கிலோ தக்காளி 200 ரூபாய்"
        val items = tamilParser.parseTranscript(transcript)

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("தக்காளி", item.originalTerm)
        assertEquals("Tomato", item.standardName)
        assertEquals(5.0, item.quantity, 0.001)
        assertEquals("கிலோ (kg)", item.unit)
        assertEquals(40.0, item.unitPrice, 0.001)
        assertEquals(200.0, item.totalPrice, 0.001)
        assertEquals(SalesValidationStatus.VERIFIED, item.validationStatus)
    }

    @Test
    fun `test tamil multi-item dictation with cash and credit notes`() {
        val transcript = "2 பாக்கெட் எண்ணெய் 260 ரூபாய் ரொக்கம், 1 டஜன் சோப்பு 120 ரூபாய் கடன்"
        val items = tamilParser.parseTranscript(transcript)

        assertEquals(2, items.size)

        val oil = items[0]
        assertEquals("எண்ணெய்", oil.originalTerm)
        assertEquals("Cooking Oil", oil.standardName)
        assertEquals(2.0, oil.quantity, 0.001)
        assertEquals("பாக்கெட் (packet)", oil.unit)
        assertEquals(130.0, oil.unitPrice, 0.001)
        assertEquals(260.0, oil.totalPrice, 0.001)
        assertTrue(oil.notes?.contains("ரொக்கம்") == true)

        val soap = items[1]
        assertEquals("சோப்பு", soap.originalTerm)
        assertEquals("Soap", soap.standardName)
        assertEquals(1.0, soap.quantity, 0.001)
        assertEquals("டஜன் (dozen)", soap.unit)
        assertEquals(120.0, soap.unitPrice, 0.001)
        assertEquals(120.0, soap.totalPrice, 0.001)
        assertTrue(soap.notes?.contains("கடன்") == true)
    }

    // =========================================================================
    // 2. HINDI SALES PARSING
    // =========================================================================

    @Test
    fun `test hindi devanagari and word number conversions`() {
        assertEquals(5.0, hindiParser.parseNumber("5")!!, 0.001)
        assertEquals(5.0, hindiParser.parseNumber("५")!!, 0.001)
        assertEquals(5.0, hindiParser.parseNumber("पांच")!!, 0.001)
        assertEquals(2.0, hindiParser.parseNumber("दो")!!, 0.001)
        assertEquals(10.0, hindiParser.parseNumber("दस")!!, 0.001)
        assertEquals(20.0, hindiParser.parseNumber("बीस")!!, 0.001)
        assertEquals(50.0, hindiParser.parseNumber("पचास")!!, 0.001)
        assertEquals(100.0, hindiParser.parseNumber("सौ")!!, 0.001)
        assertEquals(200.0, hindiParser.parseNumber("दो सौ")!!, 0.001)
        assertEquals(1000.0, hindiParser.parseNumber("हजार")!!, 0.001)
    }

    @Test
    fun `test hindi single item dictation extraction`() {
        val transcript = "5 किलो टमाटर 200 रुपये"
        val items = hindiParser.parseTranscript(transcript)

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("टमाटर", item.originalTerm)
        assertEquals("Tomato", item.standardName)
        assertEquals(5.0, item.quantity, 0.001)
        assertEquals("किलो (kg)", item.unit)
        assertEquals(40.0, item.unitPrice, 0.001)
        assertEquals(200.0, item.totalPrice, 0.001)
        assertEquals(SalesValidationStatus.VERIFIED, item.validationStatus)
    }

    @Test
    fun `test hindi multi-item dictation with cash and credit notes`() {
        val transcript = "2 पैकेट तेल 260 रुपये नकद, 1 दर्जन साबुन 120 रुपये उधार"
        val items = hindiParser.parseTranscript(transcript)

        assertEquals(2, items.size)

        val oil = items[0]
        assertEquals("तेल", oil.originalTerm)
        assertEquals("Cooking Oil", oil.standardName)
        assertEquals(2.0, oil.quantity, 0.001)
        assertEquals("पैकेट (packet)", oil.unit)
        assertEquals(130.0, oil.unitPrice, 0.001)
        assertEquals(260.0, oil.totalPrice, 0.001)
        assertTrue(oil.notes?.contains("नकद") == true)

        val soap = items[1]
        assertEquals("साबुन", soap.originalTerm)
        assertEquals("Soap", soap.standardName)
        assertEquals(1.0, soap.quantity, 0.001)
        assertEquals("दर्जन (dozen)", soap.unit)
        assertEquals(120.0, soap.unitPrice, 0.001)
        assertEquals(120.0, soap.totalPrice, 0.001)
        assertTrue(soap.notes?.contains("उधार") == true)
    }

    // =========================================================================
    // 3. ENGLISH SALES PARSING
    // =========================================================================

    @Test
    fun `test english sales parsing`() {
        val transcript = "5 kg tomato 200 rupees cash, 2 packets oil 260 rupees"
        val items = englishParser.parseTranscript(transcript)

        assertEquals(2, items.size)

        val tomato = items[0]
        assertEquals("Tomato", tomato.standardName)
        assertEquals(5.0, tomato.quantity, 0.001)
        assertEquals("kg", tomato.unit)
        assertEquals(40.0, tomato.unitPrice, 0.001)
        assertEquals(200.0, tomato.totalPrice, 0.001)
        assertTrue(tomato.notes?.contains("Cash", ignoreCase = true) == true)

        val oil = items[1]
        assertEquals("Cooking Oil", oil.standardName)
        assertEquals(2.0, oil.quantity, 0.001)
        assertEquals("packet", oil.unit)
        assertEquals(130.0, oil.unitPrice, 0.001)
        assertEquals(260.0, oil.totalPrice, 0.001)
    }

    // =========================================================================
    // 4. MULTILINGUAL DISPATCHER & USE CASE EXECUTION
    // =========================================================================

    @Test
    fun `test multilingual sales parser auto-routes correctly`() {
        val tamilResult = multilingualParser.parseTranscript("5 கிலோ தக்காளி 200")
        assertEquals(1, tamilResult.size)
        assertEquals("Tomato", tamilResult[0].standardName)

        val hindiResult = multilingualParser.parseTranscript("5 किलो टमाटर 200")
        assertEquals(1, hindiResult.size)
        assertEquals("Tomato", hindiResult[0].standardName)

        val englishResult = multilingualParser.parseTranscript("5 kg tomato 200")
        assertEquals(1, englishResult.size)
        assertEquals("Tomato", englishResult[0].standardName)

        val teluguResult = multilingualParser.parseTranscript("5 కేజీల టమాటా 200")
        assertEquals(1, teluguResult.size)
        assertEquals("Tomato", teluguResult[0].standardName)
    }

    @Test
    fun `test extract sales log use case with tamil and hindi`() = runTest {
        val mockRepo = object : SalesLogRepository {
            override fun getSalesLogsStream() = flowOf(emptyList<SalesLog>())
            override suspend fun getSalesLogById(id: String) = null
            override suspend fun saveSalesLog(salesLog: SalesLog) = VernAiResult.Success(Unit)
            override suspend fun deleteSalesLog(id: String) = VernAiResult.Success(Unit)
        }

        val useCase = ExtractSalesLogUseCase(
            llmEngine = MockLlmInferenceEngine(),
            parser = SalesLogParser(),
            multilingualParser = multilingualParser,
            repository = mockRepo,
            inferenceLock = InferenceLock(),
            dispatchers = testDispatchers
        )

        val tamilRes = useCase("5 கிலோ தக்காளி 200 ரூபாய்", Language.TAMIL)
        assertTrue(tamilRes is VernAiResult.Success)
        val tamilLog = (tamilRes as VernAiResult.Success).data
        assertEquals(1, tamilLog.items.size)
        assertEquals(200.0, tamilLog.grandTotal, 0.001)
        assertEquals(Language.TAMIL, tamilLog.detectedLanguage)

        val hindiRes = useCase("5 किलो टमाटर 200 रुपये", Language.HINDI)
        assertTrue(hindiRes is VernAiResult.Success)
        val hindiLog = (hindiRes as VernAiResult.Success).data
        assertEquals(1, hindiLog.items.size)
        assertEquals(200.0, hindiLog.grandTotal, 0.001)
        assertEquals(Language.HINDI, hindiLog.detectedLanguage)
    }

    // =========================================================================
    // 5. LOCALIZED ARITHMETIC RECONCILIATION & DISCREPANCIES
    // =========================================================================

    @Test
    fun `test arithmetic validator produces localized discrepancy messages`() {
        val discrepancyItem = SalesItem(
            id = "1",
            date = "2026-09-20",
            originalTerm = "அரிசி",
            standardName = "Rice",
            quantity = 10.0,
            unit = "கிலோ (kg)",
            unitPrice = 40.0,
            totalPrice = 350.0 // 10 * 40 = 400 != 350
        )

        val tamilResult = validator.validateAndReconcile(discrepancyItem, Language.TAMIL)
        assertEquals(SalesValidationStatus.ARITHMETIC_MISMATCH, tamilResult.status)
        assertNotNull(tamilResult.prompt)
        assertTrue(tamilResult.prompt!!.contains("ரூ. 400.00") || tamilResult.prompt!!.contains("350.00"))
        assertTrue(tamilResult.prompt!!.contains("முரண்பாடு") || tamilResult.prompt!!.contains("சரிபார்க்கவும்"))

        val hindiResult = validator.validateAndReconcile(
            discrepancyItem.copy(originalTerm = "चावल"),
            Language.HINDI
        )
        assertEquals(SalesValidationStatus.ARITHMETIC_MISMATCH, hindiResult.status)
        assertNotNull(hindiResult.prompt)
        assertTrue(hindiResult.prompt!!.contains("400.00") || hindiResult.prompt!!.contains("350.00"))
        assertTrue(hindiResult.prompt!!.contains("हिसाब में अंतर") || hindiResult.prompt!!.contains("अंतर") || hindiResult.prompt!!.contains("सही करें"))
    }

    // =========================================================================
    // 6. EXPORTING TO OFFICE KIT SHEETS (CSV UTF-8 BOM & SPREADSHEETML XLSX)
    // =========================================================================

    @Test
    fun `test sales ledger csv export has utf-8 bom and localized tamil headers`() {
        val tempFile = Files.createTempFile("sales_ledger_tamil", ".csv").toFile()
        val salesLog = SalesLog(
            rawSpokenText = "5 கிலோ தக்காளி 200",
            detectedLanguage = Language.TAMIL,
            items = listOf(
                SalesItem(
                    id = "1",
                    date = "2026-09-20",
                    originalTerm = "தக்காளி (Tomato)",
                    standardName = "Tomato",
                    quantity = 5.0,
                    unit = "கிலோ (kg)",
                    unitPrice = 40.0,
                    totalPrice = 200.0,
                    notes = "ரொக்கம்",
                    validationStatus = SalesValidationStatus.VERIFIED
                )
            ),
            grandTotal = 200.0
        )

        val result = exporter.exportToCsv(salesLog, tempFile)
        assertTrue(result is VernAiResult.Success)

        val bytes = tempFile.readBytes()
        // Verify UTF-8 Byte Order Mark (BOM)
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])

        val content = tempFile.readText(Charsets.UTF_8)
        assertTrue(content.contains("தேதி (Date)"))
        assertTrue(content.contains("பொருள் (Item)"))
        assertTrue(content.contains("அளவு (Qty)"))
        assertTrue(content.contains("தக்காளி (Tomato)"))
        assertTrue(content.contains("மொத்த விற்பனை (Grand Total)"))

        tempFile.delete()
    }

    @Test
    fun `test sales ledger xlsx export has localized hindi headers and worksheet name`() {
        val tempFile = Files.createTempFile("sales_ledger_hindi", ".xlsx").toFile()
        val salesLog = SalesLog(
            rawSpokenText = "5 किलो टमाटर 200",
            detectedLanguage = Language.HINDI,
            items = listOf(
                SalesItem(
                    id = "1",
                    date = "2026-09-20",
                    originalTerm = "टमाटर (Tomato)",
                    standardName = "Tomato",
                    quantity = 5.0,
                    unit = "किलो (kg)",
                    unitPrice = 40.0,
                    totalPrice = 200.0,
                    notes = "नकद",
                    validationStatus = SalesValidationStatus.VERIFIED
                )
            ),
            grandTotal = 200.0
        )

        val result = exporter.exportToXlsx(salesLog, tempFile)
        assertTrue(result is VernAiResult.Success)

        val xmlContent = tempFile.readText(Charsets.UTF_8)
        assertTrue(xmlContent.contains("बिक्री खाता")) // Localized Worksheet Name
        assertTrue(xmlContent.contains("दिनांक (Date)"))
        assertTrue(xmlContent.contains("सामग्री (Item)"))
        assertTrue(xmlContent.contains("मात्रा (Qty)"))
        assertTrue(xmlContent.contains("टमाटर (Tomato)"))
        assertTrue(xmlContent.contains("=SUM(")) // Native spreadsheet formula

        tempFile.delete()
    }

    // =========================================================================
    // 7. SCHOLARSHIP FORM GUIDANCE (Scenario 2: Pune Student)
    // =========================================================================

    @Test
    fun `test scholarship form guidance extracts eligibility, docs, steps, deadline and 0 fee in marathi`() {
        val formText = TestDocuments.getSampleDocumentText(TestDocumentType.SCHOLARSHIP_APPLICATION_FORM)

        val guidanceMarathi = explainUseCase.extractFormFillingGuide(formText, Language.MARATHI)
        assertEquals(Language.MARATHI, guidanceMarathi.targetLanguage)
        assertFalse(guidanceMarathi.eligibilityCriteria.isEmpty())
        assertFalse(guidanceMarathi.mandatoryDocuments.isEmpty())
        assertFalse(guidanceMarathi.sectionWiseInstructions.isEmpty())
        assertTrue(guidanceMarathi.submissionDeadline.contains("31") || guidanceMarathi.submissionDeadline.contains("२०२६") || guidanceMarathi.submissionDeadline.contains("2026"))
        assertTrue(guidanceMarathi.applicationFee.contains("0") || guidanceMarathi.applicationFee.contains("०") || guidanceMarathi.applicationFee.contains("मोफत") || guidanceMarathi.applicationFee.contains("Free"))

        val guidanceHindi = explainUseCase.extractFormFillingGuide(formText, Language.HINDI)
        assertEquals(Language.HINDI, guidanceHindi.targetLanguage)
        assertFalse(guidanceHindi.eligibilityCriteria.isEmpty())
        assertFalse(guidanceHindi.mandatoryDocuments.isEmpty())

        val guidanceTamil = explainUseCase.extractFormFillingGuide(formText, Language.TAMIL)
        assertEquals(Language.TAMIL, guidanceTamil.targetLanguage)
        assertFalse(guidanceTamil.eligibilityCriteria.isEmpty())
        assertFalse(guidanceTamil.mandatoryDocuments.isEmpty())

        val guidanceEnglish = explainUseCase.extractFormFillingGuide(formText, Language.ENGLISH)
        assertEquals(Language.ENGLISH, guidanceEnglish.targetLanguage)
        assertFalse(guidanceEnglish.eligibilityCriteria.isEmpty())
        assertFalse(guidanceEnglish.mandatoryDocuments.isEmpty())
    }
}
