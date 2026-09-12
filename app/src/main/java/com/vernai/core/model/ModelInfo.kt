package com.vernai.core.model

import java.io.File

enum class ModelType {
    LLM_GGUF,
    ASR_INDIC_CONFORMER,
    ASR_WHISPER,
    OCR_TESSERACT_LANGPACK
}

/**
 * Metadata defining local on-device weights.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val type: ModelType,
    val relativeStoragePath: String,
    val expectedSizeBytes: Long,
    val sha256Checksum: String,
    val ramRequiredMb: Long,
    val description: String
) {
    fun getResolvedFile(baseDir: File): File = File(baseDir, relativeStoragePath)
}
