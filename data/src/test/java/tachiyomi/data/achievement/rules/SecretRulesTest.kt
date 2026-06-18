package tachiyomi.data.achievement.rules

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository

class SecretRulesTest {

    private lateinit var context: RuleContext

    @BeforeEach
    fun setup() {
        context = mockk()
    }

    @Test
    fun `SaitamaRule triggers when library has exactly 1 manga 1 anime 1 novel`() = runTest {
        val rule = SaitamaRule()
        coEvery { context.getLibraryCount(AchievementCategory.MANGA) } returns 1
        coEvery { context.getLibraryCount(AchievementCategory.ANIME) } returns 1
        coEvery { context.getLibraryCount(AchievementCategory.NOVEL) } returns 1

        rule.evaluateFull(context) shouldBe 1

        val event = AchievementEvent.LibraryAdded(1L, AchievementCategory.MANGA)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `JojoRule triggers when library title like jojo is present`() = runTest {
        val rule = JojoRule()
        coEvery { context.hasLibraryTitleLike("jojo") } returns true

        rule.evaluateFull(context) shouldBe 1

        val event = AchievementEvent.LibraryAdded(1L, AchievementCategory.MANGA)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `HaremKingRule triggers when harem genre titles count is 20 or more`() = runTest {
        val rule = HaremKingRule()
        coEvery { context.hasLibraryGenre("Harem") } returns 20

        rule.evaluateFull(context) shouldBe 1
    }

    @Test
    fun `IsekaiTruckRule triggers when isekai genre titles count is 20 or more`() = runTest {
        val rule = IsekaiTruckRule()
        coEvery { context.hasLibraryGenre("Isekai") } returns 20

        rule.evaluateFull(context) shouldBe 1
    }

    @Test
    fun `CrybabyRule triggers when completed title has Tragedy or Drama genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = CrybabyRule(mangaRepo, animeRepo, novelRepo)

        val manga = mockk<Manga> {
            coEvery { genre } returns listOf("Drama")
        }
        coEvery { mangaRepo.getMangaById(1L) } returns manga

        val event = AchievementEvent.MangaCompleted(1L)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `ShonenRule triggers when completed shonen titles count is 10 or more`() = runTest {
        val rule = ShonenRule()
        coEvery { context.hasLibraryGenre("Shounen") } returns 6
        coEvery { context.hasLibraryGenre("Shonen") } returns 4

        rule.evaluateFull(context) shouldBe 1
    }

    @Test
    fun `DekuRule triggers when completed title has Super Power genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = DekuRule(mangaRepo, animeRepo, novelRepo)

        val anime = mockk<Anime> {
            coEvery { genre } returns listOf("Super Power")
        }
        coEvery { animeRepo.getAnimeById(2L) } returns anime

        val event = AchievementEvent.AnimeCompleted(2L)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `ErenRule triggers when completed title has Military genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = ErenRule(mangaRepo, animeRepo, novelRepo)

        val novel = mockk<Novel> {
            coEvery { genre } returns listOf("Military")
        }
        coEvery { novelRepo.getNovelById(3L) } returns novel

        val event = AchievementEvent.NovelCompleted(3L)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `LelouchRule triggers when completed title has Psychological genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = LelouchRule(mangaRepo, animeRepo, novelRepo)

        val manga = mockk<Manga> {
            coEvery { genre } returns listOf("Psychological")
        }
        coEvery { mangaRepo.getMangaById(4L) } returns manga

        val event = AchievementEvent.MangaCompleted(4L)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `OnePieceRule triggers when total read chapters is 1000 or more`() = runTest {
        val rule = OnePieceRule()
        coEvery { context.getChaptersRead(AchievementCategory.BOTH) } returns 1000

        rule.evaluateFull(context) shouldBe 1000
    }

    @Test
    fun `GokuRule triggers when total points is 9000 or more`() = runTest {
        val rule = GokuRule()
        coEvery { context.getCurrentPoints() } returns 9000

        rule.evaluateFull(context) shouldBe 9000

        val event = AchievementEvent.AppStart(12)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(9000)
    }

    @Test
    fun `CrybabyRule evaluateFull returns 1 when a completed manga has Tragedy genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = CrybabyRule(mangaRepo, animeRepo, novelRepo)

        val completedManga = manga(
            id = 1L,
            status = SManga.COMPLETED.toLong(),
            genre = listOf("Tragedy"),
        )
        coEvery { mangaRepo.getLibraryManga() } returns listOf(
            libraryManga(completedManga, totalChapters = 100, readCount = 100),
        )
        coEvery { animeRepo.getLibraryAnime() } returns emptyList()
        coEvery { novelRepo.getLibraryNovel() } returns emptyList()

        rule.evaluateFull(context) shouldBe 1
    }

    @Test
    fun `CrybabyRule evaluateFull returns 0 when no completed title has Tragedy or Drama`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = CrybabyRule(mangaRepo, animeRepo, novelRepo)

        val ongoingManga = manga(
            id = 1L,
            status = SManga.ONGOING.toLong(),
            genre = listOf("Tragedy"),
        )
        coEvery { mangaRepo.getLibraryManga() } returns listOf(
            libraryManga(ongoingManga, totalChapters = 100, readCount = 50),
        )
        coEvery { animeRepo.getLibraryAnime() } returns emptyList()
        coEvery { novelRepo.getLibraryNovel() } returns emptyList()

        rule.evaluateFull(context) shouldBe 0
    }

    @Test
    fun `DekuRule evaluateFull returns 1 when a completed anime has Super Power genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = DekuRule(mangaRepo, animeRepo, novelRepo)

        val completedAnime = anime(
            id = 2L,
            status = SAnime.COMPLETED.toLong(),
            genre = listOf("Super Power"),
        )
        coEvery { mangaRepo.getLibraryManga() } returns emptyList()
        coEvery { animeRepo.getLibraryAnime() } returns listOf(
            libraryAnime(completedAnime, totalEpisodes = 24, seenCount = 24),
        )
        coEvery { novelRepo.getLibraryNovel() } returns emptyList()

        rule.evaluateFull(context) shouldBe 1
    }

    @Test
    fun `ErenRule evaluateFull returns 1 when a completed novel has Military genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = ErenRule(mangaRepo, animeRepo, novelRepo)

        val completedNovel = novel(
            id = 3L,
            status = SManga.COMPLETED.toLong(),
            genre = listOf("Military"),
        )
        coEvery { mangaRepo.getLibraryManga() } returns emptyList()
        coEvery { animeRepo.getLibraryAnime() } returns emptyList()
        coEvery { novelRepo.getLibraryNovel() } returns listOf(
            libraryNovel(completedNovel, totalChapters = 100, readCount = 100),
        )

        rule.evaluateFull(context) shouldBe 1
    }

    @Test
    fun `LelouchRule evaluateFull returns 1 when a completed manga has Psychological genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = LelouchRule(mangaRepo, animeRepo, novelRepo)

        val completedManga = manga(
            id = 4L,
            status = SManga.COMPLETED.toLong(),
            genre = listOf("Psychological"),
        )
        coEvery { mangaRepo.getLibraryManga() } returns listOf(
            libraryManga(completedManga, totalChapters = 100, readCount = 100),
        )
        coEvery { animeRepo.getLibraryAnime() } returns emptyList()
        coEvery { novelRepo.getLibraryNovel() } returns emptyList()

        rule.evaluateFull(context) shouldBe 1
    }

