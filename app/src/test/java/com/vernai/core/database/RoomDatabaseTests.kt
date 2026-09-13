package com.vernai.core.database

import com.vernai.core.database.entity.ComplaintEntity
import com.vernai.core.database.entity.ExplanationEntity
import com.vernai.core.database.entity.SalesLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDatabaseTests {

    private val typeConverters = VernAiTypeConverters()

    @Test
    fun vernAiTypeConverters_roundTripsStringListsAccurately() {
        val original = listOf(
            "మొదటి వాస్తవం: రోడ్లపై గుంతలు",
            "రెండవ వాస్తవం: తాగునీటి కొరత",
            "Special Chars: <XML> & Quotes 'Test'"
        )

        val serialized = typeConverters.fromStringList(original)
        assertNotNull(serialized)
        assertTrue(serialized.contains("మొదటి వాస్తవం"))

        val deserialized = typeConverters.toStringList(serialized)
        assertEquals(original.size, deserialized.size)
        assertEquals(original[0], deserialized[0])
        assertEquals(original[1], deserialized[1])
        assertEquals(original[2], deserialized[2])

        // Empty list handling
        val emptySerialized = typeConverters.fromStringList(emptyList())
        val emptyDeserialized = typeConverters.toStringList(emptySerialized)
        assertTrue(emptyDeserialized.isEmpty())

        // Null string handling
        val nullDeserialized = typeConverters.toStringList(null)
        assertTrue(nullDeserialized.isEmpty())
    }

    @Test
    fun inMemorySalesLogDao_crudAndFlowOperations() = runTest {
        val dao = InMemorySalesLogDao()

        val logEntity1 = SalesLogEntity(
            id = "log-1",
            rawSpokenText = "5 కేజీల టమాటా 200",
            languageCode = "te",
            itemsJson = """[{"id":"1","originalTerm":"టమాటా","quantity":5.0,"unit":"కేజీ","unitPrice":40.0,"totalPrice":200.0}]""",
            grandTotal = 200.0,
            timestamp = 1000L
        )

        val logEntity2 = SalesLogEntity(
            id = "log-2",
            rawSpokenText = "2 నూనె ప్యాకెట్లు 260",
            languageCode = "te",
            itemsJson = """[{"id":"2","originalTerm":"నూనె","quantity":2.0,"unit":"ప్యాకెట్లు","unitPrice":130.0,"totalPrice":260.0}]""",
            grandTotal = 260.0,
            timestamp = 2000L
        )

        // Insert
        dao.insertSalesLog(logEntity1)
        dao.insertSalesLog(logEntity2)

        // Get by ID
        val retrieved1 = dao.getSalesLogById("log-1")
        assertNotNull(retrieved1)
        assertEquals(200.0, retrieved1!!.grandTotal, 0.001)

        // Flow emissions sorted by timestamp DESC
        val logs = dao.getAllSalesLogs().first()
        assertEquals(2, logs.size)
        assertEquals("log-2", logs[0].id) // timestamp 2000L
        assertEquals("log-1", logs[1].id) // timestamp 1000L

        // Replace on conflict
        val updatedEntity1 = logEntity1.copy(grandTotal = 220.0)
        dao.insertSalesLog(updatedEntity1)
        val retrievedUpdated = dao.getSalesLogById("log-1")
        assertEquals(220.0, retrievedUpdated!!.grandTotal, 0.001)

        // Delete
        dao.deleteSalesLog("log-1")
        val afterDelete = dao.getSalesLogById("log-1")
        assertNull(afterDelete)
        assertEquals(1, dao.getAllSalesLogs().first().size)
    }

    @Test
    fun inMemoryComplaintDao_crudAndFlowOperations() = runTest {
        val dao = InMemoryComplaintDao()

        val complaint = ComplaintEntity(
            id = "c-1",
            subject = "గ్రామంలో తాగునీటి ఎద్దడి",
            department = "పంచాయతీ రాజ్",
            recipientDesignation = "కార్యదర్శి",
            vernacularBody = "తాగునీటి సరఫరా నిలిచిపోయింది.",
            englishTranslation = "Drinking water issue.",
            languageCode = "te",
            senderName = "గ్రామస్తులు",
            location = "శాంతినగర్",
            timestamp = 5000L
        )

        dao.insertComplaint(complaint)

        val retrieved = dao.getComplaintById("c-1")
        assertNotNull(retrieved)
        assertEquals("గ్రామంలో తాగునీటి ఎద్దడి", retrieved!!.subject)

        val list = dao.getAllComplaints().first()
        assertEquals(1, list.size)

        dao.deleteComplaint("c-1")
        assertNull(dao.getComplaintById("c-1"))
        assertTrue(dao.getAllComplaints().first().isEmpty())
    }

    @Test
    fun inMemoryExplanationDao_crudAndFlowOperations() = runTest {
        val dao = InMemoryExplanationDao()

        val explanation = ExplanationEntity(
            id = "exp-1",
            sourceDocumentName = "Revenue_Notice.pdf",
            extractedCharacterCount = 120,
            summaryInVernacular = "ఇది మీ భూమికి సంబంధించిన నోటీసు.",
            keyActionPointsJson = """["పట్టాదారు పేరు సరిచూసుకోండి","30 రోజుల్లో దరఖాస్తు చేయండి"]""",
            legalDeadlinesJson = """["30 రోజులు"]""",
            languageCode = "te",
            timestamp = 10000L
        )

        dao.insertExplanation(explanation)

        val retrieved = dao.getExplanationById("exp-1")
        assertNotNull(retrieved)
        assertEquals("Revenue_Notice.pdf", retrieved!!.sourceDocumentName)

        val list = dao.getAllExplanations().first()
        assertEquals(1, list.size)

        dao.deleteExplanation("exp-1")
        assertNull(dao.getExplanationById("exp-1"))
        assertTrue(dao.getAllExplanations().first().isEmpty())
    }
}
