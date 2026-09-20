package com.vernai.sales.processing

import com.vernai.core.model.SalesItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * High-performance deterministic parser for English / Indian English sales dictations.
 */
class EnglishSalesParser {

    companion object {
        val NUMBER_WORDS = mapOf(
            "zero" to 0.0,
            "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
            "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
            "eleven" to 11.0, "twelve" to 12.0, "thirteen" to 13.0, "fourteen" to 14.0, "fifteen" to 15.0,
            "sixteen" to 16.0, "seventeen" to 17.0, "eighteen" to 18.0, "nineteen" to 19.0,
            "twenty" to 20.0, "thirty" to 30.0, "forty" to 40.0, "fifty" to 50.0,
            "sixty" to 60.0, "seventy" to 70.0, "eighty" to 80.0, "ninety" to 90.0,
            "hundred" to 100.0, "thousand" to 1000.0,
            "half" to 0.5, "quarter" to 0.25
        )

        val UNITS_MAP = mapOf(
            "kg" to "kg", "kgs" to "kg", "kilo" to "kg", "kilos" to "kg", "kilogram" to "kg", "kilograms" to "kg",
            "gm" to "gm", "gms" to "gm", "gram" to "gm", "grams" to "gm",
            "liter" to "L", "liters" to "L", "litre" to "L", "litres" to "L", "ltr" to "L",
            "packet" to "packet", "packets" to "packet", "pkt" to "packet", "pkts" to "packet",
            "dozen" to "dozen", "dozens" to "dozen", "doz" to "dozen",
            "bag" to "bag", "bags" to "bag", "sack" to "bag", "sacks" to "bag",
            "bottle" to "bottle", "bottles" to "bottle",
            "piece" to "pcs", "pieces" to "pcs", "pcs" to "pcs", "pc" to "pcs",
            "tin" to "tin", "tins" to "tin", "can" to "tin", "box" to "box"
        )

        val COMMODITY_MAP = mapOf(
            "tomato" to "Tomato", "tomatoes" to "Tomato",
            "onion" to "Onion", "onions" to "Onion",
            "potato" to "Potato", "potatoes" to "Potato",
            "brinjal" to "Brinjal", "eggplant" to "Brinjal",
            "chilli" to "Chilli", "chillies" to "Chilli", "green chilli" to "Green Chilli",
            "ginger" to "Ginger", "garlic" to "Garlic",
            "rice" to "Rice", "basmati rice" to "Basmati Rice",
            "oil" to "Cooking Oil", "cooking oil" to "Cooking Oil", "sunflower oil" to "Sunflower Oil",
            "sugar" to "Sugar", "jaggery" to "Jaggery",
            "dal" to "Dal", "toor dal" to "Toor Dal", "urad dal" to "Urad Dal", "moong dal" to "Moong Dal",
            "salt" to "Salt", "wheat flour" to "Wheat Flour", "flour" to "Wheat Flour", "atta" to "Atta", "maida" to "Maida",
            "tea" to "Tea Powder", "tea powder" to "Tea Powder", "coffee" to "Coffee Powder",
            "milk" to "Milk", "curd" to "Curd",
            "egg" to "Eggs", "eggs" to "Eggs",
            "soap" to "Soap", "soaps" to "Soap", "detergent" to "Detergent",
            "biscuits" to "Biscuits", "biscuit" to "Biscuits", "chocolate" to "Chocolates"
        )
    }

    fun parseTranscript(transcript: String, defaultDate: String = getTodayDateString()): List<SalesItem> {
        if (transcript.isBlank()) return emptyList()

        val docDate = extractDate(transcript) ?: defaultDate
        val clauses = segmentClauses(transcript)
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

        val lowerClause = trimmed.lowercase()

        // 1. Notes / payment mode
        when {
            lowerClause.contains("credit") || lowerClause.contains("due") || lowerClause.contains("udhar") || lowerClause.contains("khata") -> {
                val nameMatch = Regex("""([A-Za-z]+)\s*(?:credit|due)""").find(trimmed)
                val noteStr = if (nameMatch != null && nameMatch.groupValues[1].length > 1 && !isUnitOrNumber(nameMatch.groupValues[1])) {
                    "${nameMatch.groupValues[1]} (Credit)"
                } else {
                    "Credit"
                }
                detectedNotes.add(noteStr)
            }
            lowerClause.contains("cash") -> detectedNotes.add("Cash")
            lowerClause.contains("phonepe") -> detectedNotes.add("PhonePe (UPI)")
            lowerClause.contains("gpay") || lowerClause.contains("google pay") -> detectedNotes.add("Google Pay (UPI)")
            lowerClause.contains("online") || lowerClause.contains("upi") -> detectedNotes.add("UPI (Online)")
        }

        // 2. Identify numbers and units
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i].removeSuffix(",").removeSuffix(".").removeSuffix("/-")

