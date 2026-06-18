package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class ReadingImmersionRule(
    override val achievementId: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = event is AchievementEvent.SessionEnd ||
            event is AchievementEvent.ChapterRead ||
            event is AchievementEvent.EpisodeWatched ||
            event is AchievementEvent.NovelChapterRead
        if (!isMatch) return RuleResult.NoChange
        return RuleResult.Update(maxSessionMinutes(context))
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return maxSessionMinutes(context)
    }

    private suspend fun maxSessionMinutes(context: RuleContext): Int {
        return (context.getMaxSessionDuration() / 60_000).toInt()
    }
}
