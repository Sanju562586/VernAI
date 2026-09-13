package com.vernai.document.export

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.Language
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import com.vernai.core.model.SalesValidationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

class DocumentExtractionAndExportTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val exporter = LocalDocumentExporter()

    @Test
    fun export_highVolumeSalesLedger_exportsCleanlyToCsvXlsxAndPdf() = runTest {
        val count = 150
        val items = (1..count).map { i ->
            SalesItem(
                id = "item-$i",
                date = "2026-09-13",
                originalTerm = "వస్తువు నంబర్ $i (Item $i)",
                standardName = "వస్తువు $i",
                quantity = (i % 10 + 1).toDouble(),
                unit = if (i % 2 == 0) "కేజీలు" else "ప్యాకెట్లు",
                unitPrice = (i * 5.0),
                totalPrice = (i % 10 + 1) * (i * 5.0),
                notes = if (i % 5 == 0) "నగదు చెల్లింపు" else null,
                validationStatus = SalesValidationStatus.VERIFIED
            )
        }

        val largeSalesLog = SalesLog(
            id = "large-log-1",
            rawSpokenText = "భారీ అమ్మకాల లెడ్జర్ రికార్డులు ($count వస్తువులు)",
            detectedLanguage = Language.TELUGU,
            items = items,
            grandTotal = items.sumOf { it.totalPrice },
            timestamp = System.currentTimeMillis()
        )

        // 1. CSV Export
        val csvFile = File(tempFolder.root, "Large_Sales_Ledger.csv")
        val csvResult = exporter.exportSalesLog(largeSalesLog, csvFile, ExportConfig(ExportFormat.CSV, Language.TELUGU))
        assertTrue("Large CSV export must succeed", csvResult is VernAiResult.Success)
        assertTrue(csvFile.exists() && csvFile.length() > 0)
        val csvLines = csvFile.readLines()
        assertTrue("CSV must have header + $count rows + grand total", csvLines.size >= count + 1)

        // 2. XLSX Export
        val xlsxFile = File(tempFolder.root, "Large_Sales_Ledger.xlsx")
        val xlsxResult = exporter.exportSalesLog(largeSalesLog, xlsxFile, ExportConfig(ExportFormat.XLSX, Language.TELUGU))
        assertTrue("Large XLSX export must succeed", xlsxResult is VernAiResult.Success)
        assertTrue(xlsxFile.exists() && xlsxFile.length() > 0)
        ZipFile(xlsxFile).use { zip ->
            val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml")
            assertNotNull("XLSX must contain xl/worksheets/sheet1.xml", sheetEntry)
            val sheetContent = zip.getInputStream(sheetEntry).bufferedReader().readText()
            assertTrue("XLSX must contain SUM formula", sheetContent.contains("SUM("))
        }

        // 3. PDF Export
        val pdfFile = File(tempFolder.root, "Large_Sales_Ledger.pdf")
        val pdfResult = exporter.exportSalesLog(largeSalesLog, pdfFile, ExportConfig(ExportFormat.PDF, Language.TELUGU))
        assertTrue("Large PDF export must succeed", pdfResult is VernAiResult.Success)
        assertTrue(pdfFile.exists() && pdfFile.length() > 0)
        val pdfContent = pdfFile.readText()
        assertTrue("PDF must contain PDF header", pdfContent.contains("%PDF-1.4"))
    }

    @Test
    fun export_handlesSpecialCharactersAndXmlEntities() = runTest {
        val specialDraft = ComplaintDraft(
            id = "special-1",
            subject = "విషయము: భూ సర్వే & హద్దుల వివాదం <అత్యవసరం> \"కోర్టు ఆదేశాలు\"",
            department = "రెవెన్యూ & సర్వే శాఖ",
            recipientDesignation = "తహశీల్దార్ & ఎగ్జిక్యూటివ్ మేజిస్ట్రేట్",
            vernacularBody = """
                ఆర్యా,
                నా సర్వే నంబర్ 45/B లో హద్దు రాళ్ళు 'తీసివేశారు'.
                మరియు వర్షపు నీరు <గ్రామంలోనికి> ప్రవహిస్తోంది & రోడ్లు తెగిపోయాయి.
                నష్టం: ₹50,000/- & మరిన్ని వివరాలు.
            """.trimIndent(),
            englishTranslation = "Dispute regarding land survey & boundary stones <Urgent>.",
            targetLanguage = Language.TELUGU
        )

        // DOCX Export with XML entity escaping inside ZIP package
        val docxFile = File(tempFolder.root, "Special_Chars_Letter.docx")
        val docxResult = exporter.exportComplaintLetter(specialDraft, docxFile, ExportConfig(ExportFormat.DOCX, Language.TELUGU))
        assertTrue("DOCX with special characters must succeed", docxResult is VernAiResult.Success)
        assertTrue(docxFile.exists() && docxFile.length() > 0)

        ZipFile(docxFile).use { zip ->
            val documentXmlEntry = zip.getEntry("word/document.xml")
            assertNotNull("DOCX must contain word/document.xml", documentXmlEntry)
            val docxXmlContent = zip.getInputStream(documentXmlEntry).bufferedReader().readText()
            assertTrue("XML special characters must be escaped as &amp;", docxXmlContent.contains("&amp;"))
            assertTrue("XML special characters must be escaped as &lt;", docxXmlContent.contains("&lt;"))
            assertTrue("XML special characters must be escaped as &gt;", docxXmlContent.contains("&gt;"))
        }

        // PDF Export
        val pdfFile = File(tempFolder.root, "Special_Chars_Letter.pdf")
        val pdfResult = exporter.exportComplaintLetter(specialDraft, pdfFile, ExportConfig(ExportFormat.PDF, Language.TELUGU))
        assertTrue("PDF with special characters must succeed", pdfResult is VernAiResult.Success)
        assertTrue(pdfFile.exists() && pdfFile.length() > 0)
    }

    @Test
    fun export_handlesEmptyDataSetsGracefully() = runTest {
        // Empty sales log
        val emptyLog = SalesLog(
            id = "empty-log",
            rawSpokenText = "",
            detectedLanguage = Language.TELUGU,
            items = emptyList(),
            grandTotal = 0.0,
            timestamp = System.currentTimeMillis()
        )

        val csvFile = File(tempFolder.root, "Empty_Sales.csv")
        val csvResult = exporter.exportSalesLog(emptyLog, csvFile, ExportConfig(ExportFormat.CSV, Language.TELUGU))
        assertTrue("Empty sales log CSV export should succeed gracefully", csvResult is VernAiResult.Success)

        val xlsxFile = File(tempFolder.root, "Empty_Sales.xlsx")
        val xlsxResult = exporter.exportSalesLog(emptyLog, xlsxFile, ExportConfig(ExportFormat.XLSX, Language.TELUGU))
        assertTrue("Empty sales log XLSX export should succeed gracefully", xlsxResult is VernAiResult.Success)

        val pdfFile = File(tempFolder.root, "Empty_Sales.pdf")
        val pdfResult = exporter.exportSalesLog(emptyLog, pdfFile, ExportConfig(ExportFormat.PDF, Language.TELUGU))
        assertTrue("Empty sales log PDF export should succeed gracefully", pdfResult is VernAiResult.Success)
    }
}
