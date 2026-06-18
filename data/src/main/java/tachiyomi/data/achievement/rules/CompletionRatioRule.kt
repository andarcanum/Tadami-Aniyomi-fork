package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

/**
 * Completion ratio: progress is the percentage (0-100) of the library that is
 * completed, but only once a minimum library size is reached (to avoid a
 * trivial "1/1 = 100%" unlock). The achievement threshold is the target percent.
 */
class CompletionRatioRule(
    override val achievementId: String,
    private val minLibrarySize: Int,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch =
            event is AchievementEvent.LibraryAdded ||
                event is AchievementEvent.ChapterRead ||
                event is AchievementEvent.EpisodeWatched ||
                event is AchievementEvent.NovelChapterRead
        if (!isMatch) return RuleResult.NoChange
        return RuleResult.Update(computePercent(context))
    }

    override suspend fun evaluateFull(context: RuleContext): Int = computePercent(context)

    private suspend fun computePercent(context: RuleContext): Int {
        val library = context.getLibraryCount(AchievementCategory.BOTH)
        if (library < minLibrarySize) return 0
        val completed = context.getCompletedCount(AchievementCategory.BOTH)
        return ((completed.toDouble() / library.toDouble()) * 100).toInt().coerceIn(0, 100)
    }
}
