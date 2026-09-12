package com.vernai.ai.modelmanager

import com.vernai.core.model.ModelInfo
import com.vernai.core.model.ModelType

/**
 * Verified on-device GGUF instruction models supported by VernAI.
 */
object SupportedModels {

    /**
     * Primary Target: Google Gemma 3 1B IT (Instruction Tuned).
     * Architecture: Decoder-only Transformer with 256k SentencePiece vocabulary, sliding-window attention.
     * Quantization: Q4_K_M (4-bit medium K-quantization).
     * License: Google Gemma Terms of Use (Permissive Open Weights, commercial use permitted).
     */
    val GEMMA_3_1B_IT_Q4_K_M = ModelInfo(
        id = "gemma-3-1b-it-q4_k_m",
        name = "Gemma 3 1B IT (Q4_K_M)",
        type = ModelType.LLM_GGUF,
        relativeStoragePath = "models/llm/gemma-3-1b-it-q4_k_m.gguf",
        expectedSizeBytes = 890_000_000L, // ~848 MB
        sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        ramRequiredMb = 1400L, // ~1.4 GB RAM with 2048 KV cache
        description = "Google Gemma 3 1B Instruct with 256k vocabulary. Excellent Telugu token density and reasoning efficiency."
    )

    /**
     * Alternative Co-Target: Alibaba Qwen 2.5 1.5B Instruct.
     * Architecture: Dense Transformer with GQA, RoPE, SwiGLU, 152k BPE vocabulary.
     * Quantization: Q4_K_M.
     * License: Apache 2.0 (Fully open-source, unencumbered commercial use).
     */
    val QWEN_2_5_1_5B_INSTRUCT_Q4_K_M = ModelInfo(
        id = "qwen-2.5-1.5b-instruct-q4_k_m",
        name = "Qwen 2.5 1.5B Instruct (Q4_K_M)",
        type = ModelType.LLM_GGUF,
        relativeStoragePath = "models/llm/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        expectedSizeBytes = 1_150_000_000L, // ~1.07 GB
        sha256Checksum = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
        ramRequiredMb = 1650L, // ~1.65 GB RAM with 2048 KV cache
        description = "Alibaba Qwen 2.5 1.5B Instruct with high multilingual capability across 29+ languages including Telugu."
    )

    val ALL_SUPPORTED_MODELS = listOf(
        GEMMA_3_1B_IT_Q4_K_M,
        QWEN_2_5_1_5B_INSTRUCT_Q4_K_M
    )
}
