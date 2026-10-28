package eu.kanade.tachiyomi.data.backup.create.creators

import android.app.Application
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dataanime.Animehistory
import dataanime.Animes
import dataanime.Episodes
import dataanime.Reels_album_watched
import dataanime.Reels_albums
import dataanime.Reels_candidate
import dataanime.Reels_consumption
import dataanime.Reels_favorites
import dataanime.Reels_follows
import dataanime.Reels_hidden
import dataanime.Reels_watch_history
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.mi.data.AnimeDatabase
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AnimeBackupCreatorTest {

    private lateinit var database: AnimeDatabase
    private lateinit var creator: AnimeBackupCreator

    @Before
    fun setup() {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AnimeDatabase.Schema.create(driver)
        database = AnimeDatabase(
            driver = driver,
            animehistoryAdapter = Animehistory.Adapter(
                last_seenAdapter = DateColumnAdapter,
            ),
            episodesAdapter = Episodes.Adapter(memoAdapter = MemoColumnAdapter),
            animesAdapter = Animes.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                fetch_typeAdapter = FetchTypeColumnAdapter,
            ),
            reels_favoritesAdapter = Reels_favorites.Adapter(
                added_atAdapter = DateColumnAdapter,
            ),
            reels_followsAdapter = Reels_follows.Adapter(
                added_atAdapter = DateColumnAdapter,
            ),
            reels_watch_historyAdapter = Reels_watch_history.Adapter(
                watched_atAdapter = DateColumnAdapter,
            ),
            reels_hiddenAdapter = Reels_hidden.Adapter(
                hidden_atAdapter = DateColumnAdapter,
            ),
            reels_albumsAdapter = Reels_albums.Adapter(
                added_atAdapter = DateColumnAdapter,
            ),
            reels_album_watchedAdapter = Reels_album_watched.Adapter(
                marked_atAdapter = DateColumnAdapter,
            ),
            reels_consumptionAdapter = Reels_consumption.Adapter(
                watched_qualified_atAdapter = DateColumnAdapter,
                skipped_forward_atAdapter = DateColumnAdapter,
                last_action_atAdapter = DateColumnAdapter,
            ),
            reels_candidateAdapter = Reels_candidate.Adapter(
                discovered_atAdapter = DateColumnAdapter,
                last_validated_atAdapter = DateColumnAdapter,
                retry_afterAdapter = DateColumnAdapter,
            ),
        )
        creator = AnimeBackupCreator(FakeAnimeDatabaseHandler(database))
    }

    @Test
    fun `collects episodes, categories, tracks and history for library`() = runTest {
        val animeId1 = insertAnime(url = "https://x/1", favorite = true)
        val animeId2 = insertAnime(url = "https://x/2", favorite = true)
        val animeId3 = insertAnime(url = "https://x/3", favorite = false)
        val episodeId1 = insertEpisode(animeId = animeId1, url = "/e1", sourceOrder = 0)
        insertEpisode(animeId = animeId1, url = "/e2", sourceOrder = 1)
        insertEpisode(animeId = animeId3, url = "/e3", sourceOrder = 0)
        database.animehistoryQueries.upsert(episodeId = episodeId1, seenAt = Date(1700000000000))
        database.categoriesQueries.insert(name = "Watching", order = 0, flags = 0)
        database.animes_categoriesQueries.insert(animeId = animeId1, categoryId = 1)
        database.anime_syncQueries.insert(
            animeId = animeId1,
            syncId = 1L,
            remoteId = 42L,
            libraryId = null,
            title = "MAL",
            lastEpisodeSeen = 0.0,
            totalEpisodes = 0,
            status = 1,
            score = 0.0,
            remoteUrl = "",
            startDate = 0,
            finishDate = 0,
            private = false,
        )

        val backup = creator(
            listOf(animeDomain(animeId1, "https://x/1"), animeDomain(animeId2, "https://x/2")),
            BackupOptions(),
        )

        backup.size shouldBe 2
        val a1 = backup.first { it.url == "https://x/1" }
        a1.episodes.map { it.url } shouldBe listOf("/e1", "/e2")
        a1.categories shouldBe listOf(0L)
        a1.tracking.size shouldBe 1
        a1.history.first().url shouldBe "/e1"
        a1.history.first().lastRead shouldBe 1700000000000
        val a2 = backup.first { it.url == "https://x/2" }
        a2.episodes shouldBe emptyList()
        a2.categories shouldBe emptyList()
        a2.tracking shouldBe emptyList()
        a2.history shouldBe emptyList()
        backup.none { it.url == "https://x/3" } shouldBe true
    }

    @Test
    fun `option flags gate sections`() = runTest {
        val animeId1 = insertAnime(url = "https://x/1", favorite = true)
        insertEpisode(animeId = animeId1, url = "/e1", sourceOrder = 0)

        val backup = creator(
            listOf(animeDomain(animeId1, "https://x/1")),
            BackupOptions(chapters = false, categories = false, tracking = false, history = false),
        )

        val a1 = backup.single()
        a1.episodes shouldBe emptyList()
        a1.categories shouldBe emptyList()
        a1.tracking shouldBe emptyList()
        a1.history shouldBe emptyList()
    }

    private fun insertAnime(url: String, favorite: Boolean): Long {
        database.animesQueries.insert(
            source = 1L,
            url = url,
            artist = null,
            author = null,
            description = null,
            notes = "",
            genre = null,
            title = "Anime $url",
            status = 0L,
            thumbnailUrl = null,
            favorite = favorite,
            pinned = false,
            lastUpdate = null,
            nextUpdate = null,
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
            seasonNumber = -1.0,
            seasonSourceOrder = 0L,
            backgroundUrl = null,
            backgroundLastModified = 0L,
            completedAt = null,
        )
        return database.animesQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun insertEpisode(animeId: Long, url: String, sourceOrder: Long): Long {
        database.episodesQueries.insert(
            animeId = animeId,
            url = url,
            name = "Episode $sourceOrder",
            scanlator = null,
            seen = false,
            bookmark = false,
            lastSecondSeen = 0L,
            totalSeconds = 0L,
            episodeNumber = sourceOrder.toDouble(),
            sourceOrder = sourceOrder,
            dateFetch = 0L,
            dateUpload = 0L,
            version = 0L,
            summary = null,
            previewUrl = null,
            fillermark = false,
        )
        return database.episodesQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun animeDomain(id: Long, url: String) = Anime(
        id = id,
        source = 1L,
        favorite = true,
        pinned = false,
        lastUpdate = 0L,
        nextUpdate = 0L,
        fetchInterval = 0,
        dateAdded = 0L,
        viewerFlags = 0L,
        episodeFlags = 0L,
        coverLastModified = 0L,
        backgroundLastModified = 0L,
        url = url,
        title = "Anime $url",
        artist = null,
        author = null,
        description = null,
        genre = null,
        status = 0L,
        thumbnailUrl = null,
        backgroundUrl = null,
        updateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
        initialized = true,
        lastModifiedAt = 0L,
        favoriteModifiedAt = null,
        version = 0L,
        fetchType = FetchType.Episodes,
        parentId = null,
        seasonFlags = 0L,
        seasonNumber = -1.0,
        seasonSourceOrder = 0L,
    )
}

private class FakeAnimeDatabaseHandler(
    private val database: AnimeDatabase,
) : AnimeDatabaseHandler {
    override suspend fun <T> await(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> T,
    ): T = block(database)

    override suspend fun <T : Any> awaitList(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> Query<T>,
    ): List<T> = block(database).executeAsList()

    override suspend fun <T : Any> awaitOne(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> Query<T>,
    ): T = error("unused")

    override suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> ExecutableQuery<T>,
    ): T = error("unused")

    override suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> Query<T>,
    ): T? = error("unused")

    override suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> ExecutableQuery<T>,
    ): T? = error("unused")

    override fun <T : Any> subscribeToList(
        block: (AnimeDatabase) -> Query<T>,
    ) = error("unused")

    override fun <T : Any> subscribeToOne(
        block: (AnimeDatabase) -> Query<T>,
    ) = error("unused")

    override fun <T : Any> subscribeToOneOrNull(
        block: (AnimeDatabase) -> Query<T>,
    ) = error("unused")

    override fun <T : Any> subscribeToPagingSource(
        countQuery: (AnimeDatabase) -> Query<Long>,
        queryProvider: (AnimeDatabase, Long, Long) -> Query<T>,
    ) = error("unused")
}
