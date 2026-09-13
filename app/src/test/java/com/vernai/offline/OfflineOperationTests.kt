package com.vernai.offline

import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.ai.model.ModelIntegrityVerifier
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.result.VernAiResult
import com.vernai.document.export.ExportConfig
import com.vernai.document.export.ExportFormat
import com.vernai.document.export.LocalDocumentExporter
import com.vernai.domain.model.letter.LetterInput
import com.vernai.domain.model.letter.LetterRecipient
import com.vernai.domain.model.letter.LetterType
import com.vernai.domain.usecase.GenerateTeluguFormalLetterUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class OfflineOperationTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun airGapVerification_strictAbsenceOfNetworkPermissions() {
        val manifestCandidates = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        )
        val manifest = manifestCandidates.firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist", manifest)

        val manifestContent = manifest!!.readText()

        // Zero Internet permission
        assertFalse(
            "CRITICAL SECURITY: AndroidManifest.xml must NEVER declare android.permission.INTERNET",
            manifestContent.contains("android.permission.INTERNET")
        )

        // Zero Network State permission (often used by analytics SDKs)
        assertFalse(
            "AndroidManifest.xml should not declare ACCESS_NETWORK_STATE unless justified",
            manifestContent.contains("android.permission.ACCESS_NETWORK_STATE")
        )

        // Verifies RECORD_AUDIO is declared for on-device microphone
        assertTrue(
            "AndroidManifest.xml must declare RECORD_AUDIO for local speech recognition",
            manifestContent.contains("android.permission.RECORD_AUDIO")
        )
    }

    @Test
    fun modelIntegrity_verifiesSha256AndRejectsCorruptedWeights() {
        val mockWeights = tempFolder.newFile("qwen2.5_indic_test.gguf")
        mockWeights.writeText("GGUF_MAGIC_V3_INDIC_TELUGU_WEIGHTS", StandardCharsets.UTF_8)

        val validHash = ModelIntegrityVerifier.calculateSha256(mockWeights.inputStream())
        val validResult = ModelIntegrityVerifier.verifyChecksum(mockWeights, validHash)
        assertTrue("Valid model weights must pass integrity verification", validResult is VernAiResult.Success)

        // Tampered model
        val tamperedHash = "0000000000000000000000000000000000000000000000000000000000000000"
        val tamperedResult = ModelIntegrityVerifier.verifyChecksum(mockWeights, tamperedHash)
        assertTrue("Tampered model weights must be rejected with error", tamperedResult is VernAiResult.Error)
        val errorMsg = (tamperedResult as VernAiResult.Error).message ?: ""
        assertTrue("Error message must be in Telugu", errorMsg.contains("హాష్ సరిపోలలేదు") || errorMsg.contains("విఫలమైంది"))
    }

    @Test
    fun offlinePipelines_operateEndToEndWithoutNetwork() = runTest {
        val llmEngine = MockLlmInferenceEngine()
        val dispatchers = DefaultVernAiDispatchers()
        val exporter = LocalDocumentExporter()

        val letterUseCase = GenerateTeluguFormalLetterUseCase(
            llmEngine = llmEngine,
            dispatchers = dispatchers
        )

        val input = LetterInput(
            teluguVoiceTranscript = "మా గ్రామంలో వీధి దీపాలు పనిచేయడం లేదు",
            letterType = LetterType.COMPLAINT,
            recipient = LetterRecipient(
                designation = "గ్రామ సర్పంచ్ గారు",
                departmentOrOffice = "గ్రామ పంచాయతీ కార్యాలయం",
                officeAddress = "ఖమ్మం జిల్లా"
            ),
            userProvidedFacts = listOf("గత 15 రోజులుగా రాత్రి వేళల్లో వీధి దీపాలు వెలగడం లేదు"),
            location = "శాంతినగర్",
            date = "13-09-2026",
            applicantName = "కాలనీ గ్రామస్తులు"
        )

        // 1. On-device LLM Inference
        val letterResult = letterUseCase.execute(input)
        assertTrue("Offline letter generation must succeed", letterResult is VernAiResult.Success)
        val structured = (letterResult as VernAiResult.Success).data
        assertTrue(structured.formalTeluguLetterBody.isNotBlank())

        // 2. On-device Document Export to PDF
        val pdfOut = File(tempFolder.root, "Offline_Letter.pdf")
        val exportResult = exporter.exportComplaintLetter(
            draft = com.vernai.core.model.ComplaintDraft(
                subject = structured.subject,
                department = input.recipient.departmentOrOffice,
                recipientDesignation = input.recipient.designation,
                vernacularBody = structured.formalTeluguLetterBody,
                englishTranslation = structured.englishTranslation,
                targetLanguage = com.vernai.core.model.Language.TELUGU
            ),
            destinationFile = pdfOut,
            config = ExportConfig(ExportFormat.PDF, com.vernai.core.model.Language.TELUGU)
        )

        assertTrue("Offline PDF export must succeed", exportResult is VernAiResult.Success)
        assertTrue("Exported PDF must exist on local disk", pdfOut.exists() && pdfOut.length() > 0)
    }
}
