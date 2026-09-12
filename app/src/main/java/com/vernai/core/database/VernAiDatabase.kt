package com.vernai.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.vernai.core.database.dao.ComplaintDao
import com.vernai.core.database.dao.ExplanationDao
import com.vernai.core.database.dao.SalesLogDao
import com.vernai.core.database.entity.ComplaintEntity
import com.vernai.core.database.entity.ExplanationEntity
import com.vernai.core.database.entity.SalesLogEntity
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

@Database(
    entities = [
        SalesLogEntity::class,
        ComplaintEntity::class,
        ExplanationEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(VernAiTypeConverters::class)
abstract class VernAiDatabase : RoomDatabase() {
    abstract fun salesLogDao(): SalesLogDao
    abstract fun complaintDao(): ComplaintDao
    abstract fun explanationDao(): ExplanationDao

    companion object {
        const val DATABASE_NAME = "vernai_offline.db"
    }
}
