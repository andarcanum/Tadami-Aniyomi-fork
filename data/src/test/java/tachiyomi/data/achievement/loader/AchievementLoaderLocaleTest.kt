package tachiyomi.data.achievement.loader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.model.AchievementJson
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.Reward
import tachiyomi.domain.achievement.model.RewardType

class AchievementLoaderLocaleTest {

    @Test
    fun `same locale tag does not require refresh`() {
        shouldRefreshAchievementTexts("en", "en") shouldBe false
    }

    @Test
    fun `different locale tag requires refresh`() {
        shouldRefreshAchievementTexts("ru", "en") shouldBe true
    }

    @Test
    fun `missing saved locale tag requires refresh`() {
        shouldRefreshAchievementTexts(null, "en") shouldBe true
    }

    @Test
    fun `backfill is required when json has rewards but db row is missing them`() {
        val db = listOf(
            simpleAchievement("secret_goku", rewards = null),
        )
        val json = listOf(
            simpleJson(
                id = "secret_goku",
                rewards = listOf(reward("aura_matrix", RewardType.AURA)),
            ),
        )

        shouldBackfillRewards(db, json) shouldBe true
    }

    @Test
    fun `backfill is not required when db row already has matching rewards`() {
        val db = listOf(
            simpleAchievement("secret_goku", rewards = listOf(reward("aura_matrix", RewardType.AURA))),
        )
        val json = listOf(
            simpleJson(
                id = "secret_goku",
                rewards = listOf(reward("aura_matrix", RewardType.AURA)),
            ),
        )

        shouldBackfillRewards(db, json) shouldBe false
    }

    @Test
    fun `backfill is not required for achievements that never had rewards`() {
        val db = listOf(
            simpleAchievement("no_rewards", rewards = null),
        )
        val json = listOf(
            simpleJson(id = "no_rewards", rewards = null),
        )

        shouldBackfillRewards(db, json) shouldBe false
    }

    @Test
    fun `backfill triggers when any reward-bearing row is stale`() {
        val db = listOf(
            simpleAchievement("a", rewards = listOf(reward("aura_x", RewardType.AURA))),
            simpleAchievement("b", rewards = null),
        )
        val json = listOf(
            simpleJson(id = "a", rewards = listOf(reward("aura_x", RewardType.AURA))),
            simpleJson(id = "b", rewards = listOf(reward("aura_y", RewardType.AURA))),
        )

        shouldBackfillRewards(db, json) shouldBe true
    }

    @Test
    fun `progress rule version requires recalculation even when json version is already calculated`() {
        shouldRecalculateAchievementProgress(
            calculationVersion = 10,
            jsonVersion = 10,
            progressRuleVersion = 1,
            currentProgressRuleVersion = 2,
        ) shouldBe true
    }

    @Test
    fun `progress recalculation is skipped when json and rule versions are current`() {
        shouldRecalculateAchievementProgress(
            calculationVersion = 10,
            jsonVersion = 10,
            progressRuleVersion = 2,
            currentProgressRuleVersion = 2,
        ) shouldBe false
    }

    private fun simpleAchievement(
        id: String,
        rewards: List<Reward>?,
    ) = Achievement(
        id = id,
        type = AchievementType.SECRET,
        category = AchievementCategory.SECRET,
        title = id,
        rewards = rewards,
    )

    private fun simpleJson(
        id: String,
        rewards: List<Reward>?,
    ) = AchievementJson(
        id = id,
        type = "secret",
        category = "secret",
        title = id,
        rewards = rewards,
    )

    private fun reward(id: String, type: RewardType) = Reward(
        type = type,
        id = id,
        title = id,
    )
}
