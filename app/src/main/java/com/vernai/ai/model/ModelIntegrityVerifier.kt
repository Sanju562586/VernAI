package com.vernai.ai.model

import com.vernai.core.common.result.VernAiResult
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Validates cryptographic SHA-256 integrity of GGUF and ONNX models before loading.
 * Uses 8KB streaming chunks to prevent memory overhead when verifying large files.
 */
object ModelIntegrityVerifier {

    fun verifyChecksum(
        modelFile: File,
        expectedSha256: String,
        onProgress: ((progress: Float) -> Unit)? = null
    ): VernAiResult<Boolean> {
        return runCatching {
            if (!modelFile.exists() || !modelFile.isFile) {
                return VernAiResult.Error(
                    IllegalArgumentException("మోడల్ ఫైల్ కనుగొనబడలేదు (Model file not found)"),
                    "మోడల్ ఫైల్ అందుబాటులో లేదు: ${modelFile.path}"
                )
            }

            val totalBytes = modelFile.length()
            if (totalBytes == 0L) {
                return VernAiResult.Error(
                    IllegalStateException("మోడల్ ఫైల్ ఖాళీగా ఉంది (Model file is 0 bytes)"),
                    "మోడల్ ఫైల్ ఖాళీగా ఉంది"
                )
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesReadTotal = 0L

            FileInputStream(modelFile).use { fis ->
                var read = fis.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    bytesReadTotal += read
                    onProgress?.invoke(bytesReadTotal.toFloat() / totalBytes)
                    read = fis.read(buffer)
                }
            }

            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            val cleanExpected = expectedSha256.trim().lowercase()

            if (actualHash.equals(cleanExpected, ignoreCase = true)) {
                VernAiResult.Success(true)
            } else {
                VernAiResult.Error(
                    SecurityException("మోడల్ హ్యాష్ సరిపోలలేదు (Checksum mismatch)"),
                    "హాష్ సరిపోలలేదు. ఆశించినది: $cleanExpected, వాస్తవమైనది: $actualHash"
                )
            }
        }.getOrElse { e ->
            VernAiResult.Error(e, "ధృవీకరణ విఫలమైంది: ${e.localizedMessage}")
        }
    }

    fun calculateSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read = inputStream.read(buffer)
        while (read != -1) {
            digest.update(buffer, 0, read)
            read = inputStream.read(buffer)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
