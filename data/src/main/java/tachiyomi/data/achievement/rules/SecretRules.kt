package tachiyomi.data.achievement.rules

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository

class SaitamaRule : AchievementRule {
    override val achievementId: String = "secret_saitama"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        if (event !is AchievementEvent.LibraryAdded && event !is AchievementEvent.LibraryRemoved) {
            return RuleResult.NoChange
        }
        val isSaitama = checkSaitama(context)
        return if (isSaitama) RuleResult.Update(1) else RuleResult.NoChange
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return if (checkSaitama(context)) 1 else 0
    }

    private suspend fun checkSaitama(context: RuleContext): Boolean {
        return context.getLibraryCount(AchievementCategory.MANGA) == 1 &&
            context.getLibraryCount(AchievementCategory.ANIME) == 1 &&
            context.getLibraryCount(AchievementCategory.NOVEL) == 1
    }
}

class JojoRule : AchievementRule {
    override val achievementId: String = "secret_jojo"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        if (event !is AchievementEvent.LibraryAdded &&
            event !is AchievementEvent.LibraryRemoved
        ) {
            return RuleResult.NoChange
        }
        val isJojo = context.hasLibraryTitleLike("jojo")
        return if (isJojo) RuleResult.Update(1) else RuleResult.NoChange
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return if (context.hasLibraryTitleLike("jojo")) 1 else 0
    }
}

class HaremKingRule : AchievementRule {
    override val achievementId: String = "secret_harem_king"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        if (event !is AchievementEvent.LibraryAdded &&
            event !is AchievementEvent.LibraryRemoved
        ) {
            return RuleResult.NoChange
        }
        val count = context.hasLibraryGenre("Harem")
        return if (count >= 20) RuleResult.Update(1) else RuleResult.NoChange
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        val count = context.hasLibraryGenre("Harem")
        return if (count >= 20) 1 else 0
    }
}

class IsekaiTruckRule : AchievementRule {
    override val achievementId: String = "secret_isekai_truck"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        if (event !is AchievementEvent.LibraryAdded &&
            event !is AchievementEvent.LibraryRemoved
        ) {
            return RuleResult.NoChange
        }
        val count = context.hasLibraryGenre("Isekai")
        return if (count >= 20) RuleResult.Update(1) else RuleResult.NoChange
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        val count = context.hasLibraryGenre("Isekai")
        return if (count >= 20) 1 else 0
    }
}

class CrybabyRule(
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
    private val novelRepository: NovelRepository,
) : AchievementRule {
    override val achievementId: String = "secret_crybaby"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val genres = when (event) {
            is AchievementEvent.MangaCompleted -> {
                mangaRepository.getMangaById(event.mangaId).genre
            }
            is AchievementEvent.AnimeCompleted -> {
                animeRepository.getAnimeById(event.animeId).genre
            }
            is AchievementEvent.NovelCompleted -> {
                novelRepository.getNovelById(event.novelId).genre
            }
            else -> null
        }
        if (genres == null) return RuleResult.NoChange

        val isTragedyOrDrama = genres.any { g ->
            GenreAliases.genreMatches(g, listOf("Tragedy", "Drama"))
        }
        return if (isTragedyOrDrama) RuleResult.Update(1) else RuleResult.NoChange
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        val completedManga = mangaRepository.getLibraryManga()
            .filter { it.manga.status == SManga.COMPLETED.toLong() }
        if (completedManga.any {
                it.manga.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Tragedy", "Drama")) } ==
                    true
            }
        ) {
            return 1
        }
        val completedAnime = animeRepository.getLibraryAnime()
            .filter { it.anime.status == SAnime.COMPLETED.toLong() }
        if (completedAnime.any {
                it.anime.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Tragedy", "Drama")) } ==
                    true
            }
        ) {
            return 1
        }
        val completedNovel = novelRepository.getLibraryNovel()
            .filter { it.novel.status == SManga.COMPLETED.toLong() }
        if (completedNovel.any {
                it.novel.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Tragedy", "Drama")) } ==
                    true
            }
        ) {
            return 1
        }
        return 0
    }
}

class ShonenRule : AchievementRule {
    override val achievementId: String = "secret_shonen"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        if (event !is AchievementEvent.MangaCompleted &&
            event !is AchievementEvent.AnimeCompleted &&
            event !is AchievementEvent.NovelCompleted
        ) {
            return RuleResult.NoChange
        }

        val total = context.hasLibraryGenre("Shounen") + context.hasLibraryGenre("Shonen")
        return if (total >= 10) RuleResult.Update(1) else RuleResult.NoChange
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        val total = context.hasLibraryGenre("Shounen") + context.hasLibraryGenre("Shonen")
        return if (total >= 10) 1 else 0
    }
}

