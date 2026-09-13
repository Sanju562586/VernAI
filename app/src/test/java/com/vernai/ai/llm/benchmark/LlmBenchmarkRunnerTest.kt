package com.vernai.ai.llm.benchmark

import com.vernai.ai.llm.GenerationParameters
import com.vernai.ai.llm.LlmEngineState
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.result.VernAiResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LlmBenchmarkRunnerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : VernAiDispatchers {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val asrInference: CoroutineDispatcher = testDispatcher
        override val llmInference: CoroutineDispatcher = testDispatcher
    }

    private lateinit var mockModelFile: File

    @Before
    fun setup() {
        mockModelFile = tempFolder.newFile("gemma-3-1b-it-Q4_K_M.gguf")
        mockModelFile.writeBytes(ByteArray(1024 * 1024 * 10)) // 10 MB mock
    }

    private class FakeLlmEngine(
        val tokensToEmit: List<String> = listOf("గౌరవనీయులైన ", "గ్రామ ", "సర్పంచ్ ", "గారికి, ", "నమస్కారం.")
    ) : LlmInferenceEngine {
        private val _state = MutableStateFlow<LlmEngineState>(LlmEngineState.Unloaded)
        override val state: StateFlow<LlmEngineState> = _state
        private var loaded = false

        override suspend fun loadModel(
            modelFile: File,
            contextLength: Int,
            nThreads: Int,
            backend: ExecutionBackend
        ): VernAiResult<Unit> {
            loaded = true
            _state.value = LlmEngineState.Ready
            return VernAiResult.Success(Unit)
        }

        override fun streamTokens(prompt: String, params: GenerationParameters): Flow<String> = flow {
            for (t in tokensToEmit) {
                emit(t)
            }
        }

        override suspend fun generateCompleteText(
            prompt: String,
            params: GenerationParameters
        ): VernAiResult<String> {
            return VernAiResult.Success(tokensToEmit.joinToString(""))
        }

        override suspend fun unloadModel() {
            loaded = false
            _state.value = LlmEngineState.Unloaded
        }

        override fun isLoaded(): Boolean = loaded
        override fun close() { loaded = false }
    }

    @Test
    fun testBenchmarkExecutionCollectsAllMetrics() = runTest(testDispatcher) {
        val fakeEngine = FakeLlmEngine()

        val runner = LlmBenchmarkRunner(
            context = null,
            engine = fakeEngine,
            dispatchers = testDispatchers
        )

        val result = runner.runBenchmark(
            modelFile = mockModelFile,
            backend = ExecutionBackend.CPU_NEON,
            threadCount = 4,
            testPrompt = "పంచాయతీ వీధి దీపాలు సమస్య పరిష్కరించండి.",
            maxTokens = 32
        )

        assertNotNull(result)
        assertEquals("gemma-3-1b-it-Q4_K_M", result.modelName)
        assertEquals(ExecutionBackend.CPU_NEON, result.backend)
        assertEquals(4, result.threadCount)
        assertTrue(result.modelLoadTimeMs >= 0)
        assertTrue(result.timeToFirstTokenMs >= 0)
        assertTrue(result.tokensPerSecond > 0f)
        assertTrue(result.promptProcessingSpeedTps > 0f)
        assertEquals(5, result.generatedTokenCount)
        assertTrue(result.peakRamMb > 0)
    }

    @Test
    fun testHardwareBackendSupportDiagnostic() {
        val backendInfo = ExecutionBackend.detectBackendSupport(context = null)

        assertTrue(backendInfo.containsKey(ExecutionBackend.CPU_NEON))
        assertTrue(backendInfo.containsKey(ExecutionBackend.GPU_VULKAN))
        assertTrue(backendInfo.containsKey(ExecutionBackend.NPU_QUALCOMM_QNN))

        // CPU NEON is verified production-ready
        assertTrue(ExecutionBackend.CPU_NEON.isProductionReady)

        // Qualcomm NPU is NOT natively compatible with GGUF format
        val npuInfo = backendInfo[ExecutionBackend.NPU_QUALCOMM_QNN]!!
        assertFalse(npuInfo.isSupported)
        assertTrue(npuInfo.details.contains("QNN serialized graph"))
    }

    @Test
    fun testMarkdownReportGeneration() {
        val telemetry = DeviceThermalMonitor.TelemetrySnapshot(
            batteryTempCelsius = 31.5f,
            batteryLevelPercent = 85,
            batteryVoltageMv = 4150,
            thermalStatus = "NONE (Nominal)",
            isThermalThrottling = false,
            cpuFrequencySummary = "Silver: 1800MHz / Gold: 2800MHz / Prime: 3200MHz",
            memoryFreeMb = 3200L
        )

        val benchmarkResult = LlmBenchmarkResult(
            modelName = "gemma-3-1b-it-Q4_K_M",
            modelSizeBytes = 848L * 1024L * 1024L,
            backend = ExecutionBackend.CPU_NEON,
            threadCount = 4,
            contextLength = 1024,
            modelLoadTimeMs = 420L,
            peakRamMb = 1420L,
            promptTokenCount = 48,
            promptProcessingTimeMs = 120L,
            timeToFirstTokenMs = 135L,
            generatedTokenCount = 128,
            generationTimeMs = 5800L,
            tokensPerSecond = 22.1f,
            promptProcessingSpeedTps = 400.0f,
            startTelemetry = telemetry,
            endTelemetry = telemetry.copy(batteryTempCelsius = 32.8f, batteryLevelPercent = 84)
        )

        val report = benchmarkResult.toMarkdownReport(deviceInfo = "iQOO 12 (Snapdragon 8 Gen 3)")

        assertNotNull(report)
        assertTrue(report.contains("VernAI Local LLM Benchmark Report"))
        assertTrue(report.contains("gemma-3-1b-it-Q4_K_M"))
        assertTrue(report.contains("CPU (ARM NEON SIMD)"))
        assertTrue(report.contains("22.1 tokens/sec"))
        assertTrue(report.contains("1420 MB"))
        assertTrue(report.contains("+1.3°C"))
        assertTrue(report.contains("iQOO 12 (Snapdragon 8 Gen 3)"))
    }
}
