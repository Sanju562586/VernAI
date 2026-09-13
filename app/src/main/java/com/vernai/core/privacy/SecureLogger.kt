package com.vernai.core.privacy

import android.util.Log

/**
 * Privacy-preserving logger for VernAI.
 * Prevents sensitive personal data (PII), speech transcripts, citizen grievance details,
 * Aadhaar/phone numbers, and financial ledger data from leaking into system logs (Logcat).
 */
object SecureLogger {

    var isLoggingEnabled: Boolean = true // Can be tied to BuildConfig.DEBUG

    // Regex patterns for Indian PII and sensitive civic/financial indicators
    private val PHONE_REGEX = Regex("""(?:\+91[-.\s]?)?[6-9]\d{9}""")
    private val AADHAAR_REGEX = Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}\b""")
    private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}""")
    private val SURVEY_NUM_REGEX = Regex("""(?i)(?:survey|sy\.?|సర్వే)\s*(?:no\.?|నంబర్)?\s*[:#-]?\s*\d+(?:[/-]\w+)?""")
    private val CURRENCY_AMOUNT_REGEX = Regex("""(?i)(?:₹|rs\.?|రూ\.?|రూపాయలు)\s*\d+(?:\.\d{1,2})?""")

    /**
     * Sanitizes an arbitrary string by redacting phone numbers, Aadhaar numbers,
     * emails, survey numbers, and currency amounts.
     */
    fun sanitize(message: String): String {
        if (message.isBlank()) return message

        var sanitized = message
        sanitized = PHONE_REGEX.replace(sanitized, "[REDACTED_PHONE]")
        sanitized = AADHAAR_REGEX.replace(sanitized, "[REDACTED_AADHAAR]")
        sanitized = EMAIL_REGEX.replace(sanitized, "[REDACTED_EMAIL]")
        sanitized = SURVEY_NUM_REGEX.replace(sanitized, "[REDACTED_SURVEY_NO]")
        sanitized = CURRENCY_AMOUNT_REGEX.replace(sanitized, "[REDACTED_AMOUNT]")
        return sanitized
    }

    fun d(tag: String, message: String) {
        if (!isLoggingEnabled) return
        try {
            Log.d(tag, sanitize(message))
        } catch (_: Throwable) {
            // Android Log not mocked in pure unit tests
        }
    }

    fun i(tag: String, message: String) {
        if (!isLoggingEnabled) return
        try {
            Log.i(tag, sanitize(message))
        } catch (_: Throwable) {
            // Android Log not mocked in pure unit tests
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (!isLoggingEnabled) return
        try {
            if (throwable != null) {
                Log.w(tag, sanitize(message), throwable)
            } else {
                Log.w(tag, sanitize(message))
            }
        } catch (_: Throwable) {
            // Android Log not mocked in pure unit tests
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!isLoggingEnabled) return
        try {
            if (throwable != null) {
                Log.e(tag, sanitize(message), throwable)
            } else {
                Log.e(tag, sanitize(message))
            }
        } catch (_: Throwable) {
            // Android Log not mocked in pure unit tests
        }
    }
}
