package com.vernai.domain.repository

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.SalesLog
import kotlinx.coroutines.flow.Flow

interface SalesLogRepository {
    fun getSalesLogsStream(): Flow<List<SalesLog>>
    suspend fun getSalesLogById(id: String): SalesLog?
    suspend fun saveSalesLog(salesLog: SalesLog): VernAiResult<Unit>
    suspend fun deleteSalesLog(id: String): VernAiResult<Unit>
}

interface ComplaintRepository {
    fun getComplaintsStream(): Flow<List<ComplaintDraft>>
    suspend fun getComplaintById(id: String): ComplaintDraft?
    suspend fun saveComplaint(complaint: ComplaintDraft): VernAiResult<Unit>
    suspend fun deleteComplaint(id: String): VernAiResult<Unit>
}

interface DocumentRepository {
    fun getExplanationsStream(): Flow<List<ExplanationReport>>
    suspend fun getExplanationById(id: String): ExplanationReport?
    suspend fun saveExplanation(report: ExplanationReport): VernAiResult<Unit>
    suspend fun deleteExplanation(id: String): VernAiResult<Unit>
}
