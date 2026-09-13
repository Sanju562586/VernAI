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
import com.vernai.domain.repository.SalesLogRepository
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

class TeluguSalesProcessingTests {

    private lateinit var parser: TeluguSalesParser
    private lateinit var validator: SalesArithmeticValidator
    private lateinit var exporter: SalesLedgerExporter

    private val testDispatchers = object : VernAiDispatchers {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val asrInference: CoroutineDispatcher = Dispatchers.Unconfined
        override val llmInference: CoroutineDispatcher = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        parser = TeluguSalesParser()
        validator = SalesArithmeticValidator
        exporter = SalesLedgerExporter()
    }

    // ==========================================
    // 1. DETERMINISTIC TELUGU NUMBER WORDS & DIGITS
    // ==========================================

    @Test
    fun teluguParser_standardNumbersAndUnits_extractsCorrectly() {
        val transcript = "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు"
        val items = parser.parseTranscript(transcript, "2026-09-13")

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("టమాటా", item.originalTerm)
        assertEquals("Tomato", item.standardName)
        assertEquals(5.0, item.quantity, 0.01)
        assertEquals("కేజీ (kg)", item.unit)
        assertEquals(200.0, item.totalPrice, 0.01)
    }

    @Test
    fun teluguParser_teluguNativeDigits_normalizesAndExtracts() {
        val transcript = "౫ కేజీల టమాటా ౨౦౦ రూపాయలు"
        val items = parser.parseTranscript(transcript)

        assertEquals(1, items.size)
        assertEquals(5.0, items[0].quantity, 0.01)
        assertEquals(200.0, items[0].totalPrice, 0.01)
    }

    @Test
    fun teluguParser_numberWordsAndCompounds_convertsAccurately() {
        val transcript = "రెండు నూనె ప్యాకెట్లు రెండు వందల యాభై రూపాయలు"
        val items = parser.parseTranscript(transcript)

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(2.0, item.quantity, 0.01)
        assertEquals("ప్యాకెట్ (packet)", item.unit)
        assertEquals(250.0, item.totalPrice, 0.01)
    }

    @Test
    fun teluguParser_teluguFractions_handlesAccurately() {
        // అర (half = 0.5)
        val halfItem = parser.parseSingleClause("అర కేజీ అల్లం 60 రూపాయలు", "2026-09-13")
        assertNotNull(halfItem)
        assertEquals(0.5, halfItem!!.quantity, 0.01)
        assertEquals(60.0, halfItem.totalPrice, 0.01)

        // ఒకటిన్నర (1.5)
        val oneAndHalf = parser.parseSingleClause("ఒకటిన్నర లీటర్ల నూనె 210 రూపాయలు", "2026-09-13")
        assertNotNull(oneAndHalf)
        assertEquals(1.5, oneAndHalf!!.quantity, 0.01)
        assertEquals("లీటర్ (L)", oneAndHalf.unit)
        assertEquals(210.0, oneAndHalf.totalPrice, 0.01)

        // రెండున్నర (2.5)
        val twoAndHalf = parser.parseSingleClause("రెండున్నర కేజీల ఉల్లిపాయలు 100 రూపాయలు", "2026-09-13")
        assertNotNull(twoAndHalf)
        assertEquals(2.5, twoAndHalf!!.quantity, 0.01)
        assertEquals(100.0, twoAndHalf.totalPrice, 0.01)
    }

    @Test
    fun teluguParser_notesAndPaymentModes_extractedCleanly() {
        val transcript = "1 డజన్ సబ్బులు 120 రూపాయలు రమేష్ కి అరువు"
        val items = parser.parseTranscript(transcript)

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(1.0, item.quantity, 0.01)
        assertEquals("డజన్ (dozen)", item.unit)
        assertEquals(120.0, item.totalPrice, 0.01)
        assertNotNull(item.notes)
        assertTrue(item.notes!!.contains("అరువు") || item.notes!!.contains("రమేష్"))
    }

    @Test
    fun teluguParser_multiItemClauses_segmentsCorrectly() {
        val transcript = "5 కేజీల టమాటా 200 రూపాయలు, 2 నూనె ప్యాకెట్లు 260 రూపాయలు నగదు మరియు 10 కేజీల బియ్యం 400 రూపాయలు"
        val items = parser.parseTranscript(transcript)

        assertEquals(3, items.size)
        assertEquals(5.0, items[0].quantity, 0.01)
        assertEquals(2.0, items[1].quantity, 0.01)
        assertEquals(10.0, items[2].quantity, 0.01)
    }

    // ==========================================
    // 2. ARITHMETIC VALIDATION & AMBIGUITY HANDLING
    // ==========================================

    @Test
    fun arithmeticValidator_exactArithmeticMatch_marksVerified() {
        val item = SalesItem(
            originalTerm = "టమాటా",
            quantity = 5.0,
            unit = "కేజీ (kg)",
            unitPrice = 40.0,
            totalPrice = 200.0
        )
        val result = validator.validateAndReconcile(item)

        assertEquals(SalesValidationStatus.VERIFIED, result.status)
        assertEquals(200.0, result.item.totalPrice, 0.01)
        assertEquals(40.0, result.item.unitPrice, 0.01)
    }

