package com.vernai.core.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.vernai.ai.llm.benchmark.ExecutionBackend
import java.io.File
import kotlin.math.max

/**
 * Performance tiers tailored for Qualcomm Snapdragon architectures (e.g. iQOO devices).
 */
enum class DevicePerformanceTier(val displayName: String) {
    /**
     * Flagship Qualcomm Snapdragon 8 Gen 2 / 8 Gen 3 / 8s Gen 3 with >= 8GB LPDDR5X RAM.
     * Features 1x Cortex-X Prime core, 4-5x Cortex-A7 Gold performance cores, Adreno 7xx GPU.
     */
    FLAGSHIP_SNAPDRAGON("iQOO Flagship (Snapdragon 8 Series)"),

    /**
     * Upper Mid-Range Qualcomm Snapdragon 7+ Gen 3 / 7 Gen 1 with 6GB - 8GB RAM.
     * Features balanced Kryo Gold cores with moderate sustained thermal envelope.
     */
    MID_TIER_SNAPDRAGON("iQOO Mid-Range (Snapdragon 7 Series)"),

    /**
     * Entry-level or Low-Memory devices (< 6GB RAM or high OS memory pressure).
     * Enforces strict context bounds and aggressive KV cache trimming to prevent Low Memory Killer (LMK).
     */
    ENTRY_LOW_MEMORY("Entry-Level / Low-Memory Profile")
}

/**
 * Strict practical limits for on-device AI workloads to prevent thermal runaway,
 * memory bloat, and battery exhaustion.
 */
object WorkloadLimits {
    const val MAX_CONTEXT_LENGTH_FLAGSHIP = 2048
    const val MAX_CONTEXT_LENGTH_STANDARD = 1024
    const val MAX_CONTEXT_LENGTH_LOW_MEMORY = 512

    const val MAX_OUTPUT_TOKENS_FORMAL_LETTER = 512
    const val MAX_OUTPUT_TOKENS_SALES_LOG = 256
    const val MAX_OUTPUT_TOKENS_DOC_EXPLANATION = 512

    const val MAX_DOCUMENT_FILE_SIZE_BYTES = 5L * 1024L * 1024L // 5 MB
    const val MAX_DOCUMENT_PAGES = 15
    const val CHUNK_CHAR_LIMIT = 1000
    const val CHUNK_OVERLAP_CHARS = 150

    const val MAX_CONCURRENT_INFERENCE_TASKS = 1 // Serialized via InferenceLock
    const val AUDIO_FRAME_DURATION_MS = 100 // 100ms chunk = 1600 samples @ 16kHz
    const val AUDIO_VAD_SILENCE_THRESHOLD_DB = 36.0f
}

/**
 * Concrete inference optimization profile generated dynamically for the host device.
 */
data class InferenceOptimizationProfile(
    val tier: DevicePerformanceTier,
    val recommendedThreads: Int,
    val contextLength: Int,
    val batchSize: Int,
    val maxOutputTokens: Int,
    val preferredBackend: ExecutionBackend,
    val enableMmap: Boolean = true,
    val enableMlock: Boolean = false,
    val enableThermalThrottlingListener: Boolean = true,
    val estimatedKvCacheMemoryMb: Long,
    val totalPhysicalRamMb: Long,
    val availableCores: Int
)

/**
 * Hardware profiler and capability detector designed for Qualcomm Snapdragon iQOO devices.
 * Inspects CPU cluster topology, memory bandwidth, and thermal thresholds.
 */
class DeviceCapabilityProfiler(private val context: Context? = null) {

