package com.vernai.ai.parser

import com.vernai.domain.model.letter.LetterInput
import com.vernai.domain.model.letter.LetterRecipient
import com.vernai.domain.model.letter.LetterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TeluguLetterParserTest {

    private lateinit var parser: TeluguLetterParser
    private lateinit var defaultInput: LetterInput

    @Before
    fun setUp() {
        parser = TeluguLetterParser()
        defaultInput = LetterInput(
            teluguVoiceTranscript = "శాంతినగర్‌లో 10 రోజులుగా వీధి దీపాలు పనిచేయడం లేదు, తాగునీటి సరఫరా నిలిచిపోయింది.",
            letterType = LetterType.COMPLAINT,
            recipient = LetterRecipient(
                designation = "పంచాయతీ కార్యదర్శి / సర్పంచ్ గారు",
                departmentOrOffice = "గ్రామ పంచాయతీ కార్యాలయం",
                officeAddress = "ఖమ్మం జిల్లా"
            ),
            userProvidedFacts = listOf(
                "గత 10 రోజులుగా వీధి దీపాలు పనిచేయడం లేదు",
                "తాగునీటి సరఫరా నిలిచిపోయింది",
                "150 కుటుంబాలు ఇబ్బందులు పడుతున్నాయి"
            ),
            location = "శాంతినగర్",
            date = "13-09-2026",
            applicantName = "కాలనీ ప్రజలు"
        )
    }

    @Test
    fun parseOrThrow_validStructuredJson_returnsCorrectStructuredLetter() {
        val validJson = """
            {
              "subject": "విషయము: వీధి దీపాలు మరియు తాగునీటి సరఫరా పునరుద్ధరణ కొరకు వినతి.",
              "salutation": "గౌరవనీయులైన పంచాయతీ కార్యదర్శి గారికి,",
              "reference": null,
              "context_paragraph": "విన్నవించునది ఏమనగా, మేము శాంతినగర్ గ్రామ నివాసితులము.",
              "factual_details_paragraph": "గత 10 రోజులుగా వీధి దీపాలు పనిచేయడం లేదు. తాగునీటి సరఫరా నిలిచిపోయింది. దీనితో 150 కుటుంబాలు తీవ్ర ఇబ్బందులు పడుతున్నాయి.",
              "requested_action_paragraph": "కావున తక్షణమే పరిశీలించి వీధి దీపాలు సరిచేయించి, నీటి సరఫరా పునరుద్ధరించగలరని కోరుతున్నాము.",
              "closing": "ఇట్లు,\nభవదీయులు,",
              "signature_name_placeholder": "కాలనీ ప్రజలు",
              "place": "శాంతినగర్",
              "date": "13-09-2026",
              "english_subject": "Subject: Request for restoration of street lights and water supply.",
              "english_body": "Respected Sir, Please restore water and lights. Yours faithfully, Colony Residents.",
              "preserved_facts": [
                "గత 10 రోజులుగా వీధి దీపాలు పనిచేయడం లేదు",
                "తాగునీటి సరఫరా నిలిచిపోయింది",
                "150 కుటుంబాలు ఇబ్బందులు పడుతున్నాయి"
              ]
            }
        """.trimIndent()

        val structured = parser.parseOrThrow(validJson, defaultInput)

        assertNotNull(structured)
        assertEquals("విషయము: వీధి దీపాలు మరియు తాగునీటి సరఫరా పునరుద్ధరణ కొరకు వినతి.", structured.subject)
        assertEquals("గౌరవనీయులైన పంచాయతీ కార్యదర్శి గారికి,", structured.salutation)
        assertEquals(3, structured.bodyParagraphs.size)
        assertEquals("శాంతినగర్", structured.place)
        assertEquals("13-09-2026", structured.date)
        assertEquals("కాలనీ ప్రజలు", structured.signaturePlaceholders.applicantName)
        assertEquals(3, structured.preservedFacts.size)
        assertTrue(structured.formalTeluguLetterBody.contains("శాంతినగర్"))
        assertTrue(structured.formalTeluguLetterBody.contains("150 కుటుంబాలు"))
    }

    @Test
    fun parseOrThrow_markdownCodeFencedJson_parsesSuccessfully() {
        val fencedJson = """
            ```json
            {
              "subject": "విషయము: మురుగు కాలువల శుభ్రత కొరకు దరఖాస్తు.",
              "salutation": "గౌరవనీయులైన కమిషనర్ గారికి,",
              "context_paragraph": "విన్నవించునది ఏమనగా...",
              "factual_details_paragraph": "కాలువలు నిండిపోయి రోడ్లపై నీరు పారుతున్నది.",
              "requested_action_paragraph": "వెంటనే పారిశుధ్య సిబ్బందిని పంపించగలరు.",
              "closing": "ఇట్లు,\nభవదీయులు,",
              "signature_name_placeholder": "[దరఖాస్తుదారుడి పేరు]",
              "place": "[ప్రదేశం]",
              "date": "[తేదీ]",
              "english_subject": "Subject: Drainage cleaning request",
              "english_body": "To The Commissioner, Please clean the drainage. Yours faithfully.",
              "preserved_facts": []
            }
            ```
        """.trimIndent()

        val structured = parser.parseOrThrow(fencedJson, defaultInput.copy(userProvidedFacts = emptyList()))
        assertNotNull(structured)
        assertEquals("విషయము: మురుగు కాలువల శుభ్రత కొరకు దరఖాస్తు.", structured.subject)
        assertEquals("గౌరవనీయులైన కమిషనర్ గారికి,", structured.salutation)
    }

    @Test
    fun parseOrThrow_malformedJsonSyntax_throwsMalformedJsonException() {
        val malformedJson = """
            {
              "subject": "విషయము: వీధి దీపాలు
              "salutation": "గౌరవనీయులైన పంచాయతీ
              ... UNFINISHED CORRUPTED OUTPUT ...
        """.trimIndent()

        try {
            parser.parseOrThrow(malformedJson, defaultInput)
            fail("Expected LetterParsingException.MalformedJson to be thrown")
        } catch (e: LetterParsingException.MalformedJson) {
            assertTrue(e.message!!.contains("valid JSON"))
        }
    }

    @Test
    fun parseOrThrow_missingSubject_throwsSchemaValidationFailed() {
        val missingSubjectJson = """
            {
              "subject": "",
              "salutation": "గౌరవనీయులైన అధికారి గారికి,",
              "context_paragraph": "విన్నవించునది ఏమనగా...",
              "requested_action_paragraph": "పరిష్కరించగలరు.",
              "closing": "ఇట్లు,"
            }
        """.trimIndent()

        try {
            parser.parseOrThrow(missingSubjectJson, defaultInput)
            fail("Expected LetterParsingException.SchemaValidationFailed to be thrown")
        } catch (e: LetterParsingException.SchemaValidationFailed) {
            assertTrue(e.validationErrors.any { it.contains("subject") })
        }
    }

    @Test
    fun parseOrThrow_missingSalutation_throwsSchemaValidationFailed() {
        val missingSalutationJson = """
            {
              "subject": "విషయము: రోడ్డు మరమ్మత్తుల గురించి.",
              "salutation": "  ",
              "context_paragraph": "విన్నవించునది ఏమనగా...",
              "requested_action_paragraph": "పరిష్కరించగలరు.",
              "closing": "ఇట్లు,"
            }
        """.trimIndent()

        try {
            parser.parseOrThrow(missingSalutationJson, defaultInput)
            fail("Expected LetterParsingException.SchemaValidationFailed to be thrown")
        } catch (e: LetterParsingException.SchemaValidationFailed) {
            assertTrue(e.validationErrors.any { it.contains("salutation") })
        }
    }

    @Test
    fun parseOrThrow_missingBodyParagraphs_throwsSchemaValidationFailed() {
        val missingBodyJson = """
            {
              "subject": "విషయము: దరఖాస్తు.",
              "salutation": "గౌరవనీయులైన అధికారి గారికి,",
              "context_paragraph": "",
              "factual_details_paragraph": "",
              "requested_action_paragraph": "చర్యలు తీసుకోగలరు.",
              "closing": "ఇట్లు,"
            }
        """.trimIndent()

        try {
            parser.parseOrThrow(missingBodyJson, defaultInput)
            fail("Expected LetterParsingException.SchemaValidationFailed to be thrown")
        } catch (e: LetterParsingException.SchemaValidationFailed) {
            assertTrue(e.validationErrors.any { it.contains("paragraph") })
        }
    }

    @Test
    fun parseOrThrow_missingRequestedAction_throwsSchemaValidationFailed() {
        val missingActionJson = """
            {
              "subject": "విషయము: దరఖాస్తు.",
              "salutation": "గౌరవనీయులైన అధికారి గారికి,",
              "context_paragraph": "సమస్య వివరాలు...",
              "requested_action_paragraph": "",
              "closing": "ఇట్లు,"
            }
        """.trimIndent()

        try {
            parser.parseOrThrow(missingActionJson, defaultInput)
            fail("Expected LetterParsingException.SchemaValidationFailed to be thrown")
        } catch (e: LetterParsingException.SchemaValidationFailed) {
            assertTrue(e.validationErrors.any { it.contains("requested_action_paragraph") })
        }
    }

    @Test
    fun parseOrThrow_antiHallucination_verifiesPlaceholdersWhenMetadataOmitted() {
        val inputWithoutMetadata = defaultInput.copy(
            location = null,
            date = null,
            applicantName = null,
            userProvidedFacts = emptyList()
        )

        val jsonWithoutMetadata = """
            {
              "subject": "విషయము: సాధారణ వినతి.",
              "salutation": "గౌరవనీయులైన కార్యదర్శి గారికి,",
              "context_paragraph": "విన్నవించునది ఏమనగా...",
              "requested_action_paragraph": "పరిశీలించగలరు.",
              "closing": "ఇట్లు,\nభవదీయుడు,"
            }
        """.trimIndent()

        val structured = parser.parseOrThrow(jsonWithoutMetadata, inputWithoutMetadata)

        assertEquals("[ప్రదేశం/గ్రామం]", structured.place)
        assertEquals("[తేదీ: DD-MM-YYYY]", structured.date)
        assertEquals("[దరఖాస్తుదారుడి పేరు]", structured.signaturePlaceholders.applicantName)
    }

    @Test
    fun parseWithFallback_corruptedModelOutput_returnsDeterministicZeroHallucinationLetter() {
        val corruptedOutput = "I am sorry, but as an AI language model I cannot draft this letter. Error 500."

        val fallbackLetter = parser.parseWithFallback(corruptedOutput, defaultInput)

        assertNotNull(fallbackLetter)
        assertTrue(fallbackLetter.subject.contains("ఫిర్యాదు"))
        assertTrue(fallbackLetter.formalTeluguLetterBody.contains("శాంతినగర్"))
        // Check that user-provided facts were preserved in the fallback
        defaultInput.userProvidedFacts.forEach { fact ->
            assertTrue("Fact should be preserved: $fact", fallbackLetter.formalTeluguLetterBody.contains(fact))
        }
        assertEquals("శాంతినగర్", fallbackLetter.place)
        assertEquals("13-09-2026", fallbackLetter.date)
        assertEquals("కాలనీ ప్రజలు", fallbackLetter.signaturePlaceholders.applicantName)
    }
}
