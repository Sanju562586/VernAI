package com.vernai.document.processing

import java.util.UUID

/**
 * An individual semantic chunk of a long document tailored to fit within
 * local on-device LLM context limits (typically 2048 tokens) without truncation.
 */
data class DocumentChunk(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val totalChunks: Int,
    val text: String,
    val characterStart: Int,
    val characterEnd: Int,
    val headingPreview: String
)

/**
 * Intelligent boundary-aware text chunker.
 * Divides extracted document text along paragraph and sentence boundaries,
 * maintaining character overlap to preserve semantic continuity across chunk borders.
 */
class DocumentChunker(
    val maxChunkChars: Int = 1200,
    val overlapChars: Int = 150
) {

    fun chunkDocument(text: String): List<DocumentChunk> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.length <= maxChunkChars) {
            return listOf(
                DocumentChunk(
                    index = 0,
                    totalChunks = 1,
                    text = trimmed,
                    characterStart = 0,
                    characterEnd = trimmed.length,
                    headingPreview = extractPreview(trimmed)
                )
            )
        }

        val rawChunks = mutableListOf<Pair<Int, Int>>() // Pair of (start, end)
        var cursor = 0

        while (cursor < trimmed.length) {
            val prospectiveEnd = (cursor + maxChunkChars).coerceAtMost(trimmed.length)
            if (prospectiveEnd >= trimmed.length) {
                rawChunks.add(cursor to trimmed.length)
                break
            }

            // Find a natural semantic breakpoint near prospectiveEnd
            val windowStart = (prospectiveEnd - 250).coerceAtLeast(cursor)
            val windowText = trimmed.substring(windowStart, prospectiveEnd)

            val paragraphBreak = windowText.lastIndexOf("\n\n")
            val sentenceBreak = windowText.lastIndexOfAny(charArrayOf('.', '!', '?', '|', '\n'))

            val splitPoint = when {
                paragraphBreak != -1 -> windowStart + paragraphBreak + 2
                sentenceBreak != -1 -> windowStart + sentenceBreak + 1
                else -> prospectiveEnd
            }

            rawChunks.add(cursor to splitPoint)
            val nextCursor = (splitPoint - overlapChars).coerceAtLeast(cursor + 1)
            cursor = nextCursor
        }

        val total = rawChunks.size
        return rawChunks.mapIndexed { idx, (start, end) ->
            val chunkText = trimmed.substring(start, end).trim()
            DocumentChunk(
                index = idx,
                totalChunks = total,
                text = chunkText,
                characterStart = start,
                characterEnd = end,
                headingPreview = extractPreview(chunkText)
            )
        }
    }

    private fun extractPreview(chunkText: String): String {
        val firstLine = chunkText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (firstLine.length > 50) firstLine.take(47) + "..." else firstLine.ifBlank { "భాగం (Section)" }
    }
}