            val parsedNum = parseCompoundNumber(tokens, i)
            if (parsedNum != null) {
                val numVal = parsedNum.first
                val advance = parsedNum.second
                val nextToken = tokens.getOrNull(i + advance)?.removeSuffix(",")?.removeSuffix(".")?.lowercase()
                val nextNextToken = tokens.getOrNull(i + advance + 1)?.removeSuffix(",")?.removeSuffix(".")?.lowercase()

                val directUnit = nextToken?.let { UNITS_MAP[it] }
                val delayedUnit = nextNextToken?.let { UNITS_MAP[it] }

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
                    nextToken.contains("rs") || nextToken.contains("rupee") || nextToken.contains("inr") ||
                    nextToken == "for" || nextToken == "each" || nextToken.contains("rate")
                )
                val isPerUnit = tokens.getOrNull(i - 1)?.lowercase()?.let {
                    it.contains("per") || it.contains("each")
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

            val standaloneUnit = UNITS_MAP[token.lowercase()]
            if (standaloneUnit != null && detectedUnit == null) {
                detectedUnit = standaloneUnit
                if (detectedQuantity == null) detectedQuantity = 1.0
            }

            i++
        }

        if (detectedUnit == null) {
            for (t in tokens) {
                val clean = t.removeSuffix(",").removeSuffix(".").lowercase()
                val u = UNITS_MAP[clean]
                if (u != null) {
                    detectedUnit = u
                    break
                }
            }
        }

        var itemName = extractItemName(tokens)
        if (itemName.isBlank() || itemName.equals("item", ignoreCase = true)) {
            itemName = extractItemFallback(clause)
        }
        val standardName = COMMODITY_MAP[itemName.lowercase()] ?: COMMODITY_MAP[itemName.lowercase().split(" ").firstOrNull()] ?: itemName

        val quantity = detectedQuantity ?: 0.0
        val unit = detectedUnit ?: "unit"
        val totalPrice = detectedTotalPrice ?: 0.0
        val unitPrice = detectedUnitPrice ?: if (quantity > 0.0 && totalPrice > 0.0) (Math.round((totalPrice / quantity) * 100.0) / 100.0) else 0.0
        val notes = if (detectedNotes.isNotEmpty()) detectedNotes.joinToString(", ") else null

        return SalesItem(
            id = UUID.randomUUID().toString(),
            date = date,
            originalTerm = itemName.ifBlank { "Item" },
            standardName = standardName,
            quantity = quantity,
            unit = unit,
            unitPrice = unitPrice,
            totalPrice = totalPrice,
            notes = notes
        )
    }

    fun parseCompoundNumber(tokens: List<String>, startIndex: Int): Pair<Double, Int>? {
        var idx = startIndex
        var total = 0.0
        var currentGroup = 0.0
        var matchedAny = false

        while (idx < tokens.size) {
            val raw = tokens[idx].replace("₹", "").replace("Rs.", "").replace("Rs", "").replace("/-", "").removeSuffix(",").removeSuffix(".").trim().lowercase()

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
                when (raw) {
                    "hundred" -> {
                        currentGroup = if (currentGroup == 0.0) 100.0 else currentGroup * 100.0
                    }
                    "thousand" -> {
                        currentGroup = if (currentGroup == 0.0) 1000.0 else currentGroup * 1000.0
                        total += currentGroup
                        currentGroup = 0.0
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
            val lower = clean.lowercase()
            if (COMMODITY_MAP.containsKey(lower)) {
                return clean
            }
            if (!isNumber(clean) && !isUnit(clean) && !isPaymentWord(clean) && clean.length > 1) {
                candidates.add(clean)
            }
        }
        return candidates.take(2).joinToString(" ").ifBlank { "Item" }
    }

    private fun extractItemFallback(clause: String): String {
        val lower = clause.lowercase()
        for ((k, v) in COMMODITY_MAP) {
            if (lower.contains(k)) return v
        }
        return "Item"
    }

    private fun isNumber(token: String): Boolean {
        return token.toDoubleOrNull() != null || NUMBER_WORDS.containsKey(token.lowercase())
    }

    private fun isUnit(token: String): Boolean {
        return UNITS_MAP.containsKey(token.lowercase())
    }

    private fun isUnitOrNumber(token: String): Boolean {
        return isNumber(token) || isUnit(token)
    }

    private fun isPaymentWord(token: String): Boolean {
        val lower = token.lowercase()
        return lower in listOf("rupees", "rupee", "rs", "inr", "cash", "credit", "due", "online", "upi", "total", "sold", "today")
    }

    private fun segmentClauses(text: String): List<String> {
        return text.split(Regex("""[,;\n]+|\s+and\s+|\s+also\s+""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun extractDate(text: String): String? {
        val today = getTodayDateString()
        val lower = text.lowercase()
        if (lower.contains("today")) return today
        if (lower.contains("yesterday")) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }

        val dateMatch = Regex("""\b(\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b""").find(text)
        if (dateMatch != null) {
            return dateMatch.value
        }
        return null
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