    @Test
    fun `CrybabyRule triggers when completed title has Russian Drama genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = CrybabyRule(mangaRepo, animeRepo, novelRepo)

        val manga = mockk<Manga> {
            coEvery { genre } returns listOf("Драма")
        }
        coEvery { mangaRepo.getMangaById(1L) } returns manga

        val event = AchievementEvent.MangaCompleted(1L)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `CrybabyRule triggers when completed title has Russian Tragedy genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = CrybabyRule(mangaRepo, animeRepo, novelRepo)

        val completedManga = manga(
            id = 1L,
            status = SManga.COMPLETED.toLong(),
            genre = listOf("Трагедия"),
        )
        coEvery { mangaRepo.getLibraryManga() } returns listOf(
            libraryManga(completedManga, totalChapters = 100, readCount = 100),
        )
        coEvery { animeRepo.getLibraryAnime() } returns emptyList()
        coEvery { novelRepo.getLibraryNovel() } returns emptyList()

        rule.evaluateFull(context) shouldBe 1
    }

    @Test
    fun `DekuRule triggers when completed title has Russian Super Power genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = DekuRule(mangaRepo, animeRepo, novelRepo)

        val anime = mockk<Anime> {
            coEvery { genre } returns listOf("Суперсила")
        }
        coEvery { animeRepo.getAnimeById(2L) } returns anime

        val event = AchievementEvent.AnimeCompleted(2L)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `ErenRule triggers when completed title has Russian Military genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = ErenRule(mangaRepo, animeRepo, novelRepo)

        val novel = mockk<Novel> {
            coEvery { genre } returns listOf("Военное")
        }
        coEvery { novelRepo.getNovelById(3L) } returns novel

        val event = AchievementEvent.NovelCompleted(3L)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `LelouchRule triggers when completed title has Russian Psychological genre`() = runTest {
        val mangaRepo = mockk<MangaRepository>()
        val animeRepo = mockk<AnimeRepository>()
        val novelRepo = mockk<NovelRepository>()
        val rule = LelouchRule(mangaRepo, animeRepo, novelRepo)

        val manga = mockk<Manga> {
            coEvery { genre } returns listOf("Психологическое")
        }
        coEvery { mangaRepo.getMangaById(4L) } returns manga

        val event = AchievementEvent.MangaCompleted(4L)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `JojoRule triggers when library title contains Russian djodzho`() = runTest {
        val rule = JojoRule()
        coEvery { context.hasLibraryTitleLike("jojo") } returns true

        rule.evaluateFull(context) shouldBe 1
    }

    private fun manga(
        id: Long,
        status: Long,
        genre: List<String>?,
    ): Manga = Manga.create().copy(
        id = id,
        status = status,
        genre = genre,
    )

    private fun anime(
        id: Long,
        status: Long,
        genre: List<String>?,
    ): Anime = Anime.create().copy(
        id = id,
        status = status,
        genre = genre,
    )

    private fun novel(
        id: Long,
        status: Long,
        genre: List<String>?,
    ): Novel = Novel.create().copy(
        id = id,
        status = status,
        genre = genre,
    )

    private fun libraryManga(
        manga: Manga,
        totalChapters: Long,
        readCount: Long,
    ) = tachiyomi.domain.library.manga.LibraryManga(
        manga = manga,
        category = 0L,
        totalChapters = totalChapters,
        readCount = readCount,
        bookmarkCount = 0L,
        latestUpload = 0L,
        chapterFetchedAt = 0L,
        lastRead = 0L,
    )

    private fun libraryAnime(
        anime: Anime,
        totalEpisodes: Long,
        seenCount: Long,
    ) = tachiyomi.domain.library.anime.LibraryAnime(
        anime = anime,
        category = 0L,
        totalCount = totalEpisodes,
        seenCount = seenCount,
        bookmarkCount = 0L,
        fillermarkCount = 0L,
        latestUpload = 0L,
        episodeFetchedAt = 0L,
        lastSeen = 0L,
    )

    private fun libraryNovel(
        novel: Novel,
        totalChapters: Long,
        readCount: Long,
    ) = tachiyomi.domain.library.novel.LibraryNovel(
        novel = novel,
        category = 0L,
        totalChapters = totalChapters,
        readCount = readCount,
        bookmarkCount = 0L,
        latestUpload = 0L,
        chapterFetchedAt = 0L,
        lastRead = 0L,
    )
}
