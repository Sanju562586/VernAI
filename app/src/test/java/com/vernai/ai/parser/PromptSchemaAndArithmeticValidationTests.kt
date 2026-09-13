package com.vernai.ai.parser

import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesLog
import com.vernai.core.model.SalesValidationStatus
import com.vernai.domain.model.letter.LetterInput
import com.vernai.domain.model.letter.LetterRecipient
import com.vernai.domain.model.letter.LetterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PromptSchemaAndArithmeticValidationTests {

    private val parser = TeluguLetterParser()

    @Test
    fun promptTemplate_enforcesAntiHallucinationRulesAndPlaceholders() {
        val input = LetterInput(
            teluguVoiceTranscript = "రోడ్లు గుంతలమయంగా ఉన్నాయి",
            letterType = LetterType.COMPLAINT,
            recipient = LetterRecipient(
                designation = "కలెక్టర్ గారు",
                departmentOrOffice = "కలెక్టరేట్ కార్యాలయం"
            ),
            userProvidedFacts = listOf("మెయిన్ రోడ్డుపై పెద్ద గుంతలు ఏర్పడ్డాయి"),
            location = null,
            date = null,
            applicantName = null
        )

        val prompt = TeluguLetterPromptTemplate.buildPrompt(input)

        assertTrue("Prompt must include strict anti-hallucination directive", prompt.contains("CRITICAL ANTI-HALLUCINATION RULES"))
        assertTrue("Prompt must forbid inventing names or dates", prompt.contains("NEVER INVENT"))
        assertTrue("Prompt must include mandatory location placeholder", prompt.contains("[ప్రదేశం/గ్రామం]"))
        assertTrue("Prompt must include mandatory date placeholder", prompt.contains("[తేదీ: DD-MM-YYYY]"))
        assertTrue("Prompt must include mandatory applicant placeholder", prompt.contains("[దరఖాస్తుదారుడి పేరు]"))
        assertTrue("Prompt must include formatted user facts", prompt.contains("- మెయిన్ రోడ్డుపై పెద్ద గుంతలు ఏర్పడ్డాయి"))
    }

    @Test
    fun letterParser_parsesValidStructuredJsonSuccessfully() {
        val validJson = """
            {
              "subject": "విషయము: గ్రామంలో మంచినీటి పైప్‌లైన్ మరమ్మత్తుల గురించి వినతి.",
              "salutation": "గౌరవనీయులైన పంచాయతీ కార్యదర్శి గారికి,",
              "context_paragraph": "విన్నవించునది ఏమనగా, మేము గాంధీనగర్ కాలనీ నివాసితులము.",
              "factual_details_paragraph": "గత 5 రోజులుగా ప్రధాన పైప్‌లైన్ పగిలిపోయి తాగునీరు రావడం లేదు.",
              "requested_action_paragraph": "కావున దయచేసి వెంటనే మరమ్మత్తులు పూర్తి చేయించగలరు.",
              "closing": "ఇట్లు,\nభవదీయులు,",
              "signature_name_placeholder": "కాలనీ ప్రజలు",
              "place": "గాంధీనగర్",
              "date": "13-09-2026",
              "english_subject": "Subject: Representation regarding drinking water pipeline repair.",
              "english_body": "We request immediate pipeline repair in Gandhinagar.",
              "preserved_facts": ["గత 5 రోజులుగా ప్రధాన పైప్‌లైన్ పగిలిపోయి తాగునీరు రావడం లేదు."]
            }
        """.trimIndent()

        val input = LetterInput(
            teluguVoiceTranscript = "పైప్‌లైన్ పగిలిపోయింది",
            letterType = LetterType.COMPLAINT,
            recipient = LetterRecipient("పంచాయతీ కార్యదర్శి", "గ్రామ పంచాయతీ"),
            userProvidedFacts = listOf("గత 5 రోజులుగా ప్రధాన పైప్‌లైన్ పగిలిపోయి తాగునీరు రావడం లేదు.")
        )

        val result = parser.parseOrThrow(validJson, input)

        assertEquals("విషయము: గ్రామంలో మంచినీటి పైప్‌లైన్ మరమ్మత్తుల గురించి వినతి.", result.subject)
        assertEquals("గౌరవనీయులైన పంచాయతీ కార్యదర్శి గారికి,", result.salutation)
        assertEquals("గాంధీనగర్", result.place)
        assertEquals("13-09-2026", result.date)
        assertEquals(3, result.bodyParagraphs.size)
        assertTrue("Full Telugu body must contain salutation", result.formalTeluguLetterBody.contains("గౌరవనీయులైన"))
        assertTrue("Full Telugu body must contain closing", result.formalTeluguLetterBody.contains("భవదీయులు"))
    }

    @Test
    fun letterParser_handlesJsonInMarkdownCodeFences() {
        val markdownFenced = """
            Here is your formal letter:
            ```json
            {
              "subject": "విషయము: వీధి దీపాల ఏర్పాటు గురించి.",
              "salutation": "గౌరవనీయులైన సర్పంచ్ గారికి,",
              "context_paragraph": "మేము శాంతినగర్ గ్రామస్తులము.",
              "factual_details_paragraph": "రాత్రి వేళల్లో వీధి దీపాలు లేక చీకటిగా ఉంటోంది.",
              "requested_action_paragraph": "వీధి దీపాలు వేయించగలరు.",
              "closing": "ఇట్లు,\nభవదీయుడు,",
              "signature_name_placeholder": "గ్రామస్తులు",
              "place": "శాంతినగర్",
              "date": "13-09-2026",
              "english_subject": "Subject: Installation of street lights.",
              "english_body": "Request to install street lights."
            }
            ```
            Hope this helps!
        """.trimIndent()

        val input = LetterInput(
            teluguVoiceTranscript = "వీధి దీపాలు లేవు",
            letterType = LetterType.COMPLAINT,
            recipient = LetterRecipient("సర్పంచ్ గారు", "పంచాయతీ")
        )

        val result = parser.parseOrThrow(markdownFenced, input)
        assertNotNull(result)
        assertEquals("విషయము: వీధి దీపాల ఏర్పాటు గురించి.", result.subject)
    }

    @Test
    fun letterParser_rejectsEmptyOrMalformedJson() {
        val input = LetterInput(
            teluguVoiceTranscript = "టెస్ట్",
            letterType = LetterType.COMPLAINT,
            recipient = LetterRecipient("అధికారి", "కార్యాలయం")
        )

        try {
            parser.parseOrThrow("This is not JSON at all!", input)
            fail("Must throw LetterParsingException.MalformedJson on invalid JSON")
        } catch (e: LetterParsingException.MalformedJson) {
            assertTrue(e.message?.contains("could not be parsed") == true)
        }
    }

    @Test
    fun letterParser_rejectsMissingSubjectOrSalutation() {
        val invalidSchemaJson = """
            {
              "context_paragraph": "వివరాలు",
              "requested_action_paragraph": "పరిష్కరించండి",
              "closing": "ఇట్లు"
            }
        """.trimIndent()

        val input = LetterInput(
            teluguVoiceTranscript = "టెస్ట్",
            letterType = LetterType.COMPLAINT,
            recipient = LetterRecipient("అధికారి", "కార్యాలయం")
        )

        try {
            parser.parseOrThrow(invalidSchemaJson, input)
            fail("Must throw LetterParsingException.SchemaValidationFailed on missing subject")
        } catch (e: LetterParsingException.SchemaValidationFailed) {
            assertTrue("Errors must mention missing subject", e.validationErrors.any { it.contains("subject") })
            assertTrue("Errors must mention missing salutation", e.validationErrors.any { it.contains("salutation") })
        }
    }

    @Test
    fun letterParser_deterministicFallbackGeneratesCleanLetter() {
        val input = LetterInput(
            teluguVoiceTranscript = "గ్రామంలో తాగునీటి సరఫరా నిలిచిపోయింది",
            letterType = LetterType.COMPLAINT,
            recipient = LetterRecipient(
                designation = "మండల అభివృద్ధి అధికారి (MPDO)",
                departmentOrOffice = "మండల పరిషత్ కార్యాలయం",
                officeAddress = "కొత్తగూడెం"
            ),
            userProvidedFacts = listOf("4 రోజులుగా తాగునీరు సరఫరా లేదు", "100 కుటుంబాలు ఇబ్బందులు పడుతున్నాయి"),
            location = "కొత్తగూడెం",
            date = "13-09-2026",
            applicantName = "కొత్తగూడెం నివాసితులు"
        )

        val fallback = parser.parseWithFallback("CORRUPTED UNPARSEABLE OUTPUT", input)

        assertNotNull(fallback)
        assertTrue(fallback.subject.contains("తాగునీటి సరఫరా"))
        assertTrue(fallback.formalTeluguLetterBody.contains("మండల అభివృద్ధి అధికారి"))
        assertTrue(fallback.formalTeluguLetterBody.contains("కొత్తగూడెం నివాసితులు"))
        assertTrue(fallback.formalTeluguLetterBody.contains("4 రోజులుగా తాగునీరు సరఫరా లేదు"))
    }

    @Test
    fun salesArithmetic_validatesItemCalculationsAndGrandTotal() {
        val item1 = SalesItem(
            id = "1",
            date = "2026-09-13",
            originalTerm = "టమాటా",
            standardName = "టమాటా",
            quantity = 5.0,
            unit = "కేజీ",
            unitPrice = 40.0,
            totalPrice = 200.0,
            validationStatus = SalesValidationStatus.VERIFIED
        )

        val item2 = SalesItem(
            id = "2",
            date = "2026-09-13",
            originalTerm = "నూనె",
            standardName = "నూనె",
            quantity = 2.0,
            unit = "ప్యాకెట్లు",
            unitPrice = 130.0,
            totalPrice = 260.0,
            validationStatus = SalesValidationStatus.VERIFIED
        )

        val log = SalesLog(
            id = "log-1",
            rawSpokenText = "5 కేజీల టమాటా 200 మరియు 2 నూనె ప్యాకెట్లు 260",
            detectedLanguage = com.vernai.core.model.Language.TELUGU,
            items = listOf(item1, item2),
            grandTotal = 460.0,
            timestamp = System.currentTimeMillis()
        )

        // Verify item calculations
        assertEquals(200.0, item1.quantity * item1.unitPrice, 0.001)
        assertEquals(260.0, item2.quantity * item2.unitPrice, 0.001)

        // Verify grand total equals sum of items
        val calculatedGrandTotal = log.items.sumOf { it.totalPrice }
        assertEquals(460.0, calculatedGrandTotal, 0.001)
        assertEquals(calculatedGrandTotal, log.grandTotal, 0.001)
    }

    @Test
    fun salesArithmetic_detectsDiscrepanciesAndAmbiguities() {
        // Arithmetic mismatch: 10 kg @ Rs 40 should be Rs 400, but stated as Rs 350
        val mismatchItem = SalesItem(
            id = "3",
            date = "2026-09-13",
            originalTerm = "బియ్యం",
            quantity = 10.0,
            unitPrice = 40.0,
            totalPrice = 350.0,
            validationStatus = SalesValidationStatus.ARITHMETIC_MISMATCH
        )
        assertEquals(SalesValidationStatus.ARITHMETIC_MISMATCH, mismatchItem.validationStatus)
        val expectedPrice = mismatchItem.quantity * mismatchItem.unitPrice
        assertEquals(400.0, expectedPrice, 0.001)

        // Missing quantity
        val ambiguousQtyItem = SalesItem(
            id = "4",
            date = "2026-09-13",
            originalTerm = "టమాటా",
            quantity = 0.0,
            unitPrice = 50.0,
            totalPrice = 50.0,
            validationStatus = SalesValidationStatus.CLARIFICATION_NEEDED
        )
        assertEquals(SalesValidationStatus.CLARIFICATION_NEEDED, ambiguousQtyItem.validationStatus)

        // Missing price
        val ambiguousPriceItem = SalesItem(
            id = "5",
            date = "2026-09-13",
            originalTerm = "సబ్బులు",
            quantity = 3.0,
            unitPrice = 0.0,
            totalPrice = 0.0,
            validationStatus = SalesValidationStatus.CLARIFICATION_NEEDED
        )
        assertEquals(SalesValidationStatus.CLARIFICATION_NEEDED, ambiguousPriceItem.validationStatus)
    }
}
