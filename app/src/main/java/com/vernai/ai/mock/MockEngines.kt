package com.vernai.ai.mock

import com.vernai.ai.asr.AsrEngine
import com.vernai.ai.asr.AsrState
import com.vernai.ai.llm.benchmark.ExecutionBackend
import com.vernai.ai.llm.GenerationParameters
import com.vernai.ai.llm.LlmEngineState
import com.vernai.ai.llm.LlmInferenceEngine
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.AudioSnippet
import com.vernai.core.model.Language
import com.vernai.core.model.TranscriptionResult
import com.vernai.document.export.DocumentExporter
import com.vernai.document.export.ExportConfig
import com.vernai.document.processing.DocumentProcessor
import com.vernai.document.processing.ExtractedDocument
import com.vernai.core.model.ComplaintDraft
import com.vernai.core.model.ExplanationReport
import com.vernai.core.model.SalesLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.InputStream

/**
 * Mock ASR Engine emitting realistic Telugu, Tamil, Hindi, and English speech transcription tokens.
 */
class MockAsrEngine : AsrEngine {
    private val _state = MutableStateFlow<AsrState>(AsrState.Ready)
    override val state: StateFlow<AsrState> = _state.asStateFlow()

    private var activeLanguage: Language = Language.TELUGU

    override suspend fun initialize(targetLanguageHint: Language?): VernAiResult<Unit> {
        targetLanguageHint?.let { activeLanguage = it }
        _state.value = AsrState.Ready
        return VernAiResult.Success(Unit)
    }

    override fun startLiveTranscription(languageHint: Language?): Flow<TranscriptionResult> = flow {
        val lang = languageHint ?: activeLanguage
        _state.value = AsrState.Recording(decibels = 65.0f)

        val sampleUtterances = when (lang) {
            Language.TAMIL -> listOf(
                "இன்று",
                "இன்று 5 கிலோ தக்காளி",
                "இன்று 5 கிலோ தக்காளி 200 ரூபாய்,",
                "இன்று 5 கிலோ தக்காளி 200 ரூபாய், 2 பாக்கெட் எண்ணெய் 260 ரூபாய் ரொக்கம் விற்றேன்."
            )
            Language.HINDI -> listOf(
                "आज",
                "आज 5 किलो टमाटर",
                "आज 5 किलो टमाटर 200 रुपये,",
                "आज 5 किलो टमाटर 200 रुपये, 2 पैकेट तेल 260 रुपये में बेचा।"
            )
            Language.ENGLISH -> listOf(
                "Today",
                "Today 5 kg tomato",
                "Today 5 kg tomato 200 rupees,",
                "Today 5 kg tomato 200 rupees and 2 packets oil 260 rupees sold."
            )
            else -> listOf(
                "ఈరోజు",
                "ఈరోజు 5 కేజీల టమాటా",
                "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు,",
                "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు, 2 నూనె ప్యాకెట్లు 260 రూపాయలు అమ్మిన."
            )
        }

        for (i in sampleUtterances.indices) {
            delay(400)
            val isFinal = i == sampleUtterances.lastIndex
            emit(
                TranscriptionResult(
                    text = sampleUtterances[i],
                    isFinal = isFinal,
                    detectedLanguage = lang,
                    confidence = 0.94f
                )
            )
        }
        _state.value = AsrState.Ready
    }

    override suspend fun stopLiveTranscription(): VernAiResult<TranscriptionResult> {
        _state.value = AsrState.Ready
        val text = when (activeLanguage) {
            Language.TAMIL -> "இன்று 5 கிலோ தக்காளி 200 ரூபாய், 2 பாக்கெட் எண்ணெய் 260 ரூபாய் ரொக்கம் விற்றேன்."
            Language.HINDI -> "आज 5 किलो टमाटर 200 रुपये, 2 पैकेट तेल 260 रुपये में बेचा।"
            Language.ENGLISH -> "Today 5 kg tomato 200 rupees and 2 packets oil 260 rupees sold."
            else -> "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు, 2 నూనె ప్యాకెట్లు 260 రూపాయలు అమ్మిన."
        }
        return VernAiResult.Success(
            TranscriptionResult(
                text = text,
                isFinal = true,
                detectedLanguage = activeLanguage,
                confidence = 0.96f
            )
        )
    }

    override suspend fun transcribeSnippet(snippet: AudioSnippet, languageHint: Language?): VernAiResult<TranscriptionResult> {
        languageHint?.let { activeLanguage = it }
        return stopLiveTranscription()
    }

    override fun isReady(): Boolean = true
    override fun close() {
        _state.value = AsrState.Idle
    }
}

