package com.vernai.document.processing

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.Language
import java.io.File
import java.io.InputStream

data class ExtractedDocument(
    val title: String,
    val rawText: String,
    val detectedLanguage: Language,
    val pageCount: Int,
    val isScannedImage: Boolean
)

/**
 * Parses documents offline from PDFs or scanned images.
 * Digital PDFs extracted via PDFBox; Scanned pages via Tesseract 5 NDK.
 */
interface DocumentProcessor {
    /**
     * Extracts text from a local PDF or image file.
     */
    suspend fun extractText(
        file: File,
        languageHint: Language? = null
    ): VernAiResult<ExtractedDocument>

    /**
     * Extracts text from an open input stream (e.g. Android content URI).
     */
    suspend fun extractFromStream(
        fileName: String,
        mimeType: String,
        stream: InputStream,
        languageHint: Language? = null
    ): VernAiResult<ExtractedDocument>
}
