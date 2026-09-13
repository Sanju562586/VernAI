package com.vernai.sales.processing

import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesValidationStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * High-performance deterministic parser for Telugu sales dictations.
 * Converts spoken Telugu digits, compound number words, units, payment notes, and item terms
 * into strongly-typed [SalesItem] domain entities without LLM hallucination.
 */
class TeluguSalesParser {

    companion object {
        // Telugu digit mapping
        private val TELUGU_DIGITS = mapOf(
            '౦' to '0', '౧' to '1', '౨' to '2', '౩' to '3', '౪' to '4',
            '౫' to '5', '౬' to '6', '౭' to '7', '౮' to '8', '౯' to '9'
        )

        // Telugu number words to values
        val NUMBER_WORDS = mapOf(
            "సున్నా" to 0.0,
            "ఒకటి" to 1.0, "ఒక" to 1.0, "వన్" to 1.0, "ఒకటో" to 1.0,
            "రెండు" to 2.0, "రెండో" to 2.0, "టూ" to 2.0,
            "మూడు" to 3.0, "మూడో" to 3.0, "త్రీ" to 3.0,
            "నాలుగు" to 4.0, "నాలుగో" to 4.0, "ఫోర్" to 4.0,
            "ఐదు" to 5.0, "అయిదు" to 5.0, "ఐదో" to 5.0, "ఫైవ్" to 5.0,
            "ఆరు" to 6.0, "ఆరో" to 6.0, "సిక్స్" to 6.0,
            "ఏడు" to 7.0, "ఏడో" to 7.0, "సెవెన్" to 7.0,
            "ఎనిమిది" to 8.0, "ఎనిమిదో" to 8.0, "ఎయిట్" to 8.0,
            "తొమ్మిది" to 9.0, "తొమ్మిదో" to 9.0, "నైన్" to 9.0,
            "పది" to 10.0, "టెన్" to 10.0,
            "పదకొండు" to 11.0, "పన్నెండు" to 12.0, "పదమూడు" to 13.0,
            "పద్నాలుగు" to 14.0, "పదిహేను" to 15.0, "పదహారు" to 16.0,
            "పదిహేడు" to 17.0, "పద్దెనిమిది" to 18.0, "పందొమ్మిది" to 19.0,
            "ఇరవై" to 20.0, "ముప్పై" to 30.0, "నలభై" to 40.0,
            "యాభై" to 50.0, "యాబై" to 50.0, "అరవై" to 60.0,
            "డెబ్బై" to 70.0, "ఎనభై" to 80.0, "తొంభై" to 90.0,
            "వంద" to 100.0, "వందలు" to 100.0, "వందల" to 100.0, "నూట" to 100.0,
            "వేయి" to 1000.0, "వెయ్యి" to 1000.0, "వేలు" to 1000.0, "వేల" to 1000.0,
            // Fractions & mixed fractions
            "అర" to 0.5, "పావు" to 0.25, "ముప్పావు" to 0.75,
            "ఒకటిన్నర" to 1.5, "రెండున్నర" to 2.5, "మూడున్నర" to 3.5,
            "నాలుగున్నర" to 4.5, "ఐదున్నర" to 5.5
        )

        // Telugu units
        val UNITS_MAP = mapOf(
            "కేజీ" to "కేజీ (kg)", "కేజీలు" to "కేజీ (kg)", "కేజీల" to "కేజీ (kg)",
            "కిలో" to "కేజీ (kg)", "కిలోలు" to "కేజీ (kg)", "కిలోల" to "కేజీ (kg)",
            "kg" to "కేజీ (kg)", "kgs" to "కేజీ (kg)",
            "గ్రాములు" to "గ్రాములు (gm)", "గ్రాం" to "గ్రాములు (gm)", "గ్రా." to "గ్రాములు (gm)",
            "gm" to "గ్రాములు (gm)", "grams" to "గ్రాములు (gm)",
            "లీటర్" to "లీటర్ (L)", "లీటర్లు" to "లీటర్ (L)", "లీటర్ల" to "లీటర్ (L)",
            "లీ." to "లీటర్ (L)", "ltr" to "లీటర్ (L)", "liters" to "లీటర్ (L)",
            "ప్యాకెట్" to "ప్యాకెట్ (packet)", "ప్యాకెట్లు" to "ప్యాకెట్ (packet)",
            "ప్యాకెట్ల" to "ప్యాకెట్ (packet)", "packets" to "ప్యాకెట్ (packet)",
            "డజన్" to "డజన్ (dozen)", "డజన్లు" to "డజన్ (dozen)", "డజన్ల" to "డజన్ (dozen)",
            "dozen" to "డజన్ (dozen)",
            "బస్తా" to "బస్తా (bag)", "బస్తాలు" to "బస్తా (bag)", "బస్తాల" to "బస్తా (bag)",
            "సంచి" to "బస్తా (bag)", "సంచులు" to "బస్తా (bag)", "సంచుల" to "బస్తా (bag)",
            "బాటిల్" to "బాటిల్ (bottle)", "బాటిళ్ళు" to "బాటిల్ (bottle)", "బాటిళ్ల" to "బాటిల్ (bottle)",
            "సీసా" to "బాటిల్ (bottle)", "సీసాలు" to "బాటిల్ (bottle)",
            "కట్ట" to "పీస్/కట్ట (pcs)", "కట్టలు" to "పీస్/కట్ట (pcs)",
            "పీస్" to "పీస్/కట్ట (pcs)", "పీసులు" to "పీస్/కట్ట (pcs)", "pcs" to "పీస్/కట్ట (pcs)",
            "డబ్బా" to "డబ్బా (tin)", "డబ్బాలు" to "డబ్బా (tin)"
        )

        // Known Telugu merchandise terms with English equivalents
        val COMMODITY_MAP = mapOf(
            "టమాటా" to "Tomato", "టమాటాలు" to "Tomato", "టమాట" to "Tomato",
            "ఉల్లిపాయలు" to "Onion", "ఉల్లిగడ్డలు" to "Onion", "ఉల్లి" to "Onion",
            "బంగాళాదుంపలు" to "Potato", "ఆలూ" to "Potato",
            "వంకాయలు" to "Brinjal", "బెండకాయలు" to "Ladies Finger",
            "పచ్చిమిర్చి" to "Green Chilli", "ఎండుమిర్చి" to "Red Chilli",
            "అల్లం" to "Ginger", "వెల్లుల్లి" to "Garlic",
            "బియ్యం" to "Rice", "నూనె" to "Cooking Oil", "నూనె ప్యాకెట్లు" to "Cooking Oil",
            "పంచదార" to "Sugar", "చక్కెర" to "Sugar",
            "కందిపప్పు" to "Toor Dal", "మినప్పప్పు" to "Urad Dal", "పెసరపప్పు" to "Moong Dal",
            "ఉప్పు" to "Salt", "గోధుమపిండి" to "Wheat Flour", "మైదా" to "Maida",
            "టీ పొడి" to "Tea Powder", "కాఫీ పొడి" to "Coffee Powder",
            "పాలు" to "Milk", "పెరుగు" to "Curd",
            "గుడ్లు" to "Eggs", "కోడిగుడ్లు" to "Eggs",
            "సబ్బులు" to "Soaps", "సబ్బు" to "Soap", "డిటర్జెంట్" to "Detergent",
            "షాంపూ" to "Shampoo", "బిస్కెట్లు" to "Biscuits", "చాక్లెట్లు" to "Chocolates"
        )
    }

