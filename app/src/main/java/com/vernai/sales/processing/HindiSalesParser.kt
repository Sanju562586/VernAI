package com.vernai.sales.processing

import com.vernai.core.model.SalesItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * High-performance deterministic parser for Hindi sales dictations.
 * Converts spoken Hindi digits, compound number words, units, payment notes, and item terms
 * into strongly-typed [SalesItem] domain entities without LLM hallucination.
 */
class HindiSalesParser {

    companion object {
        // Devanagari digit mapping
        private val HINDI_DIGITS = mapOf(
            '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
            '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
        )

        // Hindi number words to values
        val NUMBER_WORDS = mapOf(
            "शून्य" to 0.0, "ज़ीरो" to 0.0, "जीरो" to 0.0,
            "एक" to 1.0, "वन" to 1.0,
            "दो" to 2.0, "टू" to 2.0,
            "तीन" to 3.0, "थ्री" to 3.0,
            "चार" to 4.0, "फोर" to 4.0,
            "पांच" to 5.0, "पाँच" to 5.0, "फाइव" to 5.0,
            "छह" to 6.0, "छे" to 6.0, "सिक्स" to 6.0,
            "सात" to 7.0, "सेवन" to 7.0,
            "आठ" to 8.0, "एट" to 8.0,
            "नौ" to 9.0, "नाइन" to 9.0,
            "दस" to 10.0, "टेन" to 10.0,
            "ग्यारह" to 11.0, "बारह" to 12.0, "तेरह" to 13.0, "चौदह" to 14.0, "पंद्रह" to 15.0,
            "सोलह" to 16.0, "सत्रह" to 17.0, "अठारह" to 18.0, "उन्नीस" to 19.0,
            "बीस" to 20.0, "इक्कीस" to 21.0, "बाईस" to 22.0, "तेईस" to 23.0, "चौबीस" to 24.0, "पच्चीस" to 25.0,
            "तीस" to 30.0, "चालीस" to 40.0, "पचास" to 50.0, "साठ" to 60.0, "सत्तर" to 70.0, "अस्सी" to 80.0, "नब्बे" to 90.0,
            "सौ" to 100.0, "सौवां" to 100.0,
            "हजार" to 1000.0, "हज़ार" to 1000.0,
            // Fractions & mixed fractions
            "आधा" to 0.5, "पाव" to 0.25, "पौन" to 0.75,
            "डेढ़" to 1.5, "ढाई" to 2.5, "साढ़े तीन" to 3.5, "साढ़े चार" to 4.5, "साढ़े पांच" to 5.5
        )

        // Hindi units
        val UNITS_MAP = mapOf(
            "किलो" to "किलो (kg)", "किलोग्राम" to "किलो (kg)", "केजी" to "किलो (kg)",
            "kg" to "किलो (kg)", "kgs" to "किलो (kg)", "kilo" to "किलो (kg)",
            "ग्राम" to "ग्राम (gm)", "ग्राम्स" to "ग्राम (gm)", "gm" to "ग्राम (gm)",
            "लीटर" to "लीटर (L)", "ltr" to "लीटर (L)", "liters" to "लीटर (L)",
            "पैकेट" to "पैकेट (packet)", "पैकेट्स" to "पैकेट (packet)", "packet" to "पैकेट (packet)",
            "दर्जन" to "दर्जन (dozen)", "dozen" to "दर्जन (dozen)",
            "बोरी" to "बोरी (bag)", "कट्टा" to "बोरी (bag)", "थैला" to "बोरी (bag)", "bag" to "बोरी (bag)",
            "बोतल" to "बोतल (bottle)", "bottle" to "बोतल (bottle)",
            "पीस" to "नग/पीस (pcs)", "नग" to "नग/पीस (pcs)", "pcs" to "नग/पीस (pcs)", "piece" to "नग/पीस (pcs)",
            "डिब्बा" to "डिब्बा (tin)", "टिन" to "डिब्बा (tin)", "tin" to "डिब्बा (tin)"
        )

        // Known Hindi merchandise terms
        val COMMODITY_MAP = mapOf(
            "टमाटर" to "Tomato", "प्याज" to "Onion", "कांदा" to "Onion",
            "आलू" to "Potato", "बैंगन" to "Brinjal", "भिंडी" to "Ladies Finger",
            "हरी मिर्च" to "Green Chilli", "मिर्च" to "Chilli", "अदरक" to "Ginger", "लहसुन" to "Garlic",
            "चावल" to "Rice", "बासमती चावल" to "Basmati Rice",
            "तेल" to "Cooking Oil", "सरसों का तेल" to "Mustard Oil", "रिफाइंड तेल" to "Refined Oil",
            "चीनी" to "Sugar", "शक्कर" to "Sugar", "गुड़" to "Jaggery",
            "अरहर दाल" to "Toor Dal", "तूर दाल" to "Toor Dal", "दाल" to "Dal", "उड़द दाल" to "Urad Dal", "मूंग दाल" to "Moong Dal", "चना दाल" to "Chana Dal",
            "नमक" to "Salt", "आटा" to "Wheat Flour", "गेहूं का आटा" to "Wheat Flour", "मैदा" to "Maida",
            "चायपत्ती" to "Tea Powder", "चाय" to "Tea Powder", "कॉफी" to "Coffee Powder",
            "दूध" to "Milk", "दही" to "Curd",
            "अंडे" to "Eggs", "अंडा" to "Eggs",
            "साबुन" to "Soap", "सर्फ" to "Detergent", "डिटर्जेंट" to "Detergent",
            "बिस्कुट" to "Biscuits", "चॉकलेट" to "Chocolates"
        )
    }

