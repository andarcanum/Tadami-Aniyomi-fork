package eu.kanade.presentation.entries.novel

import tachiyomi.domain.items.novelchapter.model.NovelChapter

fun novelChapterDateText(
    chapter: NovelChapter,
    parsedDateText: String?,
): String? {
    return when {
        !parsedDateText.isNullOrBlank() -> parsedDateText
        !chapter.dateUploadRaw.isNullOrBlank() -> chapter.dateUploadRaw
        else -> null
    }
}
