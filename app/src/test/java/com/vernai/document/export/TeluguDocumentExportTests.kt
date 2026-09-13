package com.vernai.document.export

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.Language
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import com.vernai.core.model.SalesValidationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class TeluguDocumentExportTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val docxExporter = DocxDocumentExporter()
    private val pdfExporter = PdfDocumentExporter()
    private val xlsxExporter = XlsxDocumentExporter()
    private val salesLedgerExporter = SalesLedgerExporter()
    private val localExporter = LocalDocumentExporter(
        docxExporter = docxExporter,
        pdfExporter = pdfExporter,
        xlsxExporter = xlsxExporter,
        salesLedgerExporter = salesLedgerExporter
    )

    private val sampleDraft = ComplaintDraft(
        id = "letter-101",
        subject = "విషయము: గ్రామ పరిధిలో వీధి దీపాలు మరియు త్రాగునీటి సమస్య పరిష్కారం కొరకు వినతి.",
        department = "గ్రామ పంచాయతీ కార్యాలయం",
        recipientDesignation = "సర్పంచ్ / పంచాయతీ కార్యదర్శి గారు",
        vernacularBody = """
            గౌరవనీయులైన అధికారులకు నమస్కరించి వ్రాయునది ఏమనగా,
            మా గ్రామం శాంతినగర్‌లో గత 10 రోజులుగా ప్రధాన వీధి దీపాలు వెలగడం లేదు. దీని వలన రాత్రి వేళల్లో మహిళలు, వృద్ధులు మరియు చిన్నపిల్లలు రాకపోకలు సాగించడానికి తీవ్ర భయాందోళనలు చెందుతున్నారు.
            
            అలాగే, మెయిన్ పైప్‌లైన్ లీకేజీ కారణంగా గత 4 రోజులుగా త్రాగునీటి సరఫరా నిలిచిపోయింది. సుమారు 150 కుటుంబాలు తాగునీటి కొరకు తీవ్ర ఇబ్బందులు ఎదుర్కొంటున్నారు.
            
            కావున దయచేసి తక్షణమే స్పందించి వీధి దీపాలను మరమ్మత్తు చేయించి, తాగునీటి సరఫరాను పునరుద్ధరించవలసిందిగా కోరుచున్నాము.
        """.trimIndent(),
        englishTranslation = "Formal complaint letter regarding street lights and drinking water pipeline disruption in Shanthinagar village.",
        targetLanguage = Language.TELUGU,
        senderName = "శాంతినగర్ కాలనీ గ్రామస్తులు",
        location = "శాంతినగర్, ఖమ్మం జిల్లా",
        timestamp = 1757760000000L
    )

    private val sampleSalesLog = SalesLog(
        id = "sales-201",
        rawSpokenText = "టమాటా 5 కేజీలు 200, నూనె 2 ప్యాకెట్లు 260, ఉల్లిపాయలు 3 కేజీలు 150",
        detectedLanguage = Language.TELUGU,
        items = listOf(
            SalesItem(
                id = "item-1",
                originalTerm = "టమాటా",
                quantity = 5.0,
                unit = "కేజీ",
                unitPrice = 40.0,
                totalPrice = 200.0,
                date = "2026-09-13",
                notes = "నగదు",
                validationStatus = SalesValidationStatus.VERIFIED
            ),
            SalesItem(
                id = "item-2",
                originalTerm = "వంట నూనె",
                quantity = 2.0,
                unit = "ప్యాకెట్",
                unitPrice = 130.0,
                totalPrice = 260.0,
                date = "2026-09-13",
                notes = "ఫోన్‌పే",
                validationStatus = SalesValidationStatus.VERIFIED
            ),
            SalesItem(
                id = "item-3",
                originalTerm = "ఉల్లిపాయలు",
                quantity = 3.0,
                unit = "కేజీ",
                unitPrice = 50.0,
                totalPrice = 150.0,
                date = "2026-09-13",
                notes = "అరువు",
                validationStatus = SalesValidationStatus.VERIFIED
            )
        ),
        grandTotal = 610.0,
        timestamp = 1757760000000L
    )

    // =========================================================================
    // 1. FORMAL LETTER TESTS (DOCX & PDF)
    // =========================================================================

    @Test
    fun testExportFormalLetterToDocx() {
        val dest = tempFolder.newFile("FormalLetter.docx")
        val result = docxExporter.exportComplaintLetter(sampleDraft, dest)

        assertTrue("Export must succeed", result is VernAiResult.Success)
        assertTrue("Destination file must exist", dest.exists())
        assertTrue("File size must be positive", dest.length() > 500)

        // Validate via DocumentFileValidator
        val validation = DocumentFileValidator.validateDocx(dest)
        assertTrue("DOCX file structure must be valid: ${validation.errors}", validation.isValid)
        assertTrue("DOCX must preserve Telugu Unicode characters", validation.hasTeluguUnicode)

        // Inspect ZIP structure and document.xml content
        val entries = mutableListOf<String>()
        var docXml = ""
        ZipInputStream(dest.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                if (entry.name == "word/document.xml") {
                    docXml = String(zis.readBytes(), StandardCharsets.UTF_8)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue("Must contain [Content_Types].xml", entries.contains("[Content_Types].xml"))
        assertTrue("Must contain word/document.xml", entries.contains("word/document.xml"))
        assertTrue("Must contain word/styles.xml", entries.contains("word/styles.xml"))

        // Verify Telugu contents preserved inside XML runs
        assertTrue("Must contain Telugu subject", docXml.contains("వీధి దీపాలు"))
        assertTrue("Must contain Telugu recipient", docXml.contains("సర్పంచ్"))
        assertTrue("Must contain Telugu closing", docXml.contains("ఇట్లు"))
        assertTrue("Must specify Nirmala UI font for Indic ligatures", docXml.contains("Nirmala UI"))
    }

    @Test
    fun testExportFormalLetterToPdf() {
        val dest = tempFolder.newFile("FormalLetter.pdf")
        val result = pdfExporter.exportComplaintLetter(sampleDraft, dest)

        assertTrue("PDF export must succeed", result is VernAiResult.Success)
        assertTrue("Destination file must exist", dest.exists())
        assertTrue("File size must be positive", dest.length() > 200)

        val validation = DocumentFileValidator.validatePdf(dest)
        assertTrue("PDF structure must be valid: ${validation.errors}", validation.isValid)

        val bytes = dest.readBytes()
        val header = String(bytes.take(8).toByteArray(), StandardCharsets.ISO_8859_1)
        assertTrue("Must start with %PDF-", header.startsWith("%PDF-"))

        val tail = String(bytes.takeLast(64).toByteArray(), StandardCharsets.ISO_8859_1)
        assertTrue("Must end with %%EOF", tail.contains("%%EOF"))
    }

    // =========================================================================
    // 2. SALES LOG TESTS (CSV, XLSX, PDF)
    // =========================================================================

    @Test
    fun testExportSalesLogToCsv_withUtf8Bom() {
        val dest = tempFolder.newFile("SalesLog.csv")
        val result = salesLedgerExporter.exportToCsv(sampleSalesLog, dest)

        assertTrue("CSV export must succeed", result is VernAiResult.Success)
        assertTrue("File must exist", dest.exists())

        val validation = DocumentFileValidator.validateCsv(dest)
        assertTrue("CSV validation must pass: ${validation.errors}", validation.isValid)
        assertTrue("CSV must preserve Telugu Unicode", validation.hasTeluguUnicode)

        // Verify UTF-8 BOM
        val bytes = dest.readBytes()
        assertEquals("Byte 0 must be 0xEF", 0xEF.toByte(), bytes[0])
        assertEquals("Byte 1 must be 0xBB", 0xBB.toByte(), bytes[1])
        assertEquals("Byte 2 must be 0xBF", 0xBF.toByte(), bytes[2])

        val content = String(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)
        assertTrue("CSV must contain Telugu item 'టమాటా'", content.contains("టమాటా"))
        assertTrue("CSV must contain Telugu item 'వంట నూనె'", content.contains("వంట నూనె"))
        assertTrue("CSV must contain grand total 610.00", content.contains("610.00"))
    }

    @Test
    fun testExportSalesLogToXlsx_openXmlZip() {
        val dest = tempFolder.newFile("SalesLog.xlsx")
        val result = xlsxExporter.exportSalesLog(sampleSalesLog, dest)

        assertTrue("XLSX export must succeed", result is VernAiResult.Success)
        assertTrue("File must exist", dest.exists())
        assertTrue("File size must be non-trivial", dest.length() > 500)

        val validation = DocumentFileValidator.validateXlsx(dest)
        assertTrue("XLSX validation must pass: ${validation.errors}", validation.isValid)
        assertTrue("XLSX must preserve Telugu Unicode", validation.hasTeluguUnicode)

        // Check OpenXML ZIP entries
        val entries = mutableListOf<String>()
        var sheetXml = ""
        ZipInputStream(dest.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    sheetXml = String(zis.readBytes(), StandardCharsets.UTF_8)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue("Must contain xl/workbook.xml", entries.contains("xl/workbook.xml"))
        assertTrue("Must contain xl/worksheets/sheet1.xml", entries.contains("xl/worksheets/sheet1.xml"))
        assertTrue("Must contain xl/styles.xml", entries.contains("xl/styles.xml"))

        // Verify formulas and Telugu data in worksheet
        assertTrue("Sheet must contain calculation formula C2*E2", sheetXml.contains("<f>C2*E2</f>"))
        assertTrue("Sheet must contain SUM formula", sheetXml.contains("SUM(F2:F"))
        assertTrue("Sheet must contain Telugu item 'టమాటా'", sheetXml.contains("టమాటా"))
    }

    @Test
    fun testExportSalesLogToPdf() {
        val dest = tempFolder.newFile("SalesLog.pdf")
        val result = pdfExporter.exportSalesLog(sampleSalesLog, dest)

        assertTrue("PDF export must succeed", result is VernAiResult.Success)
        assertTrue("File must exist", dest.exists())

        val validation = DocumentFileValidator.validatePdf(dest)
        assertTrue("PDF validation must pass: ${validation.errors}", validation.isValid)
    }

    // =========================================================================
    // 3. TELUGU COMPLEX LIGATURES & SPECIAL CHARACTERS
    // =========================================================================

    @Test
    fun testTeluguComplexLigaturesAndConjuncts() {
        val complexTeluguDraft = ComplaintDraft(
            subject = "విషయము: శాస్త్రోక్తమైన ప్రార్థనలు & ప్రాథమికోన్నత పాఠశాల పునరుద్ధరణ కొరకు విన్నపం.",
            recipientDesignation = "జిల్లా విద్యాశాఖాధికారి (DEO) గారికి",
            department = "పాఠశాల విద్యాశాఖ, శ్రీకాకుళం",
            vernacularBody = """
                గౌరవనీయులైన అయ్యవార్లు,
                సంపూర్ణమైన అక్షరాస్యత సాధన కొరకు మా గ్రామంలోని 'శ్రీ ప్రకాశం' ప్రాథమికోన్నత పాఠశాలలో 
                అవసరమైన సౌకర్యాలు (విద్యుద్దీపాలు, గ్రంథాలయం, వ్యాయామశాల) ఏర్పాటు చేయవలసిందిగా ప్రార్థన.
                విశేషణాలు: క్క, ష్ట, త్ర, క్ష, ర్ణ, జ్ఞ, శ్ర, స్త్ర, ఒ, ఔ, ౧, ౨, ౩.
                ప్రాచీన చిహ్నాలు: శ్రీరామరక్ష । శుభమస్తు ॥
            """.trimIndent(),
            englishTranslation = "Formal representation regarding school modernization.",
            targetLanguage = Language.TELUGU,
            senderName = "గ్రామ విద్యా కమిటీ సభ్యులు & పౌరులు",
            location = "శ్రీకాకుళం",
            timestamp = 1757760000000L
        )

        val docxDest = tempFolder.newFile("ComplexTelugu.docx")
        val docxResult = docxExporter.exportComplaintLetter(complexTeluguDraft, docxDest)
        assertTrue("Complex Telugu DOCX export must succeed", docxResult is VernAiResult.Success)

        val validation = DocumentFileValidator.validateDocx(docxDest)
        assertTrue("DOCX validation must pass: ${validation.errors}", validation.isValid)
        assertTrue("Telugu Unicode must be detected", validation.hasTeluguUnicode)
        assertFalse("Must not contain Unicode replacement character", validation.errors.any { it.contains("\\uFFFD") })
    }

    @Test
    fun testSpecialCharactersAndXmlEscaping() {
        val specialDraft = ComplaintDraft(
            subject = "Subject with & < > \" ' special chars & Telugu: బియ్యం & పప్పుల ధరలు",
            department = "Department <Civil Supplies & Ration>",
            recipientDesignation = "Officer \"In-Charge\"",
            vernacularBody = "Body with <brackets>, & ampersands, \"quotes\", 'apostrophes', and Rupee symbol ₹ 1,500/-.",
            englishTranslation = "English translation with & and < >.",
            targetLanguage = Language.TELUGU,
            senderName = "Mr. & Mrs. Rao <Citizen>"
        )

        val docxDest = tempFolder.newFile("SpecialChars.docx")
        val result = docxExporter.exportComplaintLetter(specialDraft, docxDest)
        assertTrue("DOCX with special chars must succeed", result is VernAiResult.Success)

        val validation = DocumentFileValidator.validateDocx(docxDest)
        assertTrue("DOCX must be valid XML with escaped special characters: ${validation.errors}", validation.isValid)
    }

    // =========================================================================
    // 4. EMPTY DATA & LONG DOCUMENTS TESTS
    // =========================================================================

    @Test
    fun testEmptyDataHandling_doesNotCrash() {
        val emptyDraft = ComplaintDraft(
            subject = "",
            department = "",
            recipientDesignation = "",
            vernacularBody = "",
            englishTranslation = "",
            targetLanguage = Language.TELUGU,
            senderName = null,
            location = null
        )

        val docxDest = tempFolder.newFile("EmptyDraft.docx")
        val docxResult = docxExporter.exportComplaintLetter(emptyDraft, docxDest)
        assertTrue("Empty draft DOCX export must succeed gracefully", docxResult is VernAiResult.Success)
        assertTrue(DocumentFileValidator.validateDocx(docxDest).isValid)

        val pdfDest = tempFolder.newFile("EmptyDraft.pdf")
        val pdfResult = pdfExporter.exportComplaintLetter(emptyDraft, pdfDest)
        assertTrue("Empty draft PDF export must succeed gracefully", pdfResult is VernAiResult.Success)
        assertTrue(DocumentFileValidator.validatePdf(pdfDest).isValid)

        val emptySalesLog = SalesLog(
            rawSpokenText = "",
            detectedLanguage = Language.TELUGU,
            items = emptyList(),
            grandTotal = 0.0
        )

        val xlsxDest = tempFolder.newFile("EmptySales.xlsx")
        val xlsxResult = xlsxExporter.exportSalesLog(emptySalesLog, xlsxDest)
        assertTrue("Empty sales log XLSX export must succeed", xlsxResult is VernAiResult.Success)
        assertTrue(DocumentFileValidator.validateXlsx(xlsxDest).isValid)

        val csvDest = tempFolder.newFile("EmptySales.csv")
        val csvResult = salesLedgerExporter.exportToCsv(emptySalesLog, csvDest)
        assertTrue("Empty sales log CSV export must succeed", csvResult is VernAiResult.Success)
        assertTrue(DocumentFileValidator.validateCsv(csvDest).isValid)
    }

    @Test
    fun testLongDocumentHandling_multiPage() {
        val longBody = (1..20).joinToString("\n\n") { i ->
            "పరిచ్ఛేదం $i: శాంతినగర్ గ్రామ పరిధిలో మౌలిక సదుపాయాల కొరత వలన గ్రామీణ ప్రజలు, ముఖ్యంగా రైతాంగం మరియు విద్యార్థులు ఎదుర్కొంటున్న సమస్యల సమగ్ర వివరాలు మరియు ప్రభుత్వ సంక్షేమ పథకాల అమలు కొరకు నివేదిక."
        }

        val longDraft = sampleDraft.copy(vernacularBody = longBody)

        val docxDest = tempFolder.newFile("LongDocument.docx")
        val docxResult = docxExporter.exportComplaintLetter(longDraft, docxDest)
        assertTrue("Long document DOCX export must succeed", docxResult is VernAiResult.Success)
        assertTrue(DocumentFileValidator.validateDocx(docxDest).isValid)

        val pdfDest = tempFolder.newFile("LongDocument.pdf")
        val pdfResult = pdfExporter.exportComplaintLetter(longDraft, pdfDest)
        assertTrue("Long document PDF export must succeed", pdfResult is VernAiResult.Success)
        assertTrue(DocumentFileValidator.validatePdf(pdfDest).isValid)

        // Long sales log with 60 items spanning multiple pages
        val manyItems = (1..60).map { idx ->
            SalesItem(
                id = "item-$idx",
                originalTerm = "వస్తువు సంఖ్య $idx",
                quantity = (idx % 10 + 1).toDouble(),
                unit = if (idx % 2 == 0) "కేజీ" else "ప్యాకెట్",
                unitPrice = 25.0 * (idx % 5 + 1),
                totalPrice = (idx % 10 + 1) * 25.0 * (idx % 5 + 1),
                date = "2026-09-13",
                notes = if (idx % 3 == 0) "నగదు" else "ఫోన్‌పే",
                validationStatus = SalesValidationStatus.VERIFIED
            )
        }
        val longSalesLog = SalesLog(
            rawSpokenText = "బల్క్ అమ్మకాలు",
            detectedLanguage = Language.TELUGU,
            items = manyItems,
            grandTotal = manyItems.sumOf { it.totalPrice }
        )

        val longXlsxDest = tempFolder.newFile("LongSales.xlsx")
        val xlsxResult = xlsxExporter.exportSalesLog(longSalesLog, longXlsxDest)
        assertTrue("Long sales log XLSX export must succeed", xlsxResult is VernAiResult.Success)
        assertTrue(DocumentFileValidator.validateXlsx(longXlsxDest).isValid)
    }

    // =========================================================================
    // 5. EXPLANATION REPORT TESTS
    // =========================================================================

    @Test
    fun testExportExplanationReport() {
        val report = ExplanationReport(
            id = "exp-301",
            sourceDocumentName = "PattadarPassbookNotice.pdf",
            extractedCharacterCount = 280,
            summaryInVernacular = "ఈ పత్రం రెవెన్యూ శాఖ నుండి వచ్చిన భూ యాజమాన్య హక్కుల నోటీసు. ఇందులో సర్వే నంబర్ 142/A కి సంబంధించిన వివరాలు ఉన్నాయి.",
            keyActionPoints = listOf(
                "30 రోజుల్లోపు తహశీల్దార్ కార్యాలయంలో రిపోర్ట్ చేయవలెను",
                "ఆధార్ కార్డు మరియు పహాణీ నకలు జతపరచవలెను",
                "సేవా రుసుము రూ. 150/- చెల్లించవలెను"
            ),
            legalDeadlines = listOf("30 రోజులు"),
            targetLanguage = Language.TELUGU
        )

        val docxDest = tempFolder.newFile("Report.docx")
        val docxResult = docxExporter.exportExplanationReport(report, docxDest)
        assertTrue("Report DOCX export must succeed", docxResult is VernAiResult.Success)
        assertTrue(DocumentFileValidator.validateDocx(docxDest).isValid)

        val pdfDest = tempFolder.newFile("Report.pdf")
        val pdfResult = pdfExporter.exportExplanationReport(report, pdfDest)
        assertTrue("Report PDF export must succeed", pdfResult is VernAiResult.Success)
        assertTrue(DocumentFileValidator.validatePdf(pdfDest).isValid)
    }
}
