package com.vernai.domain.usecase

import com.vernai.ai.llm.GenerationParameters
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.ai.parser.IndicPromptTemplate
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.Language
import com.vernai.domain.repository.ComplaintRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID

class GenerateComplaintLetterUseCase(
    private val llmEngine: LlmInferenceEngine,
    private val repository: ComplaintRepository,
    private val inferenceLock: InferenceLock,
    private val dispatchers: VernAiDispatchers
) {
    /**
     * Streams generated tokens to the UI and saves the final letter draft to Room.
     */
    fun streamComplaintLetter(
        issueDescription: String,
        targetDepartment: String,
        language: Language,
        senderName: String? = null,
        location: String? = null
    ): Flow<String> = flow {
        val prompt = IndicPromptTemplate.buildComplaintLetterPrompt(
            issueDescription = issueDescription,
            targetDepartment = targetDepartment,
            language = language,
            senderName = senderName,
            location = location
        )

        val fullTextBuilder = StringBuilder()
        inferenceLock.withLlmLock {
            llmEngine.streamTokens(prompt, GenerationParameters(temperature = 0.3f))
                .collect { token ->
                    fullTextBuilder.append(token)
                    emit(token)
                }
        }

        // Parse and store draft
        val fullText = fullTextBuilder.toString()
        val draft = ComplaintDraft(
            id = UUID.randomUUID().toString(),
            subject = "Complaint regarding $targetDepartment",
            department = targetDepartment,
            recipientDesignation = "The Officer in Charge",
            vernacularBody = fullText,
            englishTranslation = "",
            targetLanguage = language,
            senderName = senderName,
            location = location
        )
        repository.saveComplaint(draft)
    }.flowOn(dispatchers.default)
}
