package com.vernai.ai.asr.evaluation.metrics

/**
 * Elementary edit operations for Levenshtein alignment between reference and hypothesis.
 */
sealed interface EditOp {
    data class Match(val refToken: String, val hypToken: String) : EditOp
    data class Substitution(val refToken: String, val hypToken: String) : EditOp
    data class Deletion(val refToken: String) : EditOp
    data class Insertion(val hypToken: String) : EditOp
}

/**
 * Alignment result containing edit counts and aligned operation trace.
 */
data class AlignmentResult(
    val substitutions: Int,
    val deletions: Int,
    val insertions: Int,
    val matches: Int,
    val referenceLength: Int,
    val hypothesisLength: Int,
    val operations: List<EditOp>
) {
    val errorCount: Int get() = substitutions + deletions + insertions
    val errorRate: Float
        get() = if (referenceLength > 0) {
            errorCount.toFloat() / referenceLength.toFloat()
        } else if (hypothesisLength > 0) {
            1.0f
        } else {
            0.0f
        }
}

/**
 * Wagner-Fischer Dynamic Programming Edit Distance Alignment Algorithm.
 */
object EditDistance {

    /**
     * Computes alignment between two token sequences (words or characters).
     */
    fun align(reference: List<String>, hypothesis: List<String>): AlignmentResult {
        val n = reference.size
        val m = hypothesis.size

        // dp[i][j] holds distance between reference[0..i-1] and hypothesis[0..j-1]
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            val refTok = reference[i - 1]
            for (j in 1..m) {
                val hypTok = hypothesis[j - 1]
                if (refTok == hypTok) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    val subCost = dp[i - 1][j - 1] + 1
                    val delCost = dp[i - 1][j] + 1
                    val insCost = dp[i][j - 1] + 1
                    dp[i][j] = minOf(subCost, delCost, insCost)
                }
            }
        }

        // Backtracking to recover exact sequence of edit operations
        val operations = mutableListOf<EditOp>()
        var i = n
        var j = m
        var substitutions = 0
        var deletions = 0
        var insertions = 0
        var matches = 0

        while (i > 0 || j > 0) {
            val current = dp[i][j]
            val refTok = if (i > 0) reference[i - 1] else ""
            val hypTok = if (j > 0) hypothesis[j - 1] else ""

            if (i > 0 && j > 0 && refTok == hypTok && current == dp[i - 1][j - 1]) {
                operations.add(EditOp.Match(refTok, hypTok))
                matches++
                i--
                j--
            } else if (i > 0 && j > 0 && current == dp[i - 1][j - 1] + 1) {
                operations.add(EditOp.Substitution(refTok, hypTok))
                substitutions++
                i--
                j--
            } else if (i > 0 && current == dp[i - 1][j] + 1) {
                operations.add(EditOp.Deletion(refTok))
                deletions++
                i--
            } else {
                operations.add(EditOp.Insertion(hypTok))
                insertions++
                j--
            }
        }

        operations.reverse()

        return AlignmentResult(
            substitutions = substitutions,
            deletions = deletions,
            insertions = insertions,
            matches = matches,
            referenceLength = n,
            hypothesisLength = m,
            operations = operations
        )
    }
}
