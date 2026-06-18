package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

/**
 * Genre depth: progress is the number of library titles matching a given genre
 * (resolved through GenreAliases inside RuleContext.hasLibraryGenre).
 */
class GenreCountRule(
    override val achievementId: String,
    private val genre: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        if (event !is AchievementEvent.LibraryAdded) return RuleResult.NoChange
        return RuleResult.Update(context.hasLibraryGenre(genre))
    }

    override suspend fun evaluateFull(context: RuleContext): Int = context.hasLibraryGenre(genre)
}
