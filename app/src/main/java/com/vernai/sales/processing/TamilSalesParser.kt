package com.vernai.sales.processing

import com.vernai.core.model.SalesItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * High-performance deterministic parser for Tamil sales dictations.
 * Converts spoken Tamil digits, compound number words, units, payment notes, and item terms
 * into strongly-typed [SalesItem] domain entities without LLM hallucination.
 */
class TamilSalesParser {

    companion object {
        // Tamil digit mapping
        private val TAMIL_DIGITS = mapOf(
            '௦' to '0', '௧' to '1', '௨' to '2', '௩' to '3', '௪' to '4',
            '௫' to '5', '௬' to '6', '௭' to '7', '௮' to '8', '௯' to '9'
        )

        // Tamil number words to values
        val NUMBER_WORDS = mapOf(
            "பூஜ்ஜியம்" to 0.0,
            "ஒன்று" to 1.0, "ஒன்னு" to 1.0, "ஒரு" to 1.0, "ஒன்" to 1.0,
            "இரண்டு" to 2.0, "ரெண்டு" to 2.0, "டூ" to 2.0,
            "மூன்று" to 3.0, "மூணு" to 3.0, "த்ரீ" to 3.0,
            "நான்கு" to 4.0, "நாலு" to 4.0, "போர்" to 4.0,
            "ஐந்து" to 5.0, "அஞ்சு" to 5.0, "பைவ்" to 5.0,
            "ஆறு" to 6.0, "சிக்ஸ்" to 6.0,
            "ஏழு" to 7.0, "செவன்" to 7.0,
            "எட்டு" to 8.0, "எய்ட்" to 8.0,
            "ஒன்பது" to 9.0, "ஒம்போது" to 9.0, "நைன்" to 9.0,
            "பத்து" to 10.0, "டென்" to 10.0,
            "பதினொன்று" to 11.0, "பதினோரு" to 11.0, "பன்னிரண்டு" to 12.0, "பன்னண்டு" to 12.0,
            "பதின்மூன்று" to 13.0, "பதிமூணு" to 13.0, "பதினான்கு" to 14.0, "பதினாலு" to 14.0,
            "பதினைந்து" to 15.0, "பதினஞ்சு" to 15.0, "பதினாறு" to 16.0, "பதினேழு" to 17.0,
            "பதினெட்டு" to 18.0, "பத்தொன்பது" to 19.0,
            "இருபது" to 20.0, "முப்பது" to 30.0, "நாற்பது" to 40.0,
            "ஐம்பது" to 50.0, "அறுபது" to 60.0, "எழுபது" to 70.0, "எண்பது" to 80.0, "தொண்ணூறு" to 90.0,
            "நூறு" to 100.0, "நூற்றி" to 100.0, "நூறுக்கு" to 100.0,
            "இருநூறு" to 200.0, "முந்நூறு" to 300.0, "நானூறு" to 400.0, "ஐந்நூறு" to 500.0,
            "அறுநூறு" to 600.0, "எழுநூறு" to 700.0, "எண்ணூறு" to 800.0, "தொள்ளாயிரம்" to 900.0,
            "ஆயிரம்" to 1000.0, "ஆயிரத்து" to 1000.0,
            // Fractions & mixed fractions
            "அரை" to 0.5, "கால்" to 0.25, "முக்கால்" to 0.75,
            "ஒன்றரை" to 1.5, "ஒன்னரை" to 1.5, "இரண்டரை" to 2.5, "ரெண்டரை" to 2.5,
            "மூன்றரை" to 3.5, "நாலரை" to 4.5, "ஐந்தரை" to 5.5
        )

        // Tamil units
        val UNITS_MAP = mapOf(
            "கிலோ" to "கிலோ (kg)", "கிலோக்கள்" to "கிலோ (kg)", "கிலோவுக்கு" to "கிலோ (kg)",
            "kg" to "கிலோ (kg)", "kgs" to "கிலோ (kg)", "kilo" to "கிலோ (kg)",
            "கிராம்" to "கிராம் (gm)", "கிராம்கள்" to "கிராம் (gm)",
            "gm" to "கிராம் (gm)", "grams" to "கிராம் (gm)",
            "லிட்டர்" to "லிட்டர் (L)", "லிட்டர்கள்" to "லிட்டர் (L)", "லிட்டருக்கு" to "லிட்டர் (L)",
            "ltr" to "லிட்டர் (L)", "liters" to "லிட்டர் (L)", "லி." to "லிட்டர் (L)",
            "பாக்கெட்" to "பாக்கெட் (packet)", "பாக்கெட்டுகள்" to "பாக்கெட் (packet)",
            "பாக்கெட்ஸ்" to "பாக்கெட் (packet)", "packet" to "பாக்கெட் (packet)", "packets" to "பாக்கெட் (packet)",
            "டஜன்" to "டஜன் (dozen)", "டஜன்கள்" to "டஜன் (dozen)", "dozen" to "டஜன் (dozen)",
            "மூட்டை" to "மூட்டை (bag)", "மூட்டைகள்" to "மூட்டை (bag)", "பை" to "மூட்டை (bag)", "பைகள்" to "மூட்டை (bag)",
            "பாட்டில்" to "பாட்டில் (bottle)", "பாட்டில்கள்" to "பாட்டில் (bottle)",
            "துண்டு" to "எண்ணிக்கை (pcs)", "பீஸ்" to "எண்ணிக்கை (pcs)", "பீஸ்கள்" to "எண்ணிக்கை (pcs)",
            "pcs" to "எண்ணிக்கை (pcs)", "piece" to "எண்ணிக்கை (pcs)", "எண்ணிக்கை" to "எண்ணிக்கை (pcs)",
            "டின்" to "டின் (tin)", "டப்பா" to "டின் (tin)", "டப்பாக்கள்" to "டின் (tin)"
        )

        // Known Tamil merchandise terms with English equivalents
        val COMMODITY_MAP = mapOf(
            "தக்காளி" to "Tomato", "நாட்டு தக்காளி" to "Tomato",
            "வெங்காயம்" to "Onion", "சின்ன வெங்காயம்" to "Shallots/Onion", "பெரிய வெங்காயம்" to "Big Onion",
            "உருளைக்கிழங்கு" to "Potato", "உருளை" to "Potato",
            "கத்தரிக்காய்" to "Brinjal", "வெண்டைக்காய்" to "Ladies Finger",
            "பச்சை மிளகாய்" to "Green Chilli", "மிளகாய்" to "Chilli", "வர மிளகாய்" to "Red Chilli",
            "இஞ்சி" to "Ginger", "பூண்டு" to "Garlic",
            "அரிசி" to "Rice", "பொன்னி அரிசி" to "Ponni Rice", "பச்சரிசி" to "Raw Rice", "புழுங்கல் அரிசி" to "Boiled Rice",
            "எண்ணெய்" to "Cooking Oil", "சமையல் எண்ணெய்" to "Cooking Oil", "நல்லெண்ணெய்" to "Sesame Oil", "கடலை எண்ணெய்" to "Groundnut Oil",
            "சர்க்கரை" to "Sugar", "சீனி" to "Sugar", "வெல்லம்" to "Jaggery",
            "துவரம் பருப்பு" to "Toor Dal", "பருப்பு" to "Dal", "உளுத்தம் பருப்பு" to "Urad Dal", "பாசிப்பருப்பு" to "Moong Dal", "கடலை பருப்பு" to "Chana Dal",
            "உப்பு" to "Salt", "கல் உப்பு" to "Rock Salt", "தூள் உப்பு" to "Table Salt",
            "கோதுமை மாவு" to "Wheat Flour", "கோதுமை" to "Wheat", "மைதா" to "Maida",
            "டீத்தூள்" to "Tea Powder", "தேயிலை" to "Tea Powder", "காபி தூள்" to "Coffee Powder",
            "பால்" to "Milk", "தயிர்" to "Curd",
            "முட்டை" to "Eggs", "முட்டைகள்" to "Eggs", "கோழி முட்டை" to "Eggs",
            "சோப்பு" to "Soap", "சோப்" to "Soap", "சோப்புகள்" to "Soap", "சலவை சோப்" to "Detergent", "சலவை சோப்பு" to "Detergent", "டிடர்ஜென்ட்" to "Detergent",
            "ஷாம்பு" to "Shampoo", "பிஸ்கட்" to "Biscuits", "சாக்லேட்" to "Chocolates"
        )
    }

