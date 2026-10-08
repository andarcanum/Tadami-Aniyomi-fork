package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class StreakRule(
    override val achievementId: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = event is AchievementEvent.ChapterRead ||
            event is AchievementEvent.NovelChapterRead ||
            event is AchievementEvent.EpisodeWatched ||
            event is AchievementEvent.SessionEnd
        if (!isMatch) return RuleResult.NoChange

        val streak = context.getCurrentStreak()
        return RuleResult.Update(streak)
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return context.getCurrentStreak()
    }
}