    @Test
    fun arithmeticValidator_missingTotalPrice_computesDeterministically() {
        val item = SalesItem(
            originalTerm = "నూనె",
            quantity = 3.0,
            unit = "ప్యాకెట్ (packet)",
            unitPrice = 130.0,
            totalPrice = 0.0
        )
        val result = validator.validateAndReconcile(item)

        assertEquals(SalesValidationStatus.VERIFIED, result.status)
        assertEquals(390.0, result.item.totalPrice, 0.01)
    }

    @Test
    fun arithmeticValidator_missingUnitPrice_computesDeterministically() {
        val item = SalesItem(
            originalTerm = "బియ్యం",
            quantity = 10.0,
            unit = "కేజీ (kg)",
            unitPrice = 0.0,
            totalPrice = 450.0
        )
        val result = validator.validateAndReconcile(item)

        assertEquals(SalesValidationStatus.VERIFIED, result.status)
        assertEquals(45.0, result.item.unitPrice, 0.01)
    }

    @Test
    fun arithmeticValidator_arithmeticDiscrepancy_flagsMismatchInsteadOfSilentlyOverwriting() {
        // Dictated: 10 kg @ Rs 40/kg, but total stated 350 (expected 400)
        val item = SalesItem(
            originalTerm = "బియ్యం",
            quantity = 10.0,
            unit = "కేజీ (kg)",
            unitPrice = 40.0,
            totalPrice = 350.0
        )
        val result = validator.validateAndReconcile(item)

        assertEquals(SalesValidationStatus.ARITHMETIC_MISMATCH, result.status)
        assertNotNull(result.discrepancyMessage)
        assertTrue(result.discrepancyMessage!!.contains("లెక్క సరిపోలేదు"))
        assertNotNull(result.prompt)
        assertTrue(result.prompt!!.contains("సరిచేయమంటారా"))
    }

    @Test
    fun arithmeticValidator_missingQuantity_asksClarificationInsteadOfGuessing() {
        val item = SalesItem(
            originalTerm = "టమాటా",
            quantity = 0.0, // missing
            unit = "unit",
            unitPrice = 0.0,
            totalPrice = 150.0
        )
        val result = validator.validateAndReconcile(item)

        assertEquals(SalesValidationStatus.CLARIFICATION_NEEDED, result.status)
        assertNotNull(result.prompt)
        assertTrue(result.prompt!!.contains("పరిమాణం స్పష్టంగా లేదు"))
    }

    @Test
    fun arithmeticValidator_resolveClarification_updatesItemAndReconciles() {
        val ambiguousItem = SalesItem(
            originalTerm = "టమాటా",
            quantity = 0.0,
            unit = "కేజీ (kg)",
            unitPrice = 0.0,
            totalPrice = 150.0
        )

        // Shopkeeper clarifies: quantity was 3.0 kg
        val resolved = validator.resolveClarification(
            item = ambiguousItem,
            resolvedQuantity = 3.0,
            resolvedUnitPrice = null,
            resolvedTotal = 150.0
        )

        assertEquals(SalesValidationStatus.VERIFIED, resolved.validationStatus)
        assertEquals(3.0, resolved.quantity, 0.01)
        assertEquals(50.0, resolved.unitPrice, 0.01) // 150 / 3 = 50
        assertEquals(150.0, resolved.totalPrice, 0.01)
    }

    // ==========================================
    // 3. DUPLICATE DETECTION & MERGING
    // ==========================================

    @Test
    fun duplicateEngine_detectsDuplicateItemOnSameDate() {
        val existingItem = SalesItem(
            id = "101",
            date = "2026-09-13",
            originalTerm = "టమాటా",
            standardName = "Tomato",
            quantity = 5.0,
            unit = "కేజీ (kg)",
            totalPrice = 200.0
        )
        val duplicateCandidate = SalesItem(
            id = "102",
            date = "2026-09-13",
            originalTerm = "టమాటాలు",
            standardName = "Tomato",
            quantity = 2.0,
            unit = "కేజీ (kg)",
            totalPrice = 80.0
        )

        val check = DuplicatePreventionEngine.checkDuplicate(duplicateCandidate, listOf(existingItem))
        assertTrue(check.isDuplicate)
        assertNotNull(check.messageInTelugu)
    }