    /**
     * Parses spoken Tamil transcript into a list of [SalesItem]s.
     */
    fun parseTranscript(transcript: String, defaultDate: String = getTodayDateString()): List<SalesItem> {
        val cleanText = normalizeTamilDigits(transcript.trim())
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

    /**
     * Parses a single clause describing one sale item in Tamil.
     */
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
            trimmed.contains("கடன்") || trimmed.contains("பாக்கி") || trimmed.contains("உதார்") -> {
                val nameMatch = Regex("""([A-Za-z\u0B80-\u0BFF]+)\s*(?:க்கு|கடன்|பாக்கி)""").find(trimmed)
                val noteStr = if (nameMatch != null && nameMatch.groupValues[1].length > 1 && !isUnitOrNumber(nameMatch.groupValues[1])) {
                    "${nameMatch.groupValues[1]} (கடன் - Credit)"
                } else {
                    "கடன் (Credit)"
                }
                detectedNotes.add(noteStr)
            }
            trimmed.contains("ரொக்கம்") || trimmed.contains("காசு") || trimmed.contains("கேஷ்") -> detectedNotes.add("ரொக்கம் (Cash)")
            trimmed.contains("போன்பே") || trimmed.contains("போன் பே") -> detectedNotes.add("PhonePe (UPI)")
            trimmed.contains("கூகுள்பே") || trimmed.contains("கூகுள் பே") || trimmed.contains("ஜிபே") -> detectedNotes.add("Google Pay (UPI)")
            trimmed.contains("ஆன்லைன்") || trimmed.contains("யுபிஐ") -> detectedNotes.add("UPI (Online)")
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

                // Direct unit (e.g. 5 கிலோ)
                val directUnit = nextToken?.let { UNITS_MAP[it] ?: UNITS_MAP[it.lowercase()] }
                // Delayed unit (e.g. 2 எண்ணெய் பாக்கெட்)
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
                    nextToken.contains("ரூபா") || nextToken.contains("ரூ.") || nextToken == "ரூ" ||
                    nextToken.contains("rs") || nextToken.contains("விலை") || nextToken.contains("ஒவ்வொரு") || nextToken.contains("கிலோவுக்கு")
                )
                val isPerUnit = tokens.getOrNull(i - 1)?.let {
                    it.contains("கிலோவுக்கு") || it.contains("ஒவ்வொன்றும்") || it.contains("லிட்டருக்கு")
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

            // Standalone unit check (e.g. "டஜன் சோப்புகள்" -> 1 dozen)
            val standaloneUnit = UNITS_MAP[token] ?: UNITS_MAP[token.lowercase()]
            if (standaloneUnit != null && detectedUnit == null) {
                detectedUnit = standaloneUnit
                if (detectedQuantity == null) detectedQuantity = 1.0
            }

            i++
        }

