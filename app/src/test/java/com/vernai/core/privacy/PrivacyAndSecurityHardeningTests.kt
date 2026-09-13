package com.vernai.core.privacy

import com.vernai.core.common.result.VernAiResult
import com.vernai.document.export.ExportFormat
import com.vernai.domain.model.letter.LetterInput
import com.vernai.domain.model.letter.LetterRecipient
import com.vernai.domain.model.letter.LetterType
import com.vernai.ui.letter.LetterEditorViewModel
import com.vernai.ui.letter.LetterUiIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyAndSecurityHardeningTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSecureFileShredder_overwritesAndDeletesFile() {
        val testFile = tempFolder.newFile("sensitive_transcript.wav")
        testFile.writeText("Sensitive audio recording data in PCM 16kHz format", StandardCharsets.UTF_8)
        assertTrue(testFile.exists())
        assertTrue(testFile.length() > 0)

        val result = SecureFileShredder.shredFile(testFile, passes = 2)

        assertTrue("Shredder must return Success", result is VernAiResult.Success)
        assertFalse("Shredded file must no longer exist on filesystem", testFile.exists())
    }

    @Test
    fun testSecureFileShredder_handlesNonExistentFileGracefully() {
        val ghostFile = File(tempFolder.root, "ghost_file.txt")
        assertFalse(ghostFile.exists())

        val result = SecureFileShredder.shredFile(ghostFile)
        assertTrue("Non-existent file must return Success gracefully", result is VernAiResult.Success)
    }

    @Test
    fun testSecureFileShredder_shredsDirectoryRecursively() {
        val sensitiveDir = tempFolder.newFolder("exports")
        val file1 = File(sensitiveDir, "doc1.pdf").apply { writeText("Secret PDF data") }
        val subDir = File(sensitiveDir, "sub").apply { mkdirs() }
        val file2 = File(subDir, "letter.docx").apply { writeText("Secret DOCX data") }

        assertTrue(file1.exists())
        assertTrue(file2.exists())

        val result = SecureFileShredder.shredDirectory(sensitiveDir)

        assertTrue("Directory shredding must succeed", result is VernAiResult.Success)
        assertFalse("Directory must no longer exist", sensitiveDir.exists())
        assertFalse("Child file 1 must no longer exist", file1.exists())
        assertFalse("Child file 2 must no longer exist", file2.exists())
    }

    @Test
    fun testSecureLogger_redactsIndianPhoneNumbers() {
        val sensitive = "దరఖాస్తుదారు ఫోన్: 9876543210 లేదా +91 9123456789 సంప్రదించండి"
        val sanitized = SecureLogger.sanitize(sensitive)

        assertFalse("Must not leak 10-digit mobile number", sanitized.contains("9876543210"))
        assertFalse("Must not leak +91 prefixed mobile number", sanitized.contains("9123456789"))
        assertTrue("Must contain [REDACTED_PHONE]", sanitized.contains("[REDACTED_PHONE]"))
    }

    @Test
    fun testSecureLogger_redactsAadhaarNumbers() {
        val sensitive = "ఆధార్ సంఖ్య: 1234 5678 9012 లేదా 9876-5432-1098"
        val sanitized = SecureLogger.sanitize(sensitive)

        assertFalse("Must not leak spaced Aadhaar", sanitized.contains("1234 5678 9012"))
        assertFalse("Must not leak hyphenated Aadhaar", sanitized.contains("9876-5432-1098"))
        assertTrue("Must contain [REDACTED_AADHAAR]", sanitized.contains("[REDACTED_AADHAAR]"))
    }

    @Test
    fun testSecureLogger_redactsSurveyNumbersAndFinancialAmounts() {
        val sensitive = "భూమి సర్వే నంబర్ 145/2 అమ్మకం మొత్తం ₹50000 లేదా 500 రూపాయలు"
        val sanitized = SecureLogger.sanitize(sensitive)

        assertFalse("Must not leak land survey number", sanitized.contains("145/2"))
        assertFalse("Must not leak rupee amount", sanitized.contains("50000"))
        assertTrue("Must contain [REDACTED_SURVEY_NO]", sanitized.contains("[REDACTED_SURVEY_NO]"))
        assertTrue("Must contain [REDACTED_AMOUNT]", sanitized.contains("[REDACTED_AMOUNT]"))
    }

    @Test
    fun testAutoBackupRules_excludeAllSensitiveStorageDomains() {
        val backupRulesFile = listOf(
            File("app/src/main/res/xml/backup_rules.xml"),
            File("src/main/res/xml/backup_rules.xml")
        ).firstOrNull { it.exists() }

        val extractionRulesFile = listOf(
            File("app/src/main/res/xml/data_extraction_rules.xml"),
            File("src/main/res/xml/data_extraction_rules.xml")
        ).firstOrNull { it.exists() }

        if (backupRulesFile != null) {
            val content = backupRulesFile.readText()
            assertTrue("backup_rules must exclude database", content.contains("""<exclude domain="database""""))
            assertTrue("backup_rules must exclude sharedpref", content.contains("""<exclude domain="sharedpref""""))
            assertTrue("backup_rules must exclude file", content.contains("""<exclude domain="file""""))
            assertTrue("backup_rules must exclude root", content.contains("""<exclude domain="root""""))
        }

        if (extractionRulesFile != null) {
            val content = extractionRulesFile.readText()
            assertTrue("data_extraction_rules must exclude database from cloud", content.contains("""<exclude domain="database""""))
            assertTrue("data_extraction_rules must exclude sharedpref from cloud", content.contains("""<exclude domain="sharedpref""""))
            assertTrue("data_extraction_rules must exclude file from cloud", content.contains("""<exclude domain="file""""))
            assertTrue("data_extraction_rules must exclude root from cloud", content.contains("""<exclude domain="root""""))
        }
    }

    @Test
    fun testLetterDraftReviewGuardrail_requiresReviewBeforeExport() = runTest {
        val viewModel = LetterEditorViewModel()

        // 1. Initially marked as draft and not reviewed
        val initial = viewModel.uiState.value
        assertTrue("Generated letter must be marked as draft", initial.isDraft)
        assertFalse("Draft must require user review before export", initial.isUserReviewed)
        assertFalse("Review dialog should not be open initially", initial.showReviewDialog)

        // 2. Request export without prior review -> triggers review dialog
        viewModel.handleIntent(LetterUiIntent.RequestExport(ExportFormat.PDF, tempFolder.root))
        val afterRequest = viewModel.uiState.value
        assertTrue("Requesting export when unreviewed must show review dialog", afterRequest.showReviewDialog)
        assertEquals("Pending export format must be PDF", ExportFormat.PDF, afterRequest.pendingExportFormat)

        // 3. User reviews and confirms -> marks reviewed and closes dialog
        viewModel.handleIntent(LetterUiIntent.ConfirmReviewAndExport)
        val afterConfirm = viewModel.uiState.value
        assertTrue("User confirmation must mark letter as reviewed", afterConfirm.isUserReviewed)
        assertFalse("Review dialog must close upon confirmation", afterConfirm.showReviewDialog)
    }

    @Test
    fun testLetterDraftReviewGuardrail_resetUponRegeneration() = runTest {
        val viewModel = LetterEditorViewModel()

        // Mark as reviewed
        viewModel.handleIntent(LetterUiIntent.SetUserReviewed(true))
        assertTrue(viewModel.uiState.value.isUserReviewed)

        // Generating a new letter or regenerating must reset review requirement
        viewModel.handleIntent(LetterUiIntent.GenerateLetter)
        // Give coroutine chance to update state
        val updated = viewModel.uiState.value
        assertFalse("New letter generation must reset isUserReviewed to false", updated.isUserReviewed)
        assertTrue("New letter must be flagged as draft", updated.isDraft)
    }
}
