package eu.kanade.domain.items.novelchapter.model

import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapter

fun NovelChapter.toSNovelChapter(): SNovelChapter {
    return SNovelChapter.create().also {
        it.url = url
        it.name = name
        it.date_upload = dateUpload
        it.chapter_number = chapterNumber.toFloat()
        it.scanlator = scanlator
    }
}

fun NovelChapter.copyFromSNovelChapter(sChapter: SNovelChapter): NovelChapter {
    return this.copy(
        name = sChapter.name,
        url = sChapter.url,
        dateUpload = sChapter.date_upload,
        chapterNumber = sChapter.chapter_number.toDouble(),
        scanlator = sChapter.scanlator?.ifBlank { null }?.trim(),
    )
}