        // 3. Fallback unit check from clause if not yet detected
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

        // 4. Extract Item Name
        var itemName = extractItemName(tokens)
        if (itemName.isBlank() || itemName == "பொருள்") {
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
            originalTerm = itemName.ifBlank { "பொருள் (Item)" },
            standardName = standardName,
            quantity = quantity,
            unit = unit,
            unitPrice = unitPrice,
            totalPrice = totalPrice,
            notes = notes
        )
    }

    /**
     * Parses a single token or numeral in Tamil script, digits, or words.
     */
    fun parseNumber(token: String): Double? {
        val normalized = normalizeTamilDigits(token.trim())
        val direct = normalized.toDoubleOrNull()
        if (direct != null) return direct
        val compound = parseCompoundNumber(listOf(normalized), 0)
        if (compound != null) return compound.first
        return NUMBER_WORDS[normalized]
    }

    /**
     * Parses compound Tamil numerals like "இருநூற்று ஐம்பது" (250) or "நூற்று ஐம்பது" (150).
     */
    fun parseCompoundNumber(tokens: List<String>, startIndex: Int): Pair<Double, Int>? {
        var idx = startIndex
        var total = 0.0
        var currentGroup = 0.0
        var matchedAny = false

        while (idx < tokens.size) {
            val raw = tokens[idx].replace("₹", "").replace("ரூ.", "").replace("/-", "").removeSuffix(",").removeSuffix(".").trim()

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
                    raw in listOf("நூறு", "நூற்றி", "நூறுக்கு") -> {
                        currentGroup = if (currentGroup == 0.0) 100.0 else currentGroup * 100.0
                    }
                    raw in listOf("ஆயிரம்", "ஆயிரத்து") -> {
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
        return candidates.take(2).joinToString(" ").ifBlank { "பொருள்" }
    }

    private fun extractItemFallback(clause: String): String {
        for ((k, _) in COMMODITY_MAP) {
            if (clause.contains(k)) return k
        }
        return "பொருள்"
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
        return token in listOf("ரூபாய்", "ரூ.", "ரூ", "ரொக்கம்", "காசு", "கேஷ்", "கடன்", "பாக்கி", "போன்பே", "ஆன்லைன்", "மொத்தம்", "கிலோவுக்கு", "விலை")
    }

    private fun segmentClauses(text: String): List<String> {
        return text.split(Regex("""[,;\n]+|\s+மற்றும்\s+|\s+அப்புறம்\s+|\s+கூட\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun extractDate(text: String): String? {
        val today = getTodayDateString()
        if (text.contains("இன்று") || text.contains("இன்னைக்கு")) return today
        if (text.contains("நேற்று") || text.contains("நேத்து")) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }
        if (text.contains("முந்தாநாள்")) {
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

    fun normalizeTamilDigits(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            sb.append(TAMIL_DIGITS[ch] ?: ch)
        }
        return sb.toString()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
