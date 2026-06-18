package tachiyomi.data.achievement

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.History
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.novel.data.NovelDatabase
import tachiyomi.data.Database as MangaDatabase

class StatsReadCountSqlTest {

    @Test
    fun `manga read count uses chapter read flags and not history rows`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MangaDatabase.Schema.create(driver)
        val db = MangaDatabase(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            ),
        )

        db.mangasQueries.insertManga(id = 1)
        repeat(5) { index ->
            db.chaptersQueries.insertChapter(id = index + 1L, mangaId = 1L, read = true)
        }
        db.historyQueries.upsert(chapterId = 1L, readAt = java.util.Date(1L), time_read = 100L)

        db.chaptersQueries.getTotalReadChapterCount().executeAsOne() shouldBe 5L
        db.historyQueries.getTotalChaptersRead().executeAsOne() shouldBe 1L
        driver.close()
    }

    @Test
    fun `novel read count uses novel chapter read flags and not history rows`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NovelDatabase.Schema.create(driver)
        val db = NovelDatabase(
            driver = driver,
            novel_historyAdapter = Novel_history.Adapter(last_readAdapter = DateColumnAdapter),
            novelsAdapter = Novels.Adapter(
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            ),
        )

        db.novelsQueries.insertNovel(id = 1)
        repeat(4) { index ->
            db.novel_chaptersQueries.insertNovelChapter(id = index + 1L, novelId = 1L, read = true)
        }
        db.novel_historyQueries.upsert(chapterId = 1L, readAt = java.util.Date(1L), time_read = 100L)

        db.novel_chaptersQueries.getTotalReadChapterCount().executeAsOne() shouldBe 4L
        db.novel_historyQueries.getTotalChaptersRead().executeAsOne() shouldBe 1L
        driver.close()
    }

    @Test
    fun `anime seen count uses episode seen flags and not history rows`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AnimeDatabase.Schema.create(driver)
        val db = AnimeDatabase(
            driver = driver,
            animehistoryAdapter = Animehistory.Adapter(last_seenAdapter = DateColumnAdapter),
            animesAdapter = Animes.Adapter(
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = tachiyomi.data.AnimeUpdateStrategyColumnAdapter,
                fetch_typeAdapter = tachiyomi.data.FetchTypeColumnAdapter,
            ),
        )

        db.animesQueries.insertAnime(id = 1)
        repeat(3) { index ->
            db.episodesQueries.insertEpisode(id = index + 1L, animeId = 1L, seen = true)
        }
        db.animehistoryQueries.upsert(episodeId = 1L, seenAt = java.util.Date(1L))

        db.episodesQueries.getTotalSeenEpisodeCount().executeAsOne() shouldBe 3L
        db.animehistoryQueries.getTotalEpisodesWatched().executeAsOne() shouldBe 1L
        driver.close()
    }

    private fun data.MangasQueries.insertManga(id: Long) {
        insert(
            source = 1L,
            url = "/manga/$id",
            artist = null,
            author = null,
            description = null,
            notes = "",
            genre = emptyList(),
            title = "Manga $id",
            status = 1L,
            thumbnailUrl = null,
            favorite = true,
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
        )
    }

    private fun data.ChaptersQueries.insertChapter(id: Long, mangaId: Long, read: Boolean) {
        insert(
            mangaId = mangaId,
            url = "/chapter/$id",
            name = "Chapter $id",
            scanlator = null,
            read = read,
            bookmark = false,
            lastPageRead = 0L,
            chapterNumber = id.toDouble(),
            sourceOrder = id,
            dateFetch = 0L,
            dateUpload = 0L,
            version = 0L,
        )
    }

    private fun datanovel.NovelsQueries.insertNovel(id: Long) {
        insert(
            source = 1L,
            url = "/novel/$id",
            author = null,
            description = null,
            notes = "",
            genre = emptyList(),
            title = "Novel $id",
            status = 1L,
            thumbnailUrl = null,
            favorite = true,
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
        )
    }

    private fun datanovel.Novel_chaptersQueries.insertNovelChapter(id: Long, novelId: Long, read: Boolean) {
        insert(
            novelId = novelId,
            url = "/novel-chapter/$id",
            name = "Chapter $id",
            scanlator = null,
            read = read,
            bookmark = false,
            lastPageRead = 0L,
            chapterNumber = id.toDouble(),
            sourceOrder = id,
            dateFetch = 0L,
            dateUpload = 0L,
            dateUploadRaw = null,
            version = 0L,
        )
    }

    private fun dataanime.AnimesQueries.insertAnime(id: Long) {
        insert(
            source = 1L,
            url = "/anime/$id",
            artist = null,
            author = null,
            description = null,
            notes = "",
            genre = emptyList(),
            title = "Anime $id",
            status = 1L,
            thumbnailUrl = null,
            favorite = true,
            pinned = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 0L,
            episodeFlags = 0L,
            coverLastModified = 0L,
            dateAdded = 0L,
            updateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0L,
            version = 0L,
            fetchType = FetchType.Episodes,
            parentId = null,
            seasonFlags = 0L,
            seasonNumber = 0.0,
            seasonSourceOrder = 0L,
            backgroundUrl = null,
            backgroundLastModified = 0L,
        )
    }

    private fun dataanime.EpisodesQueries.insertEpisode(id: Long, animeId: Long, seen: Boolean) {
        insert(
            animeId = animeId,
            url = "/episode/$id",
            name = "Episode $id",
            scanlator = null,
            seen = seen,
            bookmark = false,
            lastSecondSeen = if (seen) 1_000L else 0L,
            totalSeconds = 1_000L,
            episodeNumber = id.toDouble(),
            sourceOrder = id,
            dateFetch = 0L,
            dateUpload = 0L,
            version = 0L,
            summary = null,
            previewUrl = null,
            fillermark = false,
        )
    }
}
