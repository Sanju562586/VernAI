package com.vernai.domain.usecase

import com.vernai.ai.llm.GenerationParameters
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.ai.parser.TeluguLetterParser
import com.vernai.ai.parser.TeluguLetterPromptTemplate
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.Language
import com.vernai.domain.model.letter.LetterInput
import com.vernai.domain.model.letter.StructuredLetter
import com.vernai.domain.repository.ComplaintRepository
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Executes the Telugu Formal Letter generation pipeline:
 * 1. Formulates hallucination-minimized prompt.
 * 2. Runs local LLM inference under strict mutual exclusion lock.
 * 3. Parses and validates schema against anti-hallucination rules.
 * 4. Persists the resulting draft into Room database via [ComplaintRepository].
 */
class GenerateTeluguFormalLetterUseCase(
    private val llmEngine: LlmInferenceEngine,
    private val repository: ComplaintRepository? = null,
    private val parser: TeluguLetterParser = TeluguLetterParser(),
    private val inferenceLock: InferenceLock = InferenceLock(),
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) {

    suspend fun execute(input: LetterInput): VernAiResult<StructuredLetter> = withContext(dispatchers.default) {
        val prompt = TeluguLetterPromptTemplate.buildPrompt(input)

        val llmResult = inferenceLock.withLlmLock {
            llmEngine.generateCompleteText(
                prompt = prompt,
                params = GenerationParameters(temperature = 0.2f)
            )
        }

        val structuredLetter = when (llmResult) {
            is VernAiResult.Success -> {
                parser.parseWithFallback(llmResult.data, input)
            }
            is VernAiResult.Error -> {
                // If local LLM fails or is unavailable, use zero-hallucination deterministic template
                parser.buildDeterministicFallback(input)
            }
            is VernAiResult.Loading -> {
                parser.buildDeterministicFallback(input)
            }
        }

        // Persist to Room database
        repository?.let { repo ->
            withContext(dispatchers.io) {
                val draft = ComplaintDraft(
                    id = UUID.randomUUID().toString(),
                    subject = structuredLetter.subject,
                    department = input.recipient.departmentOrOffice,
                    recipientDesignation = input.recipient.designation,
                    vernacularBody = structuredLetter.formalTeluguLetterBody,
                    englishTranslation = structuredLetter.englishTranslation,
                    targetLanguage = Language.TELUGU,
                    senderName = input.applicantName ?: structuredLetter.signaturePlaceholders.applicantName,
                    location = structuredLetter.place
                )
                repo.saveComplaint(draft)
            }
        }

        VernAiResult.Success(structuredLetter)
    }
}
