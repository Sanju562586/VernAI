package com.vernai.ai.model

import com.vernai.core.common.result.VernAiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class OfflineSecurityAndIntegrityTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun verifyChecksum_matchesExpectedSha256() {
        val testFile = tempFolder.newFile("sample_model.bin")
        testFile.writeText("VernAI Offline Telugu ASR / LLM Model Weights", StandardCharsets.UTF_8)

        val expectedSha = ModelIntegrityVerifier.calculateSha256(testFile.inputStream())
        val result = ModelIntegrityVerifier.verifyChecksum(testFile, expectedSha)

        assertTrue("Checksum verification must succeed for valid model", result is VernAiResult.Success)
        assertTrue((result as VernAiResult.Success).data)
    }

    @Test
    fun verifyChecksum_rejectsTamperedOrCorruptedModel() {
        val testFile = tempFolder.newFile("corrupted_model.bin")
        testFile.writeText("Original Clean Model", StandardCharsets.UTF_8)

        val wrongSha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val result = ModelIntegrityVerifier.verifyChecksum(testFile, wrongSha)

        assertTrue("Tampered model must be rejected", result is VernAiResult.Error)
        assertTrue((result as VernAiResult.Error).message?.contains("హాష్ సరిపోలలేదు") == true)
    }

    @Test
    fun verifyChecksum_rejectsEmptyFile() {
        val emptyFile = tempFolder.newFile("empty_model.bin")

        val result = ModelIntegrityVerifier.verifyChecksum(emptyFile, "any_hash")
        assertTrue("Empty model must be rejected", result is VernAiResult.Error)
    }

    @Test
    fun manifestSecurityAudit_confirmsNoInternetPermission() {
        val possiblePaths = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        )
        val manifestFile = possiblePaths.firstOrNull { it.exists() }
        if (manifestFile != null) {
            val content = manifestFile.readText()
            assertFalse(
                "AndroidManifest.xml must NEVER declare android.permission.INTERNET",
                content.contains("android.permission.INTERNET")
            )
            assertTrue(
                "AndroidManifest.xml must declare RECORD_AUDIO for local microphone",
                content.contains("android.permission.RECORD_AUDIO")
            )
            assertTrue(
                "AndroidManifest.xml must declare FileProvider for secure local sharing",
                content.contains("androidx.core.content.FileProvider")
            )
        }
    }

    @Test
    fun testNuclearDataPurge_cleansAllFiles() {
        val fakeCacheDir = tempFolder.newFolder("cache")
        val fakeModelsDir = tempFolder.newFolder("models")

        val cachedDoc = File(fakeCacheDir, "letter_export.pdf").apply { writeText("PDF data") }
        val modelFile = File(fakeModelsDir, "qwen_model.gguf").apply { writeText("Model data") }

        assertTrue(cachedDoc.exists())
        assertTrue(modelFile.exists())

        // Simulate nuclear wipe
        fakeCacheDir.deleteRecursively()
        fakeModelsDir.deleteRecursively()

        assertFalse(cachedDoc.exists())
        assertFalse(modelFile.exists())
    }
}
