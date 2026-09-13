package com.vernai.document.export

import com.vernai.core.common.result.VernAiResult
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
 * Pure-Kotlin, zero-dependency OpenXML Spreadsheet (.xlsx) generator.
 * Produces standard zipped .xlsx files compatible with Microsoft Excel,
 * Google Sheets, LibreOffice Calc, and Android office suites.
 *
 * Fully preserves Telugu Unicode text using inlineStr representations and UTF-8 encoding.
 * Emits native cell styling, custom column widths, currency formats (₹), and dynamic calculation formulas.
 */
class XlsxDocumentExporter {

    /**
     * Exports [SalesLog] to a standard OpenXML (.xlsx) file.
     */
    fun exportSalesLog(salesLog: SalesLog, destinationFile: File): VernAiResult<File> {
        return runCatching {
            destinationFile.parentFile?.mkdirs()

            val sheetXml = buildWorksheetXml(salesLog)

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

                    // 3. xl/_rels/workbook.xml.rels
                    zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                    zos.write(WORKBOOK_RELS_XML.toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()

                    // 4. xl/workbook.xml
                    zos.putNextEntry(ZipEntry("xl/workbook.xml"))
                    zos.write(WORKBOOK_XML.toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()

                    // 5. xl/styles.xml
                    zos.putNextEntry(ZipEntry("xl/styles.xml"))
                    zos.write(STYLES_XML.toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()

                    // 6. xl/worksheets/sheet1.xml
                    zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                    zos.write(sheetXml.toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()
                }
            }

            VernAiResult.Success(destinationFile)
        }.getOrElse { e ->
            VernAiResult.Error(e, "XLSX లెడ్జర్ ఎగుమతి విఫలమైంది (Sales log XLSX export failed: ${e.localizedMessage})")
        }
    }

    private fun buildWorksheetXml(salesLog: SalesLog): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
           xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheetViews>
    <sheetView tabSelected="1" workbookViewId="0"/>
  </sheetViews>
  <sheetFormatPr defaultRowHeight="20"/>
  <cols>
    <col min="1" max="1" width="14" customWidth="1"/> <!-- Date -->
    <col min="2" max="2" width="26" customWidth="1"/> <!-- Item Name -->
    <col min="3" max="3" width="12" customWidth="1"/> <!-- Quantity -->
    <col min="4" max="4" width="12" customWidth="1"/> <!-- Unit -->
    <col min="5" max="5" width="15" customWidth="1"/> <!-- Unit Price -->
    <col min="6" max="6" width="18" customWidth="1"/> <!-- Total Price -->
    <col min="7" max="7" width="25" customWidth="1"/> <!-- Notes -->
  </cols>
  <sheetData>
""")

        // Row 1: Header
        sb.append("""    <row r="1" ht="28" customHeight="1">
      <c r="A1" s="1" t="inlineStr"><is><t>తేదీ (Date)</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>వస్తువు పేరు (Item)</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>పరిమాణం (Qty)</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>కొలత (Unit)</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>ధర (Unit Price)</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>మొత్తం (Total Price)</t></is></c>
      <c r="G1" s="1" t="inlineStr"><is><t>గమనికలు (Notes)</t></is></c>
    </row>
""")

        val items = salesLog.items
        var currentRow = 2

        if (items.isEmpty()) {
            sb.append("""    <row r="2">
      <c r="A2" t="inlineStr"><is><t>రికార్డులు లేవు (No records)</t></is></c>
    </row>
""")
            currentRow = 3
        } else {
            val defaultDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(salesLog.timestamp))
            for (item in items) {
                val date = escapeXml(item.date.ifBlank { defaultDate })
                val name = escapeXml(item.originalTerm)
                val unit = escapeXml(item.unit)
                val notes = escapeXml(item.notes ?: "")

                val qtyStr = String.format(Locale.US, "%.2f", item.quantity)
                val unitPriceStr = String.format(Locale.US, "%.2f", item.unitPrice)
                val totalPriceStr = String.format(Locale.US, "%.2f", item.totalPrice)

                sb.append("""    <row r="$currentRow" ht="22">
      <c r="A$currentRow" t="inlineStr"><is><t>$date</t></is></c>
      <c r="B$currentRow" t="inlineStr"><is><t>$name</t></is></c>
      <c r="C$currentRow" s="3"><v>$qtyStr</v></c>
      <c r="D$currentRow" t="inlineStr"><is><t>$unit</t></is></c>
      <c r="E$currentRow" s="2"><v>$unitPriceStr</v></c>
      <c r="F$currentRow" s="2"><f>C$currentRow*E$currentRow</f><v>$totalPriceStr</v></c>
      <c r="G$currentRow" t="inlineStr"><is><t>$notes</t></is></c>
    </row>
""")
                currentRow++
            }
        }

        // Summary Row: Grand Total
        val grandTotalStr = String.format(Locale.US, "%.2f", salesLog.grandTotal)
        val sumFormula = if (items.isNotEmpty()) "SUM(F2:F${currentRow - 1})" else "0"

        sb.append("""    <row r="$currentRow" ht="26" customHeight="1">
      <c r="A$currentRow" s="4" t="inlineStr"><is><t>మొత్తం ఆదాయం (Grand Total)</t></is></c>
      <c r="B$currentRow" s="4"/>
      <c r="C$currentRow" s="4"/>
      <c r="D$currentRow" s="4"/>
      <c r="E$currentRow" s="4"/>
      <c r="F$currentRow" s="4"><f>$sumFormula</f><v>$grandTotalStr</v></c>
      <c r="G$currentRow" s="4"/>
    </row>
""")

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    companion object {
        private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

        private const val PACKAGE_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="అమ్మకాల లెడ్జర్" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

        private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="&#x20B9;#,##0.00"/>
  </numFmts>
  <fonts count="3">
    <font><name val="Nirmala UI"/><sz val="11"/></font>
    <font><b/><color rgb="FFFFFFFF"/><name val="Nirmala UI"/><sz val="11"/></font>
    <font><b/><color rgb="FF0F172A"/><name val="Nirmala UI"/><sz val="12"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0284C7"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFEF3C7"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/></border>
    <border><left/><right/><top style="thin"/><bottom style="double"/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="5">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="164" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyNumberFormat="1"/>
  </cellXfs>
</styleSheet>"""
    }
}