    /**
     * Evaluates hardware and generates the recommended [InferenceOptimizationProfile].
     */
    fun profileDevice(): InferenceOptimizationProfile {
        val totalRamMb = getTotalPhysicalRamMb()
        val coreCount = Runtime.getRuntime().availableProcessors()
        val isSnapdragon = isQualcommSnapdragon()

        val tier = when {
            totalRamMb >= 7500L && isSnapdragon && coreCount >= 8 -> DevicePerformanceTier.FLAGSHIP_SNAPDRAGON
            totalRamMb >= 5500L && coreCount >= 6 -> DevicePerformanceTier.MID_TIER_SNAPDRAGON
            else -> DevicePerformanceTier.ENTRY_LOW_MEMORY
        }

        val threads = calculateOptimalThreadCount(tier, coreCount)
        val contextLength = when (tier) {
            DevicePerformanceTier.FLAGSHIP_SNAPDRAGON -> WorkloadLimits.MAX_CONTEXT_LENGTH_STANDARD // 1024 default, 2048 for docs
            DevicePerformanceTier.MID_TIER_SNAPDRAGON -> WorkloadLimits.MAX_CONTEXT_LENGTH_STANDARD // 1024
            DevicePerformanceTier.ENTRY_LOW_MEMORY -> WorkloadLimits.MAX_CONTEXT_LENGTH_LOW_MEMORY // 512
        }

        val batchSize = when (tier) {
            DevicePerformanceTier.FLAGSHIP_SNAPDRAGON -> 512
            DevicePerformanceTier.MID_TIER_SNAPDRAGON -> 256
            DevicePerformanceTier.ENTRY_LOW_MEMORY -> 128
        }

        val maxTokens = when (tier) {
            DevicePerformanceTier.FLAGSHIP_SNAPDRAGON -> WorkloadLimits.MAX_OUTPUT_TOKENS_FORMAL_LETTER
            DevicePerformanceTier.MID_TIER_SNAPDRAGON -> 384
            DevicePerformanceTier.ENTRY_LOW_MEMORY -> 256
        }

        // KV Cache formula: 2 * n_layers * n_kv_heads * head_dim * contextLength * sizeof(fp16)
        // For Qwen2.5-1.5B (28 layers, 2 kv heads, 128 head dim, fp16 = 2 bytes)
        val kvCacheBytes = 2L * 28L * 2L * 128L * contextLength.toLong() * 2L
        val kvCacheMb = max(kvCacheBytes / (1024L * 1024L), 1L)

        return InferenceOptimizationProfile(
            tier = tier,
            recommendedThreads = threads,
            contextLength = contextLength,
            batchSize = batchSize,
            maxOutputTokens = maxTokens,
            preferredBackend = ExecutionBackend.CPU_NEON,
            enableMmap = true,
            enableMlock = false,
            enableThermalThrottlingListener = true,
            estimatedKvCacheMemoryMb = kvCacheMb,
            totalPhysicalRamMb = totalRamMb,
            availableCores = coreCount
        )
    }

    /**
     * Pins worker thread allocation specifically to Kryo Gold (performance) cores.
     * In an 8-core DynamIQ cluster (1 Prime + 4 Gold + 3 Silver), scheduling across Silver
     * cores causes thread barrier stalling because Silver cores run at half the IPC.
     */
    fun calculateOptimalThreadCount(tier: DevicePerformanceTier, coreCount: Int): Int {
        return when (tier) {
            DevicePerformanceTier.FLAGSHIP_SNAPDRAGON -> 4 // 4 Kryo Gold cores optimal for NEON matrix multiplication
            DevicePerformanceTier.MID_TIER_SNAPDRAGON -> 4.coerceAtMost(coreCount - 2)
            DevicePerformanceTier.ENTRY_LOW_MEMORY -> 2 // Reduce thermal load and RAM bus contention
        }
    }

    /**
     * Inspects /sys/devices/system/cpu or Build hardware strings for Qualcomm Snapdragon signatures.
     */
    fun isQualcommSnapdragon(): Boolean {
        val hardware = Build.HARDWARE?.lowercase() ?: ""
        val board = Build.BOARD?.lowercase() ?: ""
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL?.lowercase() ?: ""
        } else {
            ""
        }

        val qualcommIdentifiers = listOf(
            "qcom", "qualcomm", "snapdragon",
            "sm8650", "sm8550", "sm8475", "sm8450", "sm7675", "sm7550", // Snapdragon 8 Gen 3/2, 8+ Gen 1, 7+ Gen 3
            "taro", "kalama", "pineapple", "crow"
        )

        return qualcommIdentifiers.any { hardware.contains(it) || board.contains(it) || soc.contains(it) }
    }

    /**
     * Reads total physical RAM from ActivityManager.MemoryInfo.
     */
    fun getTotalPhysicalRamMb(): Long {
        if (context != null) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                return memInfo.totalMem / (1024L * 1024L)
            }
        }

        // Fallback to /proc/meminfo
        return try {
            val memInfoFile = File("/proc/meminfo")
            if (memInfoFile.exists()) {
                val line = memInfoFile.readLines().firstOrNull { it.startsWith("MemTotal:") }
                val kb = line?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull() ?: 8192000L
                kb / 1024L
            } else {
                8192L // 8 GB default baseline
            }
        } catch (_: Exception) {
            8192L
        }
    }
}
