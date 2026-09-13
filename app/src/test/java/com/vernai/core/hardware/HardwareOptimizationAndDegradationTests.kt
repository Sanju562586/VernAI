package com.vernai.core.hardware

import android.os.PowerManager
import com.vernai.ai.llm.benchmark.ExecutionBackend
import com.vernai.core.common.memory.MemoryPressureLevel
import com.vernai.core.common.memory.MemoryPressureMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareOptimizationAndDegradationTests {

    private class MockMemoryPressureMonitor(
        initialLevel: MemoryPressureLevel = MemoryPressureLevel.NORMAL
    ) : MemoryPressureMonitor {
        private val _pressure = MutableStateFlow(initialLevel)
        override val pressureLevel: StateFlow<MemoryPressureLevel> = _pressure
        override fun getAvailableMemoryMb(): Long = 3500L
        override fun isSafeForLlmInference(requiredMemoryMb: Long): Boolean =
            _pressure.value != MemoryPressureLevel.CRITICAL

        fun setLevel(level: MemoryPressureLevel) {
            _pressure.value = level
        }
    }

    @Test
    fun deviceCapabilityProfiler_computesOptimalKryoGoldThreadCount() {
        val profiler = DeviceCapabilityProfiler(context = null)

        // Flagship Snapdragon with 8 cores -> Pin to 4 Kryo Gold cores
        val flagshipThreads = profiler.calculateOptimalThreadCount(
            tier = DevicePerformanceTier.FLAGSHIP_SNAPDRAGON,
            coreCount = 8
        )
        assertEquals("Flagship Snapdragon must pin to 4 Kryo Gold cores", 4, flagshipThreads)

        // Mid-tier with 8 cores -> Pin to 4 cores
        val midThreads = profiler.calculateOptimalThreadCount(
            tier = DevicePerformanceTier.MID_TIER_SNAPDRAGON,
            coreCount = 8
        )
        assertEquals("Mid-tier Snapdragon must use 4 cores", 4, midThreads)

        // Low memory / budget device -> Scale to 2 cores to avoid thermal/bus congestion
        val lowMemoryThreads = profiler.calculateOptimalThreadCount(
            tier = DevicePerformanceTier.ENTRY_LOW_MEMORY,
            coreCount = 8
        )
        assertEquals("Low-memory tier must scale to 2 cores", 2, lowMemoryThreads)
    }

    @Test
    fun deviceCapabilityProfiler_enforcesPracticalWorkloadLimits() {
        val profiler = DeviceCapabilityProfiler(context = null)
        val profile = profiler.profileDevice()

        assertNotNull(profile)
        assertTrue("Total RAM should be greater than 0", profile.totalPhysicalRamMb > 0)
        assertTrue("Context length must not exceed practical upper bound", profile.contextLength <= WorkloadLimits.MAX_CONTEXT_LENGTH_FLAGSHIP)
        assertTrue("Batch size must be either 128, 256, or 512", profile.batchSize in listOf(128, 256, 512))
        assertTrue("KV Cache memory estimate must be reasonable (< 200 MB)", profile.estimatedKvCacheMemoryMb < 200)
        assertEquals(ExecutionBackend.CPU_NEON, profile.preferredBackend)
        assertTrue(profile.enableMmap)
        assertFalse("Mlock must remain false on Android", profile.enableMlock)
    }

    @Test
    fun gracefulDegradation_normalOperatingState_preservesFullThroughput() {
        val baseProfile = InferenceOptimizationProfile(
            tier = DevicePerformanceTier.FLAGSHIP_SNAPDRAGON,
            recommendedThreads = 4,
            contextLength = 1024,
            batchSize = 512,
            maxOutputTokens = 512,
            preferredBackend = ExecutionBackend.CPU_NEON,
            estimatedKvCacheMemoryMb = 28L,
            totalPhysicalRamMb = 12288L,
            availableCores = 8
        )

        val memoryMonitor = MockMemoryPressureMonitor(MemoryPressureLevel.NORMAL)
        val degradationManager = GracefulDegradationManager(
            context = null,
            baseProfile = baseProfile,
            memoryMonitor = memoryMonitor
        )

        val policy = degradationManager.currentPolicy.value
        assertEquals(4, policy.activeThreads)
        assertEquals(1024, policy.activeContextLength)
        assertEquals(512, policy.activeMaxOutputTokens)
        assertEquals(0L, policy.interTokenDelayMs)
        assertFalse(policy.isThrottlingActive)
        assertTrue(policy.isLlmExecutionPermitted)
    }

    @Test
    fun gracefulDegradation_thermalModerate_scalesDownThreadsAndPacesDecode() {
        val baseProfile = InferenceOptimizationProfile(
            tier = DevicePerformanceTier.FLAGSHIP_SNAPDRAGON,
            recommendedThreads = 4,
            contextLength = 1024,
            batchSize = 512,
            maxOutputTokens = 512,
            preferredBackend = ExecutionBackend.CPU_NEON,
            estimatedKvCacheMemoryMb = 28L,
            totalPhysicalRamMb = 12288L,
            availableCores = 8
        )

        val memoryMonitor = MockMemoryPressureMonitor(MemoryPressureLevel.NORMAL)
        val degradationManager = GracefulDegradationManager(
            context = null,
            baseProfile = baseProfile,
            memoryMonitor = memoryMonitor
        )

        // Simulate device warming up to THERMAL_STATUS_MODERATE
        degradationManager.updateConditions(
            thermalStatus = PowerManager.THERMAL_STATUS_MODERATE,
            memoryPressure = MemoryPressureLevel.NORMAL
        )

        val policy = degradationManager.currentPolicy.value
        assertTrue("Throttling should be flagged active", policy.isThrottlingActive)
        assertEquals("Threads must scale down from 4 to 2 to cool Snapdragon chassis", 2, policy.activeThreads)
        assertEquals("15ms inter-token delay should be applied to shed thermal load", 15L, policy.interTokenDelayMs)
        assertTrue("LLM execution remains permitted at throttled speed", policy.isLlmExecutionPermitted)
        assertTrue(policy.degradationReason?.contains("Thermal status MODERATE") == true)
    }

    @Test
    fun gracefulDegradation_thermalSevere_scalesToSingleCoreWithHighPacing() {
        val baseProfile = InferenceOptimizationProfile(
            tier = DevicePerformanceTier.FLAGSHIP_SNAPDRAGON,
            recommendedThreads = 4,
            contextLength = 1024,
            batchSize = 512,
            maxOutputTokens = 512,
            preferredBackend = ExecutionBackend.CPU_NEON,
            estimatedKvCacheMemoryMb = 28L,
            totalPhysicalRamMb = 12288L,
            availableCores = 8
        )

        val memoryMonitor = MockMemoryPressureMonitor(MemoryPressureLevel.NORMAL)
        val degradationManager = GracefulDegradationManager(
            context = null,
            baseProfile = baseProfile,
            memoryMonitor = memoryMonitor
        )

        // Simulate extreme thermal stress
        degradationManager.updateConditions(
            thermalStatus = PowerManager.THERMAL_STATUS_SEVERE,
            memoryPressure = MemoryPressureLevel.NORMAL
        )

        val policy = degradationManager.currentPolicy.value
        assertEquals("Severe thermal stress drops thread allocation to 1", 1, policy.activeThreads)
        assertEquals("30ms inter-token delay applied", 30L, policy.interTokenDelayMs)
        assertEquals("Max output tokens capped to 256", 256, policy.activeMaxOutputTokens)
        assertTrue(policy.isThrottlingActive)
    }

    @Test
    fun gracefulDegradation_memoryModerate_clampsContextLength() {
        val baseProfile = InferenceOptimizationProfile(
            tier = DevicePerformanceTier.FLAGSHIP_SNAPDRAGON,
            recommendedThreads = 4,
            contextLength = 2048,
            batchSize = 512,
            maxOutputTokens = 512,
            preferredBackend = ExecutionBackend.CPU_NEON,
            estimatedKvCacheMemoryMb = 56L,
            totalPhysicalRamMb = 12288L,
            availableCores = 8
        )

        val memoryMonitor = MockMemoryPressureMonitor(MemoryPressureLevel.NORMAL)
        val degradationManager = GracefulDegradationManager(
            context = null,
            baseProfile = baseProfile,
            memoryMonitor = memoryMonitor
        )

        // Simulate MODERATE memory pressure (e.g. background apps consuming RAM)
        degradationManager.updateConditions(
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            memoryPressure = MemoryPressureLevel.MODERATE
        )

        val policy = degradationManager.currentPolicy.value
        assertTrue("Throttling active under memory pressure", policy.isThrottlingActive)
        assertEquals("Context length clamped to 512 tokens to conserve memory", 512, policy.activeContextLength)
        assertEquals("Max tokens clamped to 256", 256, policy.activeMaxOutputTokens)
        assertTrue(policy.isLlmExecutionPermitted)
    }

    @Test
    fun gracefulDegradation_memoryCritical_disablesLlmAndTriggersDeterministicFallback() {
        val baseProfile = InferenceOptimizationProfile(
            tier = DevicePerformanceTier.FLAGSHIP_SNAPDRAGON,
            recommendedThreads = 4,
            contextLength = 1024,
            batchSize = 512,
            maxOutputTokens = 512,
            preferredBackend = ExecutionBackend.CPU_NEON,
            estimatedKvCacheMemoryMb = 28L,
            totalPhysicalRamMb = 12288L,
            availableCores = 8
        )

        val memoryMonitor = MockMemoryPressureMonitor(MemoryPressureLevel.NORMAL)
        val degradationManager = GracefulDegradationManager(
            context = null,
            baseProfile = baseProfile,
            memoryMonitor = memoryMonitor
        )

        // Simulate CRITICAL memory pressure (LMK imminent)
        degradationManager.updateConditions(
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            memoryPressure = MemoryPressureLevel.CRITICAL
        )

        val policy = degradationManager.currentPolicy.value
        assertFalse("LLM execution must be prohibited under CRITICAL memory pressure", policy.isLlmExecutionPermitted)
        assertTrue(policy.isThrottlingActive)
        assertTrue("Reason specifies deterministic fallback", policy.degradationReason?.contains("deterministic rule-based generator") == true)
    }
}
