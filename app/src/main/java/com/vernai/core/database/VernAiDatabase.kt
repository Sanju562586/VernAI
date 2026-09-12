package com.vernai.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.vernai.core.database.dao.ComplaintDao
import com.vernai.core.database.dao.ExplanationDao
import com.vernai.core.database.dao.SalesLogDao
import com.vernai.core.database.entity.ComplaintEntity
import com.vernai.core.database.entity.ExplanationEntity
import com.vernai.core.database.entity.SalesLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class VernAiTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        return json.encodeToString(list ?: emptyList())
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        return if (data.isNullOrBlank()) emptyList() else json.decodeFromString(data)
    }
}

class InMemorySalesLogDao : SalesLogDao {
    private val _logs = MutableStateFlow<Map<String, SalesLogEntity>>(emptyMap())

    override fun getAllSalesLogs(): Flow<List<SalesLogEntity>> {
        return _logs.asStateFlow().map { it.values.sortedByDescending { log -> log.timestamp } }
    }

    override suspend fun getSalesLogById(id: String): SalesLogEntity? = _logs.value[id]

    override suspend fun insertSalesLog(log: SalesLogEntity) {
        _logs.value = _logs.value + (log.id to log)
    }

    override suspend fun deleteSalesLog(id: String) {
        _logs.value = _logs.value - id
    }
}

class InMemoryComplaintDao : ComplaintDao {
    private val _complaints = MutableStateFlow<Map<String, ComplaintEntity>>(emptyMap())

    override fun getAllComplaints(): Flow<List<ComplaintEntity>> {
        return _complaints.asStateFlow().map { it.values.sortedByDescending { c -> c.timestamp } }
    }

    override suspend fun getComplaintById(id: String): ComplaintEntity? = _complaints.value[id]

    override suspend fun insertComplaint(complaint: ComplaintEntity) {
        _complaints.value = _complaints.value + (complaint.id to complaint)
    }

    override suspend fun deleteComplaint(id: String) {
        _complaints.value = _complaints.value - id
    }
}

class InMemoryExplanationDao : ExplanationDao {
    private val _explanations = MutableStateFlow<Map<String, ExplanationEntity>>(emptyMap())

    override fun getAllExplanations(): Flow<List<ExplanationEntity>> {
        return _explanations.asStateFlow().map { it.values.sortedByDescending { e -> e.timestamp } }
    }

    override suspend fun getExplanationById(id: String): ExplanationEntity? = _explanations.value[id]

    override suspend fun insertExplanation(entity: ExplanationEntity) {
        _explanations.value = _explanations.value + (entity.id to entity)
    }

    override suspend fun deleteExplanation(id: String) {
        _explanations.value = _explanations.value - id
    }
}

class FallbackVernAiDatabase(context: Context) : VernAiDatabase() {
    private val salesDao = InMemorySalesLogDao()
    private val complaintDaoInstance = InMemoryComplaintDao()
    private val explanationDaoInstance = InMemoryExplanationDao()

    override fun salesLogDao(): SalesLogDao = salesDao
    override fun complaintDao(): ComplaintDao = complaintDaoInstance
    override fun explanationDao(): ExplanationDao = explanationDaoInstance

    override fun createInvalidationTracker(): InvalidationTracker {
        return InvalidationTracker(this, "sales_logs", "complaints", "document_explanations")
    }

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(config.context)
                .name(config.name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
    }

    override fun clearAllTables() {}
}

@Database(
    entities = [
        SalesLogEntity::class,
        ComplaintEntity::class,
        ExplanationEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(VernAiTypeConverters::class)
abstract class VernAiDatabase : RoomDatabase() {
    abstract fun salesLogDao(): SalesLogDao
    abstract fun complaintDao(): ComplaintDao
    abstract fun explanationDao(): ExplanationDao

    companion object {
        const val DATABASE_NAME = "vernai_offline.db"

        fun create(context: Context): VernAiDatabase {
            return runCatching {
                Room.databaseBuilder(
                    context.applicationContext,
                    VernAiDatabase::class.java,
                    DATABASE_NAME
                ).fallbackToDestructiveMigration().build()
            }.getOrElse {
                FallbackVernAiDatabase(context)
            }
        }
    }
}
