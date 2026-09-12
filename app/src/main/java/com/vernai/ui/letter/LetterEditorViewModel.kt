package com.vernai.ui.letter

import androidx.lifecycle.viewModelScope
import com.vernai.ai.mock.MockDocumentExporter
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.document.export.DocumentExporter
import com.vernai.document.export.ExportConfig
import com.vernai.document.export.ExportFormat
import com.vernai.domain.repository.ComplaintRepository
import com.vernai.ui.common.MviViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class LetterEditorViewModel(
    private val complaintRepository: ComplaintRepository? = null,
    private val llmEngine: com.vernai.ai.llm.LlmInferenceEngine = com.vernai.ai.mock.MockLlmInferenceEngine(),
    private val exporter: DocumentExporter = MockDocumentExporter(),
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) : MviViewModel<LetterUiState, LetterUiIntent, LetterUiSideEffect>(LetterUiState()) {

    override fun handleIntent(intent: LetterUiIntent) {
        when (intent) {
            is LetterUiIntent.UpdateSubject -> setState { copy(subject = intent.subject) }
            is LetterUiIntent.UpdateDepartment -> setState { copy(department = intent.department) }
            is LetterUiIntent.UpdateVernacularBody -> setState { copy(vernacularBody = intent.body) }
            is LetterUiIntent.UpdateEnglishTranslation -> setState { copy(englishTranslation = intent.translation) }
            is LetterUiIntent.SwitchTab -> setState { copy(activeTab = intent.tabIndex) }
            is LetterUiIntent.RegenerateLetter -> regenerateWithLlm()
            is LetterUiIntent.SaveLetterDraft -> saveDraft()
            is LetterUiIntent.ExportDocument -> exportLetter(intent.format, intent.cacheDir)
        }
    }

    private fun regenerateWithLlm() {
        setState { copy(isRegenerating = true) }
        viewModelScope.launch(dispatchers.default) {
            val prompt = "గ్రామీణ పౌర సమస్య: ${uiState.value.subject} గురించి ${uiState.value.department} కు అధికారిక వినతిపత్రం ముసాయిదా రూపొందించండి."
            val result = llmEngine.generateCompleteText(prompt)
            val generatedBody = if (result is VernAiResult.Success && result.data.isNotBlank()) {
                result.data
            } else {
                """
గౌరవనీయులైన పంచాయతీ అధికారి గారికి,

మా ప్రాంతమైన శాంతినగర్‌లో విద్యుత్ దీపాలు మరియు తాగునీటి సమస్య తీవ్రంగా ఉన్నందున, ప్రజా శ్రేయస్సు దృష్ట్యా తక్షణ విచారణ చేపట్టి పరిష్కరించవలసిందిగా కోరుచున్నాము.

ధన్యవాదములతో,
గ్రామస్తులు.
                """.trimIndent()
            }

            setState {
                copy(
                    isRegenerating = false,
                    vernacularBody = generatedBody
                )
            }
            sendSideEffect(LetterUiSideEffect.ShowToast("ముసాయిదా తిరిగి రూపొందించబడింది (Regenerated)"))
        }
    }

    private fun saveDraft() {
        val repo = complaintRepository ?: return
        viewModelScope.launch(dispatchers.io) {
            val draft = ComplaintDraft(
                id = UUID.randomUUID().toString(),
                subject = uiState.value.subject,
                department = uiState.value.department,
                recipientDesignation = "The Officer in Charge",
                vernacularBody = uiState.value.vernacularBody,
                englishTranslation = uiState.value.englishTranslation,
                targetLanguage = uiState.value.selectedLanguage
            )
            repo.saveComplaint(draft)
            sendSideEffect(LetterUiSideEffect.ShowToast("లేఖ భద్రపరచబడింది (Letter Saved to Room DB)"))
        }
    }

    private fun exportLetter(format: ExportFormat, cacheDir: File) {
        setState { copy(isExporting = true) }
        viewModelScope.launch(dispatchers.io) {
            val destination = File(cacheDir, "Complaint_${System.currentTimeMillis()}.${format.extension}")
            val draft = ComplaintDraft(
                subject = uiState.value.subject,
                department = uiState.value.department,
                recipientDesignation = "The Officer in Charge",
                vernacularBody = uiState.value.vernacularBody,
                englishTranslation = uiState.value.englishTranslation,
                targetLanguage = uiState.value.selectedLanguage
            )
            val result = exporter.exportComplaintLetter(
                draft = draft,
                destinationFile = destination,
                config = ExportConfig(format = format, targetLanguage = uiState.value.selectedLanguage)
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
}
