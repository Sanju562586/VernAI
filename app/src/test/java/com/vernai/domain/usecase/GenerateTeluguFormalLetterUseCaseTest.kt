package com.vernai.domain.usecase

import com.vernai.ai.mock.MockLlmInferenceEngine
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.model.ComplaintDraft
import com.vernai.domain.model.letter.LetterInput
import com.vernai.domain.model.letter.LetterRecipient
import com.vernai.domain.model.letter.LetterType
import com.vernai.domain.repository.ComplaintRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenerateTeluguFormalLetterUseCaseTest {

    private lateinit var mockLlm: MockLlmInferenceEngine
    private lateinit var mockRepo: InMemoryComplaintRepository
    private lateinit var useCase: GenerateTeluguFormalLetterUseCase

    private class InMemoryComplaintRepository : ComplaintRepository {
        val savedComplaints = mutableListOf<ComplaintDraft>()

        override fun getComplaintsStream(): Flow<List<ComplaintDraft>> = flowOf(savedComplaints)
        override suspend fun getComplaintById(id: String): ComplaintDraft? = savedComplaints.find { it.id == id }
        override suspend fun saveComplaint(draft: ComplaintDraft): VernAiResult<Unit> {
            savedComplaints.add(draft)
            return VernAiResult.Success(Unit)
        }
        override suspend fun deleteComplaint(id: String): VernAiResult<Unit> {
            savedComplaints.removeAll { it.id == id }
            return VernAiResult.Success(Unit)
        }
    }

    @Before
    fun setUp() {
        mockLlm = MockLlmInferenceEngine()
        mockRepo = InMemoryComplaintRepository()
        useCase = GenerateTeluguFormalLetterUseCase(
            llmEngine = mockLlm,
            repository = mockRepo
        )
    }

    @Test
    fun execute_withMockEngine_returnsStructuredLetterAndPersistsDraft() = runTest {
        val input = LetterInput(
            teluguVoiceTranscript = "శాంతినగర్ కాలనీలో వీధి దీపాలు, తాగునీటి సమస్య ఉంది.",
            letterType = LetterType.COMPLAINT,
            recipient = LetterRecipient(
                designation = "పంచాయతీ కార్యదర్శి / సర్పంచ్ గారు",
                departmentOrOffice = "గ్రామ పంచాయతీ కార్యాలయం",
                officeAddress = "ఖమ్మం జిల్లా"
            ),
            userProvidedFacts = listOf(
                "గత 10 రోజులుగా వీధి దీపాలు పనిచేయడం లేదు",
                "పైప్‌లైన్ లీకేజీ వల్ల 4 రోజులుగా నీటి సరఫరా నిలిచిపోయింది",
                "150 కుటుంబాలు తీవ్ర ఇబ్బందులు పడుతున్నాయి"
            ),
            location = "శాంతినగర్",
            date = "13-09-2026",
            applicantName = "కాలనీ గ్రామస్తులు"
        )

        val result = useCase.execute(input)

        assertTrue(result is VernAiResult.Success)
        val letter = (result as VernAiResult.Success).data

        assertNotNull(letter)
        assertTrue(letter.subject.contains("విషయము:"))
        assertTrue(letter.salutation.contains("గౌరవనీయులైన"))
        assertEquals(3, letter.bodyParagraphs.size)
        assertEquals("శాంతినగర్", letter.place)
        assertEquals("13-09-2026", letter.date)

        // Verify that draft was persisted to repository
        assertEquals(1, mockRepo.savedComplaints.size)
        val saved = mockRepo.savedComplaints[0]
        assertEquals("గ్రామ పంచాయతీ కార్యాలయం", saved.department)
        assertEquals("పంచాయతీ కార్యదర్శి / సర్పంచ్ గారు", saved.recipientDesignation)
        assertTrue(saved.vernacularBody.contains("శాంతినగర్"))
    }
}
