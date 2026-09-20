package com.vernai.ai.parser

import com.vernai.core.model.Language
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
private data class RawSalesJson(
    val items: List<RawSalesItem> = emptyList()
)

@Serializable
private data class RawSalesItem(
    val date: String = "",
    val original_term: String = "",
    val standard_name: String = "",
    val quantity: Double = 0.0,
    val unit: String = "unit",
    val unit_price: Double = 0.0,
    val total_price: Double = 0.0,
    val notes: String? = null
)

/**
 * Parses and validates structured LLM responses into typed domain entities.
 */
class SalesLogParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Extracts JSON block from raw LLM output, parsing it into a validated SalesLog.
     */
    fun parse(rawOutput: String, spokenTranscript: String, language: Language): SalesLog {
        // Strip markdown code fences if present
        val cleanJson = extractJsonSubstring(rawOutput)
        val parsed = runCatching {
            json.decodeFromString<RawSalesJson>(cleanJson)
        }.getOrDefault(RawSalesJson())

        val defaultTerm = when (language) {
            Language.TAMIL -> "பொருள்"
            Language.HINDI, Language.MARATHI -> "सामग्री"
            Language.ENGLISH -> "Item"
            else -> "వస్తువు"
        }

        val rawItems = parsed.items.map { raw ->
            val rawItem = SalesItem(
                id = UUID.randomUUID().toString(),
                date = raw.date,
                originalTerm = raw.original_term.ifBlank { defaultTerm },
                standardName = raw.standard_name.ifBlank { raw.original_term.ifBlank { defaultTerm } },
                quantity = raw.quantity,
                unit = raw.unit,
                unitPrice = raw.unit_price,
                totalPrice = raw.total_price,
                notes = raw.notes
            )
            com.vernai.sales.processing.SalesArithmeticValidator.validateAndReconcile(rawItem, language).item
        }

        val checkedItems = com.vernai.sales.processing.DuplicatePreventionEngine.flagDuplicates(rawItems, language)
        val grandTotal = checkedItems.sumOf { it.totalPrice }

        return SalesLog(
            rawSpokenText = spokenTranscript,
            detectedLanguage = language,
            items = checkedItems,
            grandTotal = grandTotal
        )
    }

    private fun extractJsonSubstring(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            text.substring(start, end + 1)
        } else {
            "{}"
        }
    }
}
