package com.vernai.core.model

import java.util.UUID

/**
 * An individual itemized record within a sales ledger entry.
 */
data class SalesItem(
    val id: String = UUID.randomUUID().toString(),
    val originalTerm: String,
    val standardName: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val totalPrice: Double
)

/**
 * A completed sales transaction or daily ledger log.
 */
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