    fun parseTranscript(transcript: String, defaultDate: String = getTodayDateString()): List<SalesItem> {
        val cleanText = normalizeHindiDigits(transcript.trim())
        if (cleanText.isBlank()) return emptyList()

        val docDate = extractDate(cleanText) ?: defaultDate
        val clauses = segmentClauses(cleanText)
        val items = mutableListOf<SalesItem>()

        for (clause in clauses) {
            val parsedItem = parseSingleClause(clause, docDate)
            if (parsedItem != null) {
                items.add(parsedItem)
            }
        }

        return items
    }

    fun parseSingleClause(clause: String, date: String): SalesItem? {
        val trimmed = clause.trim()
        if (trimmed.length < 2) return null

        val tokens = trimmed.split(Regex("""\s+"""))
        var detectedQuantity: Double? = null
        var detectedUnit: String? = null
        var detectedUnitPrice: Double? = null
        var detectedTotalPrice: Double? = null
        val detectedNotes = mutableListOf<String>()

        // 1. Check notes / payment mode
        when {
            trimmed.contains("उधार") || trimmed.contains("बाकी") || trimmed.contains("खाता") -> {
                val nameMatch = Regex("""([A-Za-z\u0900-\u097F]+)\s*(?:को|का)?\s*(?:उधार|बाकी|खाते)""").find(trimmed)
                val noteStr = if (nameMatch != null && nameMatch.groupValues[1].length > 1 && !isUnitOrNumber(nameMatch.groupValues[1])) {
                    "${nameMatch.groupValues[1]} (उधार - Credit)"
                } else {
                    "उधार (Credit)"
                }
                detectedNotes.add(noteStr)
            }
            trimmed.contains("नकद") || trimmed.contains("कैश") -> detectedNotes.add("नकद (Cash)")
            trimmed.contains("फोनपे") || trimmed.contains("फोन पे") -> detectedNotes.add("PhonePe (UPI)")
            trimmed.contains("गूगलपे") || trimmed.contains("गूगल पे") || trimmed.contains("जीपे") -> detectedNotes.add("Google Pay (UPI)")
            trimmed.contains("ऑनलाइन") || trimmed.contains("यूपीआई") -> detectedNotes.add("UPI (Online)")
        }

        // 2. Identify compound numbers and units
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i].removeSuffix(",").removeSuffix(".").removeSuffix("/-")