/**
 * Mock LLM Engine simulating streaming tokens for Qwen2.5 GGUF across Telugu, Tamil, Hindi, and English.
 */
class MockLlmInferenceEngine : LlmInferenceEngine {
    private val _state = MutableStateFlow<LlmEngineState>(LlmEngineState.Ready)
    override val state: StateFlow<LlmEngineState> = _state.asStateFlow()

    override suspend fun loadModel(
        modelFile: File,
        contextLength: Int,
        nThreads: Int,
        backend: ExecutionBackend
    ): VernAiResult<Unit> {
        _state.value = LlmEngineState.Ready
        return VernAiResult.Success(Unit)
    }

    override fun streamTokens(prompt: String, params: GenerationParameters): Flow<String> = flow {
        _state.value = LlmEngineState.Generating(tokensGenerated = 0, tokensPerSec = 16.5f)

        val sampleText = if (params.grammar != null) {
            when {
                prompt.contains("Tamil", ignoreCase = true) || prompt.contains("தமிழ்") ->
                    """{"items":[{"original_term":"தக்காளி","standard_name":"Tomato","quantity":5.0,"unit":"kg","unit_price":40.0,"total_price":200.0},{"original_term":"எண்ணெய்","standard_name":"Cooking Oil","quantity":2.0,"unit":"packet","unit_price":130.0,"total_price":260.0}]}"""
                prompt.contains("Hindi", ignoreCase = true) || prompt.contains("हिन्दी") ->
                    """{"items":[{"original_term":"टमाटर","standard_name":"Tomato","quantity":5.0,"unit":"kg","unit_price":40.0,"total_price":200.0},{"original_term":"तेल","standard_name":"Cooking Oil","quantity":2.0,"unit":"packet","unit_price":130.0,"total_price":260.0}]}"""
                prompt.contains("English", ignoreCase = true) ->
                    """{"items":[{"original_term":"Tomato","standard_name":"Tomato","quantity":5.0,"unit":"kg","unit_price":40.0,"total_price":200.0},{"original_term":"Cooking Oil","standard_name":"Cooking Oil","quantity":2.0,"unit":"packet","unit_price":130.0,"total_price":260.0}]}"""
                else ->
                    """{"items":[{"original_term":"టమాటా","standard_name":"Tomato","quantity":5.0,"unit":"kg","unit_price":40.0,"total_price":200.0},{"original_term":"నూనె ప్యాకెట్లు","standard_name":"Cooking Oil","quantity":2.0,"unit":"packet","unit_price":130.0,"total_price":260.0}]}"""
            }
        } else {
            """
                విషయం: గ్రామ పంచాయతీ పరిధిలో వీధి దీపాలు మరియు తాగునీటి సమస్య పరిష్కారం కొరకు వినతిపత్రం.
                
                గౌరవనీయులైన సర్పంచ్ / పంచాయతీ కార్యదర్శి గారికి,
                
                మా గ్రామమైన శాంతినగర్ కాలనీలో గత వారం రోజులుగా వీధి దీపాలు పనిచేయడం లేదు. రాత్రి వేళల్లో ప్రజలు తీవ్ర ఇబ్బందులు పడుతున్నారు.
                
                కావున దయచేసి తక్షణమే స్పందించి వీధి దీపాలు సరిచేయించాలని కోరుతున్నాము.
                
                ఇట్లు,
                గ్రామ ప్రజలు.
            """.trimIndent()
        }

        val tokens = sampleText.split(" ")
        var count = 0
        for (token in tokens) {
            delay(50)
            count++
            _state.value = LlmEngineState.Generating(tokensGenerated = count, tokensPerSec = 17.2f)
            emit("$token ")
        }
        _state.value = LlmEngineState.Ready
    }

