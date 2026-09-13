package com.vernai.domain.model.letter

import kotlinx.serialization.Serializable

/**
 * Types of formal civic and administrative letters in Telugu governance.
 */
enum class LetterType(
    val teluguTitle: String,
    val englishTitle: String,
    val defaultSubjectPrefix: String
) {
    COMPLAINT("ఫిర్యాదు / వినతిపత్రం", "Grievance / Complaint", "విషయము: ఫిర్యాదు - "),
    REPRESENTATION("విజ్ఞాపన పత్రం", "Representation", "విషయము: విజ్ఞాపన - "),
    REQUEST("దరఖాస్తు / విన్నపం", "Application / Request", "విషయము: దరఖాస్తు - "),
    RTI_APPLICATION("సమాచార హక్కు దరఖాస్తు", "RTI Application", "విషయము: సమాచార హక్కు చట్టం 2005 సెక్షన్ 6(1) ప్రకారం దరఖాస్తు - "),
    APPEAL("పునఃపరిశీలన అప్పీలు", "Administrative Appeal", "విషయము: పునఃపరిశీలన కొరకు అప్పీలు - ")
}

/**
 * Recipient details for administrative dispatch.
 */
@Serializable
data class LetterRecipient(
    val designation: String,
    val departmentOrOffice: String,
    val officeAddress: String = ""
)

/**
 * Complete set of user inputs into the Telugu formal letter generation pipeline.
 */
data class LetterInput(
    val teluguVoiceTranscript: String,
    val letterType: LetterType = LetterType.COMPLAINT,
    val recipient: LetterRecipient,
    val userProvidedFacts: List<String> = emptyList(),
    val location: String? = null,
    val date: String? = null,
    val applicantName: String? = null
)

/**
 * Signature and footer placeholders ensuring no fabrication of applicant credentials.
 */
@Serializable
data class SignaturePlaceholders(
    val applicantName: String,
    val signatureLine: String,
    val location: String,
    val date: String,
    val contactPlaceholder: String = "[ఫోన్ నంబర్ / చిరునామా]"
)

/**
 * Full strongly-typed output from the parser and validation pipeline.
 */
@Serializable
data class StructuredLetter(
    val letterType: LetterType,
    val recipient: LetterRecipient,
    val place: String,
    val date: String,
    val salutation: String,
    val subject: String,
    val reference: String? = null,
    val bodyParagraphs: List<String>,
    val formalTeluguLetterBody: String,
    val closing: String,
    val signaturePlaceholders: SignaturePlaceholders,
    val preservedFacts: List<String>,
    val englishTranslation: String
)
