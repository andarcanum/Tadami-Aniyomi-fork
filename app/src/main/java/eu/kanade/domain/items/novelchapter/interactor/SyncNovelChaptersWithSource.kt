package eu.kanade.domain.items.novelchapter.interactor

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
import java.lang.Long.max
import java.time.Instant
import java.time.ZonedDateTime
import java.util.TreeSet

class SyncNovelChaptersWithSource(
    private val novelChapterRepository: NovelChapterRepository,
    private val shouldUpdateDbNovelChapter: ShouldUpdateDbNovelChapter,
    private val updateNovel: UpdateNovel,
    private val libraryPreferences: LibraryPreferences,
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
                    .copy(novelId = novel.id, sourceOrder = i.toLong())
            }

        val dbChapters = novelChapterRepository.getChapterByNovelId(novel.id)

        val newChapters = mutableListOf<NovelChapter>()
        val updatedChapters = mutableListOf<NovelChapter>()
        val removedChapters = dbChapters.filterNot { dbChapter ->
            sourceChapters.any { sourceChapter ->
                dbChapter.url == sourceChapter.url
            }
        }

        // Used to not set upload date of older chapters
        // to a higher value than newer chapters
        var maxSeenUploadDate = 0L

        for (sourceChapter in sourceChapters) {
            var chapter = sourceChapter

            // Recognize chapter number for the chapter.
            val chapterNumber = ChapterRecognition.parseChapterNumber(
                novel.title,
                chapter.name,
                chapter.chapterNumber,
            )
            chapter = chapter.copy(chapterNumber = chapterNumber)

            val dbChapter = dbChapters.find { it.url == chapter.url }

            if (dbChapter == null) {
                val toAddChapter = if (chapter.dateUpload == 0L) {
                    val altDateUpload = if (maxSeenUploadDate == 0L) nowMillis else maxSeenUploadDate
                    chapter.copy(dateUpload = altDateUpload)
                } else {
                    maxSeenUploadDate = max(maxSeenUploadDate, sourceChapter.dateUpload)
                    chapter
                }
                newChapters.add(toAddChapter)
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
                    updatedChapters.add(toChangeChapter)
                }
            }
        }

        // Return if there's nothing to add, delete, or update to avoid unnecessary db transactions.
        if (newChapters.isEmpty() && removedChapters.isEmpty() && updatedChapters.isEmpty()) {
            if (manualFetch || novel.fetchInterval == 0 || novel.nextUpdate < fetchWindow.first) {
                updateNovel.await(
                    NovelUpdate(
                        id = novel.id,
                        nextUpdate = novel.nextUpdate,
                        fetchInterval = novel.fetchInterval,
                    ),
                )
            }
            return emptyList()
        }

        val changedOrDuplicateReadUrls = mutableSetOf<String>()

        val deletedChapterNumbers = TreeSet<Double>()
        val deletedReadChapterNumbers = TreeSet<Double>()
        val deletedBookmarkedChapterNumbers = TreeSet<Double>()

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

        val deletedChapterNumberDateFetchMap = removedChapters.sortedByDescending { it.dateFetch }
            .associate { it.chapterNumber to it.dateFetch }

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

            // Try to to use the fetch date of the original entry to not pollute 'Updates' tab
            deletedChapterNumberDateFetchMap[chapter.chapterNumber]?.let {
                chapter = chapter.copy(dateFetch = it)
            }

            changedOrDuplicateReadUrls.add(chapter.url)

            chapter
        }

        if (removedChapters.isNotEmpty()) {
            val toDeleteIds = removedChapters.map { it.id }
            novelChapterRepository.removeChaptersWithIds(toDeleteIds)
        }

        if (updatedToAdd.isNotEmpty()) {
            updatedToAdd = novelChapterRepository.addAllChapters(updatedToAdd)
        }

        if (updatedChapters.isNotEmpty()) {
            val chapterUpdates = updatedChapters.map { it.toNovelChapterUpdate() }
            novelChapterRepository.updateAllChapters(chapterUpdates)
        }

        // Set this novel as updated since chapters were changed
        // Note that last_update actually represents last time the chapter list changed at all
        updateNovel.await(
            NovelUpdate(
                id = novel.id,
                lastUpdate = Instant.now().toEpochMilli(),
            ),
        )

        return updatedToAdd.filterNot { it.url in changedOrDuplicateReadUrls }
    }
}
