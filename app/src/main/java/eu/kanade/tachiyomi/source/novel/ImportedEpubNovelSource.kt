package eu.kanade.tachiyomi.source.novel

import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import uy.kohesive.injekt.injectLazy

internal class ImportedEpubNovelSource : NovelSource {

    private val novelRepository: NovelRepository by injectLazy()
    private val chapterRepository: NovelChapterRepository by injectLazy()

    override val id: Long = IMPORTED_EPUB_NOVEL_SOURCE_ID
    override val name: String = IMPORTED_EPUB_NOVEL_SOURCE_NAME

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        // Return the novel as-is since it's already stored locally
        return novel
    }

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        val chapters = chapterRepository.getChapterByNovelId(novel.url.toLong())
        return chapters.map { chapter ->
            SNovelChapter.create().apply {
                url = chapter.id.toString()
                name = chapter.name
                chapter_number = chapter.chapterNumber
                date_upload = chapter.dateUpload
            }
        }
    }

    override suspend fun getChapterText(chapter: SNovelChapter): String {
        // TODO: Read from stored HTML file
        return "<html><body>Chapter content</body></html>"
    }
}