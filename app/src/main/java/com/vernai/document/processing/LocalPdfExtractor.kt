package com.vernai.document.processing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

sealed class PdfExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PasswordProtected(message: String = "This PDF document is encrypted or password-protected.") : PdfExtractionException(message)
    class CorruptedPdf(message: String, cause: Throwable? = null) : PdfExtractionException(message, cause)
    class EmptyDocument(message: String = "No readable content found in PDF.") : PdfExtractionException(message)
}

data class PdfExtractionResult(
    val extractedText: String,
    val pageCount: Int,
    val isScannedOnly: Boolean // True if document contains pages but zero digital text streams (requires OCR)
)

/**
 * Android-compatible pure-Kotlin local PDF text stream parser.
 * Extracts text from digital PDF streams (FlateDecode and uncompressed)
 * without requiring heavy native desktop dependencies or remote servers.
 */
class LocalPdfExtractor {

    fun extract(stream: InputStream): PdfExtractionResult {
        val bytes = stream.readBytes()
        if (bytes.isEmpty()) {
            throw PdfExtractionException.CorruptedPdf("File is empty (0 bytes).")
        }

        val header = String(bytes.take(10).toByteArray(), StandardCharsets.ISO_8859_1)
        if (!header.startsWith("%PDF-")) {
            throw PdfExtractionException.CorruptedPdf("File header does not match PDF format (%PDF-).")
        }

        val rawContent = String(bytes, StandardCharsets.ISO_8859_1)

        if (rawContent.contains("/Encrypt")) {
            throw PdfExtractionException.PasswordProtected()
        }

        // Count /Page objects
        val pageCount = countPages(rawContent)

        // Extract text from uncompressed & compressed streams
        val textBuilder = StringBuilder()

        // 1. Scan for text operators in streams
        val streamRegex = Regex("""stream\r?\n(.*?)\r?\nendstream""", RegexOption.DOT_MATCHES_ALL)
        val streamMatches = streamRegex.findAll(rawContent)

        for (match in streamMatches) {
            val streamContent = match.groupValues[1]
            val streamBytes = streamContent.toByteArray(StandardCharsets.ISO_8859_1)

            // Try decompressing if it's FlateEncoded
            val decompressedBytes = tryDecompressFlate(streamBytes)
            val streamString = if (decompressedBytes != null) {
                String(decompressedBytes, StandardCharsets.UTF_8)
            } else {
                String(streamBytes, StandardCharsets.ISO_8859_1)
            }

            extractTextFromStreamString(streamString, textBuilder)
        }

        // 2. Direct string literal search as fallback for simple digital PDFs
        if (textBuilder.length < 20) {
            extractLiteralStringsFallback(rawContent, textBuilder)
        }

        val finalText = textBuilder.toString().trim()
        val isScanned = finalText.length < 30 && pageCount > 0

        return PdfExtractionResult(
            extractedText = finalText,
            pageCount = pageCount.coerceAtLeast(1),
            isScannedOnly = isScanned
        )
    }

    private fun extractTextFromStreamString(content: String, out: StringBuilder) {
        if (!content.contains("BT")) return

        val btRegex = Regex("""BT(.*?)ET""", RegexOption.DOT_MATCHES_ALL)
        for (block in btRegex.findAll(content)) {
            val blockText = block.groupValues[1]

            // TJ array operator: [(Text 1) 120 (Text 2)] TJ
            val tjArrayRegex = Regex("""\[(.*?)\]\s*TJ""")
            for (tjMatch in tjArrayRegex.findAll(blockText)) {
                val arrayContent = tjMatch.groupValues[1]
                val strInArrayRegex = Regex("""\((.*?)\)""")
                for (sMatch in strInArrayRegex.findAll(arrayContent)) {
                    val decoded = unescapePdfString(sMatch.groupValues[1])
                    if (decoded.isNotBlank()) {
                        out.append(decoded).append(" ")
                    }
                }
                out.append("\n")
            }

            // Tj single string operator: (Hello World) Tj
            val tjSingleRegex = Regex("""\((.*?)\)\s*Tj""")
            for (tjMatch in tjSingleRegex.findAll(blockText)) {
                val decoded = unescapePdfString(tjMatch.groupValues[1])
                if (decoded.isNotBlank()) {
                    out.append(decoded).append("\n")
                }
            }
        }
    }

    private fun extractLiteralStringsFallback(content: String, out: StringBuilder) {
        // Fallback pattern matching parenthesis strings in BT blocks
        val btBlockRegex = Regex("""BT\s+(.*?)\s+ET""", RegexOption.DOT_MATCHES_ALL)
        for (bt in btBlockRegex.findAll(content)) {
            val strRegex = Regex("""\(([^()]*?)\)""")
            for (match in strRegex.findAll(bt.groupValues[1])) {
                val str = unescapePdfString(match.groupValues[1]).trim()
                if (str.length > 2 && !str.startsWith("/") && !str.startsWith("\\")) {
                    out.append(str).append(" ")
                }
            }
            out.append("\n")
        }
    }

    private fun tryDecompressFlate(data: ByteArray): ByteArray? {
        return try {
            val inflater = Inflater(false)
            val inputStream = InflaterInputStream(ByteArrayInputStream(data), inflater)
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.toByteArray()
        } catch (_: Exception) {
            try {
                // Try nowrap inflater
                val inflater = Inflater(true)
                val inputStream = InflaterInputStream(ByteArrayInputStream(data), inflater)
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.toByteArray()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun unescapePdfString(s: String): String {
        return s.replace("""\( """, "(")
            .replace("""\)""", ")")
            .replace("""\\""", "\\")
            .replace("""\r""", "\r")
            .replace("""\n""", "\n")
            .replace("""\t""", "\t")
    }

    private fun countPages(content: String): Int {
        val typePageMatches = Regex("""/Type\s*/Page\b""").findAll(content).count()
        if (typePageMatches > 0) return typePageMatches
        val countRegex = Regex("""/Count\s+(\d+)""")
        val countMatch = countRegex.find(content)
        return countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }
}
