package tachiyomi.data.achievement

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.History
import data.Mangas
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.Database as MangaDatabase

/**
 * Pins getCompletedMangaCount to the statistics definition
 * (StatsCalculations.isCompletedByUserConsumption over libraryView) so achievements and the
 * Statistics screen report the SAME "completed manga" number.
 *
 * Red before the fix: favorite + source status COMPLETED alone used to count, so adding an
 * UNREAD completed series silently advanced the "complete N manga" achievement progress, and a
 * fully read ONGOING series never counted anywhere while stats showed a third number.
 */
class CompletedMangaCountSqlTest {

    @Test
    fun `unread completed series does not count`() {
        withDb { db ->
            db.insertManga(id = 1, status = 2L)
            db.insertChapters(mangaId = 1, total = 3, read = 0)
            db.mangasQueries.getCompletedMangaCount().executeAsOne() shouldBe 0L
        }
    }

    @Test
    fun `fully read completed series counts`() {
        withDb { db ->
            db.insertManga(id = 1, status = 2L)
            db.insertChapters(mangaId = 1, total = 3, read = 3)
            db.mangasQueries.getCompletedMangaCount().executeAsOne() shouldBe 1L
        }
    }

    @Test
    fun `partially read completed series does not count`() {
        withDb { db ->
            db.insertManga(id = 1, status = 2L)
            db.insertChapters(mangaId = 1, total = 3, read = 2)
            db.mangasQueries.getCompletedMangaCount().executeAsOne() shouldBe 0L
        }
    }

    @Test
    fun `custom completed status counts regardless of consumption`() {
        withDb { db ->
            db.insertManga(id = 1, status = 1L)
            db.mangasQueries.updateMetadata(
                customTitle = null,
                customArtist = null,
                customAuthor = null,
                customDescription = null,
                customGenre = null,
                customStatus = 2L,
                mangaId = 1L,
            )
            db.insertChapters(mangaId = 1, total = 3, read = 0)
            db.mangasQueries.getCompletedMangaCount().executeAsOne() shouldBe 1L
        }
    }

    @Test
    fun `terminal fallback statuses count when fully consumed`() {
        withDb { db ->
            // PUBLISHING_FINISHED(4), CANCELLED(5), ON_HIATUS(6) mirror the stats screen's
            // terminalFallbackStatuses.
            listOf(4L, 5L, 6L).forEachIndexed { index, status ->
                db.insertManga(id = index + 1L, status = status)
                db.insertChapters(mangaId = index + 1L, total = 2, read = 2)
            }
            db.mangasQueries.getCompletedMangaCount().executeAsOne() shouldBe 3L
        }
    }

    @Test
    fun `ongoing fully read series does not count`() {
        withDb { db ->
            db.insertManga(id = 1, status = 1L)
            db.insertChapters(mangaId = 1, total = 2, read = 2)
            db.mangasQueries.getCompletedMangaCount().executeAsOne() shouldBe 0L
        }
    }

    @Test
    fun `non-favorite and chapter-less entries do not count`() {
        withDb { db ->
            db.insertManga(id = 1, status = 2L, favorite = false)
            db.insertChapters(mangaId = 1, total = 2, read = 2)
            // favorite + completed but zero chapters: nothing consumed, totalCount = 0.
            db.insertManga(id = 2, status = 2L)
            db.mangasQueries.getCompletedMangaCount().executeAsOne() shouldBe 0L
        }
    }

    private fun withDb(block: (MangaDatabase) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MangaDatabase.Schema.create(driver)
        val db = MangaDatabase(
            driver = driver,
            chaptersAdapter = data.Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            ),
        )
        try {
            block(db)
        } finally {
            driver.close()
        }
    }

    private fun MangaDatabase.insertManga(id: Long, status: Long, favorite: Boolean = true) {
        mangasQueries.insert(
            source = 1L,
            url = "/manga/$id",
            artist = null,
            author = null,
            description = null,
            notes = "",
            genre = emptyList(),
            title = "Manga $id",
            status = status,
            thumbnailUrl = null,
            favorite = favorite,
            pinned = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            dateAdded = 0L,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0L,
            version = 0L,
            rating = -1.0,
            memo = kotlinx.serialization.json.JsonObject(emptyMap()),
            completedAt = null,
        )
    }

    private fun MangaDatabase.insertChapters(mangaId: Long, total: Int, read: Int) {
        repeat(total) { index ->
            chaptersQueries.insert(
                mangaId = mangaId,
                url = "/chapter/$mangaId/$index",
                name = "Chapter $index",
                scanlator = null,
                read = index < read,
                bookmark = false,
                lastPageRead = 0L,
                chapterNumber = index.toDouble(),
                sourceOrder = index.toLong(),
                dateFetch = 0L,
                dateUpload = 0L,
                version = 0L,
                memo = kotlinx.serialization.json.JsonObject(emptyMap()),
            )
        }
    }
}
