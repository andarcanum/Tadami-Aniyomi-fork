package tachiyomi.data.achievement

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.mi.data.AnimeDatabase as SqlDelightAnimeDatabase

@Execution(ExecutionMode.CONCURRENT)
class DiversityAchievementCheckerTest {

    private lateinit var mangaHandler: MangaDatabaseHandler
    private lateinit var animeHandler: AnimeDatabaseHandler
    private lateinit var checker: DiversityAchievementChecker

    @BeforeEach
    fun setup() {
        mangaHandler = mockk()
        animeHandler = mockk()
        checker = DiversityAchievementChecker(mangaHandler, animeHandler)
    }

    @Test
    fun `genre diversity counts unique genres correctly`() = runTest {
        coEvery {
            mangaHandler.awaitList<Any>(any())
        } returns listOf("Action, Adventure", "Comedy", "Drama, Romance")

        coEvery {
            animeHandler.awaitList<Any>(any())
        } returns listOf("Action", "Fantasy, Adventure", "Sci-Fi")

        val count = checker.getGenreDiversity()
        count shouldBe 7 // Action, Adventure, Comedy, Drama, Romance, Fantasy, Sci-Fi
    }

    @Test
    fun `genre diversity handles empty lists`() = runTest {
        coEvery {
            mangaHandler.awaitList<Any>(any())
        } returns emptyList()

        coEvery {
            animeHandler.awaitList<Any>(any())
        } returns emptyList()

        val count = checker.getGenreDiversity()
        count shouldBe 0
    }

    @Test
    fun `genre diversity handles null entries`() = runTest {
        coEvery {
            mangaHandler.awaitList<Any>(any())
        } returns listOf("Action", null, "Comedy", null)

        coEvery {
            animeHandler.awaitList<Any>(any())
        } returns listOf(null, "Drama")

        val count = checker.getGenreDiversity()
        count shouldBe 3 // Action, Comedy, Drama
    }

    @Test
    fun `genre diversity counts manga genres only`() = runTest {
        coEvery {
            mangaHandler.awaitList<Any>(any())
        } returns listOf("Action, Adventure", "Comedy", "Romance")

        val count = checker.getMangaGenreDiversity()
        count shouldBe 4 // Action, Adventure, Comedy, Romance
    }

    @Test
    fun `genre diversity counts anime genres only`() = runTest {
        coEvery {
            animeHandler.awaitList<Any>(any())
        } returns listOf("Fantasy", "Sci-Fi, Action", "Horror")

        val count = checker.getAnimeGenreDiversity()
        count shouldBe 4 // Fantasy, Sci-Fi, Action, Horror
    }

    @Test
    fun `source diversity counts unique sources`() = runTest {
        coEvery {
            mangaHandler.awaitList<Long>(any())
        } returns listOf(1L, 2L, 3L, 1L, 2L)

        coEvery {
            animeHandler.awaitList<Long>(any())
        } returns listOf(2L, 3L, 4L, 5L)

        val count = checker.getSourceDiversity()
        count shouldBe 5 // 1, 2, 3, 4, 5
    }

    @Test
    fun `source diversity handles empty lists`() = runTest {
        coEvery {
            mangaHandler.awaitList<Long>(any())
        } returns emptyList()

        coEvery {
            animeHandler.awaitList<Long>(any())
        } returns emptyList()

        val count = checker.getSourceDiversity()
        count shouldBe 0
    }

    @Test
    fun `source diversity counts manga sources only`() = runTest {
        coEvery {
            mangaHandler.awaitList<Long>(any())
        } returns listOf(10L, 20L, 30L, 10L, 40L)

        val count = checker.getMangaSourceDiversity()
        count shouldBe 4 // 10, 20, 30, 40
    }

    @Test
    fun `source diversity counts anime sources only`() = runTest {
        coEvery {
            animeHandler.awaitList<Long>(any())
        } returns listOf(100L, 200L, 100L, 300L)

        val count = checker.getAnimeSourceDiversity()
        count shouldBe 3 // 100, 200, 300
    }

    @Test
    fun `genre parsing handles various formats`() = runTest {
        coEvery {
            mangaHandler.awaitList<Any>(any())
        } returns listOf(
            "Action, Comedy, Drama",
            "Action,Comedy,Romance", // No spaces
            "  Action  ,  Comedy  ", // Extra spaces
            "Action", // Single genre
        )

        val count = checker.getMangaGenreDiversity()
        count shouldBe 4 // Action, Comedy, Drama, Romance
    }

    @Test
    fun `cache is used for repeated calls`() = runTest {
        var callCount = 0

        coEvery {
            mangaHandler.awaitList<Any>(any())
        } answers {
            callCount++
            listOf("Action", "Comedy")
        }

        coEvery {
            animeHandler.awaitList<Any>(any())
        } returns emptyList()

        // First call
        checker.getGenreDiversity()
        callCount shouldBe 1

        // Second call should use cache
        checker.getGenreDiversity()
        callCount shouldBe 1
    }

    @Test
    fun `clearCache invalidates genre cache`() = runTest {
        var callCount = 0

        coEvery {
            mangaHandler.awaitList<Any>(any())
        } answers {
            callCount++
            listOf("Action")
        }

        coEvery {
            animeHandler.awaitList<Any>(any())
        } returns emptyList()

        checker.getGenreDiversity()
        callCount shouldBe 1

        checker.clearCache()

        checker.getGenreDiversity()
        callCount shouldBe 2
    }

    @Test
    fun `clearCache invalidates source cache`() = runTest {
        var callCount = 0

        coEvery {
            mangaHandler.awaitList<Long>(any())
        } answers {
            callCount++
            listOf(1L, 2L)
        }

        coEvery {
            animeHandler.awaitList<Long>(any())
        } returns emptyList()

        checker.getSourceDiversity()
        callCount shouldBe 1

        checker.clearCache()

        checker.getSourceDiversity()
        callCount shouldBe 2
    }

    @Test
    fun `genre diversity deduplicates across categories`() = runTest {
        coEvery {
            mangaHandler.awaitList<Any>(any())
        } returns listOf("Action, Adventure", "Comedy")

        coEvery {
            animeHandler.awaitList<Any>(any())
        } returns listOf("Action", "Adventure", "Drama")

        val count = checker.getGenreDiversity()
        count shouldBe 5 // Action, Adventure, Comedy, Drama (Action and Adventure deduplicated)
    }
}
