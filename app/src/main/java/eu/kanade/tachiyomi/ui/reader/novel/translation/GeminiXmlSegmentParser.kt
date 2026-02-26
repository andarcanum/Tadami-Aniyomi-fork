package eu.kanade.tachiyomi.ui.reader.novel.translation

internal object GeminiXmlSegmentParser {

    private val xmlCodeFenceStartRegex = Regex("(?i)^\\s*```[a-z]*\\s*")
    private val xmlCodeFenceEndRegex = Regex("\\s*```\\s*$")
    private val xmlTagRegex = Regex("<([a-z]+)\\s+i=['\"](\\d+)['\"]>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)

    fun parse(
        rawResponse: String,
        expectedCount: Int,
    ): List<String?> {
        if (expectedCount <= 0) return emptyList()
        val sanitized = rawResponse
            .replace(xmlCodeFenceStartRegex, "")
            .replace(xmlCodeFenceEndRegex, "")
            .trim()

        val out = MutableList<String?>(expectedCount) { null }
        xmlTagRegex.findAll(sanitized).forEach { match ->
            val index = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (index !in 0 until expectedCount) return@forEach
            out[index] = match.groupValues[3].trim()
        }
        return out
    }
}
