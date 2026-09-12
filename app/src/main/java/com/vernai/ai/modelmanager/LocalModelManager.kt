package com.vernai.ai.modelmanager

import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ModelInfo
import com.vernai.core.model.ModelType
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream

sealed interface ModelLoadState {
    data object NotInstalled : ModelLoadState
    data class Installing(val progressPercent: Int) : ModelLoadState
    data object Installed : ModelLoadState
    data object LoadedInRam : ModelLoadState
    data class Error(val message: String) : ModelLoadState
}

/**
 * Manages local weights lifecycle on device storage:
 * verification, extraction, sideloading, and memory residency state.
 */
interface LocalModelManager {
    val modelStates: StateFlow<Map<String, ModelLoadState>>

    /**
     * Checks if all required models for offline operation are present and verified.
     */
    suspend fun areMandatoryModelsReady(): Boolean

    /**
     * Verifies SHA-256 hash of a local model file.
     */
    suspend fun verifyModelIntegrity(model: ModelInfo): Boolean

    /**
     * Installs or extracts a model from an offline source (e.g. assets or sideload directory).
     */
    suspend fun installModelFromStream(
        model: ModelInfo,
        inputStream: InputStream,
        onProgress: (Int) -> Unit
    ): VernAiResult<File>

    /**
     * Retrieves the absolute local File pointer for a model.
     */
    fun getModelFile(model: ModelInfo): File?

    /**
     * Releases any in-memory references or triggers native model unload
     * when memory pressure is critical.
     */
    suspend fun evictInactiveModelsFromMemory()
}
