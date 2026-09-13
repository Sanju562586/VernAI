package com.vernai.document.export

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.Language
import com.vernai.core.model.SalesLog
import java.io.File

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
}

data class ExportConfig(
    val format: ExportFormat,
    val targetLanguage: Language,
    val includeHeaderLogo: Boolean = false,
    val authorName: String? = null
)

/**
 * Handles professional document export with HarfBuzz/StaticLayout
 * complex Indic font ligature shaping.
 */
interface DocumentExporter {

    /**
     * Exports a formal complaint letter to PDF or DOCX.
     */
    suspend fun exportComplaintLetter(
        draft: ComplaintDraft,
        destinationFile: File,
        config: ExportConfig
    ): VernAiResult<File>

    /**
     * Exports a structured sales log ledger table to PDF or DOCX.
     */
    suspend fun exportSalesLog(
        salesLog: SalesLog,
        destinationFile: File,
        config: ExportConfig
    ): VernAiResult<File>

    /**
     * Exports a document explanation report to PDF or DOCX.
     */
    suspend fun exportExplanationReport(
        report: ExplanationReport,
        destinationFile: File,
        config: ExportConfig
    ): VernAiResult<File>
}
