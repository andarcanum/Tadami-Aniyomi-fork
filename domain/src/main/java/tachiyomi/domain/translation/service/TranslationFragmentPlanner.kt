package tachiyomi.domain.translation.service

import tachiyomi.domain.translation.model.TranslationFragment
import java.nio.ByteBuffer
import java.security.MessageDigest

const val TRANSLATION_PLANNER_VERSION = 1

object TranslationFragmentPlanner {
    fun plan(segments: List<String>, maxFragmentChars: Int = 2_000): List<TranslationFragment> {
        require(maxFragmentChars in 32..16_384)
        val result = mutableListOf<TranslationFragment>()
        segments.forEachIndexed { sourceIndex, text ->
            require(text.isNotBlank()) { "Source block is empty" }
            val parts = split(text, maxFragmentChars)
            parts.forEachIndexed { partIndex, part ->
                result += TranslationFragment(
                    id = result.size,
                    sourceIndex = sourceIndex,
                    partIndex = partIndex,
                    partCount = parts.size,
                    sourceText = part,
                    separatorAfter = if (partIndex < parts.lastIndex) part.takeLastWhile(Char::isWhitespace) else "",
                )
            }
        }
        return result
    }

    fun batches(
        fragments: List<TranslationFragment>,
        now: Long,
        maxFragments: Int,
        maxRequestChars: Int,
    ): List<List<TranslationFragment>> {
        require(maxFragments > 0 && maxRequestChars > 0)
        val result = mutableListOf<List<TranslationFragment>>()
        var current = mutableListOf<TranslationFragment>()
        var chars = 0
        fun flush() {
            if (current.isNotEmpty()) result += current
            current = mutableListOf()
            chars = 0
        }
        fragments.asSequence().filter {
            !it.isComplete && it.nextAttemptAt <= now
        }.sortedBy { it.id }.forEach { fragment ->
            val granular =
                fragment.failure?.kind == tachiyomi.domain.translation.model.TranslationFailureKind.TRUNCATED ||
                    fragment.failure?.kind == tachiyomi.domain.translation.model.TranslationFailureKind.INVALID_RESPONSE
            if (granular) flush()
            if (current.size >= maxFragments ||
                (current.isNotEmpty() && chars + fragment.sourceText.length > maxRequestChars)
            ) {
                flush()
            }
            current += fragment
            chars += fragment.sourceText.length
            if (granular) flush()
        }
        flush()
        return result
    }

    /** Only whole, validated source blocks can be overlaid; partial long blocks remain original. */
    fun assemble(fragments: List<TranslationFragment>): Map<Int, String> =
        fragments.groupBy { it.sourceIndex }.mapNotNull { (index, parts) ->
            val ordered = parts.sortedBy { it.partIndex }
            val count = ordered.first().partCount
            if (ordered.size != count || ordered.map { it.partIndex } != (0 until count).toList() ||
                ordered.any { !it.isComplete || it.partCount != count }
            ) {
                null
            } else {
                index to ordered.joinToString("") { it.translatedText!!.trim() + it.separatorAfter }
            }
        }.toMap()

    fun progress(fragments: List<TranslationFragment>): Int {
        val total = fragments.sumOf { it.sourceText.length.toLong() }
        if (total <= 0) return 0
        val complete = fragments.filter { it.isComplete }.sumOf { it.sourceText.length.toLong() }
        // 100 belongs to publication, not merely receiving the last network reply.
        return ((complete * 100L) / total).toInt().coerceIn(0, 99)
    }

    fun sourceFingerprint(segments: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ByteBuffer.allocate(4).putInt(segments.size).array())
        segments.forEach { text ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sourceSegments(fragments: List<TranslationFragment>): List<String> =
        fragments.groupBy { it.sourceIndex }.toSortedMap().map { (_, parts) ->
            parts.sortedBy { it.partIndex }.joinToString("") { it.sourceText }
        }

    private fun split(text: String, limit: Int): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + limit, text.length)
            if (end < text.length) {
                // Never cut an UTF-16 surrogate pair in half.
                if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
                var boundary = end
                while (boundary > start + limit / 2 && !text[boundary - 1].isWhitespace()) boundary--
                if (boundary > start + limit / 2) end = boundary
            }
            parts += text.substring(start, end)
            start = end
        }
        return parts
    }
}