    override suspend fun generateCompleteText(prompt: String, params: GenerationParameters): VernAiResult<String> {
        val result = if (params.grammar != null) {
            when {
                prompt.contains("Tamil", ignoreCase = true) || prompt.contains("தமிழ்") ->
                    """{"items":[{"original_term":"தக்காளி","standard_name":"Tomato","quantity":5.0,"unit":"kg","unit_price":40.0,"total_price":200.0},{"original_term":"எண்ணெய்","standard_name":"Cooking Oil","quantity":2.0,"unit":"packet","unit_price":130.0,"total_price":260.0}]}"""
                prompt.contains("Hindi", ignoreCase = true) || prompt.contains("हिन्दी") ->
                    """{"items":[{"original_term":"टमाटर","standard_name":"Tomato","quantity":5.0,"unit":"kg","unit_price":40.0,"total_price":200.0},{"original_term":"तेल","standard_name":"Cooking Oil","quantity":2.0,"unit":"packet","unit_price":130.0,"total_price":260.0}]}"""
                prompt.contains("English", ignoreCase = true) ->
                    """{"items":[{"original_term":"Tomato","standard_name":"Tomato","quantity":5.0,"unit":"kg","unit_price":40.0,"total_price":200.0},{"original_term":"Cooking Oil","standard_name":"Cooking Oil","quantity":2.0,"unit":"packet","unit_price":130.0,"total_price":260.0}]}"""
                else ->
                    """{"items":[{"original_term":"టమాటా","standard_name":"Tomato","quantity":5.0,"unit":"kg","unit_price":40.0,"total_price":200.0},{"original_term":"నూనె ప్యాకెట్లు","standard_name":"Cooking Oil","quantity":2.0,"unit":"packet","unit_price":130.0,"total_price":260.0}]}"""
            }
        } else if (prompt.contains("Telugu legal drafting engine") || prompt.contains("LETTER TYPE:")) {
            """
            {
              "subject": "విషయము: గ్రామ పరిధిలో తాగునీటి ఎద్దడి మరియు వీధి దీపాల మరమ్మత్తులు చేపట్టవలసిందిగా వినతి.",
              "salutation": "గౌరవనీయులైన పంచాయతీ కార్యదర్శి / సర్పంచ్ గారికి,",
              "reference": null,
              "context_paragraph": "విన్నవించునది ఏమనగా, నేను/మేము శాంతినగర్ కాలనీ నివాసితులము. మా ప్రాంతంలో ఎదురవుతున్న తీవ్రమైన మౌలిక వసతుల సమస్యలను మీ అమూల్యమైన దృష్టికి తీసుకువచ్చి సత్వర పరిష్కారం కోరడానికి ఈ వినతిపత్రం సమర్పిస్తున్నాము.",
              "factual_details_paragraph": "మా కాలనీలో గత 10 రోజులుగా వీధి దీపాలు వెలగకపోవడం వలన రాత్రి వేళల్లో రాకపోకలకు తీవ్ర అసౌకర్యం కలుగుతున్నది. అంతేగాక ప్రధాన పైప్‌లైన్ లీకేజీ కారణంగా గత 4 రోజులుగా తాగునీటి సరఫరా పూర్తిగా నిలిచిపోయింది. దీనివల్ల కాలనీలోని దాదాపు 150 కుటుంబాలు తీవ్ర ఇబ్బందులు ఎదుర్కొంటున్నాయి.",
              "requested_action_paragraph": "కావున దయచేసి మా సమస్యల తీవ్రతను గుర్తించి, సంబంధిత అధికారులను తక్షణమే క్షేత్రస్థాయి పరిశీలనకు ఆదేశించి, తాగునీటి సరఫరా పునరుద్ధరణ మరియు వీధి దీపాల మరమ్మత్తులు వెంటనే పూర్తి చేయించగలరని వినయపూర్వకంగా వేడుకొనుచున్నాము.",
              "closing": "ఇట్లు,\nభవదీయులు,",
              "signature_name_placeholder": "[దరఖాస్తుదారుడి పేరు]",
              "place": "శాంతినగర్",
              "date": "13-09-2026",
              "english_subject": "Subject: Representation regarding urgent restoration of drinking water supply and street lights.",
              "english_body": "To\nThe Panchayat Secretary / Sarpanch,\nGrama Panchayat Office.\n\nSubject: Representation regarding urgent restoration of drinking water supply and street lights.\n\nRespected Sir/Madam,\n\nWe the residents of Shantinagar Colony bring to your urgent attention the severe civic issues prevailing in our locality. The street lights have been dysfunctional for the last 10 days, causing safety concerns at night. Furthermore, due to a major water pipeline breach, potable water supply has been disrupted for the past 4 days, affecting over 150 households.\n\nWe earnestly request your esteemed office to inspect the location and direct the technical team to restore water supply and repair street lights on priority.\n\nYours faithfully,\nResidents of Shantinagar\nPlace: Shantinagar\nDate: 13-09-2026",
              "preserved_facts": [
                "గత 10 రోజులుగా వీధి దీపాలు పనిచేయడం లేదు",
                "పైప్‌లైన్ లీకేజీ వల్ల 4 రోజులుగా నీటి సరఫరా నిలిచిపోయింది",
                "150 కుటుంబాలు తీవ్ర ఇబ్బందులు పడుతున్నాయి"
              ]
            }
            """.trimIndent()
        } else if (prompt.contains("Selected Text:")) {
            when {
                prompt.contains("Tamil", ignoreCase = true) || prompt.contains("தமிழ்") ->
                    "தேர்ந்தெடுக்கப்பட்ட பகுதியின் சுருக்கம்: விண்ணப்பதாரர் தகுதி வரம்புகள் மற்றும் சான்றிதழ் விவரங்களை சரிபார்த்து உரிய காலத்தில் சமர்ப்பிக்க வேண்டும்."
                prompt.contains("Hindi", ignoreCase = true) || prompt.contains("हिन्दी") ->
                    "चयनित अंश का सारांश: आवेदक को अपनी पात्रता और प्रमाण पत्रों की पुष्टि कर निर्धारित समय सीमा में आवेदन करना होगा।"
                prompt.contains("Marathi", ignoreCase = true) || prompt.contains("मराठी") ->
                    "निवडलेल्या भागाचा सारांश: अर्जदाराने आपली पात्रता व प्रमाणपत्रे तपासून अंतिम मुदतीपूर्वी सादर करणे आवश्यक आहे."
                prompt.contains("English", ignoreCase = true) ->
                    "Summary of excerpt: The applicant must verify their eligibility criteria and upload valid certificates prior to the deadline."
                else ->
                    "ఈ ఎంచుకున్న భాగం ముఖ్య సూచన: పౌరులు తమ భూమి పట్టా వివరాలను సరిచూసుకోవడానికి తహశీల్దార్ ఆఫీసులో నిర్ణీత గడువులోపు హాజరుకావాలని స్పష్టం చేయుచున్నది."
            }
        } else if (prompt.contains("Document Text:") || prompt.contains("legal and civic document explainer")) {
            when {
                prompt.contains("Tamil", ignoreCase = true) || prompt.contains("தமிழ்") ->
                    """
                    [SUMMARY]
                    இந்த ஆவணம் உயர்கல்விக்கான தகுதி மற்றும் வருமான அடிப்படையிலான உதவித்தொகை வழிகாட்டுதல் பற்றியதாகும்.
                    [ACTIONS]
                    - வருமான சான்றிதழ் (ஆண்டு வருமானம் ரூ. 2.50 லட்சத்திற்குள்) மற்றும் இருப்பிட சான்றிதழ் சமர்ப்பிக்கவும்.
                    - ஆதார் இணைக்கப்பட்ட மாணவர் வங்கி கணக்கு விவரங்களை சரியாக உள்ளிடவும்.
                    [DEADLINES_AND_FEES]
                    - கடைசி தேதி: 31 அக்டோபர் 2026.
                    - விண்ணப்ப கட்டணம்: ரூ. 0/- (முற்றிலும் இலவச சேவை).
                    """.trimIndent()
                prompt.contains("Hindi", ignoreCase = true) || prompt.contains("हिन्दी") ->
                    """
                    [SUMMARY]
                    यह दस्तावेज़ उच्च शिक्षा के लिए योग्यता-सह-साधन छात्रवृत्ति योजना के आधिकारिक दिशा-निर्देश प्रदान करता है।
                    [ACTIONS]
                    - वित्तीय वर्ष 2025-26 का आय प्रमाण पत्र (आय ₹2.50 लाख से कम) और मूल निवास प्रमाण पत्र संलग्न करें।
                    - आधार से जुड़े सक्रिय बैंक खाते का विवरण (IFSC सहित) दर्ज करें।
                    [DEADLINES_AND_FEES]
                    - आवेदन की अंतिम तिथि: 31 अक्टूबर 2026.
                    - आवेदन शुल्क: ₹0/- (पूर्णतः निःशुल्क).
                    """.trimIndent()
                prompt.contains("Marathi", ignoreCase = true) || prompt.contains("मराठी") ->
                    """
                    [SUMMARY]
                    हा दस्तऐवज उच्च शिक्षणासाठी गुणवत्ता व उत्पन्न आधारित शिष्यवृत्ती योजनेची अधिकृत नियमावली स्पष्ट करतो.
                    [ACTIONS]
                    - तहसीलदार यांचे सक्षम अधिवास व उत्पन्न प्रमाणपत्र (उत्पन्न मर्यादा ₹२.५० लाख) जोडावे.
                    - आधार संलग्न असलेल्या राष्ट्रीयीकृत बँक खात्याचा IFSC कोड व तपशील अचूक नोंदवावा.
                    [DEADLINES_AND_FEES]
                    - अर्ज सादर करण्याची अंतिम मुदत: ३१ ऑक्टोबर २०२६.
                    - अर्ज शुल्क: ₹०/- (पूर्णपणे मोफत).
                    """.trimIndent()
                prompt.contains("English", ignoreCase = true) ->
                    """
                    [SUMMARY]
                    This document provides official guidelines for the Post-Matric Merit-cum-Means Scholarship Scheme.
                    [ACTIONS]
                    - Enclose Domicile Certificate and Income Certificate (Annual family income <= Rs. 2,50,000/-).
                    - Provide student active bank account details with Aadhaar/NPCI seeding and IFSC code.
                    [DEADLINES_AND_FEES]
                    - Submission Deadline: 31st October 2026.
                    - Application Fee: Rs. 0/- (100% Free of Cost).
                    """.trimIndent()
                else ->
                    """
                    [SUMMARY]
                    ఈ పత్రం రెవెన్యూ శాఖ నుండి వచ్చిన అధికారిక పట్టాదార్ పాస్ పుస్తక నోటీసు. సర్వే నంబర్ 142/A లోని 2.50 ఎకరాల భూమి హక్కుల ధృవీకరణ కొరకు జారీ చేయబడినది.
                    [ACTIONS]
                    - ఆధార్ కార్డు మరియు పహాణీ నకలుతో తహశీల్దార్ ఆఫీసుకు స్వయంగా వెళ్లాలి.
                    - సర్వీస్ ఛార్జీగా రూ. 150/- మీసేవ ద్వారా చెల్లించి రసీదు పొందాలి.
                    - సరిహద్దుల డిజిటల్ ల్యాండ్ సర్వే పూర్తి చేయించాలి.
                    [DEADLINES_AND_FEES]
                    - గడువు తేదీ: నోటీసు అందిన 30 రోజులలోపు తప్పనిసరిగా హాజరుకావలెను.
                    - చెల్లించవలసిన చలానా రుసుము: రూ. 150/- మాత్రమే.
                    """.trimIndent()
            }
        } else {
            "రైతులకు నూతన పథకం కింద అర్హులైన ప్రతి ఒక్కరికీ సబ్సిడీ అందుతుంది."
        }
        return VernAiResult.Success(result)
    }

