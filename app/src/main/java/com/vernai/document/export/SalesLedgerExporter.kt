package com.vernai.document.export

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust, standalone exporter for Sales Ledger data supporting:
 * 1. CSV with UTF-8 BOM for perfect Telugu text rendering in Excel/Sheets.
 * 2. Excel XML Spreadsheet (XLSX-compatible) with native styling, formulas, and auto-summation.
 */
class SalesLedgerExporter {

    /**
     * Exports [SalesLog] to a CSV file with UTF-8 Byte Order Mark (BOM).
     * The BOM (0xEF, 0xBB, 0xBF) guarantees that Microsoft Excel displays Indic Unicode characters properly.
     */
    fun exportToCsv(salesLog: SalesLog, destinationFile: File): VernAiResult<File> {
        return runCatching {
            destinationFile.parentFile?.mkdirs()

            FileOutputStream(destinationFile).use { fos ->
                // Write UTF-8 Byte Order Mark (BOM)
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    // Header Row
                    writer.write("తేదీ (Date),వస్తువు పేరు (Item),పరిమాణం (Quantity),కొలత (Unit),ధర (Unit Price),మొత్తం (Total Price),గమనికలు (Notes),పరిశీలన (Status)\n")

                    // Item Rows
                    for (item in salesLog.items) {
                        val date = escapeCsv(item.date.ifBlank { getTodayDateString() })
                        val name = escapeCsv(item.originalTerm)
                        val qty = String.format(Locale.US, "%.2f", item.quantity)
                        val unit = escapeCsv(item.unit)
                        val price = String.format(Locale.US, "%.2f", item.unitPrice)
                        val total = String.format(Locale.US, "%.2f", item.totalPrice)
                        val notes = escapeCsv(item.notes ?: "")
                        val status = escapeCsv(item.validationStatus.name)

                        writer.write("$date,$name,$qty,$unit,$price,$total,$notes,$status\n")
                    }

                    // Grand Total Row
                    val grandTotalStr = String.format(Locale.US, "%.2f", salesLog.grandTotal)
                    writer.write("\"మొత్తం అమ్మకాలు (Grand Total)\",\"\",\"\",\"\",\"\",$grandTotalStr,\"\",\"\n")
                    writer.flush()
                }
            }
            VernAiResult.Success(destinationFile)
        }.getOrElse { e ->
            VernAiResult.Error(e, "CSV ఎగుమతి విఫలమైంది (CSV export failed: ${e.localizedMessage})")
        }
    }

    /**
     * Exports [SalesLog] to an Excel-compatible XML Spreadsheet (SpreadsheetML 2003).
     * This format natively opens in Microsoft Excel, LibreOffice Calc, and Google Sheets,
     * embedding Telugu Unicode, bold header styling, currency formatting, and dynamic formulas.
     */
    fun exportToXlsx(salesLog: SalesLog, destinationFile: File): VernAiResult<File> {
        return runCatching {
            destinationFile.parentFile?.mkdirs()

            val xmlContent = buildExcelXml(salesLog)
            destinationFile.writeText(xmlContent, StandardCharsets.UTF_8)

            VernAiResult.Success(destinationFile)
        }.getOrElse { e ->
            VernAiResult.Error(e, "Excel ఎగుమతి విఫలమైంది (Excel export failed: ${e.localizedMessage})")
        }
    }

    private fun buildExcelXml(salesLog: SalesLog): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<?mso-application progid="Excel.Sheet"?>""").append("\n")
        sb.append("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:html="http://www.w3.org/TR/REC-html40">""").append("\n")

        // Styles
        sb.append("""  <Styles>
    <Style ss:ID="Default" ss:Name="Normal">
      <Alignment ss:Vertical="Center"/>
      <Borders/>
      <Font ss:FontName="Segoe UI" x:Family="Swiss" ss:Size="11"/>
    </Style>
    <Style ss:ID="HeaderStyle">
      <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
      <Borders>
        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2"/>
      </Borders>
      <Font ss:FontName="Segoe UI" ss:Size="11" ss:Color="#FFFFFF" ss:Bold="1"/>
      <Interior ss:Color="#0284C7" ss:Pattern="Solid"/>
    </Style>
    <Style ss:ID="CurrencyStyle">
      <Alignment ss:Horizontal="Right" ss:Vertical="Center"/>
      <NumberFormat ss:Format="₹#,##0.00"/>
    </Style>
    <Style ss:ID="QtyStyle">
      <Alignment ss:Horizontal="Right" ss:Vertical="Center"/>
      <NumberFormat ss:Format="#,##0.00"/>
    </Style>
    <Style ss:ID="TotalRowStyle">
      <Alignment ss:Horizontal="Right" ss:Vertical="Center"/>
      <Borders>
        <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="2"/>
        <Border ss:Position="Bottom" ss:LineStyle="Double" ss:Weight="3"/>
      </Borders>
      <Font ss:FontName="Segoe UI" ss:Size="12" ss:Color="#0F172A" ss:Bold="1"/>
      <Interior ss:Color="#FEF3C7" ss:Pattern="Solid"/>
      <NumberFormat ss:Format="₹#,##0.00"/>
    </Style>
    <Style ss:ID="TotalLabelStyle">
      <Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
      <Borders>
        <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="2"/>
        <Border ss:Position="Bottom" ss:LineStyle="Double" ss:Weight="3"/>
      </Borders>
      <Font ss:FontName="Segoe UI" ss:Size="12" ss:Color="#0F172A" ss:Bold="1"/>
      <Interior ss:Color="#FEF3C7" ss:Pattern="Solid"/>
    </Style>
  </Styles>""").append("\n")

        // Worksheet
        sb.append("""  <Worksheet ss:Name="అమ్మకాల లెడ్జర్">""").append("\n")
        sb.append("""    <Table ss:DefaultRowHeight="20">""").append("\n")
        sb.append("""      <Column ss:Width="100"/>""").append("\n") // Date
        sb.append("""      <Column ss:Width="160"/>""").append("\n") // Item
        sb.append("""      <Column ss:Width="80"/>""").append("\n")  // Quantity
        sb.append("""      <Column ss:Width="90"/>""").append("\n")  // Unit
        sb.append("""      <Column ss:Width="90"/>""").append("\n")  // Unit Price
        sb.append("""      <Column ss:Width="110"/>""").append("\n") // Total Price
        sb.append("""      <Column ss:Width="150"/>""").append("\n") // Notes
        sb.append("""      <Column ss:Width="90"/>""").append("\n")  // Status

        // Table Header
        sb.append("""      <Row ss:Height="26">
        <Cell ss:StyleID="HeaderStyle"><Data ss:Type="String">తేదీ (Date)</Data></Cell>
        <Cell ss:StyleID="HeaderStyle"><Data ss:Type="String">వస్తువు (Item)</Data></Cell>
        <Cell ss:StyleID="HeaderStyle"><Data ss:Type="String">పరిమాణం (Qty)</Data></Cell>
        <Cell ss:StyleID="HeaderStyle"><Data ss:Type="String">కొలత (Unit)</Data></Cell>
        <Cell ss:StyleID="HeaderStyle"><Data ss:Type="String">ధర (Unit Price)</Data></Cell>
        <Cell ss:StyleID="HeaderStyle"><Data ss:Type="String">మొత్తం (Total)</Data></Cell>
        <Cell ss:StyleID="HeaderStyle"><Data ss:Type="String">గమనికలు (Notes)</Data></Cell>
        <Cell ss:StyleID="HeaderStyle"><Data ss:Type="String">పరిశీలన (Status)</Data></Cell>
      </Row>""").append("\n")

        // Data Rows
        val items = salesLog.items
        for ((idx, item) in items.withIndex()) {
            val date = xmlEscape(item.date.ifBlank { getTodayDateString() })
            val name = xmlEscape(item.originalTerm)
            val unit = xmlEscape(item.unit)
            val notes = xmlEscape(item.notes ?: "")
            val status = xmlEscape(item.validationStatus.name)
            val rowNum = idx + 2 // 1-indexed, header is row 1

            sb.append("""      <Row>
        <Cell><Data ss:Type="String">$date</Data></Cell>
        <Cell><Data ss:Type="String">$name</Data></Cell>
        <Cell ss:StyleID="QtyStyle"><Data ss:Type="Number">${item.quantity}</Data></Cell>
        <Cell><Data ss:Type="String">$unit</Data></Cell>
        <Cell ss:StyleID="CurrencyStyle"><Data ss:Type="Number">${item.unitPrice}</Data></Cell>
        <Cell ss:StyleID="CurrencyStyle" ss:Formula="=RC[-3]*RC[-1]"><Data ss:Type="Number">${item.totalPrice}</Data></Cell>
        <Cell><Data ss:Type="String">$notes</Data></Cell>
        <Cell><Data ss:Type="String">$status</Data></Cell>
      </Row>""").append("\n")
        }

        // Summary Row with Excel SUM formula
        val totalRowCount = items.size
        val sumFormula = if (totalRowCount > 0) "=SUM(R2C:R[-1]C)" else "=0"
        val grandTotal = salesLog.grandTotal

        sb.append("""      <Row ss:Height="24">
        <Cell ss:StyleID="TotalLabelStyle"><Data ss:Type="String">మొత్తం (Grand Total)</Data></Cell>
        <Cell ss:StyleID="TotalLabelStyle"><Data ss:Type="String"></Data></Cell>
        <Cell ss:StyleID="TotalLabelStyle"><Data ss:Type="String"></Data></Cell>
        <Cell ss:StyleID="TotalLabelStyle"><Data ss:Type="String"></Data></Cell>
        <Cell ss:StyleID="TotalLabelStyle"><Data ss:Type="String"></Data></Cell>
        <Cell ss:StyleID="TotalRowStyle" ss:Formula="$sumFormula"><Data ss:Type="Number">$grandTotal</Data></Cell>
        <Cell ss:StyleID="TotalLabelStyle"><Data ss:Type="String"></Data></Cell>
        <Cell ss:StyleID="TotalLabelStyle"><Data ss:Type="String"></Data></Cell>
      </Row>""").append("\n")

        sb.append("""    </Table>
  </Worksheet>
</Workbook>""")

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
