package com.vernai.ui.letter

import androidx.lifecycle.viewModelScope
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.Language
import com.vernai.document.export.DocumentExporter
import com.vernai.document.export.ExportConfig
import com.vernai.document.export.ExportFormat
import com.vernai.document.export.LocalDocumentExporter
import com.vernai.domain.model.letter.LetterInput
import com.vernai.domain.model.letter.LetterRecipient
import com.vernai.domain.model.letter.LetterType
import com.vernai.domain.repository.ComplaintRepository
import com.vernai.domain.usecase.GenerateTeluguFormalLetterUseCase
import com.vernai.ui.common.MviViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class LetterEditorViewModel(
    private val complaintRepository: ComplaintRepository? = null,
    private val llmEngine: LlmInferenceEngine = MockLlmInferenceEngine(),
    private val exporter: DocumentExporter = LocalDocumentExporter(),
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) : MviViewModel<LetterUiState, LetterUiIntent, LetterUiSideEffect>(LetterUiState()) {

    private val generateLetterUseCase = GenerateTeluguFormalLetterUseCase(
        llmEngine = llmEngine,
        repository = complaintRepository,
        dispatchers = dispatchers
    )

    override fun handleIntent(intent: LetterUiIntent) {
        when (intent) {
            is LetterUiIntent.UpdateVoiceTranscript -> setState { copy(voiceTranscript = intent.transcript) }
            is LetterUiIntent.UpdateLetterType -> setState { copy(letterType = intent.letterType) }
            is LetterUiIntent.UpdateRecipientDesignation -> setState { copy(recipientDesignation = intent.designation) }
            is LetterUiIntent.UpdateRecipientDepartment -> setState { copy(recipientDepartment = intent.department) }
            is LetterUiIntent.UpdateRecipientAddress -> setState { copy(recipientAddress = intent.address) }
            is LetterUiIntent.UpdateUserFacts -> setState { copy(userFactsText = intent.factsText) }
            is LetterUiIntent.UpdateLocation -> setState { copy(location = intent.location) }
            is LetterUiIntent.UpdateDate -> setState { copy(date = intent.date) }
            is LetterUiIntent.UpdateApplicantName -> setState { copy(applicantName = intent.name) }

            is LetterUiIntent.GenerateLetter -> generateLetter(isRegenerate = false)
            is LetterUiIntent.RegenerateLetter -> generateLetter(isRegenerate = true)

            is LetterUiIntent.UpdateSubject -> setState { copy(subject = intent.subject) }
            is LetterUiIntent.UpdateSalutation -> setState { copy(salutation = intent.salutation) }
            is LetterUiIntent.UpdateVernacularBody -> setState { copy(vernacularBody = intent.body) }
            is LetterUiIntent.UpdateClosing -> setState { copy(closing = intent.closing) }
            is LetterUiIntent.UpdateSignaturePlaceholder -> setState { copy(signaturePlaceholder = intent.signature) }
            is LetterUiIntent.UpdateEnglishTranslation -> setState { copy(englishTranslation = intent.translation) }

            is LetterUiIntent.SwitchTab -> setState { copy(activeTab = intent.tabIndex) }
            is LetterUiIntent.SaveLetterDraft -> saveDraft()
            is LetterUiIntent.ExportDocument -> exportLetter(intent.format, intent.cacheDir)
            is LetterUiIntent.RequestExport -> handleExportRequest(intent.format, intent.cacheDir)
            is LetterUiIntent.SetUserReviewed -> setState { copy(isUserReviewed = intent.isReviewed) }
            is LetterUiIntent.ConfirmReviewAndExport -> confirmReviewAndExport()
            is LetterUiIntent.DismissReviewDialog -> setState { copy(showReviewDialog = false, pendingExportFormat = null) }
            is LetterUiIntent.LoadSampleFacts -> loadSampleFacts()
        }
    }

    private fun generateLetter(isRegenerate: Boolean) {
        if (isRegenerate) {
            setState { copy(isRegenerating = true, isUserReviewed = false, isDraft = true) }
        } else {
            setState { copy(isGenerating = true, isUserReviewed = false, isDraft = true) }
        }

        viewModelScope.launch(dispatchers.default) {
            val factsList = uiState.value.userFactsText
                .lines()
                .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                .filter { it.isNotBlank() }

            val letterInput = LetterInput(
                teluguVoiceTranscript = uiState.value.voiceTranscript,
                letterType = uiState.value.letterType,
                recipient = LetterRecipient(
                    designation = uiState.value.recipientDesignation,
                    departmentOrOffice = uiState.value.recipientDepartment,
                    officeAddress = uiState.value.recipientAddress
                ),
                userProvidedFacts = factsList,
                location = uiState.value.location.ifBlank { null },
                date = uiState.value.date.ifBlank { null },
                applicantName = uiState.value.applicantName.ifBlank { null }
            )

            val result = generateLetterUseCase.execute(letterInput)

            when (result) {
                is VernAiResult.Success -> {
                    val structured = result.data
                    setState {
                        copy(
                            isGenerating = false,
                            isRegenerating = false,
                            structuredLetter = structured,
                            subject = structured.subject,
                            salutation = structured.salutation,
                            vernacularBody = structured.formalTeluguLetterBody,
                            closing = structured.closing,
                            signaturePlaceholder = "${structured.signaturePlaceholders.signatureLine}\n(${structured.signaturePlaceholders.applicantName})",
                            englishTranslation = structured.englishTranslation,
                            preservedFacts = structured.preservedFacts,
                            isDraft = true,
                            isUserReviewed = false,
                            activeTab = 1 // Automatically switch to Preview & Edit Tab
                        )
                    }
                    val msg = if (isRegenerate) {
                        "లేఖ తిరిగి రూపొందించబడింది (Regenerated)"
                    } else {
                        "తెలుగు అధికారిక లేఖ సిద్ధమైంది (Generated)"
                    }
                    sendSideEffect(LetterUiSideEffect.ShowToast(msg))
                }
                is VernAiResult.Error -> {
                    setState { copy(isGenerating = false, isRegenerating = false) }
                    sendSideEffect(LetterUiSideEffect.ShowToast("Error: ${result.message}"))
                }
                is VernAiResult.Loading -> Unit
            }
        }
    }

    private var pendingExportCacheDir: File? = null

    private fun handleExportRequest(format: ExportFormat, cacheDir: File) {
        pendingExportCacheDir = cacheDir
        if (uiState.value.isUserReviewed) {
            exportLetter(format, cacheDir)
        } else {
            setState { copy(showReviewDialog = true, pendingExportFormat = format) }
        }
    }

    private fun confirmReviewAndExport() {
        val format = uiState.value.pendingExportFormat ?: ExportFormat.PDF
        val cacheDir = pendingExportCacheDir ?: File(".")
        setState { copy(isUserReviewed = true, showReviewDialog = false) }
        exportLetter(format, cacheDir)
    }

    private fun saveDraft() {
        val repo = complaintRepository ?: return
        setState { copy(isSaving = true) }
        viewModelScope.launch(dispatchers.io) {
            val draft = ComplaintDraft(
                id = UUID.randomUUID().toString(),
                subject = uiState.value.subject,
                department = uiState.value.recipientDepartment,
                recipientDesignation = uiState.value.recipientDesignation,
                vernacularBody = uiState.value.vernacularBody,
                englishTranslation = uiState.value.englishTranslation,
                targetLanguage = Language.TELUGU,
                senderName = uiState.value.applicantName.ifBlank { null },
                location = uiState.value.location.ifBlank { null }
            )
            repo.saveComplaint(draft)
            setState { copy(isSaving = false) }
            sendSideEffect(LetterUiSideEffect.ShowToast("వినతిపత్రం భద్రపరచబడింది (Saved to Room DB)"))
        }
    }

    private fun exportLetter(format: ExportFormat, cacheDir: File) {
        setState { copy(isExporting = true) }
        viewModelScope.launch(dispatchers.io) {
            val destination = File(cacheDir, "FormalLetter_${System.currentTimeMillis()}.${format.extension}")
            val finalSubject = if (!uiState.value.isUserReviewed) {
                "[చిత్తు ప్రతి - ధృవీకరణ అవసరం] ${uiState.value.subject}"
            } else {
                uiState.value.subject
            }
            val finalBody = if (!uiState.value.isUserReviewed) {
                "*** [చిత్తు ప్రతి - ధృవీకరణ అవసరం / DRAFT - VERIFICATION REQUIRED] ***\n\n" + uiState.value.vernacularBody
            } else {
                uiState.value.vernacularBody
            }
            val draft = ComplaintDraft(
                subject = finalSubject,
                department = uiState.value.recipientDepartment,
                recipientDesignation = uiState.value.recipientDesignation,
                vernacularBody = finalBody,
                englishTranslation = uiState.value.englishTranslation,
                targetLanguage = Language.TELUGU,
                senderName = uiState.value.applicantName.ifBlank { null },
                location = uiState.value.location.ifBlank { null }
            )
            val result = exporter.exportComplaintLetter(
                draft = draft,
                destinationFile = destination,
                config = ExportConfig(format = format, targetLanguage = Language.TELUGU)
            )
            when (result) {
                is VernAiResult.Success -> {
                    setState { copy(isExporting = false, exportedFile = result.data) }
                    sendSideEffect(LetterUiSideEffect.ShareExportedFile(result.data, format.mimeType))
                }
                is VernAiResult.Error -> {
                    setState { copy(isExporting = false) }
                    sendSideEffect(LetterUiSideEffect.ShowToast("Export Error: ${result.message}"))
                }
                is VernAiResult.Loading -> Unit
            }
        }
    }

    private fun loadSampleFacts() {
        setState {
            copy(
                letterType = LetterType.COMPLAINT,
                recipientDesignation = "పంచాయతీ కార్యదర్శి / సర్పంచ్ గారు",
                recipientDepartment = "గ్రామ పంచాయతీ కార్యాలయం",
                recipientAddress = "మండల పరిషత్, ఖమ్మం జిల్లా",
                voiceTranscript = "మా గ్రామం శాంతినగర్‌లో గత 10 రోజులుగా వీధి దీపాలు వెలగడం లేదు మరియు మెయిన్ పైప్‌లైన్ పగిలిపోయి 4 రోజులుగా మంచినీటి సరఫరా నిలిచిపోయింది.",
                userFactsText = """
                    1. గత 10 రోజులుగా శాంతినగర్ కాలనీలో వీధి దీపాలు పనిచేయడం లేదు.
                    2. ప్రధాన పైప్‌లైన్ పగిలిపోయినందున 4 రోజులుగా తాగునీటి సరఫరా నిలిచిపోయింది.
                    3. దాదాపు 150 కుటుంబాలు తాగునీటికి మరియు రాత్రి రాకపోకలకు తీవ్ర ఇబ్బందులు పడుతున్నాయి.
                """.trimIndent(),
                location = "శాంతినగర్",
                date = "13-09-2026",
                applicantName = "శాంతినగర్ కాలనీ గ్రామస్తులు",
                activeTab = 0
            )
        }
        sendSideEffect(LetterUiSideEffect.ShowToast("నమూనా వివరాలు లోడ్ అయ్యాయి (Sample Loaded)"))
    }
}
