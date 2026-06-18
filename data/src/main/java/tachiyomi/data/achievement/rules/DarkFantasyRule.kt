package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository

/**
 * Unlocks when the user has at least one library title that qualifies as
 * "dark fantasy": genres must contain a tag from darkGroupA (Dark, Horror, or
 * the single "dark fantasy" tag) AND a tag from darkGroupB (Fantasy).
 *
 * Russian aliases are resolved through GenreAliases.genreMatches, so tags like
 * "Тёмное фэнтези", "Ужасы", "Фэнтези" etc. all count.
 */
class DarkFantasyRule(
    private val mangaRepository: MangaRepository,
    private val animeRepository: AnimeRepository,
    private val novelRepository: NovelRepository,
) : AchievementRule {

    override val achievementId: String = "secret_shadow_monarch"

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
        return RuleResult.Update(if (hasDarkFantasyTitle()) 1 else 0)
    }

    override suspend fun evaluateFull(context: RuleContext): Int =
        if (hasDarkFantasyTitle()) 1 else 0

    private suspend fun hasDarkFantasyTitle(): Boolean {
        val groupA = GenreAliases.darkGroupA
        val groupB = GenreAliases.darkGroupB

        return mangaRepository.getLibraryManga().any { item ->
            val genres = item.manga.genre ?: return@any false
            genres.any { g -> GenreAliases.genreMatches(g, groupA) } &&
                genres.any { g -> GenreAliases.genreMatches(g, groupB) }
        } ||
            animeRepository.getLibraryAnime().any { item ->
                val genres = item.anime.genre ?: return@any false
                genres.any { g -> GenreAliases.genreMatches(g, groupA) } &&
                    genres.any { g -> GenreAliases.genreMatches(g, groupB) }
            } ||
            novelRepository.getLibraryNovel().any { item ->
                val genres = item.novel.genre ?: return@any false
                genres.any { g -> GenreAliases.genreMatches(g, groupA) } &&
                    genres.any { g -> GenreAliases.genreMatches(g, groupB) }
            }
    }
}
