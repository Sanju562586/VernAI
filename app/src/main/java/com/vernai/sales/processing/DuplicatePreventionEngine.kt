package com.vernai.sales.processing

import com.vernai.core.model.Language
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
        val messageInTelugu: String? = null,
        val duplicateMessage: String? = null
    )

    /**
     * Checks if [candidateItem] duplicates any item in [existingItems].
     */
    fun checkDuplicate(
        candidateItem: SalesItem,
        existingItems: List<SalesItem>,
        language: Language? = null
    ): DuplicateCheckResult {
        val normCandidate = normalizeItemName(candidateItem.standardName.ifBlank { candidateItem.originalTerm })

        val match = existingItems.find { existing ->
            existing.id != candidateItem.id &&
            normalizeItemName(existing.standardName.ifBlank { existing.originalTerm }) == normCandidate &&
            isSameDate(existing.date, candidateItem.date)
        }

        return if (match != null) {
            val lang = language ?: Language.detectFromText(candidateItem.originalTerm)
            val totalStr = String.format(java.util.Locale.US, "%.2f", match.totalPrice)
            val msg = when (lang) {
                Language.TAMIL -> "'${candidateItem.originalTerm}' இன்றைய பதிவேட்டில் ஏற்கனவே உள்ளது (${match.quantity} ${match.unit}, மொத்தம் ₹$totalStr). இதை ஒன்றாக சேர்க்கவா (Merge) அல்லது தனியாக வைக்கவா?"
                Language.HINDI, Language.MARATHI -> "'${candidateItem.originalTerm}' आज के खाते में पहले से मौजूद है (${match.quantity} ${match.unit}, कुल ₹$totalStr)। क्या इसे जोड़ें (Merge) या अलग रखें?"
                Language.ENGLISH -> "'${candidateItem.originalTerm}' already exists in today's ledger (${match.quantity} ${match.unit}, total ₹$totalStr). Merge items or keep separate?"
                else -> "'${candidateItem.originalTerm}' ఈరోజు లెడ్జర్‌లో ఇప్పటికే ఉంది (${match.quantity} ${match.unit}, మొత్తం ₹$totalStr). దీనిని కలపమంటారా (Merge) లేదా ప్రత్యేకంగా ఉంచమంటారా?"
            }
            DuplicateCheckResult(
                isDuplicate = true,
                existingItem = match,
                messageInTelugu = msg,
                duplicateMessage = msg
            )
        } else {
            DuplicateCheckResult(isDuplicate = false)
        }
    }

    /**
     * Marks duplicate items in a list with [SalesValidationStatus.DUPLICATE_WARNING].
     */
    fun flagDuplicates(items: List<SalesItem>, language: Language? = null): List<SalesItem> {
        val seen = mutableMapOf<String, SalesItem>()
        return items.map { item ->
            val key = "${normalizeItemName(item.standardName.ifBlank { item.originalTerm })}_${item.date}"
            if (seen.containsKey(key)) {
                val lang = language ?: Language.detectFromText(item.originalTerm)
                val prompt = when (lang) {
                    Language.TAMIL -> "'${item.originalTerm}' ஏற்கனவே பதிவு செய்யப்பட்டுள்ளது. அளவைக் கூட்டவா?"
                    Language.HINDI, Language.MARATHI -> "'${item.originalTerm}' पहले से दर्ज है। क्या मात्रा जोड़ें?"
                    Language.ENGLISH -> "'${item.originalTerm}' is already recorded. Merge quantity?"
                    else -> "'${item.originalTerm}' ఇప్పటికే నమోదై ఉంది. పరిమాణం కలపాలా?"
                }
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
    fun mergeItems(existing: SalesItem, duplicate: SalesItem, language: Language? = null): SalesItem {
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

        return SalesArithmeticValidator.validateAndReconcile(merged, language).item
    }

    private fun normalizeItemName(name: String): String {
        return name.lowercase().trim()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""[\(\)\[\]]"""), "")
            .replace("టమాటాలు", "టమాటా")
            .replace("ఉల్లిగడ్డలు", "ఉల్లిపాయలు")
            .replace("కోడిగుడ్లు", "గుడ్లు")
            .replace("தக்காளி", "தக்காளி")
            .replace("டொமேட்டோ", "தக்காளி")
            .replace("ஆலு", "உருளைக்கிழங்கு")
            .replace("டொமேட்டோ", "தக்காளி")
    }

    private fun isSameDate(date1: String, date2: String): Boolean {
        if (date1.isBlank() || date2.isBlank()) return true
        return date1 == date2
    }
}