            val parsedNum = parseCompoundNumber(tokens, i)
            if (parsedNum != null) {
                val numVal = parsedNum.first
                val advance = parsedNum.second
                val nextToken = tokens.getOrNull(i + advance)?.removeSuffix(",")?.removeSuffix(".")
                val nextNextToken = tokens.getOrNull(i + advance + 1)?.removeSuffix(",")?.removeSuffix(".")

                val directUnit = nextToken?.let { UNITS_MAP[it] ?: UNITS_MAP[it.lowercase()] }
                val delayedUnit = nextNextToken?.let { UNITS_MAP[it] ?: UNITS_MAP[it.lowercase()] }

                if (directUnit != null) {
                    detectedQuantity = numVal
                    detectedUnit = directUnit
                    i += advance + 1
                    continue
                } else if (delayedUnit != null) {
                    detectedQuantity = numVal
                    detectedUnit = delayedUnit
                    i += advance
                    continue
                }

                // Check price indicator
                val isPrice = nextToken != null && (
                    nextToken.contains("रुप") || nextToken.contains("रु.") || nextToken == "रु" ||
                    nextToken.contains("rs") || nextToken.contains("दर") || nextToken.contains("भाव")
                )
                val isPerUnit = tokens.getOrNull(i - 1)?.let {
                    it.contains("प्रति") || it.contains("किलो")
                } == true

                if (isPerUnit) {
                    detectedUnitPrice = numVal
                } else if (isPrice || detectedQuantity != null) {
                    if (detectedTotalPrice == null) {
                        detectedTotalPrice = numVal
                    } else if (detectedUnitPrice == null) {
                        detectedUnitPrice = numVal
                    }
                } else if (detectedQuantity == null) {
                    detectedQuantity = numVal
                } else {
                    detectedTotalPrice = numVal
                }

                i += advance
                if (isPrice) i++
                continue
            }

            // Standalone unit check
            val standaloneUnit = UNITS_MAP[token] ?: UNITS_MAP[token.lowercase()]
            if (standaloneUnit != null && detectedUnit == null) {
                detectedUnit = standaloneUnit
                if (detectedQuantity == null) detectedQuantity = 1.0
            }

