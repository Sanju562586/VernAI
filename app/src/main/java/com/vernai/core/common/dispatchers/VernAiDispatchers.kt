package com.vernai.core.common.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Interface defining dedicated dispatchers across VernAI.
 * Isolates compute-heavy native AI inference (llama.cpp, Sherpa-ONNX)
 * to dedicated single-thread or pinned thread pools to prevent starving UI/IO.
 */
interface VernAiDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    /**
     * Dedicated dispatcher for local LLM inference.
     * Pinned to avoid thread migration and memory cache invalidation.
     */
    val llmInference: CoroutineDispatcher
    /**
     * Dedicated dispatcher for audio capture & ASR processing.
     */
    val asrInference: CoroutineDispatcher
}

class DefaultVernAiDispatchers : VernAiDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default

    // Dedicated single-thread executors for NDK inference pipelines to preserve CPU cache affinity
    override val llmInference: CoroutineDispatcher = 
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "vernai-llm-thread").apply { priority = Thread.MAX_PRIORITY }
        }.asCoroutineDispatcher()

    override val asrInference: CoroutineDispatcher = 
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "vernai-asr-thread").apply { priority = Thread.NORM_PRIORITY + 1 }
        }.asCoroutineDispatcher()
}
