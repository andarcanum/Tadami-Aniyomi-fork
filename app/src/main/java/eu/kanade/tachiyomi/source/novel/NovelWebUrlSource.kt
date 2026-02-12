package eu.kanade.tachiyomi.source.novel

interface NovelWebUrlSource {
    suspend fun getNovelWebUrl(novelPath: String): String?

    suspend fun getChapterWebUrl(chapterPath: String, novelPath: String? = null): String?
}
