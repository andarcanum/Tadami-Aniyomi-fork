package eu.kanade.tachiyomi.source.novel

import android.app.Application
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.importer.ImportedEpubStorage
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

internal class ImportedEpubNovelSource(
    private val novelRepository: NovelRepository? = runCatching { Injekt.get<NovelRepository>() }.getOrNull(),
    private val chapterRepository: NovelChapterRepository? = runCatching {
        Injekt.get<NovelChapterRepository>()
    }.getOrNull(),
    private val storage: ImportedEpubStorage = ImportedEpubStorage(
        File(
            runCatching { Injekt.get<Application>().filesDir }.getOrNull()
                ?: File(System.getProperty("java.io.tmpdir") ?: "."),
            IMPORTED_EPUB_STORAGE_DIR,
        ),
    ),
) : NovelSource {

    override val id: Long = IMPORTED_EPUB_NOVEL_SOURCE_ID
    override val name: String = IMPORTED_EPUB_NOVEL_SOURCE_NAME

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        return novel
    }

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        val novelRepository = novelRepository ?: return emptyList()
        val chapterRepository = chapterRepository ?: return emptyList()
        val novelId = novel.url.toLongOrNull()
            ?: novelRepository.getNovelByUrlAndSourceId(novel.url, id)?.id
            ?: return emptyList()

        val chapters = chapterRepository.getChapterByNovelId(novelId)
            .sortedBy { it.sourceOrder }
        return chapters.map { chapter ->
            SNovelChapter.create().apply {
                url = chapter.id.toString()
                name = chapter.name
                chapter_number = chapter.chapterNumber.toFloat()
                date_upload = chapter.dateUpload
            }
        }
    }

    override suspend fun getChapterText(chapter: SNovelChapter): String {
        val chapterRepository = chapterRepository ?: return EMPTY_CHAPTER_HTML
        val chapterId = chapter.url.toLongOrNull() ?: return EMPTY_CHAPTER_HTML
        val localChapter = chapterRepository.getChapterById(chapterId) ?: return EMPTY_CHAPTER_HTML
        return storage.readChapterHtml(localChapter.novelId, localChapter.id)
    }

    private companion object {
        const val EMPTY_CHAPTER_HTML = "<html><body></body></html>"
    }
}
