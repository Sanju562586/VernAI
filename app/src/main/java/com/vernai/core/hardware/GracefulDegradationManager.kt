package com.vernai.core.hardware

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.vernai.core.common.memory.MemoryPressureLevel
import com.vernai.core.common.memory.MemoryPressureMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real-time operational policy adjusted dynamically under thermal and memory pressure.
 */
data class OperationalPolicy(
    val tier: DevicePerformanceTier,
    val activeThreads: Int,
    val activeContextLength: Int,
    val activeMaxOutputTokens: Int,
    val interTokenDelayMs: Long = 0L,
    val isThrottlingActive: Boolean = false,
    val isLlmExecutionPermitted: Boolean = true,
    val degradationReason: String? = null
)

/**
 * Manages graceful degradation across thermal states and memory pressure conditions.
 * Prevents device overheating, Low Memory Killer (LMK) crashes, and battery exhaustion.
 */
class GracefulDegradationManager(
    private val context: Context? = null,
    private val baseProfile: InferenceOptimizationProfile,
    private val memoryMonitor: MemoryPressureMonitor? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _currentPolicy = MutableStateFlow(
        OperationalPolicy(
            tier = baseProfile.tier,
            activeThreads = baseProfile.recommendedThreads,
            activeContextLength = baseProfile.contextLength,
            activeMaxOutputTokens = baseProfile.maxOutputTokens,
            interTokenDelayMs = 0L,
            isThrottlingActive = false,
            isLlmExecutionPermitted = true
        )
    )
    val currentPolicy: StateFlow<OperationalPolicy> = _currentPolicy.asStateFlow()

    private var currentThermalStatus: Int = PowerManager.THERMAL_STATUS_NONE
    private var currentMemoryPressure: MemoryPressureLevel = MemoryPressureLevel.NORMAL

    init {
        setupThermalListener()
        setupMemoryListener()
    }

    private fun setupThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context != null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.let { pm ->
                currentThermalStatus = pm.currentThermalStatus
                pm.addThermalStatusListener { status ->
                    currentThermalStatus = status
                    recalculatePolicy()
                }
            }
        }
    }

    private fun setupMemoryListener() {
        memoryMonitor?.let { monitor ->
            scope.launch {
                monitor.pressureLevel.collect { pressure ->
                    currentMemoryPressure = pressure
                    recalculatePolicy()
                }
            }
        }
    }

    /**
     * Manually updates thermal or memory conditions (used for automated testing and simulation).
     */
    fun updateConditions(thermalStatus: Int, memoryPressure: MemoryPressureLevel) {
        currentThermalStatus = thermalStatus
        currentMemoryPressure = memoryPressure
        recalculatePolicy()
    }

    /**
     * Recalculates operational constraints based on current device telemetry.
     */
    fun recalculatePolicy() {
        var threads = baseProfile.recommendedThreads
        var contextLen = baseProfile.contextLength
        var maxTokens = baseProfile.maxOutputTokens
        var delayMs = 0L
        var isThrottling = false
        var permitLlm = true
        val reasons = mutableListOf<String>()

        // 1. Evaluate Memory Pressure
        when (currentMemoryPressure) {
            MemoryPressureLevel.NORMAL -> {
                // Keep base configuration
            }
            MemoryPressureLevel.MODERATE -> {
                contextLen = contextLen.coerceAtMost(WorkloadLimits.MAX_CONTEXT_LENGTH_LOW_MEMORY) // 512
                maxTokens = maxTokens.coerceAtMost(256)
                isThrottling = true
                reasons.add("Memory pressure MODERATE: Clamping context length to $contextLen")
            }
            MemoryPressureLevel.CRITICAL -> {
                permitLlm = false
                isThrottling = true
                reasons.add("Memory pressure CRITICAL: Local LLM halted; engaging deterministic rule-based generator to prevent LMK crash")
            }
        }

        // 2. Evaluate Thermal Status (Qualcomm Snapdragon Kryo cooling logic)
        when (currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT -> {
                // Nominal operating state
            }
            PowerManager.THERMAL_STATUS_MODERATE -> {
                // Drop to 2 threads and introduce pacing to cool down chassis
                threads = threads.coerceAtMost(2)
                delayMs = 15L // 15ms decode delay sheds CPU heat while maintaining ~20 tokens/sec
                isThrottling = true
                reasons.add("Thermal status MODERATE: Threads reduced to $threads, pacing decode by 15ms")
            }
            PowerManager.THERMAL_STATUS_SEVERE -> {
                threads = 1
                delayMs = 30L
                maxTokens = maxTokens.coerceAtMost(256)
                isThrottling = true
                reasons.add("Thermal status SEVERE: Pinned to single core with 30ms pacing")
            }
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> {
                permitLlm = false
                isThrottling = true
                reasons.add("Thermal status CRITICAL: Halting active compute to protect hardware battery from swelling")
            }
        }

        val finalReason = if (reasons.isNotEmpty()) reasons.joinToString("; ") else null

        if (isThrottling) {
            Log.w("VernAI-Degradation", "Active policy updated: $finalReason")
        }

        _currentPolicy.value = OperationalPolicy(
            tier = baseProfile.tier,
            activeThreads = threads,
            activeContextLength = contextLen,
            activeMaxOutputTokens = maxTokens,
            interTokenDelayMs = delayMs,
            isThrottlingActive = isThrottling,
            isLlmExecutionPermitted = permitLlm,
            degradationReason = finalReason
        )
    }
}
