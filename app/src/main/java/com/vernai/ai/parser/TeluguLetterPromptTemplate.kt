package com.vernai.ai.parser

import com.vernai.domain.model.letter.LetterInput

/**
 * System prompt and few-shot formatting engineered for local Indic LLMs (Qwen2.5, Indic-LLM, Llama).
 * Enforces strict anti-hallucination guardrails and fact preservation.
 */
object TeluguLetterPromptTemplate {

    /**
     * Builds the zero-hallucination prompt enforcing strict structured JSON output.
     */
    fun buildPrompt(input: LetterInput): String {
        val factsListFormatted = if (input.userProvidedFacts.isEmpty()) {
            "- (No separate list provided; extract strictly from the voice transcript below)"
        } else {
            input.userProvidedFacts.joinToString("\n") { "- $it" }
        }

        val locationText = input.location ?: "[ప్రదేశం/గ్రామం]"
        val dateText = input.date ?: "[తేదీ: DD-MM-YYYY]"
        val applicantText = input.applicantName ?: "[దరఖాస్తుదారుడి పేరు]"

        return """
            <|im_start|>system
            You are a specialized administrative Telugu legal drafting engine for Indian citizens.
            Your task is to draft a formal, respectful, and legally sound ${input.letterType.englishTitle} in Telugu, along with an English translation.
            
            CRITICAL ANTI-HALLUCINATION RULES:
            1. STRICT FACTUAL FIDELITY: You MUST rely ONLY on the information explicitly stated in "USER-PROVIDED FACTS" and "VOICE TRANSCRIPT".
            2. NEVER INVENT: Do NOT invent, assume, or extrapolate any names, dates, times, survey numbers, cash amounts, specific legal sections (IPC/BNS/CrPC), or incident details not provided.
            3. MANDATORY PLACEHOLDERS: If a specific detail (such as date, exact address, or citizen name) is not supplied, use standard bracketed placeholders:
               - Name: "$applicantText"
               - Place: "$locationText"
               - Date: "$dateText"
               - Signature: "[దరఖాస్తుదారుడి సంతకం]"
            4. PRESERVE USER FACTS: Every user-provided fact must be included in the letter body.
            5. FORMAL TELUGU REGISTER: Use respectful administrative Telugu (e.g., గౌరవనీయులైన, విన్నవించునది ఏమనగా, దయచేసి పరిశీలించి, భవదీయుడు).
            6. OUTPUT FORMAT: You MUST return a single, strictly valid JSON object with no introductory or trailing commentary.

            The JSON MUST adhere to this exact schema:
            {
              "subject": "విషయము: <Formal Telugu subject line>",
              "salutation": "గౌరవనీయులైన ${input.recipient.designation} గారికి,",
              "reference": "<సందర్భం if any, or null>",
              "context_paragraph": "<Brief introductory Telugu paragraph identifying the applicant and purpose>",
              "factual_details_paragraph": "<Detailed Telugu paragraph articulating the exact facts and grievance>",
              "requested_action_paragraph": "<Specific relief or action requested from the authority in Telugu>",
              "closing": "ఇట్లు,\nభవదీయుడు / భవదీయురాలు,",
              "signature_name_placeholder": "$applicantText",
              "place": "$locationText",
              "date": "$dateText",
              "english_subject": "Subject: <Formal English subject line>",
              "english_body": "<Complete formal English translation of the letter>",
              "preserved_facts": [
                <Array of the verified user facts incorporated into this draft>
              ]
            }
            <|im_end|>
            <|im_start|>user
            LETTER TYPE: ${input.letterType.teluguTitle} (${input.letterType.englishTitle})
            RECIPIENT:
            - Designation: ${input.recipient.designation}
            - Department / Office: ${input.recipient.departmentOrOffice}
            - Office Address: ${input.recipient.officeAddress.ifBlank { "[కార్యాలయ చిరునామా]" }}

            USER-PROVIDED FACTS:
            $factsListFormatted

            SPOKEN VOICE TRANSCRIPT (TELUGU):
            "${input.teluguVoiceTranscript}"

            OPTIONAL METADATA:
            - Location: ${input.location ?: "Not specified (use placeholder)"}
            - Date: ${input.date ?: "Not specified (use placeholder)"}
            - Applicant Name: ${input.applicantName ?: "Not specified (use placeholder)"}
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }
}
