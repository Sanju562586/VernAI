package com.vernai.ai.parser

import com.vernai.core.model.Language

/**
 * System prompts and few-shot templates engineered for Qwen2.5 / Indic multilingual LLMs.
 */
object IndicPromptTemplate {

    fun buildSalesLogPrompt(spokenText: String, language: Language): String {
        return """
            <|im_start|>system
            You are a strict data extraction engine for Indian local merchants and shopkeepers.
            Analyze the user's spoken sales record in ${language.englishName} (${language.nativeName}) or mixed vernacular.
            Extract all sold items into a strict JSON object with an "items" array.
            Every item must contain:
            - "original_term": the item name as spoken in vernacular
            - "standard_name": English or clear standard name
            - "quantity": numeric value
            - "unit": unit of measurement (kg, liter, packet, piece, dozen, etc.)
            - "unit_price": unit price if mentioned, else 0
            - "total_price": total price calculated or spoken
            Output ONLY raw valid JSON. Do not include introductory or concluding words.
            <|im_end|>
            <|im_start|>user
            Spoken record: "$spokenText"
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    fun buildComplaintLetterPrompt(
        issueDescription: String,
        targetDepartment: String,
        language: Language,
        senderName: String? = null,
        location: String? = null
    ): String {
        return """
            <|im_start|>system
            You are an expert civic legal assistant for Indian citizens.
            Draft a formal, respectful, and legally sound complaint letter addressed to: $targetDepartment.
            Write the formal letter in ${language.englishName} (${language.nativeName}), followed by a concise formal English translation.
            Structure the response as:
            [SUBJECT_VERNACULAR]
            ...
            [BODY_VERNACULAR]
            ...
            [SUBJECT_ENGLISH]
            ...
            [BODY_ENGLISH]
            ...
            Include placeholder or provided details for Sender: ${senderName ?: "[పేరు/नाम/Name]"} and Location: ${location ?: "[గ్రామం/स्थान/Location]"}.
            <|im_end|>
            <|im_start|>user
            Citizen issue: "$issueDescription"
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    fun buildDocumentExplanationPrompt(
        documentText: String,
        targetLanguage: Language
    ): String {
        return """
            <|im_start|>system
            You are a legal and government document simplifier for citizens.
            Read the extracted document text and explain it in clear, simple everyday ${targetLanguage.englishName} (${targetLanguage.nativeName}).
            Provide:
            1. Short 2-3 sentence overview of what this document is.
            2. Key action items or obligations for the citizen.
            3. Critical deadlines or fees mentioned.
            Use bullet points and simple language without bureaucratic jargon.
            <|im_end|>
            <|im_start|>user
            Document Content:
            $documentText
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }
}
