package tachiyomi.data.easteregg.crystal

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.domain.easteregg.crystal.CrystalMedia
import tachiyomi.domain.easteregg.crystal.CrystalTitleMatchReason
import tachiyomi.domain.easteregg.crystal.CrystalWork
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.novel.data.NovelDatabase
import tachiyomi.data.Database as MangaDatabase

@OptIn(ExperimentalCoroutinesApi::class)
class CrystalMemorialProgressSourceTest {

    @Test
    fun `nonfavorite persisted remote entries use raw titles and old saved flags`() = runTest {
        Fixture(UnconfinedTestDispatcher(testScheduler)).use { f ->
            for (media in CrystalMedia.entries) {
                f.parent(media, 1, SAKURASOU, favorite = false, customTitle = "Not the source title")
                f.items(media, 1, 10)
                f.parent(media, 2, "Unrelated", customTitle = SAKURASOU)
                f.items(media, 2, 10)
            }
            val result = f.source.snapshot()
            result.entries.size shouldBe 3
            result.eligibleEntries.size shouldBe 3
            result.entries.forEach {
                it.entryId shouldBe 1L
                it.isLocal shouldBe true
                it.distinctCompleted shouldBe 10
                it.titleMatch.reason shouldBe CrystalTitleMatchReason.MATCHED_ALIAS
            }
        }
    }

    @Test
    fun `unrelated titles never materialize children and matching reads are transactional`() = runTest {
        Fixture(UnconfinedTestDispatcher(testScheduler)).use { f ->
            for (media in CrystalMedia.entries) {
                f.parent(media, 1, "Unrelated", customTitle = SAKURASOU)
                f.items(media, 1, 100)
                f.track(media, 1, 2, 999999, "Unrelated")
                f.parent(media, 2, SAKURASOU)
                f.items(media, 2, 10)
                f.parent(media, 3, "Another unrelated title")
                f.items(media, 3, 100)
            }
            val result = f.source.snapshot()
            result.entries.size shouldBe 6
            result.eligibleEntries.size shouldBe 3
            result.entries.filter { it.entryId == 1L }.forEach {
                it.titleMatch.reason shouldBe CrystalTitleMatchReason.CONFLICTING_BINDING
                it.distinctCompleted shouldBe 0
            }
            for (media in CrystalMedia.entries) {
                f.itemReadTransactions(media) shouldBe listOf(true)
            }
        }
    }

    @Test
    fun `tracker counts alone never qualify and unknown trackers only contribute stored titles`() = runTest {
        Fixture(UnconfinedTestDispatcher(testScheduler)).use { f ->
            for (media in CrystalMedia.entries) {
                f.parent(media, 1, SAKURASOU)
                f.track(media, 1, 2, 13759, SAKURASOU)
                f.items(media, 1, 10, completed = false)
                f.parent(media, 2, "Untranslated source title")
                f.items(media, 2, 10)
                f.track(media, 2, 99, 999999, SAKURASOU)
            }
            val result = f.source.snapshot()
            result.entries.map { it.entryId } shouldBe listOf(2L, 2L, 2L)
            result.eligibleEntries.size shouldBe 3
            result.entries.forEach { it.titleMatch.reason shouldBe CrystalTitleMatchReason.MATCHED_ALIAS }
        }
    }

