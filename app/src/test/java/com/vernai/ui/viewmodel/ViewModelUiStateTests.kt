package com.vernai.ui.viewmodel

import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import com.vernai.core.model.SalesValidationStatus
import com.vernai.document.export.ExportFormat
import com.vernai.document.export.LocalDocumentExporter
import com.vernai.document.processing.TestDocumentType
import com.vernai.domain.model.letter.LetterType
import com.vernai.domain.repository.ComplaintRepository
import com.vernai.domain.repository.SalesLogRepository
import com.vernai.ui.document.DocReaderUiIntent
import com.vernai.ui.document.DocReaderViewModel
import com.vernai.ui.letter.LetterEditorViewModel
import com.vernai.ui.letter.LetterUiIntent
import com.vernai.ui.sales.SalesUiIntent
import com.vernai.ui.sales.SalesViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FakeComplaintRepository : ComplaintRepository {
    val complaints = mutableListOf<ComplaintDraft>()
    override fun getComplaintsStream(): Flow<List<ComplaintDraft>> = flowOf(complaints)
    override suspend fun getComplaintById(id: String): ComplaintDraft? = complaints.find { it.id == id }
    override suspend fun saveComplaint(complaint: ComplaintDraft): VernAiResult<Unit> {
        complaints.add(complaint)
        return VernAiResult.Success(Unit)
    }
    override suspend fun deleteComplaint(id: String): VernAiResult<Unit> {
        complaints.removeAll { it.id == id }
        return VernAiResult.Success(Unit)
    }
}

