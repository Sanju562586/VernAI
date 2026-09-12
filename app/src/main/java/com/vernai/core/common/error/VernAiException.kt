package com.vernai.core.common.error

/**
 * Domain-specific exception hierarchy for VernAI offline engine failures.
 */
sealed class VernAiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class ModelNotFoundException(val modelId: String, val path: String) :
        VernAiException("Model '$modelId' not found at target path: $path")

    class ModelCorruptedException(val modelId: String, val expectedHash: String, val actualHash: String) :
        VernAiException("Model '$modelId' failed integrity check. Expected $expectedHash, got $actualHash")

    class OutOfMemoryImminentException(val availableMb: Long, val requiredMb: Long) :
        VernAiException("Insufficient RAM for inference. Available: ${availableMb}MB, Required: ${requiredMb}MB")

    class AsrInitializationException(message: String, cause: Throwable? = null) :
        VernAiException("Failed to initialize ASR engine: $message", cause)

    class LlmInferenceException(message: String, cause: Throwable? = null) :
        VernAiException("LLM generation aborted or failed: $message", cause)

    class GrammarParseException(val rawOutput: String, val grammarRule: String, cause: Throwable? = null) :
        VernAiException("Output failed GBNF grammar constraint validation: $grammarRule", cause)

    class DocumentExtractionException(val fileName: String, message: String, cause: Throwable? = null) :
        VernAiException("Failed to parse document '$fileName': $message", cause)

    class ExportRenderingException(val targetFormat: String, message: String, cause: Throwable? = null) :
        VernAiException("Failed to render $targetFormat with complex Indic typography: $message", cause)
}
