package tachiyomi.source.local.entries.novel

import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder

internal val ARCHIVE_CHAPTER_EXTENSIONS = setOf(
    "txt",
    "text",
    "md",
    "markdown",
    "html",
    "htm",
    "xhtml",
    "fb2",
)

internal fun isArchiveChapterEntryName(name: String?): Boolean {
    val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
    return ext in ARCHIVE_CHAPTER_EXTENSIONS
}

internal fun selectArchiveChapterEntries(entryNames: List<String>): List<String> {
    return entryNames.asSequence()
        .filter { it.isNotBlank() && !it.endsWith("/") }
        .filter(::isArchiveChapterEntryName)
        .sortedWith { e1, e2 -> e1.compareToCaseInsensitiveNaturalOrder(e2) }
        .toList()
}

internal fun archiveEntryDisplayName(entryPath: String): String {
    val withoutExtension = entryPath.substringBeforeLast('.')
    return withoutExtension
        .replace('/', ' ')
        .replace('\\', ' ')
        .replace('_', ' ')
        .trim()
}

internal fun buildArchiveChapters(
    novelUrl: String,
    archiveRelativePath: String,
    entryNames: List<String>,
    numberOffset: Float,
): List<SNovelChapter> {
    return selectArchiveChapterEntries(entryNames).mapIndexed { index, entryPath ->
        SNovelChapter.create().apply {
            url = "$novelUrl/$archiveRelativePath#$entryPath"
            name = archiveEntryDisplayName(entryPath)
            chapter_number = numberOffset + index + 1f
        }
    }
}
