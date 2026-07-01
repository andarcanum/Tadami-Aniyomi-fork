package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult
import java.time.Instant
import java.time.ZoneId

class TimeParadoxRule : AchievementRule {
    override val achievementId: String = "time_paradox"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = event is AchievementEvent.ChapterRead ||
            event is AchievementEvent.NovelChapterRead ||
            event is AchievementEvent.EpisodeWatched

        if (!isMatch) return RuleResult.NoChange

        val localTime = Instant.ofEpochMilli(event.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()

        val isTimeParadox = (localTime.hour == 11 || localTime.hour == 23) && localTime.minute == 11

        return if (isTimeParadox) {
            RuleResult.Update(1)
        } else {
            RuleResult.NoChange
        }
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return 0
    }
}
