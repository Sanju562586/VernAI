package com.vernai.document.export

import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

data class FileValidationResult(
    val isValid: Boolean,
    val format: ExportFormat,
    val fileSizeBytes: Long,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val hasTeluguUnicode: Boolean = false
)

/**
 * Validates generated export files (DOCX, PDF, CSV, XLSX) for structural integrity,
 * specification compliance, and correct Telugu Unicode character encoding.
 */
object DocumentFileValidator {

    /**
     * Validates a PDF file.
     * Checks for standard %PDF- magic bytes, %%EOF trailer, object structures, and non-empty stream.
     */
    fun validatePdf(file: File): FileValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!file.exists() || file.length() == 0L) {
            errors.add("ఫైల్ ఖాళీగా ఉంది లేదా ఉనికిలో లేదు (File does not exist or is empty)")
            return FileValidationResult(false, ExportFormat.PDF, 0L, errors)
        }

        val bytes = file.readBytes()
        val header = String(bytes.take(8).toByteArray(), StandardCharsets.ISO_8859_1)
        if (!header.startsWith("%PDF-")) {
            errors.add("చెల్లని PDF హెడర్ మ్యాజిక్ బైట్లు (Invalid PDF magic header): '$header'")
        }

        val tail = String(bytes.takeLast(128).toByteArray(), StandardCharsets.ISO_8859_1)
        if (!tail.contains("%%EOF")) {
            errors.add("PDF ముగింపు గుర్తు %%EOF కనుగొనబడలేదు (Missing %%EOF trailer)")
        }

        val fullText = String(bytes, StandardCharsets.ISO_8859_1)
        if (!fullText.contains("obj") || !fullText.contains("endobj")) {
            errors.add("PDF ఆబ్జెక్ట్ నిర్మాణాలు కనుగొనబడలేదు (Missing PDF objects)")
        }

        // Check if Telugu characters exist in the binary or font streams
        val hasTelugu = hasTeluguCharacters(String(bytes, StandardCharsets.UTF_8))

        return FileValidationResult(
            isValid = errors.isEmpty(),
            format = ExportFormat.PDF,
            fileSizeBytes = file.length(),
            errors = errors,
            warnings = warnings,
            hasTeluguUnicode = hasTelugu
        )
    }

    /**
     * Validates an Office OpenXML Word (.docx) document.
     * Ensures valid ZIP archive structure, required OOXML parts, well-formed XML, and Telugu Unicode encoding.
     */
    fun validateDocx(file: File): FileValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!file.exists() || file.length() == 0L) {
            errors.add("DOCX ఫైల్ ఖాళీగా ఉంది (DOCX file does not exist or is empty)")
            return FileValidationResult(false, ExportFormat.DOCX, 0L, errors)
        }

        val requiredEntries = setOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "word/_rels/document.xml.rels",
            "word/document.xml",
            "word/styles.xml"
        )
        val foundEntries = mutableSetOf<String>()
        var documentXmlContent = ""

        try {
            FileInputStream(file).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        foundEntries.add(entry.name)
                        if (entry.name == "word/document.xml") {
                            documentXmlContent = String(zis.readBytes(), StandardCharsets.UTF_8)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("చెల్లని DOCX ZIP ప్యాకేజీ (Corrupted ZIP structure: ${e.message})")
        }

        val missing = requiredEntries - foundEntries
        if (missing.isNotEmpty()) {
            errors.add("DOCX అవసరమైన ఫైళ్ళు తప్పిపోయాయి (Missing OOXML parts: $missing)")
        }

        var hasTelugu = false
        if (documentXmlContent.isNotBlank()) {
            // Check for unescaped raw control characters or replacement characters
            if (documentXmlContent.contains("\uFFFD")) {
                errors.add("యూనికోడ్ అక్షర దోషం కనుగొనబడింది (Unicode replacement character \\uFFFD detected)")
            }
            if (!documentXmlContent.contains("<w:document") || !documentXmlContent.contains("</w:document>")) {
                errors.add("word/document.xml అసంపూర్ణంగా ఉంది (Malformed XML structure)")
            }
            hasTelugu = hasTeluguCharacters(documentXmlContent)
        }

        return FileValidationResult(
            isValid = errors.isEmpty(),
            format = ExportFormat.DOCX,
            fileSizeBytes = file.length(),
            errors = errors,
            warnings = warnings,
            hasTeluguUnicode = hasTelugu
        )
    }

    /**
     * Validates an Excel spreadsheet (modern OpenXML .xlsx or SpreadsheetML XML).
     */
    fun validateXlsx(file: File): FileValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!file.exists() || file.length() == 0L) {
            errors.add("XLSX ఫైల్ ఖాళీగా ఉంది (XLSX file does not exist or is empty)")
            return FileValidationResult(false, ExportFormat.XLSX, 0L, errors)
        }

        var hasTelugu = false
        val bytes = file.readBytes()

        // Check if modern OpenXML ZIP (starts with PK\x03\x04)
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) {
            val requiredEntries = setOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/worksheets/sheet1.xml",
                "xl/styles.xml"
            )
            val foundEntries = mutableSetOf<String>()
            var sheetXmlContent = ""

            try {
                FileInputStream(file).use { fis ->
                    ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            foundEntries.add(entry.name)
                            if (entry.name == "xl/worksheets/sheet1.xml") {
                                sheetXmlContent = String(zis.readBytes(), StandardCharsets.UTF_8)
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                errors.add("చెల్లని XLSX ZIP ప్యాకేజీ (Corrupted XLSX ZIP: ${e.message})")
            }

            val missing = requiredEntries - foundEntries
            if (missing.isNotEmpty()) {
                errors.add("XLSX అవసరమైన ఫైళ్ళు తప్పిపోయాయి (Missing XLSX parts: $missing)")
            }

            if (sheetXmlContent.isNotBlank()) {
                if (sheetXmlContent.contains("\uFFFD")) {
                    errors.add("యూనికోడ్ రీప్లేస్‌మెంట్ అక్షరం కనుగొనబడింది (Unicode corruption)")
                }
                hasTelugu = hasTeluguCharacters(sheetXmlContent)
            }
        } else {
            // SpreadsheetML 2003 XML fallback format
            val content = String(bytes, StandardCharsets.UTF_8)
            if (!content.startsWith("<?xml") || !content.contains("<Workbook")) {
                errors.add("చెల్లని Excel XML లేదా ZIP నిర్మాణం (Neither valid OpenXML ZIP nor SpreadsheetML XML)")
            }
            if (content.contains("\uFFFD")) {
                errors.add("యూనికోడ్ రీప్లేస్‌మెంట్ అక్షరం కనుగొనబడింది")
            }
            hasTelugu = hasTeluguCharacters(content)
        }

        return FileValidationResult(
            isValid = errors.isEmpty(),
            format = ExportFormat.XLSX,
            fileSizeBytes = file.length(),
            errors = errors,
            warnings = warnings,
            hasTeluguUnicode = hasTelugu
        )
    }

    /**
     * Validates a CSV file.
     * Verifies presence of UTF-8 BOM (0xEF, 0xBB, 0xBF), comma-separated columns, and Telugu characters.
     */
    fun validateCsv(file: File): FileValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!file.exists() || file.length() == 0L) {
            errors.add("CSV ఫైల్ ఖాళీగా ఉంది (CSV file does not exist or is empty)")
            return FileValidationResult(false, ExportFormat.CSV, 0L, errors)
        }

        val bytes = file.readBytes()

        // Check UTF-8 BOM: 0xEF, 0xBB, 0xBF
        val hasBom = bytes.size >= 3 &&
                (bytes[0] == 0xEF.toByte()) &&
                (bytes[1] == 0xBB.toByte()) &&
                (bytes[2] == 0xBF.toByte())

        if (!hasBom) {
            warnings.add("UTF-8 BOM బైట్ ఆర్డర్ మార్క్ లేదు (Missing UTF-8 BOM - Excel may garble Indic text)")
        }

        val content = String(if (hasBom) bytes.copyOfRange(3, bytes.size) else bytes, StandardCharsets.UTF_8)
        val lines = content.lines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            errors.add("CSV లైన్లు ఏవీ కనుగొనబడలేదు (No data rows found in CSV)")
        } else {
            val headerColumns = lines[0].split(",").size
            for ((index, line) in lines.withIndex()) {
                // Note: quoted values might contain commas, but simple sanity check
                if (line.isBlank()) continue
                if (index > 0 && !line.contains(",")) {
                    errors.add("పంక్తి $index లో కామా విభజన లేదు (Malformed CSV row $index)")
                }
            }
        }

        val hasTelugu = hasTeluguCharacters(content)

        return FileValidationResult(
            isValid = errors.isEmpty(),
            format = ExportFormat.CSV,
            fileSizeBytes = file.length(),
            errors = errors,
            warnings = warnings,
            hasTeluguUnicode = hasTelugu
        )
    }

    /**
     * Checks if a string contains any Telugu Unicode characters (U+0C00 to U+0C7F).
     */
    fun hasTeluguCharacters(text: String): Boolean {
        return text.any { c -> c.code in 0x0C00..0x0C7F }
    }
}
