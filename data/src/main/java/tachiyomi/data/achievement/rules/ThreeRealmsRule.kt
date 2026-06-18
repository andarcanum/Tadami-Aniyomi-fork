package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult
import kotlin.math.min

/**
 * Three Realms: progress is the minimum library size across manga, anime and
 * novels. Rewards a balanced collector rather than raw volume.
 */
class ThreeRealmsRule(
    override val achievementId: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        if (event !is AchievementEvent.LibraryAdded) return RuleResult.NoChange
        return RuleResult.Update(computeMin(context))
    }

    override suspend fun evaluateFull(context: RuleContext): Int = computeMin(context)

    private suspend fun computeMin(context: RuleContext): Int {
        val manga = context.getLibraryCount(AchievementCategory.MANGA)
        val anime = context.getLibraryCount(AchievementCategory.ANIME)
        val novel = context.getLibraryCount(AchievementCategory.NOVEL)
        return min(min(manga, anime), novel)
    }
}
