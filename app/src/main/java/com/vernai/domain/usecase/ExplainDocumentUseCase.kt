package com.vernai.domain.usecase

import com.vernai.ai.llm.GenerationParameters
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import com.vernai.core.common.mutex.InferenceLock
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.Language
import com.vernai.domain.repository.DocumentRepository
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Domain Use Case for zero-hallucination Telugu explanation of civic and legal documents,
 * supporting full-document summarization as well as focused explanations of user-selected text snippets.
 */
class ExplainDocumentUseCase(
    private val llmEngine: LlmInferenceEngine,
    private val repository: DocumentRepository? = null,
    private val inferenceLock: InferenceLock = InferenceLock(),
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) {

    /**
     * Generates a structured zero-hallucination summary of the document.
     */
    suspend fun summarizeDocument(
        documentTitle: String,
        documentText: String,
        targetLanguage: Language = Language.TELUGU
    ): VernAiResult<ExplanationReport> = withContext(dispatchers.default) {
        val prompt = buildSummarizationPrompt(documentText, targetLanguage)

        val llmResult = inferenceLock.withLlmLock {
            llmEngine.generateCompleteText(
                prompt = prompt,
                params = GenerationParameters(temperature = 0.2f)
            )
        }

        val report = when (llmResult) {
            is VernAiResult.Success -> {
                parseExplanationReport(llmResult.data, documentTitle, documentText, targetLanguage)
            }
            is VernAiResult.Error -> {
                buildDeterministicFallbackReport(documentTitle, documentText, targetLanguage)
            }
            is VernAiResult.Loading -> {
                buildDeterministicFallbackReport(documentTitle, documentText, targetLanguage)
            }
        }

        // Persist to Room database if repository provided
        repository?.let { repo ->
            withContext(dispatchers.io) {
                repo.saveExplanation(report)
            }
        }

        VernAiResult.Success(report)
    }

    /**
     * Explains a specific user-selected excerpt or chunk in clear Telugu,
     * clarifying bureaucratic clauses without inventing extra conditions.
     */
    suspend fun explainSelectedText(
        selectedText: String,
        targetLanguage: Language = Language.TELUGU
    ): VernAiResult<String> = withContext(dispatchers.default) {
        val prompt = """
            <|im_start|>system
            You are a civic legal explainer for Indian citizens.
            Explain the following selected document excerpt in simple, everyday ${targetLanguage.englishName} (${targetLanguage.nativeName}).
            
            ZERO-HALLUCINATION RULES:
            1. Rely STRICTLY on the excerpt provided below.
            2. Do NOT invent consequences, fines, or rules not mentioned.
            3. Clarify in 2 to 4 simple, conversational sentences what this specific clause means for the citizen.
            <|im_end|>
            <|im_start|>user
            Selected Text:
            "$selectedText"
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()

        val llmResult = inferenceLock.withLlmLock {
            llmEngine.generateCompleteText(
                prompt = prompt,
                params = GenerationParameters(temperature = 0.2f)
            )
        }

        return@withContext when (llmResult) {
            is VernAiResult.Success -> VernAiResult.Success(llmResult.data.trim())
            is VernAiResult.Error -> {
                val fallbackExplanation = "ఎంచుకున్న భాగం వివరణ: $selectedText గురించి సంబంధిత కార్యాలయంలో నిర్ధారించుకోవాలి. పత్రంలో పేర్కొన్న సూచనలను ఖచ్చితంగా పాటించండి."
                VernAiResult.Success(fallbackExplanation)
            }
            is VernAiResult.Loading -> VernAiResult.Loading(llmResult.progress, llmResult.stage)
        }
    }

    private fun buildSummarizationPrompt(documentText: String, targetLanguage: Language): String {
        return """
            <|im_start|>system
            You are a strict, factual legal and civic document explainer for citizens.
            Analyze the extracted document text and explain it in clear, simple everyday ${targetLanguage.englishName} (${targetLanguage.nativeName}).
            
            CRITICAL ZERO-HALLUCINATION RULES:
            1. STRICT FACTUAL ADHERENCE: Base your entire response ONLY on the provided text.
            2. NEVER INVENT: Do NOT assume or invent unmentioned fees, penalties, legal sections, or deadlines.
            3. EXPLICIT OMISSION: If the document mentions NO deadline or fee, explicitly state "పత్రంలో ఎలాంటి గడువు లేదా రుసుము పేర్కొనబడలేదు".
            
            Structure the response as:
            [SUMMARY]
            (2-3 simple sentences explaining what this document is)
            [ACTIONS]
            - (Action item 1 directly from text)
            - (Action item 2 directly from text)
            [DEADLINES_AND_FEES]
            - (Deadline or fee mentioned, OR state "ఎలాంటి గడువు పేర్కొనబడలేదు")
            <|im_end|>
            <|im_start|>user
            Document Text:
            $documentText
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    private fun parseExplanationReport(
        rawOutput: String,
        documentTitle: String,
        documentText: String,
        targetLanguage: Language
    ): ExplanationReport {
        val summaryRegex = Regex("""\[SUMMARY\]\s*([\s\S]*?)(?=\[ACTIONS\]|$)""")
        val actionsRegex = Regex("""\[ACTIONS\]\s*([\s\S]*?)(?=\[DEADLINES_AND_FEES\]|$)""")
        val deadlinesRegex = Regex("""\[DEADLINES_AND_FEES\]\s*([\s\S]*?)$""")

        val summaryMatch = summaryRegex.find(rawOutput)?.groupValues?.get(1)?.trim()
        val actionsMatch = actionsRegex.find(rawOutput)?.groupValues?.get(1)?.trim()
        val deadlinesMatch = deadlinesRegex.find(rawOutput)?.groupValues?.get(1)?.trim()

        val summary = summaryMatch?.ifBlank { null }
            ?: extractFirstSentences(documentText, 2)

        val actionPoints = actionsMatch?.lines()
            ?.map { it.trim().removePrefix("-").removePrefix("*").trim() }
            ?.filter { it.isNotBlank() }
            ?: listOf("పత్రంలో పేర్కొన్న వివరాలను పరిశీలించి అవసరమైన ధృవపత్రాలు సిద్ధం చేసుకోండి.")

        val deadlines = deadlinesMatch?.lines()
            ?.map { it.trim().removePrefix("-").removePrefix("*").trim() }
            ?.filter { it.isNotBlank() }
            ?: listOf("పత్రంలో ప్రత్యేక గడువు లేదా రుసుము వివరాలు పేర్కొనబడలేదు.")

        return ExplanationReport(
            id = UUID.randomUUID().toString(),
            sourceDocumentName = documentTitle,
            extractedCharacterCount = documentText.length,
            summaryInVernacular = summary,
            keyActionPoints = actionPoints,
            legalDeadlines = deadlines,
            targetLanguage = targetLanguage
        )
    }

    fun buildDeterministicFallbackReport(
        documentTitle: String,
        documentText: String,
        targetLanguage: Language
    ): ExplanationReport {
        val lines = documentText.lines().filter { it.isNotBlank() }
        val summary = if (lines.isNotEmpty()) {
            "ఈ పత్రం ${lines.firstOrNull() ?: documentTitle} కు సంబంధించిన సమాచారాన్ని తెలియజేస్తున్నది. పౌరులు తమ హక్కులు మరియు విధులను సరిచూసుకోవడానికి జారీ చేయబడింది."
        } else {
            "పత్రం నుండి సమాచారం పొందబడింది."
        }

        val actions = mutableListOf<String>()
        val deadlines = mutableListOf<String>()

        // Deterministically scan for keywords
        lines.forEach { line ->
            val lower = line.lowercase()
            if (line.contains("సర్వే") || line.contains("ఆధార్") || line.contains("హాజరు") || line.contains("చెల్లించవలెను") || line.contains("చేసుకోవాలి")) {
                actions.add(line.trim())
            }
            if (line.contains("గడువు") || line.contains("తేదీ") || line.contains("రూ.") || line.contains("రోజులలోపు") || line.contains("ముగిసినచో")) {
                deadlines.add(line.trim())
            }
        }

        if (actions.isEmpty()) {
            actions.add("పత్రంలో పేర్కొన్న సూచనల ప్రకారం సంబంధిత కార్యాలయాన్ని సంప్రదించండి.")
        }
        if (deadlines.isEmpty()) {
            deadlines.add("పత్రంలో నిర్దిష్ట గడువు తేదీ లేదా రుసుము పేర్కొనబడలేదు.")
        }

        return ExplanationReport(
            id = UUID.randomUUID().toString(),
            sourceDocumentName = documentTitle,
            extractedCharacterCount = documentText.length,
            summaryInVernacular = summary,
            keyActionPoints = actions.take(5),
            legalDeadlines = deadlines.take(3),
            targetLanguage = targetLanguage
        )
    }

    private fun extractFirstSentences(text: String, count: Int): String {
        val sentences = text.split(Regex("""(?<=[.!?|])\s+""")).filter { it.isNotBlank() }
        return sentences.take(count).joinToString(" ")
    }
}
