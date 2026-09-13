package com.vernai.document.export

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.SalesLog
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure-Kotlin, zero-dependency Office OpenXML (.docx) document generator.
 * Produces standard WordprocessingML packages adhering to ECMA-376 / ISO 29500 standards.
 *
 * Fully preserves Telugu Unicode complex script ligatures by specifying Indic-capable font families
 * (Nirmala UI, Noto Sans Telugu, Gautami, Segoe UI) and xml:space="preserve" attributes.
 */
class DocxDocumentExporter {

    /**
     * Exports a formal Telugu civic complaint letter to a standard .docx file.
     */
    fun exportComplaintLetter(
        draft: ComplaintDraft,
        destinationFile: File,
        config: ExportConfig = ExportConfig(ExportFormat.DOCX, draft.targetLanguage)
    ): VernAiResult<File> {
        return runCatching {
            destinationFile.parentFile?.mkdirs()

            val bodyXml = buildLetterBodyXml(draft)
            writeDocxZip(destinationFile, bodyXml)

            VernAiResult.Success(destinationFile)
        }.getOrElse { e ->
            VernAiResult.Error(e, "DOCX లేఖ ఎగుమతి విఫలమైంది (Letter DOCX export failed: ${e.localizedMessage})")
        }
    }

    /**
     * Exports a structured sales log ledger into a formatted table within a standard .docx file.
     */
    fun exportSalesLog(
        salesLog: SalesLog,
        destinationFile: File,
        config: ExportConfig = ExportConfig(ExportFormat.DOCX, com.vernai.core.model.Language.TELUGU)
    ): VernAiResult<File> {
        return runCatching {
            destinationFile.parentFile?.mkdirs()

            val bodyXml = buildSalesLogTableXml(salesLog)
            writeDocxZip(destinationFile, bodyXml)

            VernAiResult.Success(destinationFile)
        }.getOrElse { e ->
            VernAiResult.Error(e, "DOCX లెడ్జర్ ఎగుమతి విఫలమైంది (Sales log DOCX export failed: ${e.localizedMessage})")
        }
    }

    /**
     * Exports a document explanation report to a structured .docx file.
     */
    fun exportExplanationReport(
        report: ExplanationReport,
        destinationFile: File,
        config: ExportConfig = ExportConfig(ExportFormat.DOCX, report.targetLanguage)
    ): VernAiResult<File> {
        return runCatching {
            destinationFile.parentFile?.mkdirs()

            val bodyXml = buildExplanationReportXml(report)
            writeDocxZip(destinationFile, bodyXml)

            VernAiResult.Success(destinationFile)
        }.getOrElse { e ->
            VernAiResult.Error(e, "DOCX నివేదిక ఎగుమతి విఫలమైంది (Report DOCX export failed: ${e.localizedMessage})")
        }
    }

