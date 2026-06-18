package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class RankUpRule(
    override val achievementId: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = event is AchievementEvent.ChapterRead ||
            event is AchievementEvent.EpisodeWatched ||
            event is AchievementEvent.NovelChapterRead ||
            event is AchievementEvent.SessionEnd ||
            event is AchievementEvent.FeatureUsed ||
            event is AchievementEvent.AppStart
        if (!isMatch) return RuleResult.NoChange
        return RuleResult.Update(context.getCurrentPoints())
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return context.getCurrentPoints()
    }
}
