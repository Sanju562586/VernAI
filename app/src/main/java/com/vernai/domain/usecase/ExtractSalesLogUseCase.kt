package com.vernai.domain.usecase

import com.vernai.ai.llm.GenerationParameters
import com.vernai.ai.llm.GrammarConstraint
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.ai.parser.IndicPromptTemplate
import com.vernai.ai.parser.SalesLogParser
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.Language
import com.vernai.core.model.SalesLog
import com.vernai.domain.repository.SalesLogRepository
import kotlinx.coroutines.withContext

/**
 * UseCase coordinating LLM grammar-constrained extraction and repository persistence.
 */
class ExtractSalesLogUseCase(
    private val llmEngine: LlmInferenceEngine,
    private val parser: SalesLogParser,
    private val repository: SalesLogRepository,
    private val inferenceLock: InferenceLock,
    private val dispatchers: VernAiDispatchers
) {
    suspend operator fun invoke(
        spokenTranscript: String,
        language: Language
    ): VernAiResult<SalesLog> = withContext(dispatchers.default) {
        if (spokenTranscript.isBlank()) {
            return@withContext VernAiResult.Error(IllegalArgumentException("Spoken transcript cannot be empty"))
        }

        val prompt = IndicPromptTemplate.buildSalesLogPrompt(spokenTranscript, language)
        val params = GenerationParameters(
            temperature = 0.1f, // Low temp for deterministic data extraction
            grammar = GrammarConstraint.SALES_LOG_JSON
        )

        val generationResult = inferenceLock.withLlmLock {
            llmEngine.generateCompleteText(prompt, params)
        }

        when (generationResult) {
            is VernAiResult.Success -> {
                val parsedLog = parser.parse(
                    rawOutput = generationResult.data,
                    spokenTranscript = spokenTranscript,
                    language = language
                )
                // Persist offline to Room
                repository.saveSalesLog(parsedLog)
                VernAiResult.Success(parsedLog)
            }
            is VernAiResult.Error -> generationResult
            is VernAiResult.Loading -> VernAiResult.Loading()
        }
    }
}