    /**
     * Parses spoken Telugu transcript into a list of [SalesItem]s.
     */
    fun parseTranscript(transcript: String, defaultDate: String = getTodayDateString()): List<SalesItem> {
        val cleanText = normalizeTeluguDigits(transcript.trim())
        if (cleanText.isBlank()) return emptyList()

        // Extract overall date mention if present
        val docDate = extractDate(cleanText) ?: defaultDate

        // Segment transcript into distinct item clauses
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
     * Parses a single clause describing one sale item.
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
            trimmed.contains("అరువు") || trimmed.contains("బాకీ") || trimmed.contains("ఉధార్") -> {
                val nameMatch = Regex("""([A-Za-z\u0C00-\u0C7F]+)\s*(?:కి|కు|గారికి)?\s*(?:అరువు|బాకీ|ఉధార్)""").find(trimmed)
                val noteStr = if (nameMatch != null && nameMatch.groupValues[1].length > 1 && !isUnitOrNumber(nameMatch.groupValues[1])) {
                    "${nameMatch.groupValues[1]} (అరువు - Credit)"
                } else {
                    "అరువు (Credit)"
                }
                detectedNotes.add(noteStr)
            }
            trimmed.contains("నగదు") || trimmed.contains("క్యాష్") -> detectedNotes.add("నగదు (Cash)")
            trimmed.contains("ఫోన్‌పే") || trimmed.contains("ఫోన్ పే") -> detectedNotes.add("PhonePe (UPI)")
            trimmed.contains("గూగుల్‌పే") || trimmed.contains("గూగుల్ పే") -> detectedNotes.add("Google Pay (UPI)")
            trimmed.contains("ఆన్‌లైన్") || trimmed.contains("యూపీఐ") -> detectedNotes.add("UPI (Online)")
        }