class DekuRule(
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
    private val novelRepository: NovelRepository,
) : AchievementRule {
    override val achievementId: String = "secret_deku"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val genres = when (event) {
            is AchievementEvent.MangaCompleted -> {
                mangaRepository.getMangaById(event.mangaId).genre
            }
            is AchievementEvent.AnimeCompleted -> {
                animeRepository.getAnimeById(event.animeId).genre
            }
            is AchievementEvent.NovelCompleted -> {
                novelRepository.getNovelById(event.novelId).genre
            }
            else -> null
        }
        if (genres == null) return RuleResult.NoChange

        val isSuperPower = genres.any { g -> GenreAliases.genreMatches(g, listOf("Super Power")) }
        return if (isSuperPower) RuleResult.Update(1) else RuleResult.NoChange
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        val completedAnime = animeRepository.getLibraryAnime()
            .filter { it.anime.status == SAnime.COMPLETED.toLong() }
        if (completedAnime.any {
                it.anime.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Super Power")) } ==
                    true
            }
        ) {
            return 1
        }
        val completedManga = mangaRepository.getLibraryManga()
            .filter { it.manga.status == SManga.COMPLETED.toLong() }
        if (completedManga.any {
                it.manga.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Super Power")) } ==
                    true
            }
        ) {
            return 1
        }
        val completedNovel = novelRepository.getLibraryNovel()
            .filter { it.novel.status == SManga.COMPLETED.toLong() }
        if (completedNovel.any {
                it.novel.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Super Power")) } ==
                    true
            }
        ) {
            return 1
        }
        return 0
    }
}

class ErenRule(
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
    private val novelRepository: NovelRepository,
) : AchievementRule {
    override val achievementId: String = "secret_eren"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val genres = when (event) {
            is AchievementEvent.MangaCompleted -> {
                mangaRepository.getMangaById(event.mangaId).genre
            }
            is AchievementEvent.AnimeCompleted -> {
                animeRepository.getAnimeById(event.animeId).genre
            }
            is AchievementEvent.NovelCompleted -> {
                novelRepository.getNovelById(event.novelId).genre
            }
            else -> null
        }
        if (genres == null) return RuleResult.NoChange

        val isMilitary = genres.any { g -> GenreAliases.genreMatches(g, listOf("Military")) }
        return if (isMilitary) RuleResult.Update(1) else RuleResult.NoChange
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        val completedNovel = novelRepository.getLibraryNovel()
            .filter { it.novel.status == SManga.COMPLETED.toLong() }
        if (completedNovel.any {
                it.novel.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Military")) } == true
            }
        ) {
            return 1
        }
        val completedManga = mangaRepository.getLibraryManga()
            .filter { it.manga.status == SManga.COMPLETED.toLong() }
        if (completedManga.any {
                it.manga.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Military")) } == true
            }
        ) {
            return 1
        }
        val completedAnime = animeRepository.getLibraryAnime()
            .filter { it.anime.status == SAnime.COMPLETED.toLong() }
        if (completedAnime.any {
                it.anime.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Military")) } == true
            }
        ) {
            return 1
        }
        return 0
    }
}

class LelouchRule(
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
    private val novelRepository: NovelRepository,
) : AchievementRule {
    override val achievementId: String = "secret_lelouch"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val genres = when (event) {
            is AchievementEvent.MangaCompleted -> {
                mangaRepository.getMangaById(event.mangaId).genre
            }
            is AchievementEvent.AnimeCompleted -> {
                animeRepository.getAnimeById(event.animeId).genre
            }
            is AchievementEvent.NovelCompleted -> {
                novelRepository.getNovelById(event.novelId).genre
            }
            else -> null
        }
        if (genres == null) return RuleResult.NoChange

        val isPsychological = genres.any { g -> GenreAliases.genreMatches(g, listOf("Psychological")) }
        return if (isPsychological) RuleResult.Update(1) else RuleResult.NoChange
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        val completedManga = mangaRepository.getLibraryManga()
            .filter { it.manga.status == SManga.COMPLETED.toLong() }
        if (completedManga.any {
                it.manga.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Psychological")) } ==
                    true
            }
        ) {
            return 1
        }
        val completedAnime = animeRepository.getLibraryAnime()
            .filter { it.anime.status == SAnime.COMPLETED.toLong() }
        if (completedAnime.any {
                it.anime.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Psychological")) } ==
                    true
            }
        ) {
            return 1
        }
        val completedNovel = novelRepository.getLibraryNovel()
            .filter { it.novel.status == SManga.COMPLETED.toLong() }
        if (completedNovel.any {
                it.novel.genre?.any { g -> GenreAliases.genreMatches(g, listOf("Psychological")) } ==
                    true
            }
        ) {
            return 1
        }
        return 0
    }
}

class OnePieceRule : AchievementRule {
    override val achievementId: String = "secret_onepiece"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = event is AchievementEvent.ChapterRead || event is AchievementEvent.NovelChapterRead
        if (!isMatch) return RuleResult.NoChange

        val total = context.getChaptersRead(AchievementCategory.BOTH)
        return RuleResult.Update(total)
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return context.getChaptersRead(AchievementCategory.BOTH)
    }
}

class GokuRule : AchievementRule {
    override val achievementId: String = "secret_goku"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val totalPoints = context.getCurrentPoints()
        if (totalPoints == currentProgress) return RuleResult.NoChange
        return RuleResult.Update(totalPoints)
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return context.getCurrentPoints()
    }
}
