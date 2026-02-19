package eu.kanade.tachiyomi.extension.novel.runtime

internal class NovelPluginResultNormalizer {
    fun normalize(
        pluginId: String,
        chapters: List<ParsedPluginChapter>,
        policy: NovelChapterFallbackPolicy,
    ): List<ParsedPluginChapter> {
        if (chapters.isEmpty()) return chapters

        val orderedChapters = normalizeOrder(pluginId, chapters)
        val seenPaths = mutableSetOf<String>()
        var fallbackIndex = 1
        val output = ArrayList<ParsedPluginChapter>(orderedChapters.size)

        orderedChapters.forEach { chapter ->
            val normalizedPath = normalizePath(chapter.path, policy)
            if (policy.dropDuplicateChapterPaths && normalizedPath != null) {
                if (!seenPaths.add(normalizedPath)) return@forEach
            }

            val normalizedName = chapter.name
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: if (policy.fillMissingChapterNames && normalizedPath != null) {
                    "${policy.chapterNamePrefix} ${fallbackIndex++}"
                } else {
                    chapter.name
                }

            output += chapter.copy(
                name = normalizedName,
                path = normalizedPath ?: chapter.path,
            )
        }

        return output
    }

    private fun normalizeOrder(
        pluginId: String,
        chapters: List<ParsedPluginChapter>,
    ): List<ParsedPluginChapter> {
        if (!pluginId.equals("scribblehub", ignoreCase = true)) return chapters
        if (chapters.size < 2) return chapters

        val firstOrder = chapterOrder(chapters.first()) ?: return chapters
        val lastOrder = chapterOrder(chapters.last()) ?: return chapters
        return if (firstOrder > lastOrder) chapters.asReversed() else chapters
    }

    private fun chapterOrder(chapter: ParsedPluginChapter): Long? {
        chapter.chapterNumber?.let { number ->
            if (!number.isNaN()) return (number * 1_000_000L).toLong()
        }
        val path = chapter.path ?: return null
        val chapterId = Regex("/chapter/(\\d+)", RegexOption.IGNORE_CASE)
            .find(path)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        if (chapterId != null) return chapterId
        val readChapterId = Regex("/read/\\d+/(\\d+)", RegexOption.IGNORE_CASE)
            .find(path)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        if (readChapterId != null) return readChapterId
        return Regex("(\\d+)(?:/)?$", RegexOption.IGNORE_CASE)
            .find(path)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
    }

    private fun normalizePath(path: String?, policy: NovelChapterFallbackPolicy): String? {
        val value = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (!policy.stripFragmentFromChapterPath) return value
        val hashIndex = value.indexOf('#')
        return if (hashIndex >= 0) value.substring(0, hashIndex) else value
    }
}
