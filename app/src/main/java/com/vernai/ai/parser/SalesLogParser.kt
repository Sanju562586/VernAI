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
    val original_term: String = "",
    val standard_name: String = "",
    val quantity: Double = 0.0,
    val unit: String = "unit",
    val unit_price: Double = 0.0,
    val total_price: Double = 0.0
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

        val domainItems = parsed.items.map { raw ->
            val computedTotal = if (raw.total_price > 0.0) raw.total_price else raw.quantity * raw.unit_price
            SalesItem(
                id = UUID.randomUUID().toString(),
                originalTerm = raw.original_term.ifBlank { "Item" },
                standardName = raw.standard_name.ifBlank { raw.original_term },
                quantity = raw.quantity,
                unit = raw.unit,
                unitPrice = raw.unit_price,
                totalPrice = computedTotal
            )
        }

        val grandTotal = domainItems.sumOf { it.totalPrice }

        return SalesLog(
            rawSpokenText = spokenTranscript,
            detectedLanguage = language,
            items = domainItems,
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
