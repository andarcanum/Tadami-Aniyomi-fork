package tachiyomi.data.achievement

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.FeatureBasedAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.achievement.handler.checkers.TimeBasedAchievementChecker
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.data.achievement.repository.AchievementRepositoryImpl
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository

/**
 * Unit tests for EVENT achievement logic.
 *
 * Tests critical fixes:
 * 1. isEventMatch pattern matching for complete_1_manga
 * 2. isEventMatch pattern matching for complete_1_anime
 * 3. isEventMatch pattern matching for read_long_manga
 * 4. read_long_manga is filtered out in handleChapterRead (documented test)
 *
 * Note: Tests for checkLongMangaAchievement, getCompletedMangaCount, getCompletedAnimeCount
 * require full database setup which is complex. Pattern matching is the core logic
 * that was fixed and is tested here.
 */
@Execution(ExecutionMode.CONCURRENT)
class EventAchievementsTest : AchievementTestBase() {

    private lateinit var repository: AchievementRepository
    private lateinit var handler: AchievementHandler

    @BeforeEach
    override fun setup() {
        super.setup()

        repository = AchievementRepositoryImpl(database)

        // Create mock dependencies
        val mangaHandler: MangaDatabaseHandler = mockk(relaxed = true)
        val animeHandler: AnimeDatabaseHandler = mockk(relaxed = true)
        val novelHandler: NovelDatabaseHandler = mockk(relaxed = true)
        val diversityChecker: DiversityAchievementChecker = mockk(relaxed = true)
        val streakChecker: StreakAchievementChecker = mockk(relaxed = true) {
            coEvery { getCurrentStreak() } returns 0
        }
        val timeBasedChecker: TimeBasedAchievementChecker = mockk(relaxed = true)
        val featureBasedChecker: FeatureBasedAchievementChecker = mockk(relaxed = true)
        val mangaRepository: MangaRepository = mockk(relaxed = true)
        val animeRepository: AnimeRepository = mockk(relaxed = true)
        val activityDataRepo: tachiyomi.domain.achievement.repository.ActivityDataRepository = mockk(relaxed = true)

        handler = AchievementHandler(
            eventBus = mockk(relaxed = true),
            repository = repository,
            diversityChecker = diversityChecker,
            streakChecker = streakChecker,
            timeBasedChecker = timeBasedChecker,
            featureBasedChecker = featureBasedChecker,
            featureCollector = mockk(relaxed = true),
            pointsManager = mockk(relaxed = true),
            unlockableManager = mockk(relaxed = true),
            mangaHandler = mangaHandler,
            animeHandler = animeHandler,
            novelHandler = novelHandler,
            mangaRepository = mangaRepository,
            animeRepository = animeRepository,
            userProfileManager = mockk(relaxed = true),
            activityDataRepository = activityDataRepo,
        )
    }

    // ==================== isEventMatch Pattern Tests ====================

