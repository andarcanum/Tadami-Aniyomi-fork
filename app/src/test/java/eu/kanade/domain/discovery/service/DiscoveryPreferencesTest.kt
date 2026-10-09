package eu.kanade.domain.discovery.service

import eu.kanade.domain.ui.model.HomeHeroMode
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class DiscoveryPreferencesTest {

    @Test
    fun `defaults are discovery-on, hero-auto, interval 12, teaser 16, seeds 3`() {
        val prefs = DiscoveryPreferences(InMemoryPreferenceStore())
        prefs.discoveryEnabled().get() shouldBe true
        prefs.homeHeroMode().get() shouldBe "auto"
        prefs.refreshIntervalHours().get() shouldBe 12
        prefs.teaserCount().get() shouldBe 16
        prefs.seedCount().get() shouldBe 3
        prefs.seedCompleted().get() shouldBe true
        prefs.seedActive14().get() shouldBe true
        prefs.seedAdded().get() shouldBe false
        prefs.rowLikeEnabled().get() shouldBe true
        prefs.rowTasteEnabled().get() shouldBe true
        prefs.rowTrendEnabled().get() shouldBe true
        prefs.rowSourceEnabled().get() shouldBe true
        prefs.seedCompletedDays().get() shouldBe 30
        prefs.seedActiveDays().get() shouldBe 14
        prefs.refreshWifiOnly().get() shouldBe false
        prefs.refreshAfterLibrary().get() shouldBe true
        prefs.showReasons().get() shouldBe true
        prefs.trendSeason().get() shouldBe "current"
        prefs.trendSort().get() shouldBe "popularity"
        prefs.collageRotationIntervalHours().get() shouldBe 2
        prefs.collageAnimationSpeed().get() shouldBe "normal"
        prefs.collageLastRotationTime(DiscoveryMediaType.ANIME).get() shouldBe 0L
        prefs.collageOffset(DiscoveryMediaType.ANIME).get() shouldBe 0
        prefs.feedManualRefreshAt(tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME).get() shouldBe 0L
        prefs.homeManualRefreshAt(tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME).get() shouldBe 0L
        prefs.manualRefreshCount(tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME).get() shouldBe 0
        prefs.filterNsfw().get() shouldBe true
        prefs.lastFailedRows(tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME).get() shouldBe ""
    }

    @Test
    fun `source participation defaults keep current behaviour`() {
        val prefs = DiscoveryPreferences(InMemoryPreferenceStore())
        prefs.discoverySourceMode(tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL).get() shouldBe "auto"
        prefs.discoverySourceExcluded(tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL).get() shouldBe ""
    }

    @Test
    fun `hero mode fromKey falls back to Continue`() {
        HomeHeroMode.fromKey("auto") shouldBe HomeHeroMode.Auto
        HomeHeroMode.fromKey("hybrid") shouldBe HomeHeroMode.Hybrid
        HomeHeroMode.fromKey("collage") shouldBe HomeHeroMode.Collage
        HomeHeroMode.fromKey(null) shouldBe HomeHeroMode.Continue
        HomeHeroMode.fromKey("bogus") shouldBe HomeHeroMode.Continue
    }

    @Test
    fun `feed refresh cooldown preference is scoped per media type`() {
        val prefs = DiscoveryPreferences(eu.kanade.tachiyomi.test.PersistingPreferenceStore())
        prefs.feedManualRefreshAt(tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL).get() shouldBe 0L
        prefs.feedManualRefreshAt(tachiyomi.domain.discovery.model.DiscoveryMediaType.NOVEL).set(123L)
        prefs.feedManualRefreshAt(tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME).get() shouldBe 0L
    }

    @Test
    fun `collage and stage rotation prefs are scoped per media type`() {
        val prefs = DiscoveryPreferences(InMemoryPreferenceStore())
        prefs.collageOffset(DiscoveryMediaType.ANIME).set(3)
        prefs.collageOffset(DiscoveryMediaType.NOVEL).get() shouldBe 0
        prefs.stageLastRotationTime(DiscoveryMediaType.MANGA).set(777L)
        prefs.stageLastRotationTime(DiscoveryMediaType.ANIME).get() shouldBe 0L
    }
}
