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
 * Mock ASR Engine emitting realistic Telugu & multilingual speech transcription tokens.
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
            Language.TELUGU -> listOf(
                "ఈరోజు",
                "ఈరోజు 5 కేజీల టమాటా",
                "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు,",
                "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు, 2 నూనె ప్యాకెట్లు 260 రూపాయలు అమ్మిన."
            )
            Language.HINDI -> listOf(
                "आज",
                "आज 5 किलो टमाटर",
                "आज 5 किलो टमाटर 200 रुपये,",
                "आज 5 किलो टमाटर 200 रुपये, 2 पैकेट तेल 260 रुपये में बेचा।"
            )
            else -> listOf(
                "Today",
                "Today 5 kg tomato",
                "Today 5 kg tomato 200 rupees,",
                "Today 5 kg tomato 200 rupees and 2 oil packets 260 rupees sold."
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
        return VernAiResult.Success(
            TranscriptionResult(
                text = "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు, 2 నూనె ప్యాకెట్లు 260 రూపాయలు అమ్మిన.",
                isFinal = true,
                detectedLanguage = activeLanguage,
                confidence = 0.96f
            )
        )
    }

    override suspend fun transcribeSnippet(snippet: AudioSnippet, languageHint: Language?): VernAiResult<TranscriptionResult> {
        return stopLiveTranscription()
    }

    override fun isReady(): Boolean = true
    override fun close() {
        _state.value = AsrState.Idle
    }
}

/**
 * Mock LLM Engine simulating streaming tokens for Qwen2.5 GGUF.
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
            """{"items":[{"original_term":"టమాటా","standard_name":"Tomato","quantity":5.0,"unit":"kg","unit_price":40.0,"total_price":200.0},{"original_term":"నూనె ప్యాకెట్లు","standard_name":"Cooking Oil","quantity":2.0,"unit":"packet","unit_price":130.0,"total_price":260.0}]}"""
        } else {
            """
                విషయం: గ్రామ పంచాయతీ పరిధిలో వీధి దీపాలు మరియు తాగునీటి సమస్య పరిష్కారం కొరకు వినతిపత్రం.
                
                గౌరవనీయులైన సర్పంచ్ / పంచాయతీ కార్యదర్శి గారికి,
                
                మా గ్రామమైన శాంతినగర్ కాలనీలో గత వారం రోజులుగా వీధి దీపాలు పనిచేయడం లేదు. రాత్రి వేళల్లో ప్రజలు, ముఖ్యంగా వృద్ధులు మరియు పిల్లలు తీవ్ర ఇబ్బందులు పడుతున్నారు. అలాగే మంచినీటి సరఫరా కూడా అస్తవ్యస్తంగా ఉంది.
                
                కావున దయచేసి తక్షణమే స్పందించి వీధి దీపాలు సరిచేయించి, తాగునీరు సక్రమంగా అందించాలని కోరుతున్నాము.
                
                ఇట్లు,
                గ్రామ ప్రజలు, శాంతినగర్.
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
            """{"items":[{"original_term":"టమాటా","standard_name":"Tomato","quantity":5.0,"unit":"kg","unit_price":40.0,"total_price":200.0},{"original_term":"నూనె ప్యాకెట్లు","standard_name":"Cooking Oil","quantity":2.0,"unit":"packet","unit_price":130.0,"total_price":260.0}]}"""
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
            "ఈ ఎంచుకున్న భాగం ముఖ్య సూచన: పౌరులు తమ భూమి పట్టా వివరాలను సరిచూసుకోవడానికి తహశీల్దార్ ఆఫీసులో నిర్ణీత గడువులోపు హాజరుకావాలని స్పష్టం చేయుచున్నది. ఇది అధికారిక రికార్డుల నమోదుకు తప్పనిసరి."
        } else if (prompt.contains("Document Text:") || prompt.contains("legal and civic document explainer")) {
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
                    GOVERNMENT OF TELANGANA / ANDHRA PRADESH
                    REVENUE DEPARTMENT - PATTADAR PASSBOOK / NOTICE
                    సర్వే నంబర్: 142/A, విస్తీర్ణం: 2.50 ఎకరాలు.
                    భూమి స్వభావం: పట్టా మెట్ట.
                    షరతులు: ఈ నోటీసు అందిన 30 రోజులలోపు సంబంధిత తహశీల్దార్ కార్యాలయంలో ఆధార్ మరియు పహాణీ నకలుతో రిపోర్ట్ చేయవలెను.
                    రుసుము: సర్వీస్ ఛార్జీ రూ. 150/- చెల్లించవలసి ఉంటుంది. గడువు ముగిసినచో నోటీసు రద్దగును.
                """.trimIndent(),
                detectedLanguage = Language.TELUGU,
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
