package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

/**
 * Регрессионный тест сигнала «подборка ограничена» (тост for_you_limited_pool).
 * Модель вызывает [shouldNotifyDiscoveryPoolLimited] уже после проверки
 * `notifyIfLimited && currentTitles.isNotEmpty()`, поэтому здесь покрыт только
 * решающий критерий: пул не больше окна тизера или новое окно пересекается с текущим.
 */
class HomeHubDiscoveryPoolNoticeTest {

    @Test
    fun `pool not larger than the teaser window is reported as limited`() {
        shouldNotifyDiscoveryPoolLimited(
            teaser = items("Alpha", "Beta", "Gamma"),
            currentTitles = setOf("alpha"),
            distinctPoolSize = 3,
            count = 3,
        ) shouldBe true
    }

    @Test
    fun `window overlapping the on-screen cards is reported as limited`() {
        shouldNotifyDiscoveryPoolLimited(
            teaser = items("Alpha", "Beta", "Gamma"),
            currentTitles = setOf("alpha", "delta"),
            distinctPoolSize = 12,
            count = 3,
        ) shouldBe true
    }

    @Test
    fun `fresh disjoint window from a larger pool is not reported`() {
        shouldNotifyDiscoveryPoolLimited(
            teaser = items("Delta", "Epsilon", "Zeta"),
            currentTitles = setOf("alpha", "beta", "gamma"),
            distinctPoolSize = 12,
            count = 3,
        ) shouldBe false
    }

    @Test
    fun `empty teaser from a larger pool is not reported`() {
        shouldNotifyDiscoveryPoolLimited(
            teaser = emptyList(),
            currentTitles = setOf("alpha"),
            distinctPoolSize = 12,
            count = 3,
        ) shouldBe false
    }

    private fun items(vararg titles: String): List<HomeHubDiscoveryItem> =
        titles.map { suggestion(it).toHomeHubDiscoveryItem() }

    private fun suggestion(title: String) = DiscoverySuggestion(
        id = 0L,
        mediaType = DiscoveryMediaType.MANGA,
        rowType = DiscoveryRowType.TREND,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "test",
        score = 1.0,
        position = 0L,
        createdAt = 0L,
    )
}