    @Test
    fun `complete trusted catalog whitelist includes manga and novel and rejects other IDs`() = runTest {
        Fixture(UnconfinedTestDispatcher(testScheduler)).use { f ->
            val identities = listOf(
                Triple(CrystalMedia.ANIME, 13759L, 13759L),
                Triple(CrystalMedia.MANGA, 58107L, 28107L),
                Triple(CrystalMedia.NOVEL, 60051L, 30051L),
                Triple(CrystalMedia.ANIME, 103874L, 38145L),
                Triple(CrystalMedia.MANGA, 97363L, 99391L),
            )
            identities.forEachIndexed { index, (media, anilist, mal) ->
                for ((sync, remote) in listOf(2L to anilist, 1L to mal)) {
                    val id = index * 10L + sync
                    f.parent(media, id, "No alias")
                    f.items(media, id, 10)
                    f.track(media, id, sync, remote, "No alias")
                }
            }
            val result = f.source.snapshot()
            result.eligibleEntries.size shouldBe 10
            result.entries.forEach { it.titleMatch.reason shouldBe CrystalTitleMatchReason.MATCHED_CATALOG }
            result.entries.count { it.titleMatch.adaptation?.work == CrystalWork.ROOMMATE_CAT } shouldBe 4

            for (media in CrystalMedia.entries) {
                f.parent(media, 100, SAKURASOU)
                f.items(media, 100, 10)
                f.track(media, 100, 2, 999999, SAKURASOU)
                f.parent(media, 101, SAKURASOU)
                f.items(media, 101, 10)
                f.track(media, 101, 1, 999999, SAKURASOU)
            }
            // Even an allowed ID for another medium must not override a title alias.
            f.parent(CrystalMedia.NOVEL, 102, SAKURASOU)
            f.items(CrystalMedia.NOVEL, 102, 10)
            f.track(CrystalMedia.NOVEL, 102, 2, 58107, SAKURASOU)
            val conflicts = f.source.snapshot().entries.filter { it.entryId >= 100 }
            conflicts.size shouldBe 7
            conflicts.forEach {
                it.qualifies shouldBe false
                it.titleMatch.reason shouldBe CrystalTitleMatchReason.CONFLICTING_BINDING
            }
        }
    }

    @Test
    fun `unsupported roommate novel and contradictory catalog identity fail closed`() = runTest {
        Fixture(UnconfinedTestDispatcher(testScheduler)).use { f ->
            f.parent(CrystalMedia.NOVEL, 1, ROOMMATE)
            f.items(CrystalMedia.NOVEL, 1, 10)
            f.parent(CrystalMedia.MANGA, 1, SAKURASOU)
            f.items(CrystalMedia.MANGA, 1, 10)
            f.track(CrystalMedia.MANGA, 1, 2, 97363, ROOMMATE)
            val result = f.source.snapshot()
            result.isEligible shouldBe false
            result.entries.map { it.titleMatch.reason }.toSet() shouldBe setOf(
                CrystalTitleMatchReason.CONFLICTING_BINDING,
                CrystalTitleMatchReason.UNSUPPORTED_MEDIA,
            )
        }
    }

    @Test
    fun `progress never pools separate entries and unread duplicates bridge logical copies`() = runTest {
        Fixture(UnconfinedTestDispatcher(testScheduler)).use { f ->
            for (media in CrystalMedia.entries) {
                val half = if (media == CrystalMedia.ANIME) 3 else 5
                for (id in 1L..2L) {
                    f.parent(media, id, SAKURASOU)
                    f.items(media, id, half)
                }
                f.parent(media, 3, SAKURASOU)
                f.item(media, 3, 3001, 1.0, "/a", true)
                f.item(media, 3, 3002, 2.0, "/b", true)
                f.item(media, 3, 3003, 1.0, "/b", false)
                f.item(media, 3, 3004, -1.0, "/unknown", true)
                f.item(media, 3, 3005, -1.0, "/unknown", true)
                f.item(media, 3, 3006, 999.0, "/far-number", false)
                f.item(media, 3, 3007, 1.5, "/fraction", true)
            }
            val result = f.source.snapshot()
            result.isEligible shouldBe false
            result.entries.filter { it.entryId == 3L }.forEach { it.distinctCompleted shouldBe 3 }
            result.entries.size shouldBe 9
        }
    }

