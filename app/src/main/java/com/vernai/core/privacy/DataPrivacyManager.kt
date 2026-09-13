package com.vernai.core.privacy

import android.content.Context
import androidx.room.RoomDatabase
import com.vernai.core.common.result.VernAiResult
import java.io.File

data class StorageAuditSummary(
    val databaseSizeBytes: Long,
    val cacheSizeBytes: Long,
    val modelsSizeBytes: Long,
    val totalFootprintBytes: Long
)

/**
 * Provides user sovereignty over local offline data and nuclear deletion.
 */
class DataPrivacyManager(
    private val context: Context,
    private val roomDatabase: RoomDatabase? = null
) {

    fun calculateStorageFootprint(): StorageAuditSummary {
        val dbFile = context.getDatabasePath("vernai.db")
        val dbDir = dbFile?.parentFile
        val dbSize = dbDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        val cacheSize = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val modelsDir = File(context.filesDir, "models")
        val modelsSize = if (modelsDir.exists()) modelsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L

        return StorageAuditSummary(
            databaseSizeBytes = dbSize,
            cacheSizeBytes = cacheSize,
            modelsSizeBytes = modelsSize,
            totalFootprintBytes = dbSize + cacheSize + modelsSize
        )
    }

    suspend fun wipeAllUserData(includeModels: Boolean = false): VernAiResult<Unit> {
        return runCatching {
            // 1. Wipe Room DB tables
            roomDatabase?.clearAllTables()

            // 2. Clear export cache files
            val exportCache = File(context.cacheDir, "exports")
            if (exportCache.exists()) exportCache.deleteRecursively()
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }

            // 3. Delete AI models if requested
            if (includeModels) {
                val modelsDir = File(context.filesDir, "models")
                if (modelsDir.exists()) modelsDir.deleteRecursively()
            }

            VernAiResult.Success(Unit)
        }.getOrElse { e ->
            VernAiResult.Error(e, "సమాచార తొలగింపు విఫలమైంది (Failed to wipe data: ${e.localizedMessage})")
        }
    }
}
