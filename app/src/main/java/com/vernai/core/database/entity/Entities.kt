package com.vernai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales_logs")
data class SalesLogEntity(
    @PrimaryKey val id: String,
    val rawSpokenText: String,
    val languageCode: String,
    val itemsJson: String,
    val grandTotal: Double,
    val timestamp: Long
)

@Entity(tableName = "complaints")
data class ComplaintEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val department: String,
    val recipientDesignation: String,
    val vernacularBody: String,
    val englishTranslation: String,
    val languageCode: String,
    val senderName: String?,
    val location: String?,
    val timestamp: Long
)

@Entity(tableName = "document_explanations")
data class ExplanationEntity(
    @PrimaryKey val id: String,
    val sourceDocumentName: String,
    val extractedCharacterCount: Int,
    val summaryInVernacular: String,
    val keyActionPointsJson: String,
    val legalDeadlinesJson: String,
    val languageCode: String,
    val timestamp: Long
)