    override suspend fun unloadModel() {
        _state.value = LlmEngineState.Unloaded
    }

    override fun isLoaded(): Boolean = true
    override fun close() {}
}

/**
 * Mock Document Processor simulating local OCR and PDF extraction.
 */
class MockDocumentProcessor : DocumentProcessor {
    override suspend fun extractText(file: File, languageHint: Language?): VernAiResult<ExtractedDocument> {
        delay(600)
        return VernAiResult.Success(
            ExtractedDocument(
                title = file.name,
                rawText = """
                    POST-MATRIC MERIT-CUM-MEANS SCHOLARSHIP SCHEME (HIGHER EDUCATION)
                    GOVERNMENT OF INDIA & STATE SCHOLARSHIP PORTAL
                    Application Deadline: 31st October 2026
                    Eligibility: Resident of state, family income under Rs. 2,50,000/- per annum.
                    Required Documents: Domicile Certificate, Income Certificate, Caste Certificate, Bank Passbook IFSC.
                    Application Fee: Rs. 0/- Free of Cost.
                """.trimIndent(),
                detectedLanguage = languageHint ?: Language.ENGLISH,
                pageCount = 1,
                isScannedImage = false
            )
        )
    }

    override suspend fun extractFromStream(fileName: String, mimeType: String, stream: InputStream, languageHint: Language?): VernAiResult<ExtractedDocument> {
        return extractText(File(fileName), languageHint)
    }
}

/**
 * Mock Document Exporter simulating PDF/DOCX creation.
 */
class MockDocumentExporter : DocumentExporter {
    override suspend fun exportComplaintLetter(draft: ComplaintDraft, destinationFile: File, config: ExportConfig): VernAiResult<File> {
        delay(400)
        destinationFile.parentFile?.mkdirs()
        destinationFile.writeText("VernAI Export: ${draft.subject}\n\n${draft.vernacularBody}")
        return VernAiResult.Success(destinationFile)
    }

    override suspend fun exportSalesLog(salesLog: SalesLog, destinationFile: File, config: ExportConfig): VernAiResult<File> {
        delay(400)
        destinationFile.parentFile?.mkdirs()
        destinationFile.writeText("VernAI Ledger: Grand Total = ${salesLog.grandTotal}")
        return VernAiResult.Success(destinationFile)
    }

    override suspend fun exportExplanationReport(report: ExplanationReport, destinationFile: File, config: ExportConfig): VernAiResult<File> {
        delay(400)
        destinationFile.parentFile?.mkdirs()
        destinationFile.writeText("VernAI Document Explanation: ${report.sourceDocumentName}\n\n${report.summaryInVernacular}")
        return VernAiResult.Success(destinationFile)
    }
}
