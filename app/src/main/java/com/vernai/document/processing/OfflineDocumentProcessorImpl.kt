package com.vernai.document.processing

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.Language
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Production implementation of [DocumentProcessor] using pure-local, offline processing.
 * 1. Tries digital PDF text stream parsing via [LocalPdfExtractor].
 * 2. Falls back to on-device OCR via [OcrEngine] if the PDF is scanned or image-based.
 * 3. Gracefully reports errors when documents are unreadable, password-protected, or corrupted.
 */
class OfflineDocumentProcessorImpl(
    private val pdfExtractor: LocalPdfExtractor = LocalPdfExtractor(),
    private val ocrEngine: OcrEngine = AndroidLocalOcrEngine()
) : DocumentProcessor {

    override suspend fun extractText(
        file: File,
        languageHint: Language?
    ): VernAiResult<ExtractedDocument> {
        if (!file.exists()) {
            return VernAiResult.Error(IllegalArgumentException("ఫైల్ కనుగొనబడలేదు (File does not exist: ${file.name})"))
        }
        val bytes = file.readBytes()
        return extractFromBytes(file.name, getMimeType(file.name), bytes, languageHint)
    }

    override suspend fun extractFromStream(
        fileName: String,
        mimeType: String,
        stream: InputStream,
        languageHint: Language?
    ): VernAiResult<ExtractedDocument> {
        val bytes = stream.readBytes()
        return extractFromBytes(fileName, mimeType, bytes, languageHint)
    }

    suspend fun extractFromBytes(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        languageHint: Language?
    ): VernAiResult<ExtractedDocument> {
        if (bytes.isEmpty()) {
            return VernAiResult.Error(IllegalArgumentException("పత్రం ఖాళీగా ఉంది (The document is completely empty / 0 bytes)."))
        }

        val isPdf = fileName.endsWith(".pdf", ignoreCase = true) || mimeType.contains("pdf", ignoreCase = true)

        if (isPdf) {
            try {
                val pdfResult = pdfExtractor.extract(ByteArrayInputStream(bytes))
                if (!pdfResult.isScannedOnly && pdfResult.extractedText.length >= 30) {
                    val lang = detectLanguage(pdfResult.extractedText, languageHint)
                    return VernAiResult.Success(
                        ExtractedDocument(
                            title = fileName,
                            rawText = pdfResult.extractedText,
                            detectedLanguage = lang,
                            pageCount = pdfResult.pageCount,
                            isScannedImage = false
                        )
                    )
                }

                // If scanned PDF (no digital text streams), fall back to OCR
                val ocrResult = ocrEngine.recognizeTextFromBytes(bytes, mimeType, languageHint)
                return when (ocrResult) {
                    is VernAiResult.Success -> {
                        val text = ocrResult.data.trim()
                        if (text.isBlank()) {
                            VernAiResult.Error(IllegalStateException("స్కాన్ చేసిన పేజీల నుండి వచనం గుర్తించలేకపోయాము (OCR detected no readable text)."))
                        } else {
                            val lang = detectLanguage(text, languageHint)
                            VernAiResult.Success(
                                ExtractedDocument(
                                    title = fileName,
                                    rawText = text,
                                    detectedLanguage = lang,
                                    pageCount = pdfResult.pageCount,
                                    isScannedImage = true
                                )
                            )
                        }
                    }
                    is VernAiResult.Error -> VernAiResult.Error(ocrResult.exception, ocrResult.message)
                    is VernAiResult.Loading -> VernAiResult.Loading(ocrResult.progress, ocrResult.stage)
                }
            } catch (e: PdfExtractionException.PasswordProtected) {
                return VernAiResult.Error(e, "ఈ పత్రం పాస్‌వర్డ్ ద్వారా రక్షించబడింది (This PDF is password-protected and cannot be read offline).")
            } catch (e: PdfExtractionException.CorruptedPdf) {
                return VernAiResult.Error(e, "పత్రం పాడైంది లేదా చెల్లని ఫార్మాట్ (Corrupted PDF: ${e.message})")
            } catch (e: Exception) {
                return VernAiResult.Error(e, "పత్రం సంగ్రహణ విఫలమైంది (Extraction failed: ${e.localizedMessage})")
            }
        } else {
            // Image file (JPEG, PNG, WEBP) -> Direct On-Device OCR
            val ocrResult = ocrEngine.recognizeTextFromBytes(bytes, mimeType, languageHint)
            return when (ocrResult) {
                is VernAiResult.Success -> {
                    val text = ocrResult.data.trim()
                    if (text.isBlank()) {
                        VernAiResult.Error(IllegalStateException("చిత్రం నుండి వచనం గుర్తించలేకపోయాము (OCR detected no readable text in image)."))
                    } else {
                        val lang = detectLanguage(text, languageHint)
                        VernAiResult.Success(
                            ExtractedDocument(
                                title = fileName,
                                rawText = text,
                                detectedLanguage = lang,
                                pageCount = 1,
                                isScannedImage = true
                            )
                        )
                    }
                }
                is VernAiResult.Error -> VernAiResult.Error(ocrResult.exception, ocrResult.message)
                is VernAiResult.Loading -> VernAiResult.Loading(ocrResult.progress, ocrResult.stage)
            }
        }
    }

    private fun detectLanguage(text: String, hint: Language?): Language {
        val hasTelugu = text.any { it.code in 0x0C00..0x0C7F }
        return when {
            hasTelugu -> Language.TELUGU
            hint != null -> hint
            else -> Language.ENGLISH
        }
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
}