        // 2. Identify compound numbers and units
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i].removeSuffix(",").removeSuffix(".").removeSuffix("/-")

            // Check compound number at token i
            val parsedNum = parseCompoundNumber(tokens, i)
            if (parsedNum != null) {
                val numVal = parsedNum.first
                val advance = parsedNum.second
                val nextToken = tokens.getOrNull(i + advance)?.removeSuffix(",")?.removeSuffix(".")
                val nextNextToken = tokens.getOrNull(i + advance + 1)?.removeSuffix(",")?.removeSuffix(".")

                // Check if followed directly by unit (e.g. 5 కేజీలు)
                val directUnit = nextToken?.let { UNITS_MAP[it] ?: UNITS_MAP[it.lowercase()] }
                // Check if followed by item then unit (e.g. 2 నూనె ప్యాకెట్లు)
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
                    nextToken.contains("రూపా") || nextToken.contains("రూ.") || nextToken == "రూ" ||
                    nextToken.contains("rs") || nextToken.contains("ఒక్కొక్క") || nextToken.contains("కేజీకి")
                )
                val isPerUnit = tokens.getOrNull(i - 1)?.let {
                    it.contains("కేజీకి") || it.contains("ఒక్కొక్క") || it.contains("లీటరుకి")
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
                if (isPrice) i++ // skip price marker
                continue
            }

            // Standalone unit check (e.g. "డజన్ సబ్బులు" -> 1 dozen)
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
        if (itemName.isBlank() || itemName == "వస్తువు") {
            itemName = extractItemFallback(clause)
        }
        val standardName = COMMODITY_MAP[itemName] ?: COMMODITY_MAP[itemName.split(" ").firstOrNull()] ?: itemName

        // Finalize values
        val quantity = detectedQuantity ?: 0.0 // 0.0 signals ambiguity (missing quantity)
        val unit = detectedUnit ?: "unit"
        val unitPrice = detectedUnitPrice ?: 0.0
        val totalPrice = detectedTotalPrice ?: 0.0
        val notes = if (detectedNotes.isNotEmpty()) detectedNotes.joinToString(", ") else null

        return SalesItem(
            id = UUID.randomUUID().toString(),
            date = date,
            originalTerm = itemName.ifBlank { "వస్తువు (Item)" },
            standardName = standardName,
            quantity = quantity,
            unit = unit,
            unitPrice = unitPrice,
            totalPrice = totalPrice,
            notes = notes
        )
    }

    /**
     * Parses compound Telugu numerals like "రెండు వందల యాభై" (250) or "నూట యాభై" (150).
     */
    fun parseCompoundNumber(tokens: List<String>, startIndex: Int): Pair<Double, Int>? {
        var idx = startIndex
        var total = 0.0
        var currentGroup = 0.0
        var matchedAny = false

        while (idx < tokens.size) {
            val raw = tokens[idx].replace("₹", "").replace("రూ.", "").replace("/-", "").removeSuffix(",").removeSuffix(".").trim()

            // Direct numeric literal check (e.g. 5, 200, 2.5)
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
                    raw in listOf("వంద", "వందలు", "వందల", "నూట") -> {
                        currentGroup = if (currentGroup == 0.0) 100.0 else currentGroup * 100.0
                    }
                    raw in listOf("వేలు", "వేయి", "వెయ్యి", "వేల") -> {
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
        return candidates.take(2).joinToString(" ").ifBlank { "వస్తువు" }
    }

    private fun extractItemFallback(clause: String): String {
        for ((k, _) in COMMODITY_MAP) {
            if (clause.contains(k)) return k
        }
        return "వస్తువు"
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
        return token in listOf("రూపాయలు", "రూ.", "రూ", "నగదు", "క్యాష్", "అరువు", "బాకీ", "ఫోన్‌పే", "ఆన్‌లైన్", "మొత్తం", "కేజీకి", "ఒక్కొక్కటి")
    }

    private fun segmentClauses(text: String): List<String> {
        // Split by punctuation, or conjunctions "మరియు", "అలాగే"
        return text.split(Regex("""[,;\n]+|\s+మరియు\s+|\s+అలాగే\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun extractDate(text: String): String? {
        val today = getTodayDateString()
        if (text.contains("ఈరోజు") || text.contains("నేడు")) return today
        if (text.contains("నిన్న")) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }
        if (text.contains("మొన్న")) {
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

    fun normalizeTeluguDigits(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            sb.append(TELUGU_DIGITS[ch] ?: ch)
        }
        return sb.toString()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
