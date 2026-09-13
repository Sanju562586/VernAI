package com.vernai.sales.processing

import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesValidationStatus

/**
 * Engine for detecting and managing duplicate item entries within a daily sales ledger.
 * Prevents accidental double-counting when a shopkeeper re-dictates transactions.
 */
object DuplicatePreventionEngine {

    data class DuplicateCheckResult(
        val isDuplicate: Boolean,
        val existingItem: SalesItem? = null,
        val messageInTelugu: String? = null
    )

    /**
     * Checks if [candidateItem] duplicates any item in [existingItems].
     */
    fun checkDuplicate(candidateItem: SalesItem, existingItems: List<SalesItem>): DuplicateCheckResult {
        val normCandidate = normalizeItemName(candidateItem.standardName.ifBlank { candidateItem.originalTerm })

        val match = existingItems.find { existing ->
            existing.id != candidateItem.id &&
            normalizeItemName(existing.standardName.ifBlank { existing.originalTerm }) == normCandidate &&
            isSameDate(existing.date, candidateItem.date)
        }

        return if (match != null) {
            val msg = "'${candidateItem.originalTerm}' ఈరోజు లెడ్జర్‌లో ఇప్పటికే ఉంది (${match.quantity} ${match.unit}, మొత్తం ₹${String.format(java.util.Locale.US, "%.2f", match.totalPrice)}). దీనిని కలపమంటారా (Merge) లేదా ప్రత్యేకంగా ఉంచమంటారా?"
            DuplicateCheckResult(
                isDuplicate = true,
                existingItem = match,
                messageInTelugu = msg
            )
        } else {
            DuplicateCheckResult(isDuplicate = false)
        }
    }

    /**
     * Marks duplicate items in a list with [SalesValidationStatus.DUPLICATE_WARNING].
     */
    fun flagDuplicates(items: List<SalesItem>): List<SalesItem> {
        val seen = mutableMapOf<String, SalesItem>()
        return items.map { item ->
            val key = "${normalizeItemName(item.standardName.ifBlank { item.originalTerm })}_${item.date}"
            if (seen.containsKey(key)) {
                val prompt = "'${item.originalTerm}' ఇప్పటికే నమోదై ఉంది. పరిమాణం కలపాలా?"
                item.copy(
                    validationStatus = SalesValidationStatus.DUPLICATE_WARNING,
                    clarificationPrompt = prompt
                )
            } else {
                seen[key] = item
                item
            }
        }
    }

    /**
     * Merges two duplicate sales items by summing their quantities and recalculating total.
     */
    fun mergeItems(existing: SalesItem, duplicate: SalesItem): SalesItem {
        val combinedQuantity = existing.quantity + duplicate.quantity
        val effectiveUnitPrice = if (existing.unitPrice > 0.0) existing.unitPrice else duplicate.unitPrice
        val combinedTotal = Math.round((combinedQuantity * effectiveUnitPrice) * 100.0) / 100.0

        val combinedNotes = listOfNotNull(existing.notes, duplicate.notes)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")

        val merged = existing.copy(
            quantity = combinedQuantity,
            unitPrice = effectiveUnitPrice,
            totalPrice = combinedTotal,
            notes = combinedNotes.ifBlank { null },
            validationStatus = SalesValidationStatus.VERIFIED,
            clarificationPrompt = null
        )

        return SalesArithmeticValidator.validateAndReconcile(merged).item
    }

    private fun normalizeItemName(name: String): String {
        return name.lowercase().trim()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""[\(\)\[\]]"""), "")
            .replace("టమాటాలు", "టమాటా")
            .replace("ఉల్లిగడ్డలు", "ఉల్లిపాయలు")
            .replace("కోడిగుడ్లు", "గుడ్లు")
    }

    private fun isSameDate(date1: String, date2: String): Boolean {
        if (date1.isBlank() || date2.isBlank()) return true
        return date1 == date2
    }
}
