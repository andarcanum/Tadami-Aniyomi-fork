package eu.kanade.domain.items.novelchapter.interactor

import eu.kanade.domain.entries.novel.interactor.GetNovelExcludedScanlators
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.items.novelchapter.model.copyFromSNovelChapter
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import tachiyomi.data.items.chapter.ChapterSanitizer
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.chapter.service.ChapterRecognition
import tachiyomi.domain.items.novelchapter.interactor.ShouldUpdateDbNovelChapter
import tachiyomi.domain.items.novelchapter.model.NoChaptersException
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.toNovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import java.time.Instant
import java.time.ZonedDateTime

class SyncNovelChaptersWithSource(
    private val novelChapterRepository: NovelChapterRepository,
    private val shouldUpdateDbNovelChapter: ShouldUpdateDbNovelChapter,
    private val updateNovel: UpdateNovel,
    private val libraryPreferences: LibraryPreferences,
    private val getNovelExcludedScanlators: GetNovelExcludedScanlators,
) {

    /**
     * Method to synchronize db chapters with source ones
     *
     * @param rawSourceChapters the chapters from the source.
     * @param novel the novel the chapters belong to.
     * @param source the source the novel belongs to.
     * @return Newly added chapters
     */
    suspend fun await(
        rawSourceChapters: List<SNovelChapter>,
        novel: Novel,
        source: NovelSource,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
        retainMissingChapters: Boolean = false,
        sourceOrderOffset: Long = 0L,
    ): List<NovelChapter> {
        if (rawSourceChapters.isEmpty()) {
            throw NoChaptersException()
        }

        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()

        val sourceChapters = rawSourceChapters
            .distinctBy { it.url }
            .mapIndexed { i, sChapter ->
                NovelChapter.create()
                    .copyFromSNovelChapter(sChapter)
                    .copy(name = with(ChapterSanitizer) { sChapter.name.sanitize(novel.title) })
                    .copy(novelId = novel.id, sourceOrder = sourceOrderOffset + i.toLong())
            }

        val dbChapters = novelChapterRepository.getChapterByNovelId(novel.id)

        val newChapters = mutableListOf<NovelChapter>()
        val updatedChapters = mutableListOf<NovelChapter>()

        val sourceUrls = sourceChapters.mapTo(HashSet(sourceChapters.size)) { it.url }
        val removedChapters = if (retainMissingChapters) {
            emptyList()
        } else {
            dbChapters.filterNot { it.url in sourceUrls }
        }

        val dbChaptersByUrl = dbChapters.associateBy { it.url }

        for (sourceChapter in sourceChapters) {
            var chapter = sourceChapter

            // Recognize chapter number for the chapter.
            val chapterNumber = ChapterRecognition.parseChapterNumber(
                novel.title,
                chapter.name,
                chapter.chapterNumber,
            )
            chapter = chapter.copy(chapterNumber = chapterNumber)

            val dbChapter = dbChaptersByUrl[chapter.url]

            if (dbChapter == null) {
                newChapters.add(chapter)
            } else {
                if (shouldUpdateDbNovelChapter.await(dbChapter, chapter)) {
                    var toChangeChapter = dbChapter.copy(
                        name = chapter.name,
                        chapterNumber = chapter.chapterNumber,
                        scanlator = chapter.scanlator,
                        sourceOrder = chapter.sourceOrder,
                    )
                    if (chapter.dateUpload != 0L) {
                        toChangeChapter = toChangeChapter.copy(dateUpload = chapter.dateUpload)
                    }
                    chapter.dateUploadRaw?.let {
                        toChangeChapter = toChangeChapter.copy(dateUploadRaw = it)
                    }
                    updatedChapters.add(toChangeChapter)
                }
            }
        }

        // Return if there's nothing to add, delete, or update to avoid unnecessary db transactions.
        if (newChapters.isEmpty() && removedChapters.isEmpty() && updatedChapters.isEmpty()) {
            if (manualFetch || novel.fetchInterval == 0 || novel.nextUpdate < fetchWindow.first) {
                updateNovel.awaitUpdateFetchInterval(novel, now, fetchWindow)
            }
            return emptyList()
        }

        val changedOrDuplicateReadUrls = mutableSetOf<String>()

        val deletedChapterNumbers = HashSet<Double>()
        val deletedReadChapterNumbers = HashSet<Double>()
        val deletedBookmarkedChapterNumbers = HashSet<Double>()

        val readChapterNumbers = dbChapters
            .asSequence()
            .filter { it.read && it.isRecognizedNumber }
            .map { it.chapterNumber }
            .toSet()

        removedChapters.forEach { chapter ->
            if (chapter.read) deletedReadChapterNumbers.add(chapter.chapterNumber)
            if (chapter.bookmark) deletedBookmarkedChapterNumbers.add(chapter.chapterNumber)
            deletedChapterNumbers.add(chapter.chapterNumber)
        }

        val removedChaptersSortedDesc = removedChapters.sortedByDescending { it.dateFetch }
        val deletedChapterNumberDateFetchMap = removedChaptersSortedDesc.associate { it.chapterNumber to it.dateFetch }
        val deletedChapterNumberLastPageReadMap = removedChaptersSortedDesc.associate {
            it.chapterNumber to
                it.lastPageRead
        }

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead().get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_NEW)

        // Date fetch is set in such a way that the upper ones will have bigger value than the lower ones
        // Sources MUST return the chapters from most to less recent, which is common.
        var itemCount = newChapters.size
        var updatedToAdd = newChapters.map { toAddItem ->
            var chapter = toAddItem.copy(dateFetch = nowMillis + itemCount--)

            if (chapter.chapterNumber in readChapterNumbers && markDuplicateAsRead) {
                changedOrDuplicateReadUrls.add(chapter.url)
                chapter = chapter.copy(read = true)
            }

            if (!chapter.isRecognizedNumber || chapter.chapterNumber !in deletedChapterNumbers) return@map chapter

            chapter = chapter.copy(
                read = chapter.chapterNumber in deletedReadChapterNumbers,
                bookmark = chapter.chapterNumber in deletedBookmarkedChapterNumbers,
            )
            deletedChapterNumberLastPageReadMap[chapter.chapterNumber]?.let { lastPageRead ->
                chapter = chapter.copy(lastPageRead = lastPageRead)
            }

            // Try to to use the fetch date of the original entry to not pollute 'Updates' tab
            deletedChapterNumberDateFetchMap[chapter.chapterNumber]?.let {
                chapter = chapter.copy(dateFetch = it)
            }

            changedOrDuplicateReadUrls.add(chapter.url)

            chapter
        }

        val toDeleteIds = removedChapters.map { it.id }
        val chapterUpdates = updatedChapters.map { it.toNovelChapterUpdate() }

        if (toDeleteIds.isNotEmpty() || updatedToAdd.isNotEmpty() || chapterUpdates.isNotEmpty()) {
            val syncResult = novelChapterRepository.syncChapters(
                toAdd = updatedToAdd,
                toUpdate = chapterUpdates,
                toDelete = toDeleteIds,
            )
            if (updatedToAdd.isNotEmpty()) {
                updatedToAdd = syncResult
            }
        }

        // Recompute the expected next release date from the fresh chapter rhythm.
        updateNovel.awaitUpdateFetchInterval(novel, now, fetchWindow)

        // Set this novel as updated since chapters were changed
        // Note that last_update actually represents last time the chapter list changed at all
        updateNovel.await(
            NovelUpdate(
                id = novel.id,
                lastUpdate = Instant.now().toEpochMilli(),
            ),
        )

        val excludedScanlators = getNovelExcludedScanlators.await(novel.id)

        return updatedToAdd.filterNot {
            it.url in changedOrDuplicateReadUrls || it.scanlator in excludedScanlators
        }
    }
}
