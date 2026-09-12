package com.vernai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vernai.core.database.entity.ComplaintEntity
import com.vernai.core.database.entity.ExplanationEntity
import com.vernai.core.database.entity.SalesLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesLogDao {
    @Query("SELECT * FROM sales_logs ORDER BY timestamp DESC")
    fun getAllSalesLogs(): Flow<List<SalesLogEntity>>

    @Query("SELECT * FROM sales_logs WHERE id = :id")
    suspend fun getSalesLogById(id: String): SalesLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesLog(log: SalesLogEntity)

    @Query("DELETE FROM sales_logs WHERE id = :id")
    suspend fun deleteSalesLog(id: String)
}

@Dao
interface ComplaintDao {
    @Query("SELECT * FROM complaints ORDER BY timestamp DESC")
    fun getAllComplaints(): Flow<List<ComplaintEntity>>

    @Query("SELECT * FROM complaints WHERE id = :id")
    suspend fun getComplaintById(id: String): ComplaintEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaint(complaint: ComplaintEntity)

    @Query("DELETE FROM complaints WHERE id = :id")
    suspend fun deleteComplaint(id: String)
}

@Dao
interface ExplanationDao {
    @Query("SELECT * FROM document_explanations ORDER BY timestamp DESC")
    fun getAllExplanations(): Flow<List<ExplanationEntity>>

    @Query("SELECT * FROM document_explanations WHERE id = :id")
    suspend fun getExplanationById(id: String): ExplanationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExplanation(entity: ExplanationEntity)

    @Query("DELETE FROM document_explanations WHERE id = :id")
    suspend fun deleteExplanation(id: String)
}
