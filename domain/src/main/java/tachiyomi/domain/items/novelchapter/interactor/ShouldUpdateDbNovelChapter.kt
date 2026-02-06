package tachiyomi.domain.items.novelchapter.interactor

import tachiyomi.domain.items.novelchapter.model.NovelChapter

class ShouldUpdateDbNovelChapter {

    fun await(dbChapter: NovelChapter, sourceChapter: NovelChapter): Boolean {
        return dbChapter.scanlator != sourceChapter.scanlator ||
            dbChapter.name != sourceChapter.name ||
            dbChapter.dateUpload != sourceChapter.dateUpload ||
            dbChapter.chapterNumber != sourceChapter.chapterNumber ||
            dbChapter.sourceOrder != sourceChapter.sourceOrder
    }
}
