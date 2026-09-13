package com.vernai.document.export

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.SalesLog
import java.io.File

/**
 * Production-ready, offline-first local document exporter implementing [DocumentExporter].
 * Coordinates DOCX (Word), PDF, CSV, and XLSX export with zero cloud APIs or internet connectivity.
 */
class LocalDocumentExporter(
    private val docxExporter: DocxDocumentExporter = DocxDocumentExporter(),
    private val pdfExporter: PdfDocumentExporter = PdfDocumentExporter(),
    private val xlsxExporter: XlsxDocumentExporter = XlsxDocumentExporter(),
    private val salesLedgerExporter: SalesLedgerExporter = SalesLedgerExporter()
) : DocumentExporter {

    override suspend fun exportComplaintLetter(
        draft: ComplaintDraft,
        destinationFile: File,
        config: ExportConfig
    ): VernAiResult<File> {
        return when (config.format) {
            ExportFormat.DOCX -> docxExporter.exportComplaintLetter(draft, destinationFile, config)
            ExportFormat.PDF -> pdfExporter.exportComplaintLetter(draft, destinationFile, config)
            ExportFormat.CSV,
            ExportFormat.XLSX -> VernAiResult.Error(
                IllegalArgumentException("లేఖలను CSV లేదా XLSX లోనికి ఎగుమతి చేయడం సాధ్యం కాదు (Letters cannot be exported to CSV/XLSX)"),
                "లేఖలకు కేవలం PDF మరియు DOCX మాత్రమే సమర్థించబడతాయి"
            )
        }
    }

    override suspend fun exportSalesLog(
        salesLog: SalesLog,
        destinationFile: File,
        config: ExportConfig
    ): VernAiResult<File> {
        return when (config.format) {
            ExportFormat.CSV -> salesLedgerExporter.exportToCsv(salesLog, destinationFile)
            ExportFormat.XLSX -> xlsxExporter.exportSalesLog(salesLog, destinationFile)
            ExportFormat.PDF -> pdfExporter.exportSalesLog(salesLog, destinationFile, config)
            ExportFormat.DOCX -> docxExporter.exportSalesLog(salesLog, destinationFile, config)
        }
    }

    override suspend fun exportExplanationReport(
        report: ExplanationReport,
        destinationFile: File,
        config: ExportConfig
    ): VernAiResult<File> {
        return when (config.format) {
            ExportFormat.DOCX -> docxExporter.exportExplanationReport(report, destinationFile, config)
            ExportFormat.PDF -> pdfExporter.exportExplanationReport(report, destinationFile, config)
            ExportFormat.CSV,
            ExportFormat.XLSX -> VernAiResult.Error(
                IllegalArgumentException("వివరణ నివేదికలను పట్టిక ఫార్మాట్లలో ఎగుమతి చేయడం సాధ్యం కాదు"),
                "నివేదికలకు కేవలం PDF మరియు DOCX మాత్రమే సమర్థించబడతాయి"
            )
        }
    }
}
