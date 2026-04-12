package tachiyomi.data.achievement.loader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
}
