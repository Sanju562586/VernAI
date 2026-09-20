package com.vernai.domain.usecase

import com.vernai.ai.llm.GenerationParameters
import com.vernai.ai.llm.GrammarConstraint
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.ai.parser.IndicPromptTemplate
import com.vernai.ai.parser.SalesLogParser
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.Language
import com.vernai.core.model.SalesLog
import com.vernai.domain.repository.SalesLogRepository
import com.vernai.sales.processing.DuplicatePreventionEngine
import com.vernai.sales.processing.MultilingualSalesParser
import com.vernai.sales.processing.SalesArithmeticValidator
import com.vernai.sales.processing.TeluguSalesParser
import kotlinx.coroutines.withContext

/**
 * UseCase coordinating deterministic multilingual (Telugu, Tamil, Hindi, English) number parsing,
 * arithmetic validation, duplicate prevention, and fallback to local LLM only for semantic extraction where necessary.
 */
class ExtractSalesLogUseCase(
    private val llmEngine: LlmInferenceEngine,
    private val parser: SalesLogParser = SalesLogParser(),
    private val teluguParser: TeluguSalesParser = TeluguSalesParser(),
    private val multilingualParser: MultilingualSalesParser = MultilingualSalesParser(teluguParser = teluguParser),
    private val repository: SalesLogRepository,
    private val inferenceLock: InferenceLock = InferenceLock(),
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) {
    suspend operator fun invoke(
        spokenTranscript: String,
        language: Language = Language.TELUGU
    ): VernAiResult<SalesLog> = withContext(dispatchers.default) {
        if (spokenTranscript.isBlank()) {
            val emptyMsg = when (language) {
                Language.TAMIL -> "குரல் பதிவு காலியாக உள்ளது (Spoken transcript cannot be empty)"
                Language.HINDI, Language.MARATHI -> "आवाज रिकॉर्डिंग खाली है (Spoken transcript cannot be empty)"
                Language.ENGLISH -> "Spoken transcript cannot be empty"
                else -> "వాయిస్ రికార్డింగ్ ఖాళీగా ఉంది (Spoken transcript cannot be empty)"
            }
            return@withContext VernAiResult.Error(IllegalArgumentException(emptyMsg))
        }

        // STEP 1: Fast Deterministic Parsing across Indic and English languages
        val deterministicItems = multilingualParser.parseTranscript(spokenTranscript, language)

        val hasMeaningfulItems = deterministicItems.isNotEmpty() && deterministicItems.any {
            it.quantity > 0 || it.totalPrice > 0 || (it.originalTerm !in listOf("వస్తువు", "பொருள்", "सामग्री", "Item"))
        }

        if (hasMeaningfulItems) {
            // Run localized arithmetic validation & reconciliation on each item
            val reconciledItems = deterministicItems.map { rawItem ->
                SalesArithmeticValidator.validateAndReconcile(rawItem, language).item
            }

            // Check and flag duplicate entries in the same ledger
            val finalItems = DuplicatePreventionEngine.flagDuplicates(reconciledItems, language)
            val grandTotal = finalItems.sumOf { it.totalPrice }

            val salesLog = SalesLog(
                rawSpokenText = spokenTranscript,
                detectedLanguage = language,
                items = finalItems,
                grandTotal = grandTotal
            )

            // Persist offline to Room
            repository.saveSalesLog(salesLog)
            return@withContext VernAiResult.Success(salesLog)
        }

        // STEP 2: Local LLM semantic extraction when syntax is complex or ambiguous
        val prompt = IndicPromptTemplate.buildSalesLogPrompt(spokenTranscript, language)
        val params = GenerationParameters(
            temperature = 0.1f, // Low temperature for factual extraction
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
            is VernAiResult.Error -> {
                // If LLM fails, return deterministic best-effort rather than failing completely
                val fallbackItems = multilingualParser.parseTranscript(spokenTranscript, language).map {
                    SalesArithmeticValidator.validateAndReconcile(it, language).item
                }
                if (fallbackItems.isNotEmpty()) {
                    val log = SalesLog(
                        rawSpokenText = spokenTranscript,
                        detectedLanguage = language,
                        items = fallbackItems,
                        grandTotal = fallbackItems.sumOf { it.totalPrice }
                    )
                    repository.saveSalesLog(log)
                    VernAiResult.Success(log)
                } else {
                    generationResult
                }
            }
            is VernAiResult.Loading -> VernAiResult.Loading(generationResult.progress, generationResult.stage)
        }
    }
}
