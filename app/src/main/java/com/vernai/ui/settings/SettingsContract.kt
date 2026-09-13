package com.vernai.ui.settings

import com.vernai.ai.llm.benchmark.ExecutionBackend
import com.vernai.ai.llm.benchmark.LlmBenchmarkResult
import com.vernai.core.model.Language
import com.vernai.ui.common.UiIntent
import com.vernai.ui.common.UiSideEffect
import com.vernai.ui.common.UiState

data class ModelStatusItem(
    val name: String,
    val type: String,
    val sizeOnDisk: String,
    val isInstalled: Boolean,
    val memoryFootprint: String
)

data class SettingsUiState(
    val selectedLanguage: Language = Language.TELUGU,
    val threadCount: Int = 4,
    val useVulkanAcceleration: Boolean = false,
    val selectedBackend: ExecutionBackend = ExecutionBackend.CPU_NEON,
    val availableRamMb: Long = 3450L,
    val maxModelRamBudgetMb: Long = 2800L,
    val isBenchmarking: Boolean = false,
    val benchmarkStatus: String = "",
    val benchmarkResult: LlmBenchmarkResult? = null,
    val models: List<ModelStatusItem> = listOf(
        ModelStatusItem(
            name = "Gemma-3-1B-IT",
            type = "Local LLM (Q4_K_M GGUF)",
            sizeOnDisk = "848 MB",
            isInstalled = true,
            memoryFootprint = "1.45 GB PSS"
        ),
        ModelStatusItem(
            name = "IndicConformer Multilingual",
            type = "On-Device ASR (INT8 ONNX)",
            sizeOnDisk = "320 MB",
            isInstalled = true,
            memoryFootprint = "380 MB PSS"
        ),
        ModelStatusItem(
            name = "Tesseract 5 + Noto Indic Fonts",
            type = "Offline OCR & Typography",
            sizeOnDisk = "28 MB",
            isInstalled = true,
            memoryFootprint = "45 MB PSS"
        )
    )
) : UiState

sealed interface SettingsUiIntent : UiIntent {
    data class ChangeLanguage(val language: Language) : SettingsUiIntent
    data class UpdateThreadCount(val threads: Int) : SettingsUiIntent
    data class ToggleVulkan(val enabled: Boolean) : SettingsUiIntent
    data class SelectBackend(val backend: ExecutionBackend) : SettingsUiIntent
    data object RunLlmBenchmark : SettingsUiIntent
    data object ExportBenchmarkReport : SettingsUiIntent
    data object ClearDatabaseCache : SettingsUiIntent
}

sealed interface SettingsUiSideEffect : UiSideEffect {
    data class ShowToast(val message: String) : SettingsUiSideEffect
    data class ShareReport(val markdown: String) : SettingsUiSideEffect
}

