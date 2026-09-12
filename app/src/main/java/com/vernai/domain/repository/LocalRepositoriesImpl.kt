package com.vernai.domain.repository

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.database.VernAiDatabase
import com.vernai.core.database.entity.ComplaintEntity
import com.vernai.core.database.entity.ExplanationEntity
import com.vernai.core.database.entity.SalesLogEntity
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.Language
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalSalesLogRepository(
    private val database: VernAiDatabase
) : SalesLogRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun getSalesLogsStream(): Flow<List<SalesLog>> {
        return database.salesLogDao().getAllSalesLogs().map { list ->
            list.map { entity ->
                val items = runCatching {
                    json.decodeFromString<List<SalesItem>>(entity.itemsJson)
                }.getOrDefault(emptyList())
                SalesLog(
                    id = entity.id,
                    rawSpokenText = entity.rawSpokenText,
                    detectedLanguage = Language.fromIso(entity.languageCode),
                    items = items,
                    grandTotal = entity.grandTotal,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    override suspend fun getSalesLogById(id: String): SalesLog? {
        val entity = database.salesLogDao().getSalesLogById(id) ?: return null
        val items = runCatching {
            json.decodeFromString<List<SalesItem>>(entity.itemsJson)
        }.getOrDefault(emptyList())
        return SalesLog(
            id = entity.id,
            rawSpokenText = entity.rawSpokenText,
            detectedLanguage = Language.fromIso(entity.languageCode),
            items = items,
            grandTotal = entity.grandTotal,
            timestamp = entity.timestamp
        )
    }

    override suspend fun saveSalesLog(salesLog: SalesLog): VernAiResult<Unit> {
        return runCatching {
            val itemsJson = json.encodeToString(salesLog.items)
            val entity = SalesLogEntity(
                id = salesLog.id,
                rawSpokenText = salesLog.rawSpokenText,
                languageCode = salesLog.detectedLanguage.isoCode,
                itemsJson = itemsJson,
                grandTotal = salesLog.grandTotal,
                timestamp = salesLog.timestamp
            )
            database.salesLogDao().insertSalesLog(entity)
            VernAiResult.Success(Unit)
        }.getOrElse { VernAiResult.Error(it) }
    }

    override suspend fun deleteSalesLog(id: String): VernAiResult<Unit> {
        return runCatching {
            database.salesLogDao().deleteSalesLog(id)
            VernAiResult.Success(Unit)
        }.getOrElse { VernAiResult.Error(it) }
    }
}

class LocalComplaintRepository(
    private val database: VernAiDatabase
) : ComplaintRepository {
    override fun getComplaintsStream(): Flow<List<ComplaintDraft>> {
        return database.complaintDao().getAllComplaints().map { list ->
            list.map { entity ->
                ComplaintDraft(
                    id = entity.id,
                    subject = entity.subject,
                    department = entity.department,
                    recipientDesignation = entity.recipientDesignation,
                    vernacularBody = entity.vernacularBody,
                    englishTranslation = entity.englishTranslation,
                    targetLanguage = Language.fromIso(entity.languageCode),
                    senderName = entity.senderName,
                    location = entity.location,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    override suspend fun getComplaintById(id: String): ComplaintDraft? {
        val entity = database.complaintDao().getComplaintById(id) ?: return null
        return ComplaintDraft(
            id = entity.id,
            subject = entity.subject,
            department = entity.department,
            recipientDesignation = entity.recipientDesignation,
            vernacularBody = entity.vernacularBody,
            englishTranslation = entity.englishTranslation,
            targetLanguage = Language.fromIso(entity.languageCode),
            senderName = entity.senderName,
            location = entity.location,
            timestamp = entity.timestamp
        )
    }

    override suspend fun saveComplaint(complaint: ComplaintDraft): VernAiResult<Unit> {
        return runCatching {
            val entity = ComplaintEntity(
                id = complaint.id,
                subject = complaint.subject,
                department = complaint.department,
                recipientDesignation = complaint.recipientDesignation,
                vernacularBody = complaint.vernacularBody,
                englishTranslation = complaint.englishTranslation,
                languageCode = complaint.targetLanguage.isoCode,
                senderName = complaint.senderName,
                location = complaint.location,
                timestamp = complaint.timestamp
            )
            database.complaintDao().insertComplaint(entity)
            VernAiResult.Success(Unit)
        }.getOrElse { VernAiResult.Error(it) }
    }

    override suspend fun deleteComplaint(id: String): VernAiResult<Unit> {
        return runCatching {
            database.complaintDao().deleteComplaint(id)
            VernAiResult.Success(Unit)
        }.getOrElse { VernAiResult.Error(it) }
    }
}

class LocalDocumentRepository(
    private val database: VernAiDatabase
) : DocumentRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun getExplanationsStream(): Flow<List<ExplanationReport>> {
        return database.explanationDao().getAllExplanations().map { list ->
            list.map { entity ->
                val actions = runCatching { json.decodeFromString<List<String>>(entity.keyActionPointsJson) }.getOrDefault(emptyList())
                val deadlines = runCatching { json.decodeFromString<List<String>>(entity.legalDeadlinesJson) }.getOrDefault(emptyList())
                ExplanationReport(
                    id = entity.id,
                    sourceDocumentName = entity.sourceDocumentName,
                    extractedCharacterCount = entity.extractedCharacterCount,
                    summaryInVernacular = entity.summaryInVernacular,
                    keyActionPoints = actions,
                    legalDeadlines = deadlines,
                    targetLanguage = Language.fromIso(entity.languageCode),
                    timestamp = entity.timestamp
                )
            }
        }
    }

    override suspend fun getExplanationById(id: String): ExplanationReport? {
        val entity = database.explanationDao().getExplanationById(id) ?: return null
        val actions = runCatching { json.decodeFromString<List<String>>(entity.keyActionPointsJson) }.getOrDefault(emptyList())
        val deadlines = runCatching { json.decodeFromString<List<String>>(entity.legalDeadlinesJson) }.getOrDefault(emptyList())
        return ExplanationReport(
            id = entity.id,
            sourceDocumentName = entity.sourceDocumentName,
            extractedCharacterCount = entity.extractedCharacterCount,
            summaryInVernacular = entity.summaryInVernacular,
            keyActionPoints = actions,
            legalDeadlines = deadlines,
            targetLanguage = Language.fromIso(entity.languageCode),
            timestamp = entity.timestamp
        )
    }

    override suspend fun saveExplanation(report: ExplanationReport): VernAiResult<Unit> {
        return runCatching {
            val entity = ExplanationEntity(
                id = report.id,
                sourceDocumentName = report.sourceDocumentName,
                extractedCharacterCount = report.extractedCharacterCount,
                summaryInVernacular = report.summaryInVernacular,
                keyActionPointsJson = json.encodeToString(report.keyActionPoints),
                legalDeadlinesJson = json.encodeToString(report.legalDeadlines),
                languageCode = report.targetLanguage.isoCode,
                timestamp = report.timestamp
            )
            database.explanationDao().insertExplanation(entity)
            VernAiResult.Success(Unit)
        }.getOrElse { VernAiResult.Error(it) }
    }

    override suspend fun deleteExplanation(id: String): VernAiResult<Unit> {
        return runCatching {
            database.explanationDao().deleteExplanation(id)
            VernAiResult.Success(Unit)
        }.getOrElse { VernAiResult.Error(it) }
    }
}
