package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult
import tachiyomi.domain.book.novel.repository.NovelHighlightRepository

/**
 * «Чернильница» (save_5_quotes): прогресс = текущее число цитат в сборнике
 * (novel_highlights). [evaluateFull] делает достижение ретроактивным при бампе
 * версии JSON; [evaluateDelta] обновляет прогресс по факту БД на каждое
 * сохранение цитаты. Удаление цитат до разблокировки честно снижает прогресс.
 */
class QuoteRule(
    private val novelHighlightRepository: NovelHighlightRepository,
) : AchievementRule {

    override val achievementId: String = "save_5_quotes"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        if (event !is AchievementEvent.FeatureUsed) return RuleResult.NoChange
        if (event.feature != AchievementEvent.Feature.QUOTE_SAVED) return RuleResult.NoChange
        return RuleResult.Update(novelHighlightRepository.countAll())
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return novelHighlightRepository.countAll()
    }
}
