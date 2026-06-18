package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult
import kotlin.math.min

class AnimeNovelHybridRule(
    override val achievementId: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = event is AchievementEvent.EpisodeWatched ||
            event is AchievementEvent.NovelChapterRead
        if (!isMatch) return RuleResult.NoChange
        return RuleResult.Update(computeMin(context))
    }

    override suspend fun evaluateFull(context: RuleContext): Int = computeMin(context)

    private suspend fun computeMin(context: RuleContext): Int {
        val anime = context.getChaptersRead(AchievementCategory.ANIME)
        val novel = context.getChaptersRead(AchievementCategory.NOVEL)
        return min(anime, novel)
    }
}
