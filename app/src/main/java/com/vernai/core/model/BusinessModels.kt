package com.vernai.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Validation and verification status for individual sales ledger items.
 */
@Serializable
enum class SalesValidationStatus {
    VERIFIED,              // Arithmetic matches (quantity * unitPrice == totalPrice)
    ARITHMETIC_MISMATCH,   // Expected total != provided total
    CLARIFICATION_NEEDED,  // Missing quantity, unit, or price
    DUPLICATE_WARNING      // Same item and quantity recorded in current ledger
}

/**
 * An individual itemized record within a sales ledger entry.
 * Designed with full schema: date, item, quantity, unit price, total, notes, and validation status.
 */
@Serializable
data class SalesItem(
    val id: String = UUID.randomUUID().toString(),
    val date: String = "",
    val originalTerm: String,
    val standardName: String = originalTerm,
    val quantity: Double = 1.0,
    val unit: String = "unit",
    val unitPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    val notes: String? = null,
    val validationStatus: SalesValidationStatus = SalesValidationStatus.VERIFIED,
    val clarificationPrompt: String? = null
)

/**
 * A completed sales transaction or daily ledger log.
 */
@Serializable
data class SalesLog(
    val id: String = UUID.randomUUID().toString(),
    val rawSpokenText: String,
    val detectedLanguage: Language,
    val items: List<SalesItem>,
    val grandTotal: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Formal complaint letter draft.
 */
data class ComplaintDraft(
    val id: String = UUID.randomUUID().toString(),
    val subject: String,
    val department: String,
    val recipientDesignation: String,
    val vernacularBody: String,
    val englishTranslation: String,
    val targetLanguage: Language,
    val senderName: String? = null,
    val location: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Simplified document explanation and key action points.
 */
data class ExplanationReport(
    val id: String = UUID.randomUUID().toString(),
    val sourceDocumentName: String,
    val extractedCharacterCount: Int,
    val summaryInVernacular: String,
    val keyActionPoints: List<String>,
    val legalDeadlines: List<String>,
    val targetLanguage: Language,
    val timestamp: Long = System.currentTimeMillis()
)
