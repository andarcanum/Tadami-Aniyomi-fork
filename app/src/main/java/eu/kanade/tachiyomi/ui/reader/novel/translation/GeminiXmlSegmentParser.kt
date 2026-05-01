package eu.kanade.tachiyomi.ui.reader.novel.translation

internal object GeminiXmlSegmentParser {

    private val xmlCodeFenceStartRegex = Regex("(?i)^\\s*```[a-z0-9_-]*\\s*")
    private val xmlCodeFenceEndRegex = Regex("\\s*```\\s*$")
    private val xmlTagRegex = Regex(
        """<\s*([a-z][\w:-]*)\b(?=[^>]*\bi\s*=\s*['\"]?(\d+)['\"]?)[^>]*>([\s\S]*?)</\s*\1\s*>""",
        RegexOption.IGNORE_CASE,
    )
    private val xmlSegmentStartRegex = Regex(
        """<\s*([a-z][\w:-]*)\b(?=[^>]*\bi\s*=\s*['\"]?(\d+)['\"]?)[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val paragraphSplitRegex = Regex("""(?:\r?\n\s*){2,}""")
    private val repeatedSpacesRegex = Regex("[ \\t]{2,}")
    private val spacesAroundNewlineRegex = Regex("[ \\t]*\\n[ \\t]*")

    fun parse(
        rawResponse: String,
        expectedCount: Int,
    ): List<String?> {
        if (expectedCount <= 0) return emptyList()
        val sanitized = rawResponse.sanitizeXmlResponseForParsing()

        val out = MutableList<String?>(expectedCount) { null }
        xmlTagRegex.findAll(sanitized).forEach { match ->
            val index = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (index !in 0 until expectedCount) return@forEach
            out[index] = match.groupValues[3].trim()
        }

        recoverStartTagDelimitedSegments(
            sanitized = sanitized,
            out = out,
        )
        return out
    }

    fun parsePlaintext(
        rawResponse: String,
        expectedCount: Int,
    ): List<String?> {
        if (expectedCount <= 0) return emptyList()

        val normalized = rawResponse
            .replace("\r\n", "\n")
            .trim()
        if (normalized.isBlank()) {
            return List(expectedCount) { null }
        }

        val byParagraphs = normalized
            .split(paragraphSplitRegex)
            .map { it.removeEmojiAndNormalizeSpacing() }
            .filter { it.isNotBlank() }
        val byLines = normalized
            .lineSequence()
            .map { it.removeEmojiAndNormalizeSpacing() }
            .filter { it.isNotBlank() }
            .toList()

        val source = if (byLines.size > byParagraphs.size) byLines else byParagraphs
        val normalizedSource = if (source.size > expectedCount && expectedCount > 0) {
            val head = source.take(expectedCount - 1)
            val tail = source.drop(expectedCount - 1).joinToString("\n\n")
            head + tail
        } else {
            source
        }
        val out = MutableList<String?>(expectedCount) { null }
        normalizedSource.take(expectedCount).forEachIndexed { index, text ->
            out[index] = text
        }
        return out
    }

    private fun String.sanitizeXmlResponseForParsing(): String {
        val trimmed = replace(xmlCodeFenceStartRegex, "")
            .replace(xmlCodeFenceEndRegex, "")
            .trim()
        if (!trimmed.contains("&lt;", ignoreCase = true) || !trimmed.contains("&gt;", ignoreCase = true)) {
            return trimmed
        }
        return trimmed
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#34;", "\"")
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&#39;", "'")
            .trim()
    }

    private fun recoverStartTagDelimitedSegments(
        sanitized: String,
        out: MutableList<String?>,
    ) {
        val starts = xmlSegmentStartRegex.findAll(sanitized).toList()
        if (starts.isEmpty()) return

        starts.forEachIndexed { startPosition, match ->
            val index = match.groupValues[2].toIntOrNull() ?: return@forEachIndexed
            if (index !in out.indices) return@forEachIndexed
            if (!out[index].isNullOrBlank()) return@forEachIndexed

            val tagName = match.groupValues[1]
            val contentStart = match.range.last + 1
            val nextStart = starts.getOrNull(startPosition + 1)?.range?.first ?: sanitized.length
            val closeRegex = Regex("""</\s*${Regex.escape(tagName)}\s*>""", RegexOption.IGNORE_CASE)
            val closeBeforeNext = closeRegex.find(sanitized, contentStart)
                ?.takeIf { it.range.first <= nextStart }
                ?.range
                ?.first
            val contentEnd = closeBeforeNext ?: nextStart
            if (contentEnd <= contentStart) return@forEachIndexed

            val recovered = sanitized.substring(contentStart, contentEnd)
                .replace(Regex("""(?i)</?\s*s\s*>\s*$"""), "")
                .trim()
            if (recovered.isNotBlank()) {
                out[index] = recovered
            }
        }
    }

    private fun String.removeEmojiAndNormalizeSpacing(): String {
        if (isBlank()) return trim()

        val sb = StringBuilder(length)
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            if (!isEmojiCodePoint(codePoint)) {
                sb.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }

        return sb.toString()
            .replace(repeatedSpacesRegex, " ")
            .replace(spacesAroundNewlineRegex, "\n")
            .trim()
    }

    private fun isEmojiCodePoint(codePoint: Int): Boolean {
        return when {
            // Emoticons
            codePoint in 0x1F600..0x1F64F -> true
            // Misc Symbols and Pictographs
            codePoint in 0x1F300..0x1F5FF -> true
            // Transport and Map
            codePoint in 0x1F680..0x1F6FF -> true
            // Supplemental Symbols and Pictographs
            codePoint in 0x1F900..0x1F9FF -> true
            // Symbols and Pictographs Extended-A
            codePoint in 0x1FA70..0x1FAFF -> true
            // Misc symbols often used as emoji
            codePoint in 0x2600..0x26FF -> true
            // Dingbats
            codePoint in 0x2700..0x27BF -> true
            // Regional indicator flags
            codePoint in 0x1F1E6..0x1F1FF -> true
            // Keycap combining mark
            codePoint == 0x20E3 -> true
            // Variation selectors
            codePoint in 0xFE00..0xFE0F -> true
            // Emoji skin tones
            codePoint in 0x1F3FB..0x1F3FF -> true
            // Zero width joiner
            codePoint == 0x200D -> true
            // Tags used in some emoji sequences
            codePoint in 0xE0020..0xE007F -> true
            else -> false
        }
    }
}
