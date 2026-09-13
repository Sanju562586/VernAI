package com.vernai.ai.llm

import com.vernai.ai.llm.benchmark.ExecutionBackend
import com.vernai.core.common.result.VernAiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * GBNF (GGML BNF) grammar constraint for structured JSON extraction.
 */
data class GrammarConstraint(
    val gbnfRule: String,
    val rootRuleName: String = "root"
) {
    companion object {
        /**
         * GBNF rule enforcing exact SalesLog JSON format.
         */
        val SALES_LOG_JSON = GrammarConstraint(
            gbnfRule = """
                root ::= "{" ws "\"items\":" ws "[" ws (item ("," ws item)*)? ws "]" ws "}"
                item ::= "{" ws "\"original_term\":" ws string "," ws "\"standard_name\":" ws string "," ws "\"quantity\":" ws number "," ws "\"unit\":" ws string "," ws "\"unit_price\":" ws number "," ws "\"total_price\":" ws number ws "}"
                string ::= "\"" [^"\\\n]* "\""
                number ::= [0-9]+ ("." [0-9]+)?
                ws ::= [ \t\n\r]*
            """.trimIndent()
        )
    }
}

/**
 * Sampling parameters for on-device generation.
 */
data class GenerationParameters(
    val temperature: Float = 0.2f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 1024,
    val repeatPenalty: Float = 1.15f,
    val stopTokens: List<String> = listOf("<|im_end|>", "<|endoftext|>", "\n\nUser:"),
    val grammar: GrammarConstraint? = null
)

sealed interface LlmEngineState {
    data object Unloaded : LlmEngineState
    data class Loading(val progressPercent: Int) : LlmEngineState
    data object Ready : LlmEngineState
    data class Generating(val tokensGenerated: Int, val tokensPerSec: Float) : LlmEngineState
    data class Error(val message: String) : LlmEngineState
}

/**
 * Replaceable local LLM engine contract.
 * Primary implementation targets llama.cpp via JNI (Qwen2.5 GGUF).
 */
interface LlmInferenceEngine : AutoCloseable {
    val state: StateFlow<LlmEngineState>

    /**
     * Loads a GGUF model file into native memory using mmap.
     * @param contextLength Maximum sequence context (e.g. 2048).
     * @param nThreads Number of CPU execution threads (recommended: 4 for Snapdragon Performance cores).
     * @param backend Acceleration backend (CPU_NEON, GPU_VULKAN, or NPU_QUALCOMM_QNN).
     */
    suspend fun loadModel(
        modelFile: File,
        contextLength: Int = 2048,
        nThreads: Int = 4,
        backend: ExecutionBackend = ExecutionBackend.CPU_NEON
    ): VernAiResult<Unit>

    /**
     * Evaluates prompt and streams generated tokens sequentially.
     * Cancellation of the calling coroutine immediately halts native evaluation.
     */
    fun streamTokens(
        prompt: String,
        params: GenerationParameters = GenerationParameters()
    ): Flow<String>

    /**
     * Non-streaming complete generation helper.
     */
    suspend fun generateCompleteText(
        prompt: String,
        params: GenerationParameters = GenerationParameters()
    ): VernAiResult<String>

    /**
     * Releases model and context from RAM.
     */
    suspend fun unloadModel()

    /**
     * Returns whether the model is loaded and ready for inference.
     */
    fun isLoaded(): Boolean
}
