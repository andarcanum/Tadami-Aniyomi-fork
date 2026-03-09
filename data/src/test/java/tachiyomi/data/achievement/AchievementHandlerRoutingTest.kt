package tachiyomi.data.achievement

import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.handler.FeatureUsageCollector
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.FeatureBasedAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.achievement.handler.checkers.TimeBasedAchievementChecker
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.data.achievement.repository.AchievementRepositoryImpl
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.UserPoints
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository

@Execution(ExecutionMode.CONCURRENT)
class AchievementHandlerRoutingTest : AchievementTestBase() {

    private lateinit var eventBus: AchievementEventBus
    private lateinit var repository: AchievementRepository
    private lateinit var handler: AchievementHandler
    private lateinit var mangaHandler: MangaDatabaseHandler
    private lateinit var animeHandler: AnimeDatabaseHandler
    private lateinit var novelHandler: NovelDatabaseHandler
    private lateinit var timeBasedChecker: TimeBasedAchievementChecker
    private lateinit var featureBasedChecker: FeatureBasedAchievementChecker
    private lateinit var pointsManager: PointsManager

    @BeforeEach
    override fun setup() {
        super.setup()

        eventBus = AchievementEventBus()
        repository = AchievementRepositoryImpl(database)
        mangaHandler = mockk(relaxed = true)
        animeHandler = mockk(relaxed = true)
        novelHandler = mockk(relaxed = true)
        timeBasedChecker = mockk(relaxed = true)
        featureBasedChecker = mockk(relaxed = true)
        pointsManager = mockk(relaxed = true)

        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { novelHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { timeBasedChecker.check(any(), any()) } returns false
        coEvery { featureBasedChecker.check(any(), any()) } returns false

        handler = AchievementHandler(
            eventBus = eventBus,
            repository = repository,
            diversityChecker = mockk<DiversityAchievementChecker>(relaxed = true),
            streakChecker = mockk<StreakAchievementChecker>(relaxed = true) {
                coEvery { getCurrentStreak() } returns 0
            },
            timeBasedChecker = timeBasedChecker,
            featureBasedChecker = featureBasedChecker,
            featureCollector = FeatureUsageCollector(eventBus),
            pointsManager = pointsManager,
            unlockableManager = mockk(relaxed = true),
            mangaHandler = mangaHandler,
            animeHandler = animeHandler,
            novelHandler = novelHandler,
            mangaRepository = mockk<MangaRepository>(relaxed = true),
            animeRepository = mockk<AnimeRepository>(relaxed = true),
            userProfileManager = mockk(relaxed = true),
            activityDataRepository = mockk(relaxed = true),
        )

        handler.start()
    }

    @Test
    fun `LibraryAdded does not create quantity progress for content_god`() = runTest {
        repository.insertAchievement(
            achievement(
                id = "content_god",
                type = AchievementType.QUANTITY,
                category = AchievementCategory.BOTH,
                threshold = 5000,
            ),
        )

        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 6000L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { novelHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L

        emitAndSettle(AchievementEvent.LibraryAdded(entryId = 1L, type = AchievementCategory.MANGA))

        repository.getProgress("content_god").first().shouldBeNull()
    }

    @Test
    fun `ChapterRead and EpisodeWatched do not create library progress`() = runTest {
        repository.insertAchievement(
            achievement(
                id = "library_collector",
                type = AchievementType.LIBRARY,
                category = AchievementCategory.BOTH,
                threshold = 100,
            ),
        )

        emitAndSettle(AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1))
        repository.getProgress("library_collector").first().shouldBeNull()

        emitAndSettle(AchievementEvent.EpisodeWatched(animeId = 1L, episodeNumber = 1))
        repository.getProgress("library_collector").first().shouldBeNull()
    }

    @Test
    fun `LibraryRemoved can unlock secret_saitama and secret_jojo`() = runTest {
        repository.insertAchievement(
            achievement(
                id = "secret_saitama",
                type = AchievementType.SECRET,
                category = AchievementCategory.SECRET,
                threshold = 1,
            ),
        )
        repository.insertAchievement(
            achievement(
                id = "secret_jojo",
                type = AchievementType.SECRET,
                category = AchievementCategory.SECRET,
                threshold = 1,
            ),
        )

        coEvery { mangaHandler.awaitOneOrNull<Any>(any(), any()) } returnsMany listOf(1L, true)
        coEvery { animeHandler.awaitOneOrNull<Any>(any(), any()) } returnsMany listOf(1L, false)

        emitAndSettle(AchievementEvent.LibraryRemoved(entryId = 1L, type = AchievementCategory.MANGA))

        waitForUnlock("secret_saitama")
        waitForUnlock("secret_jojo")
    }

    @Test
    fun `read_long_manga unlocks from read chapter count`() = runTest {
        repository.insertAchievement(
            achievement(
                id = "read_long_manga",
                type = AchievementType.EVENT,
                category = AchievementCategory.MANGA,
                threshold = 1,
            ),
        )

        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 200L

        emitAndSettle(AchievementEvent.MangaCompleted(mangaId = 7L))

        waitForUnlock("read_long_manga")
    }

    @Test
    fun `read_long_manga stays locked below read threshold`() = runTest {
        repository.insertAchievement(
            achievement(
                id = "read_long_manga",
                type = AchievementType.EVENT,
                category = AchievementCategory.MANGA,
                threshold = 1,
            ),
        )

        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 199L

        emitAndSettle(AchievementEvent.MangaCompleted(mangaId = 7L))

        repository.getProgress("read_long_manga").first().shouldBeNull()
    }

    @Test
    fun `ChapterRead does not evaluate time or feature achievements`() = runTest {
        repository.insertAchievement(
            achievement(
                id = "night_owl",
                type = AchievementType.TIME_BASED,
                category = AchievementCategory.BOTH,
                threshold = 1,
            ),
        )
        repository.insertAchievement(
            achievement(
                id = "download_starter",
                type = AchievementType.FEATURE_BASED,
                category = AchievementCategory.BOTH,
                threshold = 10,
            ),
        )

        emitAndSettle(AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1))

        coVerify(exactly = 0) { timeBasedChecker.check(any(), any()) }
        coVerify(exactly = 0) { featureBasedChecker.check(any(), any()) }
    }

    @Test
    fun `unlocking another achievement immediately checks secret_goku`() = runTest {
        repository.insertAchievement(
            achievement(
                id = "first_chapter",
                type = AchievementType.EVENT,
                category = AchievementCategory.MANGA,
                threshold = 1,
            ),
        )
        repository.insertAchievement(
            achievement(
                id = "secret_goku",
                type = AchievementType.SECRET,
                category = AchievementCategory.SECRET,
                threshold = 1,
            ),
        )

        coEvery { pointsManager.getCurrentPoints() } returns UserPoints(
            totalPoints = 9000,
            achievementsUnlocked = 0,
        )

        emitAndSettle(AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1))

        waitForUnlock("first_chapter")
        waitForUnlock("secret_goku")
    }

    private suspend fun emitAndSettle(event: AchievementEvent) {
        eventBus.emit(event)
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            delay(200)
        }
    }

    private suspend fun waitForUnlock(achievementId: String) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1_500) {
                while (repository.getProgress(achievementId).first()?.isUnlocked != true) {
                    delay(25)
                }
            }
        }
    }

    private fun achievement(
        id: String,
        type: AchievementType,
        category: AchievementCategory,
        threshold: Int,
    ) = Achievement(
        id = id,
        type = type,
        category = category,
        threshold = threshold,
        points = 100,
        title = id,
    )
}
