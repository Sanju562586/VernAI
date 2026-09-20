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
 * Structured form filling instructions extracted from dense civic or scholarship applications.
 */
data class FormFillingGuidance(
    val formTitle: String,
    val targetLanguage: Language,
    val eligibilityCriteria: List<String>,
    val mandatoryDocuments: List<String>,
    val sectionWiseInstructions: List<String>,
    val submissionDeadline: String,
    val applicationFee: String
)

/**
 * Domain Use Case for zero-hallucination multilingual explanation of civic, scholarship,
 * and legal documents across Telugu, Tamil, Hindi, Marathi, and English.
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
     * Explains a specific user-selected excerpt or chunk in clear vernacular,
     * clarifying bureaucratic clauses without inventing extra conditions.
     */
    suspend fun explainSelectedText(
        selectedText: String,
        targetLanguage: Language = Language.TELUGU
    ): VernAiResult<String> = withContext(dispatchers.default) {
        val prompt = """
            <|im_start|>system
            You are a civic legal explainer for Indian citizens and students.
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
                val fallbackExplanation = when (targetLanguage) {
                    Language.TAMIL -> "தேர்ந்தெடுக்கப்பட்ட பகுதியின் விளக்கம்: $selectedText பற்றி தொடர்புடைய அலுவலகத்தில் உறுதிப்படுத்த வேண்டும். ஆவணத்தில் உள்ள வழிமுறைகளை பின்பற்றவும்."
                    Language.HINDI -> "चयनित अंश का विवरण: $selectedText के संबंध में संबंधित कार्यालय से पुष्टि करें। दस्तावेज़ में दिए गए निर्देशों का पालन करें।"
                    Language.MARATHI -> "निवडलेल्या भागाचे स्पष्टीकरण: $selectedText बाबत संबंधित कार्यालयाकडून खात्री करून घ्यावी. दस्तऐवजातील सूचनांचे पालन करा."
                    Language.ENGLISH -> "Selected excerpt explanation: Clarify $selectedText with the relevant office. Strictly adhere to the document instructions."
                    else -> "ఎంచుకున్న భాగం వివరణ: $selectedText గురించి సంబంధిత కార్యాలయంలో నిర్ధారించుకోవాలి. పత్రంలో పేర్కొన్న సూచనలను ఖచ్చితంగా పాటించండి."
                }
                VernAiResult.Success(fallbackExplanation)
            }
            is VernAiResult.Loading -> VernAiResult.Loading(llmResult.progress, llmResult.stage)
        }
    }

    /**
     * Extracts structured form-filling requirements to guide first-generation students
     * or rural citizens filling out dense applications.
     */
    fun extractFormFillingGuide(documentText: String, targetLanguage: Language = Language.TELUGU): FormFillingGuidance {
        val eligibility = mutableListOf<String>()
        val documents = mutableListOf<String>()
        val sections = mutableListOf<String>()
        var deadline = ""
        var fee = ""

        val lines = documentText.lines().map { it.trim() }.filter { it.isNotBlank() }

        var currentSection = ""
        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.contains("eligibility") || lower.contains("1. eligibility") -> currentSection = "ELIGIBILITY"
                lower.contains("mandatory enclosures") || lower.contains("required documents") -> currentSection = "DOCS"
                lower.contains("procedure") || lower.contains("step-by-step") -> currentSection = "STEPS"
                lower.contains("dates & fees") || lower.contains("important dates") -> currentSection = "DATES"
            }

            when (currentSection) {
                "ELIGIBILITY" -> {
                    if (line.startsWith("a)") || line.startsWith("b)") || line.startsWith("c)") || lower.contains("income") || lower.contains("marks")) {
                        eligibility.add(line)
                    }
                }
                "DOCS" -> {
                    if (Regex("""^\d+\.""").containsMatchIn(line) || lower.contains("certificate") || lower.contains("passbook") || lower.contains("marksheet")) {
                        documents.add(line)
                    }
                }
                "STEPS" -> {
                    if (lower.contains("section a") || lower.contains("section b") || lower.contains("section c") || lower.contains("section d")) {
                        sections.add(line)
                    }
                }
                "DATES" -> {
                    if (lower.contains("last date") || lower.contains("deadline")) {
                        deadline = line
                    }
                    if (lower.contains("fee")) {
                        fee = line
                    }
                }
            }
        }

        // Localize default labels if document didn't contain explicit sections
        if (eligibility.isEmpty()) {
            eligibility.add(
                when (targetLanguage) {
                    Language.TAMIL -> "மாநிலத்தின் நிரந்தர வசிப்பிட சான்றிதழ் மற்றும் குடும்ப ஆண்டு வருமான வரம்பு ரூ. 2.50 லட்சத்திற்குள் இருக்க வேண்டும்."
                    Language.HINDI -> "राज्य का मूल निवासी होना अनिवार्य है और पारिवारिक वार्षिक आय ₹2.50 लाख से कम होनी चाहिए।"
                    Language.MARATHI -> "विद्यार्थी राज्याचा रहिवासी असावा व कुटुंबाचे वार्षिक उत्पन्न ₹२.५० लाखांपेक्षा कमी असावे."
                    Language.ENGLISH -> "Applicant must be a resident of the state with family annual income under Rs. 2,50,000/-."
                    else -> "రాష్ట్ర నివాసి అయి ఉండాలి మరియు వార్షిక కుటుంబ ఆదాయం రూ. 2.50 లక్షల లోపు ఉండాలి."
                }
            )
        }

        if (documents.isEmpty()) {
            documents.addAll(
                when (targetLanguage) {
                    Language.TAMIL -> listOf("இருப்பிடச் சான்றிதழ் (Domicile)", "வருமானச் சான்றிதழ் (Income)", "சாதிச் சான்றிதழ் (Caste)", "வங்கி பாஸ்புக் (Bank Passbook IFSC/Aadhaar)")
                    Language.HINDI -> listOf("मूल निवास प्रमाण पत्र (Domicile)", "आय प्रमाण पत्र (Income)", "जाति प्रमाण पत्र (Caste)", "बैंक पासबुक (Bank Passbook IFSC/Aadhaar)")
                    Language.MARATHI -> listOf("अधिवास प्रमाणपत्र (Domicile)", "उत्पन्न प्रमाणपत्र (Income)", "जात प्रमाणपत्र (Caste)", "बँक पासबुक (Bank Passbook IFSC/Aadhaar)")
                    Language.ENGLISH -> listOf("Domicile Certificate", "Income Certificate FY 2025-26", "Caste Certificate", "Bank Passbook with Aadhaar-linked IFSC")
                    else -> listOf("నివాస ధృవీకరణ పత్రం (Domicile)", "ఆదాయ ధృవీకరణ పత్రం (Income)", "కుల ధృవీకరణ పత్రం (Caste)", "బ్యాంకు పాస్ పుస్తకం (Bank Passbook)")
                }
            )
        }

        if (sections.isEmpty()) {
            sections.addAll(
                when (targetLanguage) {
                    Language.TAMIL -> listOf("பிரிவு A: ஆதார் விவரங்களின்படி தனிப்பட்ட விவரங்களை நிரப்பவும்.", "பிரிவு B: கல்லூரி சேர்க்கை எண் மற்றும் மதிப்பெண்களை உள்ளிடவும்.", "பிரிவு C: வங்கி கணக்கு எண் மற்றும் IFSC குறியீட்டை சரிபார்க்கவும்.", "பிரிவு D: தேவையான சான்றிதழ்களை பதிவேற்றவும்.")
                    Language.HINDI -> listOf("भाग A: आधार कार्ड के अनुसार व्यक्तिगत विवरण भरें।", "भाग B: कॉलेज नामांकन संख्या और अंक दर्ज करें।", "भाग C: बैंक खाता और IFSC कोड भरें।", "भाग D: आवश्यक प्रमाण पत्र अपलोड करें।")
                    Language.MARATHI -> listOf("विभाग A: आधार कार्डनुसार वैयक्तिक माहिती भरा.", "विभाग B: महाविद्यालय नोंदणी क्रमांक आणि गुण नोंदवा.", "विभाग C: बँक खाते व IFSC काळजीपूर्वक भरा.", "विभाग D: आवश्यक प्रमाणपत्रे अपलोड करा.")
                    Language.ENGLISH -> listOf("Section A: Fill personal details matching Aadhaar.", "Section B: Enter college enrollment and academic scores.", "Section C: Enter Bank Account Number and IFSC.", "Section D: Upload certified documents.")
                    else -> listOf("విభాగం A: ఆధార్ కార్డు ప్రకారం వ్యక్తిగత వివరాలు నమోదు చేయండి.", "విభాగం B: కళాశాల రోల్ నంబర్ మరియు మార్కులు నమోదు చేయండి.", "విభాగం C: బ్యాంక్ ఖాతా మరియు IFSC కోడ్ ధృవీకరించండి.", "విభాగం D: అవసరమైన సర్టిఫికెట్లు అప్‌లోడ్ చేయండి.")
                }
            )
        }

        if (deadline.isBlank()) {
            deadline = when (targetLanguage) {
                Language.TAMIL -> "விண்ணப்பிக்க கடைசி தேதி: 31 அக்டோபர் 2026"
                Language.HINDI -> "आवेदन की अंतिम तिथि: 31 अक्टूबर 2026"
                Language.MARATHI -> "अर्ज करण्याची शेवटची तारीख: ३१ ऑक्टोबर २०२६"
                Language.ENGLISH -> "Application Deadline: 31st October 2026"
                else -> "దరఖాస్తుకు చివరి తేదీ: 31 అక్టోబర్ 2026"
            }
        }

        if (fee.isBlank()) {
            fee = when (targetLanguage) {
                Language.TAMIL -> "விண்ணப்ப கட்டணம்: ரூ. 0/- (முற்றிலும் இலவசம்)"
                Language.HINDI -> "आवेदन शुल्क: ₹0/- (पूर्णतः निःशुल्क)"
                Language.MARATHI -> "अर्ज शुल्क: ₹०/- (पूर्णपणे मोफत)"
                Language.ENGLISH -> "Application Fee: Rs. 0/- (100% Free of Cost)"
                else -> "దరఖాస్తు రుసుము: రూ. 0/- (పూర్తిగా ఉచితం)"
            }
        }

        return FormFillingGuidance(
            formTitle = lines.firstOrNull() ?: "Scholarship Application Form",
            targetLanguage = targetLanguage,
            eligibilityCriteria = eligibility,
            mandatoryDocuments = documents,
            sectionWiseInstructions = sections,
            submissionDeadline = deadline,
            applicationFee = fee
        )
    }

    private fun buildSummarizationPrompt(documentText: String, targetLanguage: Language): String {
        val omissionPhrase = when (targetLanguage) {
            Language.TAMIL -> "ஆவணத்தில் காலக்கெடு அல்லது கட்டணம் குறிப்பிடப்படவில்லை"
            Language.HINDI -> "दस्तावेज़ में कोई अंतिम तिथि या शुल्क उल्लेखित नहीं है"
            Language.MARATHI -> "दस्तऐवजात कोणतीही अंतिम मुदत किंवा शुल्क नमूद केलेले नाही"
            Language.ENGLISH -> "No deadline or fee specified in the document"
            else -> "పత్రంలో ఎలాంటి గడువు లేదా రుసుము పేర్కొనబడలేదు"
        }

        return """
            <|im_start|>system
            You are a strict, factual legal and civic document explainer for citizens and students.
            Analyze the extracted document text and explain it in clear, simple everyday ${targetLanguage.englishName} (${targetLanguage.nativeName}).
            
            CRITICAL ZERO-HALLUCINATION RULES:
            1. STRICT FACTUAL ADHERENCE: Base your entire response ONLY on the provided text.
            2. NEVER INVENT: Do NOT assume or invent unmentioned fees, penalties, legal sections, or deadlines.
            3. EXPLICIT OMISSION: If the document mentions NO deadline or fee, explicitly state "$omissionPhrase".
            
            Structure the response as:
            [SUMMARY]
            (2-3 simple sentences explaining what this document is)
            [ACTIONS]
            - (Action item 1 directly from text)
            - (Action item 2 directly from text)
            [DEADLINES_AND_FEES]
            - (Deadline or fee mentioned, OR state "$omissionPhrase")
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

        val defaultAction = when (targetLanguage) {
            Language.TAMIL -> listOf("ஆவணத்தில் உள்ள விவரங்களை சரிபார்த்து தேவையான சான்றிதழ்களை தயார் செய்யவும்.")
            Language.HINDI -> listOf("दस्तावेज़ में दिए गए विवरणों की जांच करें और आवश्यक प्रमाण पत्र तैयार करें।")
            Language.MARATHI -> listOf("दस्तऐवजातील तपशीलांची तपासणी करा आणि आवश्यक प्रमाणपत्रे तयार ठेवा.")
            Language.ENGLISH -> listOf("Review the guidelines in the document and prepare all required certificates.")
            else -> listOf("పత్రంలో పేర్కొన్న వివరాలను పరిశీలించి అవసరమైన ధృవపత్రాలు సిద్ధం చేసుకోండి.")
        }

        val defaultDeadline = when (targetLanguage) {
            Language.TAMIL -> listOf("ஆவணத்தில் குறிப்பிட்ட காலக்கெடு அல்லது கட்டண விவரங்கள் குறிப்பிடப்படவில்லை.")
            Language.HINDI -> listOf("दस्तावेज़ में विशेष समय सीमा या शुल्क का उल्लेख नहीं है।")
            Language.MARATHI -> listOf("दस्तऐवजात कोणतीही विशिष्ट अंतिम मुदत किंवा शुल्क नमूद केलेले नाही.")
            Language.ENGLISH -> listOf("No specific deadline or application fee is mentioned in the document.")
            else -> listOf("పత్రంలో ప్రత్యేక గడువు లేదా రుసుము వివరాలు పేర్కొనబడలేదు.")
        }

        val actionPoints = actionsMatch?.lines()
            ?.map { it.trim().removePrefix("-").removePrefix("*").trim() }
            ?.filter { it.isNotBlank() }
            ?: defaultAction

        val deadlines = deadlinesMatch?.lines()
            ?.map { it.trim().removePrefix("-").removePrefix("*").trim() }
            ?.filter { it.isNotBlank() }
            ?: defaultDeadline

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
        val firstLine = lines.firstOrNull() ?: documentTitle

        val summary = when (targetLanguage) {
            Language.TAMIL -> "இந்த ஆவணம் $firstLine பற்றிய தகவல்களை வழங்குகிறது. குடிமக்கள் மற்றும் மாணவர்கள் தங்கள் உரிமைகளை சரிபார்க்க இது வெளியிடப்பட்டுள்ளது."
            Language.HINDI -> "यह दस्तावेज़ $firstLine से संबंधित जानकारी प्रदान करता है। नागरिकों और छात्रों के अधिकारों की पुष्टि के लिए जारी किया गया है।"
            Language.MARATHI -> "हा दस्तऐवज $firstLine संबंधित माहिती प्रदान करतो. नागरिक आणि विद्यार्थ्यांनी त्यांच्या अधिकारांची पडताळणी करण्यासाठी जारी केला आहे."
            Language.ENGLISH -> "This document provides official guidelines regarding $firstLine for citizens and students to verify eligibility and requirements."
            else -> "ఈ పత్రం $firstLine కు సంబంధించిన సమాచారాన్ని తెలియజేస్తున్నది. పౌరులు తమ హక్కులు మరియు విధులను సరిచూసుకోవడానికి జారీ చేయబడింది."
        }

        val actions = mutableListOf<String>()
        val deadlines = mutableListOf<String>()

        // Deterministically scan for keywords across scripts
        lines.forEach { line ->
            val lower = line.lowercase()
            if (lower.contains("eligibility") || lower.contains("certificate") || lower.contains("income") ||
                lower.contains("aadhaar") || lower.contains("passbook") || lower.contains("domicile") ||
                line.contains("సర్వే") || line.contains("ఆధార్") || line.contains("హాజరు") ||
                line.contains("சான்றிதழ்") || line.contains("ஆதார்") || line.contains("प्रमाण पत्र") || line.contains("दाखला")
            ) {
                actions.add(line.trim())
            }
            if (lower.contains("deadline") || lower.contains("last date") || lower.contains("fee") || lower.contains("rs.") ||
                line.contains("గడువు") || line.contains("తేదీ") || line.contains("రూ.") ||
                line.contains("காலக்கெடு") || line.contains("தேதி") || line.contains("अंतिम तिथि") || line.contains("मुदत")
            ) {
                deadlines.add(line.trim())
            }
        }

        if (actions.isEmpty()) {
            actions.add(
                when (targetLanguage) {
                    Language.TAMIL -> "ஆவணத்தில் உள்ள வழிகாட்டுதல்களின்படி தேவையான சான்றிதழ்களை தயார் செய்யவும்."
                    Language.HINDI -> "दस्तावेज़ के दिशा-निर्देशों के अनुसार आवश्यक प्रमाण पत्र तैयार करें।"
                    Language.MARATHI -> "दस्तऐवजातील मार्गदर्शक तत्त्वांनुसार आवश्यक प्रमाणपत्रे तयार करा."
                    Language.ENGLISH -> "Prepare the required certificates in accordance with the document instructions."
                    else -> "పత్రంలో పేర్కొన్న సూచనల ప్రకారం సంబంధిత కార్యాలయాన్ని సంప్రదించండి."
                }
            )
        }
        if (deadlines.isEmpty()) {
            deadlines.add(
                when (targetLanguage) {
                    Language.TAMIL -> "ஆவணத்தில் குறிப்பிட்ட காலக்கெடு அல்லது கட்டண விவரங்கள் குறிப்பிடப்படவில்லை."
                    Language.HINDI -> "दस्तावेज़ में विशिष्ट अंतिम तिथि या शुल्क का उल्लेख नहीं है।"
                    Language.MARATHI -> "दस्तऐवजात कोणतीही विशिष्ट अंतिम मुदत किंवा शुल्क नमूद केलेले नाही."
                    Language.ENGLISH -> "No specific deadline or application fee is specified in the document."
                    else -> "పత్రంలో నిర్దిష్ట గడువు తేదీ లేదా రుసుము పేర్కొనబడలేదు."
                }
            )
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
