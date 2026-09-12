package com.vernai.ai.llm.llama

import java.io.File

/**
 * Native execution parameters for loading and initializing a GGUF model via llama.cpp.
 */
data class LlamaModelConfig(
    val modelFile: File,
    val contextLength: Int = 2048,
    val nThreads: Int = 4, // 4 CPU execution threads targeting Qualcomm Kryo performance cores
    val nBatch: Int = 512,  // Logical batch size for prompt pre-fill processing
    val useMmap: Boolean = true, // Demand-paged memory mapping prevents premature OS out-of-memory
    val useMlock: Boolean = false, // Must remain false on Android to prevent system freezing under pressure
    val nGpuLayers: Int = 0 // Baseline CPU NEON execution provider (0 GPU offload)
)
