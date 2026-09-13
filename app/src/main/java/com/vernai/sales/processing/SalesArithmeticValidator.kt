package com.vernai.sales.processing

import com.vernai.core.model.SalesItem
import com.vernai.core.model.SalesValidationStatus
import kotlin.math.abs

/**
 * Deterministic validator and arithmetic reconciler for sales ledger records.
 * Ensures strict calculation of quantity * unitPrice == totalPrice without guessing,
 * and formulates Telugu clarification prompts whenever data is ambiguous or contradictory.
 */
object SalesArithmeticValidator {

    data class ValidationResult(
        val item: SalesItem,
        val status: SalesValidationStatus,
        val prompt: String? = null,
        val expectedTotal: Double? = null,
        val discrepancyMessage: String? = null
    )

    /**
     * Validates and reconciles an individual [SalesItem].
     * Never guesses missing values; formulates specific conversational Telugu clarification questions.
     */
    fun validateAndReconcile(item: SalesItem): ValidationResult {
        val qty = item.quantity
        val unitPrice = item.unitPrice
        val total = item.totalPrice

        // 1. Ambiguity: Quantity is missing or zero/negative
        if (qty <= 0.0) {
            val prompt = "${item.originalTerm}కు పరిమాణం స్పష్టంగా లేదు. ఎన్ని కేజీలు, లీటర్లు లేదా ప్యాకెట్లు అమ్మారు?"
            return ValidationResult(
                item = item.copy(
                    validationStatus = SalesValidationStatus.CLARIFICATION_NEEDED,
                    clarificationPrompt = prompt
                ),
                status = SalesValidationStatus.CLARIFICATION_NEEDED,
                prompt = prompt
            )
        }

        // 2. Ambiguity: Both Unit Price and Total Price missing or zero/negative
        if (unitPrice <= 0.0 && total <= 0.0) {
            val prompt = "${item.originalTerm} ($qty ${item.unit}) ధర లేదా మొత్తం పేర్కొనబడలేదు. ఎంతకు అమ్మారు?"
            return ValidationResult(
                item = item.copy(
                    validationStatus = SalesValidationStatus.CLARIFICATION_NEEDED,
                    clarificationPrompt = prompt
                ),
                status = SalesValidationStatus.CLARIFICATION_NEEDED,
                prompt = prompt
            )
        }

        // 3. Deterministic calculation: Unit Price given, Total is missing
        if (unitPrice > 0.0 && total <= 0.0) {
            val computedTotal = roundTwoDecimals(qty * unitPrice)
            return ValidationResult(
                item = item.copy(
                    totalPrice = computedTotal,
                    validationStatus = SalesValidationStatus.VERIFIED,
                    clarificationPrompt = null
                ),
                status = SalesValidationStatus.VERIFIED,
                expectedTotal = computedTotal
            )
        }

        // 4. Deterministic calculation: Total given, Unit Price is missing
        if (total > 0.0 && unitPrice <= 0.0) {
            val computedUnitPrice = roundTwoDecimals(total / qty)
            return ValidationResult(
                item = item.copy(
                    unitPrice = computedUnitPrice,
                    validationStatus = SalesValidationStatus.VERIFIED,
                    clarificationPrompt = null
                ),
                status = SalesValidationStatus.VERIFIED,
                expectedTotal = total
            )
        }

        // 5. Cross-validation: Both Unit Price and Total Price were provided
        val expectedTotal = roundTwoDecimals(qty * unitPrice)
        val difference = abs(expectedTotal - total)

        if (difference > 0.05) {
            // Arithmetic discrepancy detected
            val discrepancyMsg = "లెక్క సరిపోలేదు: $qty ${item.unit} × ₹${formatPrice(unitPrice)} = ₹${formatPrice(expectedTotal)} కావాలి, కానీ మొత్తం ₹${formatPrice(total)} అని చెప్పారు."
            val prompt = "లెక్క తేడా: మొత్తం ₹${formatPrice(expectedTotal)} గా సరిచేయమంటారా? లేక ₹${formatPrice(total)} ఉంచమంటారా?"

            return ValidationResult(
                item = item.copy(
                    validationStatus = SalesValidationStatus.ARITHMETIC_MISMATCH,
                    clarificationPrompt = prompt
                ),
                status = SalesValidationStatus.ARITHMETIC_MISMATCH,
                prompt = prompt,
                expectedTotal = expectedTotal,
                discrepancyMessage = discrepancyMsg
            )
        }

        // 6. Verified: Arithmetic checks out perfectly
        return ValidationResult(
            item = item.copy(
                validationStatus = SalesValidationStatus.VERIFIED,
                clarificationPrompt = null
            ),
            status = SalesValidationStatus.VERIFIED,
            expectedTotal = total
        )
    }

    /**
     * Resolves an ambiguity or discrepancy with user-confirmed values.
     */
    fun resolveClarification(
        item: SalesItem,
        resolvedQuantity: Double? = null,
        resolvedUnitPrice: Double? = null,
        resolvedTotal: Double? = null,
        resolvedNotes: String? = null
    ): SalesItem {
        val newQty = resolvedQuantity ?: item.quantity
        var newUnitPrice = resolvedUnitPrice ?: item.unitPrice
        var newTotal = resolvedTotal ?: item.totalPrice

        // If user set quantity and unit price, automatically compute total
        if (resolvedUnitPrice != null && resolvedTotal == null && newQty > 0) {
            newTotal = roundTwoDecimals(newQty * newUnitPrice)
        } else if (resolvedTotal != null && resolvedUnitPrice == null && newQty > 0) {
            newUnitPrice = roundTwoDecimals(newTotal / newQty)
        }

        val updated = item.copy(
            quantity = newQty,
            unitPrice = newUnitPrice,
            totalPrice = newTotal,
            notes = resolvedNotes ?: item.notes,
            clarificationPrompt = null
        )

        return validateAndReconcile(updated).item
    }

    private fun roundTwoDecimals(value: Double): Double {
        return Math.round(value * 100.0) / 100.0
    }

    private fun formatPrice(price: Double): String {
        return String.format(java.util.Locale.US, "%.2f", price)
    }
}