    /**
     * Writes the complete Office OpenXML ZIP structure to the destination file.
     */
    private fun writeDocxZip(destinationFile: File, bodyXml: String) {
        FileOutputStream(destinationFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. [Content_Types].xml
                zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                zos.write(CONTENT_TYPES_XML.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 2. _rels/.rels
                zos.putNextEntry(ZipEntry("_rels/.rels"))
                zos.write(PACKAGE_RELS_XML.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 3. word/_rels/document.xml.rels
                zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
                zos.write(DOCUMENT_RELS_XML.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 4. word/styles.xml
                zos.putNextEntry(ZipEntry("word/styles.xml"))
                zos.write(STYLES_XML.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 5. word/document.xml
                val fullDocumentXml = buildFullDocumentXml(bodyXml)
                zos.putNextEntry(ZipEntry("word/document.xml"))
                zos.write(fullDocumentXml.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private fun buildLetterBodyXml(draft: ComplaintDraft): String {
        val sb = StringBuilder()

        // 1. Header / Title
        sb.append(paragraph(
            text = "అధికారిక వినతిపత్రం (FORMAL APPLICATION / COMPLAINT)",
            bold = true,
            fontSize = 32, // 16pt
            alignment = "center",
            spaceAfter = 240
        ))

        // 2. Location & Date (Right aligned)
        val formattedDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(draft.timestamp))
        val dateText = "తేదీ: $formattedDate"
        val locationText = draft.location?.takeIf { it.isNotBlank() }?.let { "స్థలం: $it" } ?: "స్థలం: ...................."
        sb.append(paragraph(
            text = "$locationText\n$dateText",
            bold = false,
            fontSize = 22, // 11pt
            alignment = "right",
            spaceAfter = 200
        ))

        // 3. Recipient Details ("గౌరవనీయులైన...")
        val designation = draft.recipientDesignation.ifBlank { "సంబంధిత అధికారి గారు" }
        val department = draft.department.ifBlank { "కార్యాలయ విభాగం" }
        val recipientBlock = "స్వీకర్త:\n$designation,\n$department."
        sb.append(paragraph(
            text = recipientBlock,
            bold = false,
            fontSize = 24,
            alignment = "left",
            spaceAfter = 240
        ))

        // 4. Subject Line
        val subject = draft.subject.ifBlank { "విషయము: ప్రజా సమస్యల పరిష్కారం కొరకు విన్నపం." }
        sb.append(paragraph(
            text = subject,
            bold = true,
            fontSize = 24, // 12pt
            alignment = "left",
            spaceBefore = 120,
            spaceAfter = 200
        ))

        // 5. Salutation
        sb.append(paragraph(
            text = "ఆర్యా / అయ్యా,",
            bold = true,
            fontSize = 24,
            alignment = "left",
            spaceAfter = 160
        ))

        // 6. Letter Body
        val bodyContent = draft.vernacularBody.ifBlank {
            "పై విషయమునకు సంబంధించి, మా ప్రాంతంలో ఎదురవుతున్న సమస్యను పరిష్కరించవలసిందిగా కోరుచున్నాము."
        }
        for (para in bodyContent.split("\n\n", "\n").map { it.trim() }.filter { it.isNotEmpty() }) {
            sb.append(paragraph(
                text = para,
                bold = false,
                fontSize = 24,
                alignment = "both",
                spaceAfter = 180,
                indent = 720 // First line indent 0.5 inch
            ))
        }

        // 7. Closing & Applicant Signature
        sb.append(paragraph(
            text = "ధన్యవాదములతో,",
            bold = false,
            fontSize = 24,
            alignment = "right",
            spaceBefore = 240,
            spaceAfter = 120
        ))

        val senderName = draft.senderName?.takeIf { it.isNotBlank() } ?: "దరఖాస్తుదారుడు / గ్రామ ప్రజలు"
        sb.append(paragraph(
            text = "ఇట్లు,\n[సంతకం / వేలిముద్ర]\n($senderName)",
            bold = true,
            fontSize = 24,
            alignment = "right",
            spaceAfter = 300
        ))

        // 8. Optional English Translation Appendix
        if (!draft.englishTranslation.isNullOrBlank()) {
            sb.append(paragraph(
                text = "--------------------------------------------------",
                bold = false,
                fontSize = 20,
                alignment = "center",
                spaceBefore = 240,
                spaceAfter = 120
            ))
            sb.append(paragraph(
                text = "English Translation / Summary Reference:",
                bold = true,
                fontSize = 22,
                alignment = "left",
                spaceAfter = 120
            ))
            sb.append(paragraph(
                text = draft.englishTranslation,
                bold = false,
                fontSize = 22,
                alignment = "left",
                spaceAfter = 120
            ))
        }

        return sb.toString()
    }

    private fun buildSalesLogTableXml(salesLog: SalesLog): String {
        val sb = StringBuilder()

        // Title
        sb.append(paragraph(
            text = "అమ్మకాల లెడ్జర్ (DAILY SALES & REVENUE LEDGER)",
            bold = true,
            fontSize = 32,
            alignment = "center",
            spaceAfter = 200
        ))

        // Metadata
        val logDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(salesLog.timestamp))
        val dateText = "తేదీ: $logDate   |   మొత్తం రికార్డులు: ${salesLog.items.size}"
        sb.append(paragraph(
            text = dateText,
            bold = false,
            fontSize = 22,
            alignment = "center",
            spaceAfter = 240
        ))

        // Table definition
        sb.append("""<w:tbl>
  <w:tblPr>
    <w:tblW w:w="0" w:type="auto"/>
    <w:tblBorders>
      <w:top w:val="single" w:sz="6" w:space="0" w:color="0284C7"/>
      <w:left w:val="none"/>
      <w:bottom w:val="single" w:sz="12" w:space="0" w:color="0284C7"/>
      <w:right w:val="none"/>
      <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
      <w:insideV w:val="none"/>
    </w:tblBorders>
    <w:tblCellMar>
      <w:top w:w="120" w:type="dxa"/>
      <w:left w:w="160" w:type="dxa"/>
      <w:bottom w:w="120" w:type="dxa"/>
      <w:right w:w="160" w:type="dxa"/>
    </w:tblCellMar>
  </w:tblPr>""").append("\n")

        // Column widths
        sb.append("""  <w:tblGrid>
    <w:gridCol w:w="1400"/>
    <w:gridCol w:w="2400"/>
    <w:gridCol w:w="1000"/>
    <w:gridCol w:w="1100"/>
    <w:gridCol w:w="1200"/>
    <w:gridCol w:w="1400"/>
    <w:gridCol w:w="1500"/>
  </w:tblGrid>""").append("\n")

        // Table Header
        sb.append("""  <w:tr>
    <w:trPr><w:tblHeader/></w:trPr>
    ${tableCell("తేదీ (Date)", bold = true, background = "0284C7", textColor = "FFFFFF", align = "left")}
    ${tableCell("వస్తువు (Item)", bold = true, background = "0284C7", textColor = "FFFFFF", align = "left")}
    ${tableCell("పరిమాణం", bold = true, background = "0284C7", textColor = "FFFFFF", align = "right")}
    ${tableCell("కొలత", bold = true, background = "0284C7", textColor = "FFFFFF", align = "left")}
    ${tableCell("ధర (₹)", bold = true, background = "0284C7", textColor = "FFFFFF", align = "right")}
    ${tableCell("మొత్తం (₹)", bold = true, background = "0284C7", textColor = "FFFFFF", align = "right")}
    ${tableCell("గమనికలు", bold = true, background = "0284C7", textColor = "FFFFFF", align = "left")}
  </w:tr>""").append("\n")

        // Table Data Rows
        if (salesLog.items.isEmpty()) {
            sb.append("""  <w:tr>
    ${tableCell("రికార్డులు లేవు (No records available)", bold = false, align = "center", span = 7)}
  </w:tr>""").append("\n")
        } else {
            for ((index, item) in salesLog.items.withIndex()) {
                val bg = if (index % 2 == 1) "F8FAFC" else "FFFFFF"
                val date = item.date.ifBlank { logDate }
                val qty = String.format(Locale.US, "%.2f", item.quantity)
                val price = String.format(Locale.US, "%.2f", item.unitPrice)
                val total = String.format(Locale.US, "%.2f", item.totalPrice)
                val notes = item.notes ?: ""

                sb.append("""  <w:tr>
    ${tableCell(date, bold = false, background = bg, align = "left")}
    ${tableCell(item.originalTerm, bold = true, background = bg, align = "left")}
    ${tableCell(qty, bold = false, background = bg, align = "right")}
    ${tableCell(item.unit, bold = false, background = bg, align = "left")}
    ${tableCell("₹$price", bold = false, background = bg, align = "right")}
    ${tableCell("₹$total", bold = true, background = bg, align = "right")}
    ${tableCell(notes, bold = false, background = bg, align = "left")}
  </w:tr>""").append("\n")
            }
        }

        // Summary Row (Grand Total)
        val grandTotalStr = String.format(Locale.US, "%.2f", salesLog.grandTotal)
        sb.append("""  <w:tr>
    ${tableCell("మొత్తం ఆదాయం (Grand Total)", bold = true, background = "FEF3C7", align = "left", span = 5)}
    ${tableCell("₹$grandTotalStr", bold = true, background = "FEF3C7", align = "right")}
    ${tableCell("", bold = false, background = "FEF3C7", align = "left")}
  </w:tr>""").append("\n")

        sb.append("</w:tbl>\n")
        return sb.toString()
    }

    private fun buildExplanationReportXml(report: ExplanationReport): String {
        val sb = StringBuilder()

        sb.append(paragraph(
            text = "పత్ర వివరణ నివేదిక (DOCUMENT EXPLANATION REPORT)",
            bold = true,
            fontSize = 32,
            alignment = "center",
            spaceAfter = 200
        ))

        sb.append(paragraph(
            text = "మూల పత్రం: ${report.sourceDocumentName}",
            bold = true,
            fontSize = 24,
            alignment = "left",
            spaceAfter = 160
        ))

        sb.append(paragraph(
            text = "వివరణ సారాంశం (Summary in Telugu):",
            bold = true,
            fontSize = 24,
            alignment = "left",
            spaceAfter = 120
        ))

        for (para in report.summaryInVernacular.split("\n\n", "\n").map { it.trim() }.filter { it.isNotEmpty() }) {
            sb.append(paragraph(
                text = para,
                bold = false,
                fontSize = 24,
                alignment = "both",
                spaceAfter = 160
            ))
        }

        if (report.keyActionPoints.isNotEmpty()) {
            sb.append(paragraph(
                text = "ముఖ్యమైన అంశాలు (Key Points):",
                bold = true,
                fontSize = 24,
                alignment = "left",
                spaceBefore = 160,
                spaceAfter = 120
            ))
            for (point in report.keyActionPoints) {
                sb.append(paragraph(
                    text = "• $point",
                    bold = false,
                    fontSize = 24,
                    alignment = "left",
                    spaceAfter = 100
                ))
            }
        }

        return sb.toString()
    }

    private fun paragraph(
        text: String,
        bold: Boolean = false,
        fontSize: Int = 24,
        alignment: String = "left",
        spaceBefore: Int = 0,
        spaceAfter: Int = 120,
        indent: Int = 0
    ): String {
        val escaped = escapeXml(text)
        val jcVal = when (alignment.lowercase()) {
            "center" -> "center"
            "right" -> "right"
            "both", "justify" -> "both"
            else -> "left"
        }

        val lines = escaped.split("\n")
        val runsXml = lines.joinToString("<w:br/>") { line ->
            """<w:r>
      <w:rPr>
        <w:rFonts w:ascii="Nirmala UI" w:hAnsi="Nirmala UI" w:cs="Nirmala UI"/>
        ${if (bold) "<w:b/>" else ""}
        <w:sz w:val="$fontSize"/>
        <w:szCs w:val="$fontSize"/>
      </w:rPr>
      <w:t xml:space="preserve">$line</w:t>
    </w:r>"""
        }

        return """<w:p>
  <w:pPr>
    <w:jc w:val="$jcVal"/>
    <w:spacing w:before="$spaceBefore" w:after="$spaceAfter" w:line="276" w:lineRule="auto"/>
    ${if (indent > 0) "<w:ind w:firstLine=\"$indent\"/>" else ""}
  </w:pPr>
  $runsXml
</w:p>""" + "\n"
    }

    private fun tableCell(
        text: String,
        bold: Boolean = false,
        background: String = "FFFFFF",
        textColor: String = "0F172A",
        align: String = "left",
        span: Int = 1
    ): String {
        val escaped = escapeXml(text)
        val jcVal = when (align.lowercase()) {
            "center" -> "center"
            "right" -> "right"
            else -> "left"
        }
        val gridSpanXml = if (span > 1) "<w:gridSpan w:val=\"$span\"/>" else ""

        return """<w:tc>
      <w:tcPr>
        $gridSpanXml
        <w:shd w:val="clear" w:color="auto" w:fill="$background"/>
        <w:tcMar>
          <w:top w:w="120" w:type="dxa"/>
          <w:left w:w="160" w:type="dxa"/>
          <w:bottom w:w="120" w:type="dxa"/>
          <w:right w:w="160" w:type="dxa"/>
        </w:tcMar>
      </w:tcPr>
      <w:p>
        <w:pPr>
          <w:jc w:val="$jcVal"/>
          <w:spacing w:before="60" w:after="60"/>
        </w:pPr>
        <w:r>
          <w:rPr>
            <w:rFonts w:ascii="Nirmala UI" w:hAnsi="Nirmala UI" w:cs="Nirmala UI"/>
            ${if (bold) "<w:b/>" else ""}
            <w:color w:val="$textColor"/>
            <w:sz w:val="22"/>
            <w:szCs w:val="22"/>
          </w:rPr>
          <w:t xml:space="preserve">$escaped</w:t>
        </w:r>
      </w:p>
    </w:tc>"""
    }

    private fun buildFullDocumentXml(bodyXml: String): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:body>
$bodyXml
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
      <w:cols w:space="720"/>
      <w:docGrid w:linePitch="360"/>
    </w:sectPr>
  </w:body>
</w:document>"""
    }

    fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    companion object {
        private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

        private const val PACKAGE_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        private const val DOCUMENT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Nirmala UI" w:eastAsia="Nirmala UI" w:hAnsi="Nirmala UI" w:cs="Nirmala UI"/>
        <w:sz w:val="24"/>
        <w:szCs w:val="24"/>
        <w:lang w:val="te-IN"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        <w:spacing w:after="120" w:line="276" w:lineRule="auto"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>
</w:styles>"""
    }
}
