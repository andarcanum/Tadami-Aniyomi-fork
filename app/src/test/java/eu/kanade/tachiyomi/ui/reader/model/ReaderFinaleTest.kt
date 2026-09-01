package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.domain.entries.shouldRecordAnimeCompletion
import eu.kanade.domain.entries.shouldRecordNovelCompletion
import eu.kanade.tachiyomi.data.database.models.anime.EpisodeImpl
import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class ReaderFinaleTest {

    private val dayMillis = 86_400_000L

    private fun chapter(read: Boolean) = ChapterImpl().apply {
        id = 1L
        this.read = read
    }

    private fun manga(
        status: Long = SManga.COMPLETED.toLong(),
        customStatus: Long? = null,
        dateAdded: Long = 0L,
    ) = Manga.create().copy(id = 1L, status = status, customStatus = customStatus, dateAdded = dateAdded)

    private val readChapters = listOf(chapter(read = true))

    @Test
    fun `finale celebrates a freshly completed series`() {
        shouldCelebrateFinale(
            manga = manga(),
            chapters = readChapters,
            chapterWasUnread = true,
            cardEnabled = true,
            alreadyShownForManga = false,
        ) shouldBe true
    }

    @Test
    fun `ongoing entry never celebrates even when the list is exhausted`() {
        shouldCelebrateFinale(
            manga = manga(status = SManga.ONGOING.toLong()),
            chapters = readChapters,
            chapterWasUnread = true,
            cardEnabled = true,
            alreadyShownForManga = false,
        ) shouldBe false
    }

    @Test
    fun `custom completed status is respected`() {
        shouldCelebrateFinale(
            manga = manga(status = SManga.ONGOING.toLong(), customStatus = SManga.COMPLETED.toLong()),
            chapters = readChapters,
            chapterWasUnread = true,
            cardEnabled = true,
            alreadyShownForManga = false,
        ) shouldBe true
    }

    @Test
    fun `re-reading an already-read chapter does not re-celebrate`() {
        shouldCelebrateFinale(
            manga = manga(),
            chapters = readChapters,
            chapterWasUnread = false,
            cardEnabled = true,
            alreadyShownForManga = false,
        ) shouldBe false
    }

    @Test
    fun `already shown guard prevents duplicates`() {
        shouldCelebrateFinale(
            manga = manga(),
            chapters = readChapters,
            chapterWasUnread = true,
            cardEnabled = true,
            alreadyShownForManga = true,
        ) shouldBe false
    }

    @Test
    fun `disabled setting hides the finale`() {
        shouldCelebrateFinale(
            manga = manga(),
            chapters = readChapters,
            chapterWasUnread = true,
            cardEnabled = false,
            alreadyShownForManga = false,
        ) shouldBe false
    }

    @Test
    fun `unread chapters in the list prevent the finale`() {
        shouldCelebrateFinale(
            manga = manga(),
            chapters = listOf(chapter(read = true), chapter(read = false)),
            chapterWasUnread = true,
            cardEnabled = true,
            alreadyShownForManga = false,
        ) shouldBe false
    }

    @Test
    fun `empty chapter list never celebrates`() {
        shouldCelebrateFinale(
            manga = manga(),
            chapters = emptyList(),
            chapterWasUnread = true,
            cardEnabled = true,
            alreadyShownForManga = false,
        ) shouldBe false
    }

    @Test
    fun `days on shelf is null outside the library`() {
        daysOnShelf(dateAdded = 0L, nowMs = 10L * dayMillis) shouldBe null
    }

    @Test
    fun `days on shelf hides same-day additions`() {
        val now = 10L * dayMillis
        daysOnShelf(dateAdded = now - 3_600_000L, nowMs = now) shouldBe null
    }

    @Test
    fun `days on shelf counts whole days`() {
        val now = 10L * dayMillis
        daysOnShelf(dateAdded = now - 5 * dayMillis, nowMs = now) shouldBe 5L
    }

    @Test
    fun `completion date is recorded when the final chapter is end-read`() {
        shouldRecordCompletion(
            manga = manga(),
            chapters = readChapters,
            finishedChapterIsLast = true,
        ) shouldBe true
    }

    @Test
    fun `completion date refreshes when the finale is end-read again`() {
        shouldRecordCompletion(
            manga = manga().copy(completedAt = 1L),
            chapters = readChapters,
            finishedChapterIsLast = true,
        ) shouldBe true
    }

    @Test
    fun `mid-chapter end-read does not touch the date`() {
        shouldRecordCompletion(
            manga = manga(),
            chapters = readChapters,
            finishedChapterIsLast = false,
        ) shouldBe false
    }

    @Test
    fun `ongoing completion is not recorded`() {
        shouldRecordCompletion(
            manga = manga(status = SManga.ONGOING.toLong()),
            chapters = readChapters,
            finishedChapterIsLast = true,
        ) shouldBe false
    }

    private fun episode(seen: Boolean) = EpisodeImpl().apply {
        id = 1L
        this.seen = seen
    }

    private fun anime(status: Long = SManga.COMPLETED.toLong()) =
        Anime.create().copy(id = 1L, status = status)

    private fun novel(status: Long = SManga.COMPLETED.toLong()) =
        Novel.create().copy(id = 1L, status = status)

    private fun novelChapter(read: Boolean) = NovelChapter.create().copy(id = 1L, read = read)

    @Test
    fun `anime completion date is recorded on final episode end-read`() {
        shouldRecordAnimeCompletion(
            anime = anime(),
            episodes = listOf(episode(seen = true)),
            finishedEpisodeIsLast = true,
        ) shouldBe true
    }

    @Test
    fun `anime completion date refreshes on rewatch of the finale`() {
        shouldRecordAnimeCompletion(
            anime = anime().copy(completedAt = 1L),
            episodes = listOf(episode(seen = true)),
            finishedEpisodeIsLast = true,
        ) shouldBe true
    }

    @Test
    fun `ongoing anime is not recorded`() {
        shouldRecordAnimeCompletion(
            anime = anime(status = SManga.ONGOING.toLong()),
            episodes = listOf(episode(seen = true)),
            finishedEpisodeIsLast = true,
        ) shouldBe false
    }

    @Test
    fun `novel completion date is recorded on final chapter end-read`() {
        shouldRecordNovelCompletion(
            novel = novel(),
            chapters = listOf(novelChapter(read = true)),
            finishedChapterIsLast = true,
        ) shouldBe true
    }

    @Test
    fun `novel completion date refreshes on re-read of the finale`() {
        shouldRecordNovelCompletion(
            novel = novel().copy(completedAt = 1L),
            chapters = listOf(novelChapter(read = true)),
            finishedChapterIsLast = true,
        ) shouldBe true
    }

    @Test
    fun `ongoing novel is not recorded`() {
        shouldRecordNovelCompletion(
            novel = novel(status = SManga.ONGOING.toLong()),
            chapters = listOf(novelChapter(read = true)),
            finishedChapterIsLast = true,
        ) shouldBe false
    }

    @Test
    fun `novel with unread chapters is not recorded`() {
        shouldRecordNovelCompletion(
            novel = novel(),
            chapters = listOf(novelChapter(read = true), novelChapter(read = false)),
            finishedChapterIsLast = true,
        ) shouldBe false
    }
}
