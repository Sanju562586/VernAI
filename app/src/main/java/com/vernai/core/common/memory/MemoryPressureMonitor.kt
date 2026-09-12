package com.vernai.core.common.memory

import kotlinx.coroutines.flow.StateFlow

/**
 * System memory pressure level reported by Android ComponentCallbacks2.
 */
enum class MemoryPressureLevel {
    NORMAL,
    MODERATE,    // TRIM_MEMORY_RUNNING_MODERATE / TRIM_MEMORY_BACKGROUND
    CRITICAL     // TRIM_MEMORY_RUNNING_CRITICAL / TRIM_MEMORY_COMPLETE
}

/**
 * Monitors device memory pressure and advises inference engines
 * to evict context cache or unload weights before LMK (Low Memory Killer) triggers.
 */
interface MemoryPressureMonitor {
    val pressureLevel: StateFlow<MemoryPressureLevel>
    fun getAvailableMemoryMb(): Long
    fun isSafeForLlmInference(requiredMemoryMb: Long = 2400): Boolean
}
