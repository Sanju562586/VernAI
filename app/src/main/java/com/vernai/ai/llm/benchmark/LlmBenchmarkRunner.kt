package com.vernai.ai.llm.benchmark

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import com.vernai.ai.llm.GenerationParameters
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Automated benchmark harness that empirically profiles on-device LLM performance,
 * resource utilization, and hardware thermal characteristics on Android devices.
 */
class LlmBenchmarkRunner(
    private val context: Context? = null,
    private val engine: LlmInferenceEngine,
    private val thermalMonitor: DeviceThermalMonitor = DeviceThermalMonitor(context),
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) {

    /**
     * Executes a standardized benchmark test against a target GGUF model.
     *
     * @param modelFile Local GGUF model file on storage.
     * @param backend Execution provider (CPU_NEON, GPU_VULKAN, or NPU_QUALCOMM_QNN).
     * @param threadCount Number of worker threads (typically 4 for Kryo Gold cores).
     * @param testPrompt Standardized prompt (defaults to Telugu administrative complaint request).
     * @param maxTokens Maximum decode tokens to sample.
     */
    suspend fun runBenchmark(
        modelFile: File,
        backend: ExecutionBackend = ExecutionBackend.CPU_NEON,
        threadCount: Int = 4,
        testPrompt: String = DEFAULT_BENCHMARK_PROMPT_TELUGU,
        maxTokens: Int = 64
    ): LlmBenchmarkResult = withContext(dispatchers.llmInference) {
        val startTelemetry = thermalMonitor.captureSnapshot()
        val activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        // 1. Measure Model Load Latency
        val loadStartTime = System.currentTimeMillis()
        if (!engine.isLoaded()) {
            engine.loadModel(modelFile = modelFile, contextLength = 1024, nThreads = threadCount)
        }
        val modelLoadTimeMs = System.currentTimeMillis() - loadStartTime

        // Approximate prompt tokens (Telugu script ~2 chars per token under Gemma 256k tokenizer)
        val estimatedPromptTokens = max(testPrompt.length / 2, 16)

        // 2. Measure Time To First Token (TTFT) and Generation Speed
        var firstTokenReceived = false
        var ttftMs = 0L
        var tokenCount = 0
        var generationStartTime = 0L
        val promptStartTime = System.nanoTime()

        val params = GenerationParameters(
            maxTokens = maxTokens,
            temperature = 0.3f,
            topP = 0.9f,
            repeatPenalty = 1.15f
        )

        val tokenFlow = engine.streamTokens(testPrompt, params)
        tokenFlow.collect { _ ->
            val now = System.nanoTime()
            if (!firstTokenReceived) {
                firstTokenReceived = true
                ttftMs = (now - promptStartTime) / 1_000_000L
                generationStartTime = now
            }
            tokenCount++
        }

        val totalTimeNanos = System.nanoTime() - promptStartTime
        val generationNanos = if (tokenCount > 1) System.nanoTime() - generationStartTime else 1_000_000L
        val generationSec = generationNanos / 1_000_000_000.0f
        val promptSec = max(ttftMs / 1000.0f, 0.001f)

        val tokensPerSec = if (generationSec > 0.01f && tokenCount > 1) {
            (tokenCount - 1) / generationSec
        } else {
            tokenCount / max(totalTimeNanos / 1_000_000_000.0f, 0.01f)
        }

        val promptProcessingSpeedTps = estimatedPromptTokens / promptSec

        // 3. Peak RAM Inspection via ActivityManager, Debug, and Runtime Heap
        val memInfo = Debug.MemoryInfo()
        try {
            Debug.getMemoryInfo(memInfo)
        } catch (_: Exception) {}
        val pssTotalMb = memInfo.totalPss / 1024L
        val nativeHeapMb = try { Debug.getNativeHeapAllocatedSize() / (1024 * 1024L) } catch (_: Exception) { 0L }
        val runtimeMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024L)
        val peakRamMb = max(pssTotalMb, max(nativeHeapMb, runtimeMb) + (modelFile.length() / (1024 * 1024L)))

        // 4. Capture End Telemetry
        val endTelemetry = thermalMonitor.captureSnapshot()

        LlmBenchmarkResult(
            modelName = modelFile.nameWithoutExtension,
            modelSizeBytes = modelFile.length(),
            backend = backend,
            threadCount = threadCount,
            contextLength = 1024,
            modelLoadTimeMs = modelLoadTimeMs,
            peakRamMb = peakRamMb,
            promptTokenCount = estimatedPromptTokens,
            promptProcessingTimeMs = ttftMs,
            timeToFirstTokenMs = ttftMs,
            generatedTokenCount = tokenCount,
            generationTimeMs = (generationNanos / 1_000_000L),
            tokensPerSecond = tokensPerSec,
            promptProcessingSpeedTps = promptProcessingSpeedTps,
            startTelemetry = startTelemetry,
            endTelemetry = endTelemetry
        )
    }

    companion object {
        const val DEFAULT_BENCHMARK_PROMPT_TELUGU =
            "గ్రామ పంచాయతీ పరిధిలో తాగునీటి సరఫరా మరియు వీధి దీపాలు లేకపోవడంపై సర్పంచ్ గారికి అధికారిక వినతిపత్రం తయారు చేయండి."
    }
}