    @Test
    fun `saved nine to ten and four to five invalidate identical candidate projections after commit`() = runTest {
        Fixture(UnconfinedTestDispatcher(testScheduler)).use { f ->
            for (media in CrystalMedia.entries) {
                val threshold = if (media == CrystalMedia.ANIME) 5 else 10
                f.parent(media, 1, SAKURASOU)
                f.items(media, 1, threshold - 1)
                f.item(media, 1, 999, threshold.toDouble(), "/last", false)
            }
            var signals = 0
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                f.source.changes().collect { signals++ }
            }
            runCurrent()
            (signals > 0) shouldBe true
            for (media in CrystalMedia.entries) {
                f.observesChildTable(media) shouldBe true
            }
            f.source.snapshot().isEligible shouldBe false
            for (media in CrystalMedia.entries) {
                val before = signals
                val projection = f.candidates(media)
                f.transaction(media) {
                    f.complete(media, 999)
                    runCurrent()
                    signals shouldBe before
                }
                f.candidates(media) shouldBe projection
                runCurrent()
                (signals > before) shouldBe true
                f.source.snapshot().entries.single { it.titleMatch.adaptation?.media == media }.qualifies shouldBe true
            }
            observer.cancel()
        }
    }

    @Test
    fun `local sync row changes invalidate all three media without any progress event`() = runTest {
        Fixture(UnconfinedTestDispatcher(testScheduler)).use { f ->
            for (media in CrystalMedia.entries) {
                f.parent(media, 1, "No alias")
                f.items(media, 1, 10)
            }
            var signals = 0
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                f.source.changes().collect { signals++ }
            }
            runCurrent()
            f.source.snapshot().isEligible shouldBe false
            for (media in CrystalMedia.entries) {
                val before = signals
                f.transaction(media) { f.track(media, 1, 99, 999999, SAKURASOU) }
                runCurrent()
                (signals > before) shouldBe true
                f.source.snapshot().entries.single { it.titleMatch.adaptation?.media == media }.qualifies shouldBe true
            }
            observer.cancel()
        }
    }

    private class Fixture(dispatcher: CoroutineDispatcher) : AutoCloseable {
        private val mangaDriver = RecordingDriver()
        private val animeDriver = RecordingDriver()
        private val novelDriver = RecordingDriver()
        private val manga: MangaDatabase
        private val anime: AnimeDatabase
        private val novel: NovelDatabase
        val source: CrystalProgressDataSource

        init {
            MangaDatabase.Schema.create(mangaDriver)
            AnimeDatabase.Schema.create(animeDriver)
            NovelDatabase.Schema.create(novelDriver)
            manga = MangaDatabase(
                mangaDriver,
                chaptersAdapter = data.Chapters.Adapter(MemoColumnAdapter),
                historyAdapter = data.History.Adapter(DateColumnAdapter),
                mangasAdapter = data.Mangas.Adapter(
                    StringListColumnAdapter,
                    MangaUpdateStrategyColumnAdapter,
                    StringListColumnAdapter,
                    MemoColumnAdapter,
                ),
            )
            anime = AnimeDatabase(
                animeDriver,
                episodesAdapter = dataanime.Episodes.Adapter(MemoColumnAdapter),
                animehistoryAdapter = dataanime.Animehistory.Adapter(DateColumnAdapter),
                animesAdapter = dataanime.Animes.Adapter(
                    StringListColumnAdapter,
                    AnimeUpdateStrategyColumnAdapter,
                    FetchTypeColumnAdapter,
                    StringListColumnAdapter,
                    MemoColumnAdapter,
                ),
                reels_favoritesAdapter = dataanime.Reels_favorites.Adapter(DateColumnAdapter),
                reels_followsAdapter = dataanime.Reels_follows.Adapter(DateColumnAdapter),
                reels_watch_historyAdapter = dataanime.Reels_watch_history.Adapter(DateColumnAdapter),
                reels_hiddenAdapter = dataanime.Reels_hidden.Adapter(DateColumnAdapter),
                reels_albumsAdapter = dataanime.Reels_albums.Adapter(DateColumnAdapter),
                reels_album_watchedAdapter = dataanime.Reels_album_watched.Adapter(DateColumnAdapter),
                reels_consumptionAdapter = dataanime.Reels_consumption.Adapter(
                    DateColumnAdapter,
                    DateColumnAdapter,
                    DateColumnAdapter,
                ),
                reels_candidateAdapter = dataanime.Reels_candidate.Adapter(
                    DateColumnAdapter,
                    DateColumnAdapter,
                    DateColumnAdapter,
                ),
            )
            novel = NovelDatabase(
                novelDriver,
                novel_chaptersAdapter = datanovel.Novel_chapters.Adapter(MemoColumnAdapter),
                novel_historyAdapter = datanovel.Novel_history.Adapter(DateColumnAdapter),
                novelsAdapter = datanovel.Novels.Adapter(
                    StringListColumnAdapter,
                    MangaUpdateStrategyColumnAdapter,
                    StringListColumnAdapter,
                    MemoColumnAdapter,
                ),
            )
            source = CrystalProgressDataSource(
                AndroidMangaDatabaseHandler(manga, mangaDriver, dispatcher, Dispatchers.IO),
                AndroidAnimeDatabaseHandler(anime, animeDriver, dispatcher, Dispatchers.IO),
                AndroidNovelDatabaseHandler(novel, novelDriver, dispatcher, Dispatchers.IO),
            ) {
                when (it) {
                    1L -> "myanimelist"
                    2L -> "anilist"
                    else -> "unknown"
                }
            }
        }

        fun parent(media: CrystalMedia, id: Long, title: String, favorite: Boolean = false, customTitle: String = "") {
            val flags = if (media == CrystalMedia.ANIME) "episode_flags" else "chapter_flags"
            val extraColumns = if (media == CrystalMedia.ANIME) {
                ", season_flags, season_number, season_source_order, background_last_modified"
            } else {
                ""
            }
            val extraValues = if (media == CrystalMedia.ANIME) ", 0, 0, 0, 0" else ""
            driver(media).execute(
                null,
                """
                INSERT INTO ${parentTable(media)}
                (_id, source, url, title, custom_title, status, favorite, initialized, viewer,
                 $flags, cover_last_modified, date_added$extraColumns)
                VALUES ($id, 12345, '/entry/$id', '$title', '$customTitle', 0, ${if (favorite) 1 else 0}, 1, 0,
                        0, 0, 0$extraValues)
                """.trimIndent(),
                0,
            )
        }

        fun items(media: CrystalMedia, parent: Long, count: Int, completed: Boolean = true) {
            repeat(count) { index ->
                val id = parent * 100 + index
                item(media, parent, id, index.toDouble(), "/child/$id", completed)
            }
        }

        fun item(media: CrystalMedia, parent: Long, id: Long, number: Double, url: String, completed: Boolean) {
            val anime = media == CrystalMedia.ANIME
            val flag = if (anime) "seen" else "read"
            val position = if (anime) "last_second_seen" else "last_page_read"
            val numberColumn = if (anime) "episode_number" else "chapter_number"
            val extras = if (anime) ", total_seconds, fillermark" else ""
            val values = if (anime) ", 0, 0" else ""
            driver(media).execute(
                null,
                """
                INSERT INTO ${childTable(media)}
                (_id, ${parentColumn(media)}, url, name, $flag, bookmark, $position, $numberColumn,
                 source_order, date_fetch, date_upload$extras)
                VALUES ($id, $parent, '$url', 'Saved item', ${if (completed) 1 else 0}, 0, 0, $number, 0, 0, 0$values)
                """.trimIndent(),
                0,
            )
        }

        fun track(media: CrystalMedia, parent: Long, sync: Long, remote: Long, title: String) {
            when (media) {
                CrystalMedia.MANGA -> manga.manga_syncQueries.insert(
                    parent, sync, remote, null, title, 9999.0, 9999, 0, 0.0, "", 0, 0, false,
                )
                CrystalMedia.ANIME -> anime.anime_syncQueries.insert(
                    parent, sync, remote, null, title, 9999.0, 9999, 0, 0.0, "", 0, 0, false,
                )
                CrystalMedia.NOVEL -> novel.novel_syncQueries.insert(
                    parent, sync, remote, null, title, 9999.0, 9999, 0, 0.0, "", 0, 0, false,
                )
            }
        }

        fun complete(media: CrystalMedia, id: Long) {
            when (media) {
                CrystalMedia.MANGA -> manga.chaptersQueries.update(
                    mangaId = null, url = null, name = null, scanlator = null, read = true, bookmark = null,
                    lastPageRead = null, chapterNumber = null, sourceOrder = null, dateFetch = null,
                    dateUpload = null, version = null, isSyncing = null, chapterId = id,
                )
                CrystalMedia.ANIME -> anime.episodesQueries.update(
                    animeId = null, url = null, name = null, scanlator = null, seen = true, bookmark = null,
                    lastSecondSeen = null, totalSeconds = null, episodeNumber = null, sourceOrder = null,
                    dateFetch = null, dateUpload = null, version = null, isSyncing = null, summary = null,
                    previewUrl = null, fillermark = null, episodeId = id,
                )
                CrystalMedia.NOVEL -> novel.novel_chaptersQueries.update(
                    novelId = null, url = null, name = null, scanlator = null, read = true, bookmark = null,
                    lastPageRead = null, chapterNumber = null, sourceOrder = null, dateFetch = null,
                    dateUpload = null, dateUploadRaw = null, version = null, isSyncing = null, chapterId = id,
                )
            }
        }

        fun candidates(media: CrystalMedia): List<Pair<Long, String>> = when (media) {
            CrystalMedia.MANGA -> manga.crystal_progressQueries.getCandidates { id, title ->
                id to title
            }.executeAsList()
            CrystalMedia.ANIME -> anime.crystal_progressQueries.getCandidates { id, title ->
                id to title
            }.executeAsList()
            CrystalMedia.NOVEL -> novel.crystal_progressQueries.getCandidates { id, title ->
                id to title
            }.executeAsList()
        }

        fun transaction(media: CrystalMedia, action: () -> Unit) {
            when (media) {
                CrystalMedia.MANGA -> manga.transaction { action() }
                CrystalMedia.ANIME -> anime.transaction { action() }
                CrystalMedia.NOVEL -> novel.transaction { action() }
            }
        }

        fun itemReadTransactions(media: CrystalMedia): List<Boolean> = driver(media).childReadTransactions

        fun observesChildTable(media: CrystalMedia): Boolean = childTable(media) in driver(media).observedTables

        private fun driver(media: CrystalMedia) = when (media) {
            CrystalMedia.MANGA -> mangaDriver
            CrystalMedia.ANIME -> animeDriver
            CrystalMedia.NOVEL -> novelDriver
        }

        private fun parentTable(media: CrystalMedia) = when (media) {
            CrystalMedia.MANGA -> "mangas"
            CrystalMedia.ANIME -> "animes"
            CrystalMedia.NOVEL -> "novels"
        }

        private fun parentColumn(media: CrystalMedia) = when (media) {
            CrystalMedia.MANGA -> "manga_id"
            CrystalMedia.ANIME -> "anime_id"
            CrystalMedia.NOVEL -> "novel_id"
        }

        private fun childTable(media: CrystalMedia) = when (media) {
            CrystalMedia.MANGA -> "chapters"
            CrystalMedia.ANIME -> "episodes"
            CrystalMedia.NOVEL -> "novel_chapters"
        }

        override fun close() {
            mangaDriver.close()
            animeDriver.close()
            novelDriver.close()
        }
    }

    /** Wraps real JDBC, recording only child SELECTs and SQLDelight listener dependencies. */
    private class RecordingDriver(
        private val delegate: JdbcSqliteDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY),
    ) : SqlDriver by delegate {
        val childReadTransactions = mutableListOf<Boolean>()
        val observedTables = mutableSetOf<String>()

        override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
            observedTables.addAll(queryKeys)
            delegate.addListener(*queryKeys, listener = listener)
        }

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            if (Regex("FROM\\s+(chapters|episodes|novel_chapters)\\s+WHERE").containsMatchIn(sql)) {
                childReadTransactions.add(currentTransaction() != null)
            }
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        }
    }

    private companion object {
        const val SAKURASOU = "Sakurasou no Pet na Kanojo"
        const val ROOMMATE = "Doukyonin wa Hiza, Tokidoki, Atama no Ue."
    }
}