class FakeSalesLogRepository : SalesLogRepository {
    val logs = mutableListOf<SalesLog>()
    override fun getSalesLogsStream(): Flow<List<SalesLog>> = flowOf(logs)
    override suspend fun getSalesLogById(id: String): SalesLog? = logs.find { it.id == id }
    override suspend fun saveSalesLog(salesLog: SalesLog): VernAiResult<Unit> {
        logs.add(salesLog)
        return VernAiResult.Success(Unit)
    }
    override suspend fun deleteSalesLog(id: String): VernAiResult<Unit> {
        logs.removeAll { it.id == id }
        return VernAiResult.Success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelUiStateTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private val testDispatchers = object : VernAiDispatchers {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val asrInference: CoroutineDispatcher = testDispatcher
        override val llmInference: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. LetterEditorViewModel Tests
    // =========================================================================

    @Test
    fun letterViewModel_initialStateAndFieldUpdates() = runTest(testDispatcher) {
        val repo = FakeComplaintRepository()
        val viewModel = LetterEditorViewModel(
            complaintRepository = repo,
            llmEngine = MockLlmInferenceEngine(),
            exporter = LocalDocumentExporter(),
            dispatchers = testDispatchers
        )

        // Verify initial defaults
        val initial = viewModel.uiState.value
        assertEquals(LetterType.COMPLAINT, initial.letterType)
        assertTrue(initial.isDraft)
        assertFalse(initial.isUserReviewed)
        assertFalse(initial.showReviewDialog)

        // Dispatch field updates
        viewModel.handleIntent(LetterUiIntent.UpdateVoiceTranscript("రోడ్డు పాడైపోయింది"))
        viewModel.handleIntent(LetterUiIntent.UpdateApplicantName("రాము"))
        viewModel.handleIntent(LetterUiIntent.UpdateRecipientDesignation("సర్పంచ్ గారు"))
        viewModel.handleIntent(LetterUiIntent.UpdateRecipientDepartment("గ్రామ పంచాయతీ"))
        viewModel.handleIntent(LetterUiIntent.UpdateLocation("శాంతినగర్"))
        viewModel.handleIntent(LetterUiIntent.UpdateDate("13-09-2026"))
        viewModel.handleIntent(LetterUiIntent.UpdateUserFacts("1. రోడ్డుపై పెద్ద గుంతలు ఉన్నాయి"))

        val updated = viewModel.uiState.value
        assertEquals("రోడ్డు పాడైపోయింది", updated.voiceTranscript)
        assertEquals("రాము", updated.applicantName)
        assertEquals("సర్పంచ్ గారు", updated.recipientDesignation)
        assertEquals("గ్రామ పంచాయతీ", updated.recipientDepartment)
        assertEquals("శాంతినగర్", updated.location)
        assertEquals("13-09-2026", updated.date)
        assertEquals("1. రోడ్డుపై పెద్ద గుంతలు ఉన్నాయి", updated.userFactsText)
    }

    @Test
    fun letterViewModel_generationWorkflow_enforcesReviewBeforeExport() = runTest(testDispatcher) {
        val repo = FakeComplaintRepository()
        val viewModel = LetterEditorViewModel(
            complaintRepository = repo,
            llmEngine = MockLlmInferenceEngine(),
            exporter = LocalDocumentExporter(),
            dispatchers = testDispatchers
        )

        // 1. Generate Letter
        viewModel.handleIntent(LetterUiIntent.GenerateLetter)
        advanceUntilIdle()

        val generatedState = viewModel.uiState.value
        assertFalse("Generating flag should be cleared", generatedState.isGenerating)
        assertTrue("Draft flag must remain true", generatedState.isDraft)
        assertFalse("Newly generated letter must not be marked reviewed", generatedState.isUserReviewed)
        assertEquals("Active tab should switch to Preview (Tab 1)", 1, generatedState.activeTab)
        assertNotNull("Structured letter should be populated", generatedState.structuredLetter)

        // 2. Request Export without review -> opens review confirmation dialog
        viewModel.handleIntent(LetterUiIntent.RequestExport(ExportFormat.PDF, tempFolder.root))
        advanceUntilIdle()

        val exportPromptState = viewModel.uiState.value
        assertTrue("Review dialog must appear before unverified export", exportPromptState.showReviewDialog)
        assertEquals(ExportFormat.PDF, exportPromptState.pendingExportFormat)

        // 3. Confirm review and export
        viewModel.handleIntent(LetterUiIntent.ConfirmReviewAndExport)
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertTrue("Letter must be marked reviewed after user confirmation", finalState.isUserReviewed)
        assertFalse("Review dialog must be dismissed", finalState.showReviewDialog)
    }

    // =========================================================================
    // 2. SalesViewModel Tests
    // =========================================================================

    @Test
    fun salesViewModel_manualTranscriptSubmissionAndGrandTotal() = runTest(testDispatcher) {
        val repo = FakeSalesLogRepository()
        val viewModel = SalesViewModel(
            salesLogRepository = repo,
            exporter = LocalDocumentExporter(),
            dispatchers = testDispatchers
        )

        // Submit Telugu sales dictation
        val dictatedText = "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు మరియు 2 నూనె ప్యాకెట్లు 260 రూపాయలు నగదు"
        viewModel.handleIntent(SalesUiIntent.SubmitManualTranscript(dictatedText))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("Current log should be created", state.currentLog)
        val log = state.currentLog!!
        assertTrue("Items must not be empty", log.items.isNotEmpty())

        // Verify grand total calculation
        val expectedTotal = log.items.sumOf { it.totalPrice }
        assertEquals(expectedTotal, log.grandTotal, 0.001)
    }

    @Test
    fun salesViewModel_clarificationResolution_recalculatesTotal() = runTest(testDispatcher) {
        val repo = FakeSalesLogRepository()
        val viewModel = SalesViewModel(
            salesLogRepository = repo,
            exporter = LocalDocumentExporter(),
            dispatchers = testDispatchers
        )

        // Manually create item with ambiguous quantity
        val ambiguousItem = SalesItem(
            id = "ambig-1",
            date = "2026-09-13",
            originalTerm = "మిరపకాయలు",
            quantity = 0.0,
            unitPrice = 80.0,
            totalPrice = 0.0,
            validationStatus = SalesValidationStatus.CLARIFICATION_NEEDED
        )

        viewModel.handleIntent(SalesUiIntent.AddNewItem(ambiguousItem))
        advanceUntilIdle()

        // Select for clarification
        viewModel.handleIntent(SalesUiIntent.SelectClarificationItem(ambiguousItem))
        assertEquals(ambiguousItem, viewModel.uiState.value.activeClarificationItem)

        // Resolve clarification with 2.0 kg and Rs. 160 total
        viewModel.handleIntent(
            SalesUiIntent.ResolveClarification(
                itemId = "ambig-1",
                resolvedQuantity = 2.0,
                resolvedUnitPrice = 80.0,
                resolvedTotal = 160.0,
                resolvedNotes = "నగదు"
            )
        )
        advanceUntilIdle()

        val resolvedState = viewModel.uiState.value
        assertNull("Active clarification should be cleared", resolvedState.activeClarificationItem)
        val updatedItem = resolvedState.currentLog?.items?.find { it.id == "ambig-1" }
        assertNotNull(updatedItem)
        assertEquals(2.0, updatedItem!!.quantity, 0.001)
        assertEquals(160.0, updatedItem.totalPrice, 0.001)
        assertEquals(SalesValidationStatus.VERIFIED, updatedItem.validationStatus)
        val expectedTotal = resolvedState.currentLog?.items?.sumOf { it.totalPrice } ?: 0.0
        assertEquals(expectedTotal, resolvedState.currentLog?.grandTotal ?: 0.0, 0.001)
        assertEquals(620.0, resolvedState.currentLog?.grandTotal ?: 0.0, 0.001)
    }

    // =========================================================================
    // 3. DocReaderViewModel Tests
    // =========================================================================

    @Test
    fun docReaderViewModel_importTestDocumentAndChunking() = runTest(testDispatcher) {
        val viewModel = DocReaderViewModel(
            llmEngine = MockLlmInferenceEngine(),
            dispatchers = testDispatchers
        )

        // Import test document: Revenue Pattadar Notice
        viewModel.handleIntent(DocReaderUiIntent.ImportTestDocument(TestDocumentType.REVENUE_PATTADAR_NOTICE))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Extraction should be complete", state.isExtracting)
        assertNotNull("Extracted document should be populated", state.extractedDocument)
        assertTrue("Chunks should be created for reader", state.chunks.isNotEmpty())
        assertNull("No extraction error should occur", state.extractionError)

        // Select first chunk
        val firstChunk = state.chunks.first()
        viewModel.handleIntent(DocReaderUiIntent.SelectChunk(firstChunk))
        assertEquals(firstChunk, viewModel.uiState.value.selectedChunk)

        // Explain selected chunk
        viewModel.handleIntent(DocReaderUiIntent.ExplainSelectedChunk(firstChunk))
        advanceUntilIdle()

        assertNotNull("Explanation should be generated for selected snippet", viewModel.uiState.value.selectedSnippetExplanation)

        // Toggle raw text view
        assertFalse("Raw text initially false", viewModel.uiState.value.showRawText)
        viewModel.handleIntent(DocReaderUiIntent.ToggleRawText)
        assertTrue("Raw text should be toggled true", viewModel.uiState.value.showRawText)
    }

    @Test
    fun letterEditorViewModel_speechAndPanchayatPrint() = runTest(testDispatcher) {
        val viewModel = LetterEditorViewModel(
            complaintRepository = FakeComplaintRepository(),
            llmEngine = MockLlmInferenceEngine(),
            exporter = LocalDocumentExporter(),
            dispatchers = testDispatchers
        )

        // Populate sample facts
        viewModel.handleIntent(LetterUiIntent.LoadSampleFacts)
        advanceUntilIdle()

        // Generate draft letter
        viewModel.handleIntent(LetterUiIntent.GenerateLetter)
        advanceUntilIdle()

        assertTrue("Vernacular body should be populated", viewModel.uiState.value.vernacularBody.isNotBlank())

        // Toggle speech
        assertFalse("Initially not speaking", viewModel.uiState.value.isSpeaking)
        viewModel.handleIntent(LetterUiIntent.ToggleSpeech)
        assertTrue("Speaking state should be true after toggle", viewModel.uiState.value.isSpeaking)

        viewModel.handleIntent(LetterUiIntent.ToggleSpeech)
        assertFalse("Speaking state should be false after second toggle", viewModel.uiState.value.isSpeaking)

        // Panchayat Print Intent
        val cacheFolder = tempFolder.newFolder("print_test")
        viewModel.handleIntent(LetterUiIntent.PrintLetter(cacheFolder))
        advanceUntilIdle()

        assertNotNull("Exported PDF file should be prepared for printing", viewModel.uiState.value.exportedFile)
        assertTrue("PDF file should exist on disk", viewModel.uiState.value.exportedFile!!.exists())
    }

    @Test
    fun docReaderViewModel_cameraScanAndSpeechToggle() = runTest(testDispatcher) {
        val viewModel = DocReaderViewModel(
            llmEngine = MockLlmInferenceEngine(),
            dispatchers = testDispatchers
        )

        // Mock camera JPEG bytes: valid JPEG magic bytes (FF D8 FF) + non-zero payload
        val fakeJpegBytes = ByteArray(256) { (it % 250 + 1).toByte() }.apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xD8.toByte()
            this[2] = 0xFF.toByte()
        }

        viewModel.handleIntent(DocReaderUiIntent.PickDocumentFile("camera_scan_101.jpg", "image/jpeg", fakeJpegBytes))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("Extracted document from camera image scan should be present", state.extractedDocument)
        assertTrue("Document text should contain scanned OCR content", state.extractedDocument!!.rawText.isNotBlank())
        assertTrue("Chunks should be generated for scanned image", state.chunks.isNotEmpty())

        // Test speech toggle on document reader
        assertFalse("Initially not speaking", viewModel.uiState.value.isSpeaking)
        viewModel.handleIntent(DocReaderUiIntent.ToggleSpeech)
        assertTrue("Speaking state should become true", viewModel.uiState.value.isSpeaking)

        viewModel.handleIntent(DocReaderUiIntent.ToggleSpeech)
        assertFalse("Speaking state should toggle back to false", viewModel.uiState.value.isSpeaking)
    }
}