            i++
        }

        if (detectedUnit == null) {
            for (t in tokens) {
                val clean = t.removeSuffix(",").removeSuffix(".")
                val u = UNITS_MAP[clean] ?: UNITS_MAP[clean.lowercase()]
                if (u != null) {
                    detectedUnit = u
                    break
                }
            }
        }

        var itemName = extractItemName(tokens)
        if (itemName.isBlank() || itemName == "सामग्री") {
            itemName = extractItemFallback(clause)
        }
        val standardName = COMMODITY_MAP[itemName] ?: COMMODITY_MAP[itemName.split(" ").firstOrNull()] ?: itemName

        val quantity = detectedQuantity ?: 0.0
        val unit = detectedUnit ?: "unit"
        val totalPrice = detectedTotalPrice ?: 0.0
        val unitPrice = detectedUnitPrice ?: if (quantity > 0.0 && totalPrice > 0.0) (Math.round((totalPrice / quantity) * 100.0) / 100.0) else 0.0
        val notes = if (detectedNotes.isNotEmpty()) detectedNotes.joinToString(", ") else null

        return SalesItem(
            id = UUID.randomUUID().toString(),
            date = date,
            originalTerm = itemName.ifBlank { "सामग्री (Item)" },
            standardName = standardName,
            quantity = quantity,
            unit = unit,
            unitPrice = unitPrice,
            totalPrice = totalPrice,
            notes = notes
        )
    }

    /**
     * Parses a single token or numeral in Devanagari script, digits, or Hindi words.
     */
    fun parseNumber(token: String): Double? {
        val normalized = normalizeHindiDigits(token.trim())
        val direct = normalized.toDoubleOrNull()
        if (direct != null) return direct
        val tokens = normalized.split(Regex("""\s+"""))
        val compound = parseCompoundNumber(tokens, 0)
        if (compound != null) return compound.first
        return NUMBER_WORDS[normalized]
    }

    fun parseCompoundNumber(tokens: List<String>, startIndex: Int): Pair<Double, Int>? {
        var idx = startIndex
        var total = 0.0
        var currentGroup = 0.0
        var matchedAny = false

        while (idx < tokens.size) {
            val raw = tokens[idx].replace("₹", "").replace("रु.", "").replace("/-", "").removeSuffix(",").removeSuffix(".").trim()

            val num = raw.toDoubleOrNull()
            if (num != null) {
                if (!matchedAny) {
                    return Pair(num, 1)
                } else {
                    break
                }
            }

            val wordVal = NUMBER_WORDS[raw]
            if (wordVal != null) {
                matchedAny = true
                when {
                    raw in listOf("सौ", "सौवां") -> {
                        currentGroup = if (currentGroup == 0.0) 100.0 else currentGroup * 100.0
                    }
                    raw in listOf("हजार", "हज़ार") -> {
                        currentGroup = if (currentGroup == 0.0) 1000.0 else currentGroup * 1000.0
                        total += currentGroup
                        currentGroup = 0.0
                    }
                    wordVal in listOf(0.5, 0.25, 0.75, 1.5, 2.5, 3.5, 4.5, 5.5) -> {
                        currentGroup += wordVal
                    }
                    else -> {
                        currentGroup += wordVal
                    }
                }
                idx++
            } else {
                break
            }
        }

        total += currentGroup
        return if (matchedAny) Pair(total, idx - startIndex) else null
    }

    private fun extractItemName(tokens: List<String>): String {
        val candidates = mutableListOf<String>()
        for (token in tokens) {
            val clean = token.removeSuffix(",").removeSuffix(".").trim()
            if (COMMODITY_MAP.containsKey(clean)) {
                return clean
            }
            if (!isNumber(clean) && !isUnit(clean) && !isPaymentWord(clean) && clean.length > 1) {
                candidates.add(clean)
            }
        }
        return candidates.take(2).joinToString(" ").ifBlank { "सामग्री" }
    }

    private fun extractItemFallback(clause: String): String {
        for ((k, _) in COMMODITY_MAP) {
            if (clause.contains(k)) return k
        }
        return "सामग्री"
    }

    private fun isNumber(token: String): Boolean {
        return token.toDoubleOrNull() != null || NUMBER_WORDS.containsKey(token)
    }

    private fun isUnit(token: String): Boolean {
        return UNITS_MAP.containsKey(token) || UNITS_MAP.containsKey(token.lowercase())
    }

    private fun isUnitOrNumber(token: String): Boolean {
        return isNumber(token) || isUnit(token)
    }

    private fun isPaymentWord(token: String): Boolean {
        return token in listOf("रुपये", "रुपया", "रु.", "रु", "नकद", "कैश", "उधार", "बाकी", "फोनपे", "ऑनलाइन", "कुल", "प्रति")
    }

    private fun segmentClauses(text: String): List<String> {
        return text.split(Regex("""[,;\n]+|\s+और\s+|\s+तथा\s+|\s+एवं\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun extractDate(text: String): String? {
        val today = getTodayDateString()
        if (text.contains("आज")) return today
        if (text.contains("कल")) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }
        if (text.contains("परसों")) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -2)
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }

        val dateMatch = Regex("""\b(\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b""").find(text)
        if (dateMatch != null) {
            return dateMatch.value
        }
        return null
    }

    fun normalizeHindiDigits(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            sb.append(HINDI_DIGITS[ch] ?: ch)
        }
        return sb.toString()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
