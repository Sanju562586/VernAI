package com.vernai.ai.model

import android.content.Context
import android.net.Uri
import com.vernai.core.common.result.VernAiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

sealed interface ModelImportState {
    data object NotInstalled : ModelImportState
    data class Importing(val progress: Float, val bytesImported: Long, val totalBytes: Long) : ModelImportState
    data class Verifying(val progress: Float) : ModelImportState
    data class Ready(val modelFile: File, val sizeBytes: Long, val sha256: String) : ModelImportState
    data class ChecksumMismatch(val expected: String, val actual: String) : ModelImportState
    data class Error(val message: String) : ModelImportState
}

class ModelLifecycleManager(
    private val context: Context,
    private val modelsDirectory: File = File(context.filesDir, "models")
) {
    private val _importState = MutableStateFlow<ModelImportState>(ModelImportState.NotInstalled)
    val importState: StateFlow<ModelImportState> = _importState.asStateFlow()

    init {
        modelsDirectory.mkdirs()
        checkExistingModel()
    }

    fun checkExistingModel() {
        val defaultModel = File(modelsDirectory, "qwen2.5-1.5b-instruct-q4_k_m.gguf")
        if (defaultModel.exists() && defaultModel.length() > 0) {
            _importState.value = ModelImportState.Ready(
                modelFile = defaultModel,
                sizeBytes = defaultModel.length(),
                sha256 = "verified_local"
            )
        } else {
            _importState.value = ModelImportState.NotInstalled
        }
    }

    suspend fun importFromSafUri(
        sourceUri: Uri,
        targetFileName: String,
        expectedSha256: String?
    ): VernAiResult<File> {
        return runCatching {
            val destination = File(modelsDirectory, targetFileName)
            _importState.value = ModelImportState.Importing(0f, 0L, 0L)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val totalBytes = context.contentResolver.openFileDescriptor(sourceUri, "r")?.statSize ?: -1L
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(32768)
                    var bytesCopied = 0L
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val progress = if (totalBytes > 0) bytesCopied.toFloat() / totalBytes else 0f
                        _importState.value = ModelImportState.Importing(progress, bytesCopied, totalBytes)
                        read = input.read(buffer)
                    }
                    output.flush()
                }
            } ?: throw IllegalStateException("Could not open source stream")

            if (!expectedSha256.isNullOrBlank()) {
                _importState.value = ModelImportState.Verifying(0f)
                val verifyResult = ModelIntegrityVerifier.verifyChecksum(destination, expectedSha256) { progress ->
                    _importState.value = ModelImportState.Verifying(progress)
                }
                when (verifyResult) {
                    is VernAiResult.Success -> {
                        _importState.value = ModelImportState.Ready(destination, destination.length(), expectedSha256)
                        VernAiResult.Success(destination)
                    }
                    is VernAiResult.Error -> {
                        destination.delete()
                        _importState.value = ModelImportState.ChecksumMismatch(expectedSha256, "verification_failed")
                        VernAiResult.Error(verifyResult.exception, verifyResult.message)
                    }
                    is VernAiResult.Loading -> VernAiResult.Loading(verifyResult.progress, verifyResult.stage)
                }
            } else {
                _importState.value = ModelImportState.Ready(destination, destination.length(), "unverified")
                VernAiResult.Success(destination)
            }
        }.getOrElse { e ->
            _importState.value = ModelImportState.Error(e.localizedMessage ?: "Import failed")
            VernAiResult.Error(e, "మోడల్ దిగుమతి విఫలమైంది: ${e.localizedMessage}")
        }
    }

    fun deleteModel(fileName: String = "qwen2.5-1.5b-instruct-q4_k_m.gguf"): Boolean {
        val modelFile = File(modelsDirectory, fileName)
        val deleted = if (modelFile.exists()) modelFile.delete() else true
        if (deleted) {
            _importState.value = ModelImportState.NotInstalled
        }
        return deleted
    }
}
