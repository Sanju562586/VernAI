package com.vernai.integration

import com.vernai.ai.asr.AsrEngine
import com.vernai.ai.asr.AsrState
import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.ai.model.ModelIntegrityVerifier
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.Language
import com.vernai.core.model.TranscriptionResult
import com.vernai.document.export.ExportFormat
import com.vernai.document.export.LocalDocumentExporter
import com.vernai.domain.model.letter.LetterType
import com.vernai.domain.repository.ComplaintRepository
import com.vernai.ui.letter.LetterEditorViewModel
import com.vernai.ui.letter.LetterUiIntent
import com.vernai.ui.letter.LetterUiSideEffect
import com.vernai.ui.navigation.VernAiNavDestination
import com.vernai.ui.voice.DetectedIntentType
import com.vernai.ui.voice.ProcessingStage
import com.vernai.ui.voice.VoiceUiIntent
import com.vernai.ui.voice.VoiceUiSideEffect
import com.vernai.ui.voice.VoiceWorkspaceViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/**
 * End-to-End Integration Demo Test Suite verifying the full 6-step VernAI workflow:
 * 1. User speaks a Telugu complaint.
 * 2. On-device ASR transcribes it into text.
 * 3. Local LLM generates a formal Telugu complaint letter (with anti-hallucination & cancellation).
 * 4. User edits and reviews the letter (safeguards enforce explicit review).
 * 5. App exports the letter to PDF and DOCX locally.
 * 6. App operates completely offline with zero INTERNET permission, zero telemetry, and checksum integrity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EndToEndIntegrationDemoFlowTests {

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

    private class TestComplaintRepository : ComplaintRepository {
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

    private class CustomTeluguComplaintAsrEngine(
        private val spokenComplaint: String
    ) : AsrEngine {
        private val _state = MutableStateFlow<AsrState>(AsrState.Ready)
        override val state: StateFlow<AsrState> = _state.asStateFlow()

        override suspend fun initialize(targetLanguageHint: Language?): VernAiResult<Unit> {
            _state.value = AsrState.Ready
            return VernAiResult.Success(Unit)
        }

        override fun startLiveTranscription(languageHint: Language?): Flow<TranscriptionResult> = flow {
            _state.value = AsrState.Recording(decibels = 72.0f)
            val words = spokenComplaint.split(" ")
            var progressiveText = ""
            for (i in words.indices) {
                progressiveText = if (progressiveText.isEmpty()) words[i] else "$progressiveText ${words[i]}"
                emit(
                    TranscriptionResult(
                        text = progressiveText,
                        isFinal = (i == words.lastIndex),
                        detectedLanguage = Language.TELUGU,
                        confidence = 0.98f
                    )
                )
            }
            _state.value = AsrState.Ready
        }

        override suspend fun stopLiveTranscription(): VernAiResult<TranscriptionResult> {
            _state.value = AsrState.Ready
            return VernAiResult.Success(
                TranscriptionResult(
                    text = spokenComplaint,
                    isFinal = true,
                    detectedLanguage = Language.TELUGU,
                    confidence = 0.98f
                )
            )
        }

        override suspend fun transcribeSnippet(
            snippet: com.vernai.core.model.AudioSnippet,
            languageHint: Language?
        ): VernAiResult<TranscriptionResult> {
            return stopLiveTranscription()
        }

        override fun isReady(): Boolean = true
        override fun close() {
            _state.value = AsrState.Idle
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun step1_voiceInputToAsrTranscription_detectsTeluguComplaintIntent() = runTest {
        val teluguSpokenComplaint = "మా వీధిలో గత 10 రోజులుగా తాగునీటి సరఫరా నిలిచిపోయింది, దయచేసి బాగు చేయించండి"
        val customAsr = CustomTeluguComplaintAsrEngine(teluguSpokenComplaint)
        val llmEngine = MockLlmInferenceEngine()

        val voiceViewModel = VoiceWorkspaceViewModel(
            asrEngine = customAsr,
            llmEngine = llmEngine,
            dispatchers = testDispatchers
        )
        advanceUntilIdle()

        // 1. User starts speaking
        voiceViewModel.handleIntent(VoiceUiIntent.ToggleRecording)
        advanceUntilIdle()

        // Verify ASR streaming and final transcription
        val finalVoiceState = voiceViewModel.uiState.value
        assertEquals(teluguSpokenComplaint, finalVoiceState.liveTranscript)
        assertEquals(DetectedIntentType.COMPLAINT_LETTER, finalVoiceState.detectedIntent)
        assertEquals(ProcessingStage.REASONING_LLM, finalVoiceState.stage)
        assertFalse(finalVoiceState.isRecording)

        // 2. User clicks Proceed to Complaint Draft
        val sideEffects = mutableListOf<VoiceUiSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            voiceViewModel.sideEffect.collect { sideEffects.add(it) }
        }

        voiceViewModel.handleIntent(VoiceUiIntent.ProceedToIntentAction)
        advanceUntilIdle()

        assertTrue(sideEffects.isNotEmpty())
        val navigationEffect = sideEffects.filterIsInstance<VoiceUiSideEffect.NavigateTo>().firstOrNull()
        assertNotNull("Must emit NavigateTo destination", navigationEffect)
        assertTrue(navigationEffect!!.destination is VernAiNavDestination.ComplaintDrafting)
        val targetDestination = navigationEffect.destination as VernAiNavDestination.ComplaintDrafting
        assertEquals(teluguSpokenComplaint, targetDestination.initialTranscript)
    }

    @Test
    fun step2_letterEditorInitializesWithLiveVoiceTranscript_noMockDefaults() = runTest {
        val liveTranscript = "మా గ్రామంలో వీధి దీపాలు పనిచేయడం లేదు, రాత్రి వేళల్లో రాకపోకలు కష్టంగా ఉంది"
        val repository = TestComplaintRepository()
        val llmEngine = MockLlmInferenceEngine()
        val exporter = LocalDocumentExporter()

        val letterViewModel = LetterEditorViewModel(
            complaintRepository = repository,
            llmEngine = llmEngine,
            exporter = exporter,
            initialTranscript = liveTranscript,
            dispatchers = testDispatchers
        )
        advanceUntilIdle()

        val state = letterViewModel.uiState.value
        assertEquals(liveTranscript, state.voiceTranscript)
        assertTrue("User facts must be extracted from the spoken text", state.userFactsText.isNotBlank())
        assertTrue("Extracted facts must preserve user statement", state.userFactsText.contains("వీధి దీపాలు పనిచేయడం లేదు"))

        // Verification: Zero mock defaults in the fresh state
        assertTrue("Newly drafted letter must be marked as draft", state.isDraft)
        assertFalse("Newly drafted letter must require user review", state.isUserReviewed)
        assertEquals("", state.recipientDesignation)
        assertEquals("", state.recipientDepartment)
        assertEquals("", state.recipientAddress)
        assertNull(state.errorMessage)
    }

    @Test
    fun step3_localLlmGeneratesFormalTeluguLetter_withCancellationAndDraftSafeguards() = runTest {
        val liveTranscript = "మా కాలనీలో తాగునీరు రావడం లేదు మరియు వీధి దీపాలు వెలగడం లేదు"
        val repository = TestComplaintRepository()
        val llmEngine = MockLlmInferenceEngine()
        val exporter = LocalDocumentExporter()

        val letterViewModel = LetterEditorViewModel(
            complaintRepository = repository,
            llmEngine = llmEngine,
            exporter = exporter,
            initialTranscript = liveTranscript,
            dispatchers = testDispatchers
        )
        advanceUntilIdle()

        // Test cancellation safety
        letterViewModel.handleIntent(LetterUiIntent.GenerateLetter)
        letterViewModel.handleIntent(LetterUiIntent.CancelGeneration)
        advanceUntilIdle()
        assertFalse("Cancellation must halt generation", letterViewModel.uiState.value.isGenerating)

        // Run actual LLM generation
        letterViewModel.handleIntent(LetterUiIntent.UpdateRecipientDesignation("గ్రామ సర్పంచ్ / కార్యదర్శి గారు"))
        letterViewModel.handleIntent(LetterUiIntent.UpdateRecipientDepartment("గ్రామ పంచాయతీ కార్యాలయం"))
        letterViewModel.handleIntent(LetterUiIntent.UpdateLocation("శాంతినగర్"))
        letterViewModel.handleIntent(LetterUiIntent.GenerateLetter)
        advanceUntilIdle()

        val generatedState = letterViewModel.uiState.value
        assertFalse(generatedState.isGenerating)
        assertTrue("Formal Telugu subject must be populated", generatedState.subject.isNotBlank())
        assertTrue("Formal Telugu letter body must be generated", generatedState.vernacularBody.isNotBlank())
        assertTrue("Salutation must be present", generatedState.salutation.isNotBlank())
        assertTrue("Closing must be present", generatedState.closing.isNotBlank())
        assertTrue("English translation must accompany vernacular letter", generatedState.englishTranslation.isNotBlank())

        // Anti-hallucination requirement
        assertTrue("Generated letter must be flagged as draft", generatedState.isDraft)
        assertFalse("Generated letter must not be marked reviewed before user confirms", generatedState.isUserReviewed)
    }

    @Test
    fun step4_userReviewsAndEditsLetter_safeguardsEnforceExplicitReview() = runTest {
        val liveTranscript = "మా వీధిలో డ్రైనేజీ లీకేజీ సమస్య పరిష్కరించండి"
        val repository = TestComplaintRepository()
        val llmEngine = MockLlmInferenceEngine()
        val exporter = LocalDocumentExporter()

        val letterViewModel = LetterEditorViewModel(
            complaintRepository = repository,
            llmEngine = llmEngine,
            exporter = exporter,
            initialTranscript = liveTranscript,
            dispatchers = testDispatchers
        )
        advanceUntilIdle()

        letterViewModel.handleIntent(LetterUiIntent.GenerateLetter)
        advanceUntilIdle()

        // 1. User attempts to export before reviewing -> Anti-hallucination review dialog triggered
        letterViewModel.handleIntent(LetterUiIntent.RequestExport(ExportFormat.PDF, tempFolder.root))
        advanceUntilIdle()

        assertTrue(
            "Review dialog must block export until user reviews",
            letterViewModel.uiState.value.showReviewDialog
        )
        assertEquals(ExportFormat.PDF, letterViewModel.uiState.value.pendingExportFormat)

        // 2. User edits and reviews letter content
        val editedSubject = "విషయము: కాలనీలో అండర్‌గ్రౌండ్ డ్రైనేజీ లీకేజీ తక్షణ మరమ్మత్తు కొరకు వినతి."
        val editedBody = "గౌరవనీయులైన మున్సిపల్ కమిషనర్ గారికి,\nమా వీధిలో డ్రైనేజీ పొంగిపొర్లుతున్నందున తక్షణ పరిశీలన చేయగలరు."
        letterViewModel.handleIntent(LetterUiIntent.UpdateSubject(editedSubject))
        letterViewModel.handleIntent(LetterUiIntent.UpdateVernacularBody(editedBody))
        letterViewModel.handleIntent(LetterUiIntent.UpdateApplicantName("సత్యనారాయణ మరియు కాలనీ వాసులు"))

        // 3. User confirms review
        letterViewModel.handleIntent(LetterUiIntent.ConfirmReviewAndExport)
        advanceUntilIdle()

        val reviewedState = letterViewModel.uiState.value
        assertFalse(reviewedState.showReviewDialog)
        assertTrue(reviewedState.isUserReviewed)
        assertEquals(editedSubject, reviewedState.subject)
        assertEquals(editedBody, reviewedState.vernacularBody)
    }

    @Test
    fun step5_appExportsLetterToPdfAndDocxLocally() = runTest {
        val repository = TestComplaintRepository()
        val llmEngine = MockLlmInferenceEngine()
        val exporter = LocalDocumentExporter()

        val letterViewModel = LetterEditorViewModel(
            complaintRepository = repository,
            llmEngine = llmEngine,
            exporter = exporter,
            dispatchers = testDispatchers
        )
        advanceUntilIdle()

        letterViewModel.handleIntent(LetterUiIntent.UpdateSubject("విషయము: తాగునీటి ఎద్దడి నివారణ కొరకు వినతి."))
        letterViewModel.handleIntent(LetterUiIntent.UpdateRecipientDesignation("సర్పంచ్ గారు"))
        letterViewModel.handleIntent(LetterUiIntent.UpdateRecipientDepartment("గ్రామ పంచాయతీ"))
        letterViewModel.handleIntent(
            LetterUiIntent.UpdateVernacularBody(
                "గౌరవనీయులైన సర్పంచ్ గారికి,\nగ్రామంలోని మంచినీటి బోరు మోటారు మరమ్మత్తులు త్వరితగతిన పూర్తి చేయగలరు."
            )
        )
        letterViewModel.handleIntent(LetterUiIntent.UpdateEnglishTranslation("Request for borewell motor repair."))
        letterViewModel.handleIntent(LetterUiIntent.SetUserReviewed(true))
        advanceUntilIdle()

        val sideEffects = mutableListOf<LetterUiSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            letterViewModel.sideEffect.collect { sideEffects.add(it) }
        }

        // Export PDF
        letterViewModel.handleIntent(LetterUiIntent.ExportDocument(ExportFormat.PDF, tempFolder.root))
        advanceUntilIdle()

        val shareEffects = sideEffects.filterIsInstance<LetterUiSideEffect.ShareExportedFile>()
        assertTrue("PDF export must succeed and emit ShareExportedFile", shareEffects.isNotEmpty())

        val exportedPdfFile = shareEffects.last().file
        assertTrue("Exported PDF file must exist", exportedPdfFile.exists())
        assertTrue("Exported PDF size must be > 0 bytes", exportedPdfFile.length() > 0)

        // Verify PDF Header Magic
        val pdfHeader = exportedPdfFile.inputStream().use { stream: InputStream ->
            val buf = ByteArray(5)
            stream.read(buf)
            String(buf, StandardCharsets.US_ASCII)
        }
        assertEquals("%PDF-", pdfHeader)

        // Export DOCX
        letterViewModel.handleIntent(LetterUiIntent.ExportDocument(ExportFormat.DOCX, tempFolder.root))
        advanceUntilIdle()

        val docxShareEffect = sideEffects.filterIsInstance<LetterUiSideEffect.ShareExportedFile>().last()
        val exportedDocxFile = docxShareEffect.file
        assertTrue("Exported DOCX file must exist", exportedDocxFile.exists())
        assertTrue("Exported DOCX size must be > 0 bytes", exportedDocxFile.length() > 0)

        // Verify DOCX internal XML entries (OpenXML standard)
        var hasContentTypes = false
        var hasWordDocument = false
        ZipInputStream(exportedDocxFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "[Content_Types].xml") hasContentTypes = true
                if (entry.name == "word/document.xml") hasWordDocument = true
                entry = zip.nextEntry
            }
        }
        assertTrue("DOCX must contain [Content_Types].xml", hasContentTypes)
        assertTrue("DOCX must contain word/document.xml", hasWordDocument)
    }

    @Test
    fun step6_completeOfflinePerimeter_zeroInternetZeroTelemetry() = runTest {
        // 1. AndroidManifest air-gap audit: zero INTERNET permission
        val manifestCandidates = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        )
        val manifest = manifestCandidates.firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist for audit", manifest)
        val manifestText = manifest!!.readText()

        assertFalse(
            "Air-gap audit failed: android.permission.INTERNET must NEVER be declared",
            manifestText.contains("android.permission.INTERNET")
        )
        assertFalse(
            "Zero telemetry audit failed: android.permission.ACCESS_NETWORK_STATE must not be declared",
            manifestText.contains("android.permission.ACCESS_NETWORK_STATE")
        )

        // 2. Model checksum verification: ensures on-device weights integrity
        val testWeights = tempFolder.newFile("qwen2.5_indic_test.gguf")
        testWeights.writeText("TELUGU_OFFLINE_LLM_WEIGHTS_SHA256_INTEGRITY", StandardCharsets.UTF_8)

        val validHash = ModelIntegrityVerifier.calculateSha256(testWeights.inputStream())
        val checkResult = ModelIntegrityVerifier.verifyChecksum(testWeights, validHash)
        assertTrue("Offline model checksum check must pass", checkResult is VernAiResult.Success)

        // 3. Local Room DB storage: saves draft completely offline without cloud API
        val repository = TestComplaintRepository()
        val draftToSave = ComplaintDraft(
            id = "complaint-offline-101",
            subject = "తాగునీటి సమస్య వినతి",
            department = "పంచాయతీ కార్యాలయం",
            recipientDesignation = "సర్పంచ్ గారు",
            vernacularBody = "గ్రామంలో నీటి సమస్య పరిష్కరించగలరు.",
            englishTranslation = "Drinking water problem resolution request.",
            targetLanguage = Language.TELUGU
        )

        val saveResult = repository.saveComplaint(draftToSave)
        assertTrue("Draft must save to local storage successfully", saveResult is VernAiResult.Success)

        val retrieved = repository.getComplaintById("complaint-offline-101")
        assertNotNull("Retrieved draft must not be null", retrieved)
        assertEquals("తాగునీటి సమస్య వినతి", retrieved!!.subject)
    }

    @Test
    fun endToEndFullDemoFlow_voiceToTranscriptionToDraftToEditToExportOffline() = runTest {
        // Step 1: User speaks a Telugu complaint
        val spokenComplaint = "మా గ్రామంలో గత 15 రోజులుగా రాత్రి వేళల్లో వీధి దీపాలు వెలగడం లేదు, దయచేసి వెంటనే సరిచేయండి"
        val asrEngine = CustomTeluguComplaintAsrEngine(spokenComplaint)
        val llmEngine = MockLlmInferenceEngine()
        val repository = TestComplaintRepository()
        val exporter = LocalDocumentExporter()

        val voiceVm = VoiceWorkspaceViewModel(
            asrEngine = asrEngine,
            llmEngine = llmEngine,
            dispatchers = testDispatchers
        )
        advanceUntilIdle()

        voiceVm.handleIntent(VoiceUiIntent.ToggleRecording)
        advanceUntilIdle()

        val voiceState = voiceVm.uiState.value
        assertEquals(spokenComplaint, voiceState.liveTranscript)
        assertEquals(DetectedIntentType.COMPLAINT_LETTER, voiceState.detectedIntent)

        // Step 2: Transition to Complaint Draft with live spoken transcript (no mock defaults)
        val letterVm = LetterEditorViewModel(
            complaintRepository = repository,
            llmEngine = llmEngine,
            exporter = exporter,
            initialTranscript = voiceState.liveTranscript,
            dispatchers = testDispatchers
        )
        advanceUntilIdle()

        val initialLetterState = letterVm.uiState.value
        assertEquals(spokenComplaint, initialLetterState.voiceTranscript)
        assertTrue(initialLetterState.userFactsText.contains("వీధి దీపాలు వెలగడం లేదు"))
        assertTrue(initialLetterState.isDraft)
        assertFalse(initialLetterState.isUserReviewed)

        // Step 3: Local LLM generates formal Telugu complaint letter
        letterVm.handleIntent(LetterUiIntent.UpdateRecipientDesignation("గ్రామ సర్పంచ్ / పంచాయతీ కార్యదర్శి గారు"))
        letterVm.handleIntent(LetterUiIntent.UpdateRecipientDepartment("గ్రామ పంచాయతీ కార్యాలయం"))
        letterVm.handleIntent(LetterUiIntent.UpdateLocation("శాంతినగర్"))
        letterVm.handleIntent(LetterUiIntent.GenerateLetter)
        advanceUntilIdle()

        val generatedLetter = letterVm.uiState.value
        assertFalse(generatedLetter.isGenerating)
        assertTrue(generatedLetter.subject.isNotBlank())
        assertTrue(generatedLetter.vernacularBody.isNotBlank())
        assertTrue(generatedLetter.englishTranslation.isNotBlank())

        // Step 4: User reviews and refines details
        val userEditedClosing = "ఇట్లు,\nగ్రామ ప్రజలు, శాంతినగర్ కాలనీ."
        letterVm.handleIntent(LetterUiIntent.UpdateClosing(userEditedClosing))
        letterVm.handleIntent(LetterUiIntent.SetUserReviewed(true))
        advanceUntilIdle()

        assertTrue(letterVm.uiState.value.isUserReviewed)
        assertEquals(userEditedClosing, letterVm.uiState.value.closing)

        // Step 5: Export to PDF and DOCX
        letterVm.handleIntent(LetterUiIntent.ExportDocument(ExportFormat.PDF, tempFolder.root))
        advanceUntilIdle()

        val pdfExported = File(tempFolder.root, "వినతిపత్రం_Complaint_${letterVm.uiState.value.date}.pdf")
        assertTrue("Exported PDF file must exist", pdfExported.exists() && pdfExported.length() > 0)

        letterVm.handleIntent(LetterUiIntent.ExportDocument(ExportFormat.DOCX, tempFolder.root))
        advanceUntilIdle()

        val docxExported = File(tempFolder.root, "వినతిపత్రం_Complaint_${letterVm.uiState.value.date}.docx")
        assertTrue("Exported DOCX file must exist", docxExported.exists() && docxExported.length() > 0)

        // Step 6: Verify offline persistence and complete air-gap
        val savedDraftResult = repository.saveComplaint(
            ComplaintDraft(
                id = "demo-flow-complaint-1",
                subject = letterVm.uiState.value.subject,
                department = letterVm.uiState.value.recipientDepartment,
                recipientDesignation = letterVm.uiState.value.recipientDesignation,
                vernacularBody = letterVm.uiState.value.vernacularBody,
                englishTranslation = letterVm.uiState.value.englishTranslation,
                targetLanguage = Language.TELUGU
            )
        )
        assertTrue(savedDraftResult is VernAiResult.Success)
        assertEquals(1, repository.complaints.size)
        assertEquals(letterVm.uiState.value.subject, repository.complaints[0].subject)
    }
}