    @Test
    fun duplicateEngine_mergeItems_sumsQuantitiesAndRecalculatesTotal() {
        val existingItem = SalesItem(
            id = "101",
            date = "2026-09-13",
            originalTerm = "టమాటా",
            standardName = "Tomato",
            quantity = 5.0,
            unit = "కేజీ (kg)",
            unitPrice = 40.0,
            totalPrice = 200.0,
            notes = "ఉదయం అమ్మకం"
        )
        val duplicateItem = SalesItem(
            id = "102",
            date = "2026-09-13",
            originalTerm = "టమాటా",
            standardName = "Tomato",
            quantity = 3.0,
            unit = "కేజీ (kg)",
            unitPrice = 40.0,
            totalPrice = 120.0,
            notes = "సాయంత్రం అమ్మకం"
        )

        val merged = DuplicatePreventionEngine.mergeItems(existingItem, duplicateItem)

        assertEquals(8.0, merged.quantity, 0.01)
        assertEquals(40.0, merged.unitPrice, 0.01)
        assertEquals(320.0, merged.totalPrice, 0.01) // 8 * 40
        assertTrue(merged.notes!!.contains("ఉదయం అమ్మకం"))
        assertTrue(merged.notes!!.contains("సాయంత్రం అమ్మకం"))
        assertEquals(SalesValidationStatus.VERIFIED, merged.validationStatus)
    }

    // ==========================================
    // 4. CSV & XLSX EXPORT COMPLIANCE
    // ==========================================

    @Test
    fun salesExporter_csvWithUtf8Bom_containsTeluguAndHeaders() {
        val tempFile = File.createTempFile("sales_test", ".csv")
        tempFile.deleteOnExit()

        val log = SalesLog(
            rawSpokenText = "5 కేజీల టమాటా 200 రూపాయలు",
            detectedLanguage = Language.TELUGU,
            items = listOf(
                SalesItem(
                    date = "2026-09-13",
                    originalTerm = "టమాటా",
                    quantity = 5.0,
                    unit = "కేజీ (kg)",
                    unitPrice = 40.0,
                    totalPrice = 200.0,
                    notes = "నగదు",
                    validationStatus = SalesValidationStatus.VERIFIED
                )
            ),
            grandTotal = 200.0
        )

        val result = exporter.exportToCsv(log, tempFile)
        assertTrue(result is VernAiResult.Success)

        val bytes = tempFile.readBytes()
        // Check for UTF-8 Byte Order Mark (0xEF, 0xBB, 0xBF)
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])

        val content = tempFile.readText()
        assertTrue(content.contains("తేదీ (Date)"))
        assertTrue(content.contains("టమాటా"))
        assertTrue(content.contains("200.00"))
    }

    @Test
    fun salesExporter_xlsxXmlSpreadsheet_containsFormulasAndStyles() {
        val tempFile = File.createTempFile("sales_test", ".xlsx")
        tempFile.deleteOnExit()

        val log = SalesLog(
            rawSpokenText = "5 కేజీల టమాటా 200 రూపాయలు",
            detectedLanguage = Language.TELUGU,
            items = listOf(
                SalesItem(
                    date = "2026-09-13",
                    originalTerm = "టమాటా",
                    quantity = 5.0,
                    unit = "కేజీ (kg)",
                    unitPrice = 40.0,
                    totalPrice = 200.0,
                    notes = "నగదు",
                    validationStatus = SalesValidationStatus.VERIFIED
                )
            ),
            grandTotal = 200.0
        )

        val result = exporter.exportToXlsx(log, tempFile)
        assertTrue(result is VernAiResult.Success)

        val xml = tempFile.readText()
        assertTrue(xml.contains("urn:schemas-microsoft-com:office:spreadsheet"))
        assertTrue(xml.contains("ss:Formula=\"=RC[-3]*RC[-1]\"")) // Quantity * Unit Price formula
        assertTrue(xml.contains("ss:Formula=\"=SUM(R2C:R[-1]C)\"")) // Auto-sum formula
        assertTrue(xml.contains("టమాటా"))
    }

    // ==========================================
    // 5. USE CASE WORKFLOW INTEGRATION
    // ==========================================

    @Test
    fun extractSalesLogUseCase_deterministicPath_persistsAndReturnsValidLog() = runTest {
        var savedLog: SalesLog? = null
        val fakeRepo = object : SalesLogRepository {
            override fun getSalesLogsStream() = flowOf(emptyList<SalesLog>())
            override suspend fun getSalesLogById(id: String) = null
            override suspend fun saveSalesLog(salesLog: SalesLog): VernAiResult<Unit> {
                savedLog = salesLog
                return VernAiResult.Success(Unit)
            }
            override suspend fun deleteSalesLog(id: String) = VernAiResult.Success(Unit)
        }

        val useCase = ExtractSalesLogUseCase(
            llmEngine = MockLlmInferenceEngine(),
            parser = SalesLogParser(),
            teluguParser = parser,
            repository = fakeRepo,
            inferenceLock = InferenceLock(),
            dispatchers = testDispatchers
        )

        val result = useCase("ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు, 2 నూనె ప్యాకెట్లు 260 రూపాయలు నగదు")
        assertTrue(result is VernAiResult.Success)

        val log = (result as VernAiResult.Success).data
        assertEquals(2, log.items.size)
        assertEquals(460.0, log.grandTotal, 0.01)
        assertNotNull(savedLog)
        assertEquals(460.0, savedLog!!.grandTotal, 0.01)
    }
}
