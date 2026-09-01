package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga

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
    fun `first witnessed completion is recorded`() {
        shouldRecordCompletion(
            manga = manga(),
            chapters = readChapters,
            chapterWasUnread = true,
        ) shouldBe true
    }

    @Test
    fun `existing completion date is never overwritten`() {
        shouldRecordCompletion(
            manga = manga().copy(completedAt = 1L),
            chapters = readChapters,
            chapterWasUnread = true,
        ) shouldBe false
    }

    @Test
    fun `ongoing completion is not recorded`() {
        shouldRecordCompletion(
            manga = manga(status = SManga.ONGOING.toLong()),
            chapters = readChapters,
            chapterWasUnread = true,
        ) shouldBe false
    }

    @Test
    fun `bulk-marked chapter does not record a date`() {
        shouldRecordCompletion(
            manga = manga(),
            chapters = readChapters,
            chapterWasUnread = false,
        ) shouldBe false
    }
}