    @Test
    fun `isEventMatch matches first_chapter on ChapterRead`() = runTest {
        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)
        val result = invokeIsEventMatch("first_chapter", event)
        result shouldBe true
    }

    @Test
    fun `isEventMatch matches read_10_chapters on ChapterRead`() = runTest {
        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)
        val result = invokeIsEventMatch("read_10_chapters", event)
        result shouldBe true
    }

    @Test
    fun `isEventMatch does NOT match first_novel_chapter on ChapterRead`() = runTest {
        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)
        val result = invokeIsEventMatch("first_novel_chapter", event)
        result shouldBe false
    }

    @Test
    fun `isEventMatch does NOT match first_chapter on NovelChapterRead`() = runTest {
        val event = AchievementEvent.NovelChapterRead(novelId = 1L, chapterNumber = 1)
        val result = invokeIsEventMatch("first_chapter", event)
        result shouldBe false
    }

    @Test
    fun `isEventMatch matches first_episode on EpisodeWatched`() = runTest {
        val event = AchievementEvent.EpisodeWatched(animeId = 1L, episodeNumber = 1)
        val result = invokeIsEventMatch("first_episode", event)
        result shouldBe true
    }

    @Test
    fun `isEventMatch matches watch_10_episodes on EpisodeWatched`() = runTest {
        val event = AchievementEvent.EpisodeWatched(animeId = 1L, episodeNumber = 1)
        val result = invokeIsEventMatch("watch_10_episodes", event)
        result shouldBe true
    }

    // ==================== CRITICAL FIX: complete_1_manga ====================

    @Test
    fun `isEventMatch matches complete_1_manga on MangaCompleted`() = runTest {
        val event = AchievementEvent.MangaCompleted(mangaId = 1L)
        val result = invokeIsEventMatch("complete_1_manga", event)
        result shouldBe true
    }

    @Test
    fun `isEventMatch matches complete_10_manga on MangaCompleted`() = runTest {
        val event = AchievementEvent.MangaCompleted(mangaId = 1L)
        val result = invokeIsEventMatch("complete_10_manga", event)
        result shouldBe true
    }

    @Test
    fun `isEventMatch matches complete_50_manga on MangaCompleted`() = runTest {
        val event = AchievementEvent.MangaCompleted(mangaId = 1L)
        val result = invokeIsEventMatch("complete_50_manga", event)
        result shouldBe true
    }

    @Test
    fun `isEventMatch does NOT match complete_1_anime on MangaCompleted`() = runTest {
        val event = AchievementEvent.MangaCompleted(mangaId = 1L)
        val result = invokeIsEventMatch("complete_1_anime", event)
        result shouldBe false
    }

    // ==================== CRITICAL FIX: complete_1_anime ====================

    @Test
    fun `isEventMatch matches complete_1_anime on AnimeCompleted`() = runTest {
        val event = AchievementEvent.AnimeCompleted(animeId = 1L)
        val result = invokeIsEventMatch("complete_1_anime", event)
        result shouldBe true
    }

    @Test
    fun `isEventMatch matches complete_1_novel on NovelCompleted`() = runTest {
        val event = AchievementEvent.NovelCompleted(novelId = 1L)
        val result = invokeIsEventMatch("complete_1_novel", event)
        result shouldBe true
    }

    @Test
    fun `isEventMatch matches complete_10_anime on AnimeCompleted`() = runTest {
        val event = AchievementEvent.AnimeCompleted(animeId = 1L)
        val result = invokeIsEventMatch("complete_10_anime", event)
        result shouldBe true
    }

    @Test
    fun `isEventMatch does NOT match complete_1_manga on AnimeCompleted`() = runTest {
        val event = AchievementEvent.AnimeCompleted(animeId = 1L)
        val result = invokeIsEventMatch("complete_1_manga", event)
        result shouldBe false
    }

    // ==================== CRITICAL FIX: read_long_manga ====================

    @Test
    fun `isEventMatch matches read_long_manga on MangaCompleted`() = runTest {
        val event = AchievementEvent.MangaCompleted(mangaId = 1L)
        val result = invokeIsEventMatch("read_long_manga", event)
        result shouldBe true
    }

    @Test
    fun `isEventMatch matches read_long_manga on ChapterRead via read pattern`() = runTest {
        // This documents that the pattern WOULD match, but it's filtered in handleChapterRead
        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)
        val result = invokeIsEventMatch("read_long_manga", event)
        result shouldBe true // Pattern matches via "read", but handler filters it out
    }

    // ==================== Edge Cases ====================

    @Test
    fun `isEventMatch does NOT match random_id on ChapterRead`() = runTest {
        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)
        val result = invokeIsEventMatch("random_achievement", event)
        result shouldBe false
    }

    @Test
    fun `isEventMatch does NOT match complete_X_manga on ChapterRead`() = runTest {
        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)
        val result = invokeIsEventMatch("complete_5_manga", event)
        result shouldBe false
    }

    @Test
    fun `isEventMatch does NOT match complete_X_anime on EpisodeWatched`() = runTest {
        val event = AchievementEvent.EpisodeWatched(animeId = 1L, episodeNumber = 1)
        val result = invokeIsEventMatch("complete_5_anime", event)
        result shouldBe false
    }

    // ==================== Helper Methods ====================

    /**
     * Helper to invoke private isEventMatch method via reflection
     */
    private fun invokeIsEventMatch(achievementId: String, event: AchievementEvent): Boolean {
        val method = handler.javaClass.getDeclaredMethod(
            "isEventMatch",
            String::class.java,
            AchievementEvent::class.java,
        )
        method.isAccessible = true
        return method.invoke(handler, achievementId, event) as Boolean
    }
}
