package eu.kanade.domain.discovery.service

import eu.kanade.domain.ui.model.HomeHeroMode
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import java.util.concurrent.BrokenBarrierException
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class DiscoveryPreferencesTest {

    @Test
    fun `concurrent failure claims across preference instances admit one surface`() {
        val store = eu.kanade.tachiyomi.test.PersistingPreferenceStore()
        val preferences = List(2) { DiscoveryPreferences(store) }
        preferences.first().manualRefreshWorkId(DiscoveryMediaType.ANIME).set("failed")
        val executor = Executors.newFixedThreadPool(2)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val claims = preferences.map { prefs ->
                executor.submit<Boolean> {
                    start.await()
                    prefs.claimManualRefreshFailure(DiscoveryMediaType.ANIME, "failed")
                }
            }
            start.countDown()
            claims.count { it.get(5, TimeUnit.SECONDS) } shouldBe 1
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `failure claim survives recreation and rejects replaced requests independently per media`() {
        val store = eu.kanade.tachiyomi.test.PersistingPreferenceStore()
        val first = DiscoveryPreferences(store)
        val second = DiscoveryPreferences(store)
        first.manualRefreshWorkId(DiscoveryMediaType.NOVEL).set("current")
        first.claimManualRefreshFailure(DiscoveryMediaType.NOVEL, "previous") shouldBe false
        first.claimManualRefreshFailure(DiscoveryMediaType.NOVEL, "") shouldBe false
        first.claimManualRefreshFailure(DiscoveryMediaType.NOVEL, "current") shouldBe true
        second.claimManualRefreshFailure(DiscoveryMediaType.NOVEL, "current") shouldBe false
        second.manualRefreshWorkId(DiscoveryMediaType.ANIME).set("current")
        second.claimManualRefreshFailure(DiscoveryMediaType.ANIME, "current") shouldBe true
        second.manualRefreshWorkId(DiscoveryMediaType.NOVEL).set("next")
        second.claimManualRefreshFailure(DiscoveryMediaType.NOVEL, "next") shouldBe true
    }

    @Test
    fun `concurrent manual refreshes across preference instances admit exactly one request`() {
        val backing = eu.kanade.tachiyomi.test.PersistingPreferenceStore()
        val readBarrier = CyclicBarrier(2)
        val coordinateReads = AtomicBoolean(false)
        val store = object : PreferenceStore by backing {
            override fun getLong(key: String, defaultValue: Long): Preference<Long> {
                val preference = backing.getLong(key, defaultValue)
                if (key != "discovery_manual_refresh_at_anime") return preference
                return object : Preference<Long> by preference {
                    override fun get(): Long {
                        val value = preference.get()
                        if (coordinateReads.get()) {
                            // Separate instance locks let both callers read zero before either writes.
                            // A shared lock serializes them, so the first read times out and proceeds.
                            try {
                                readBarrier.await(5, TimeUnit.SECONDS)
                            } catch (_: TimeoutException) {
                            } catch (_: BrokenBarrierException) {
                            }
                        }
                        return value
                    }
                }
            }
        }
        val first = DiscoveryPreferences(store)
        val second = DiscoveryPreferences(store)
        first.manualRefreshAt(DiscoveryMediaType.ANIME)
        second.manualRefreshAt(DiscoveryMediaType.ANIME)
        coordinateReads.set(true)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val requests = listOf(first, second).map { preferences ->
                executor.submit<Boolean> {
                    preferences.tryStartManualRefresh(DiscoveryMediaType.ANIME, 1_000_000L)
                }
            }

            requests.count { it.get(10, TimeUnit.SECONDS) } shouldBe 1
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `manual cooldown migrates home and feed timestamps and preserves the latest shared value`() {
        val cases = listOf(
            Triple(120L, 0L, 0L) to 120L,
            Triple(0L, 240L, 0L) to 240L,
            Triple(120L, 240L, 0L) to 240L,
            Triple(360L, 240L, 0L) to 360L,
            Triple(120L, 240L, 480L) to 480L,
        )
        cases.forEach { (timestamps, expected) ->
            val store = eu.kanade.tachiyomi.test.PersistingPreferenceStore()
            val prefs = DiscoveryPreferences(store)
            prefs.homeManualRefreshAt(DiscoveryMediaType.ANIME).set(timestamps.first)
            prefs.feedManualRefreshAt(DiscoveryMediaType.ANIME).set(timestamps.second)
            store.getLong("discovery_manual_refresh_at_anime", 0L).set(timestamps.third)

            prefs.manualRefreshAt(DiscoveryMediaType.ANIME).get() shouldBe expected
            prefs.homeManualRefreshAt(DiscoveryMediaType.ANIME).get() shouldBe 0L
            prefs.feedManualRefreshAt(DiscoveryMediaType.ANIME).get() shouldBe 0L
            DiscoveryPreferences(store).manualRefreshAt(DiscoveryMediaType.ANIME).get() shouldBe expected
        }
    }

    @Test
    fun `cancel before migration clears only its media and cannot restore legacy cooldown`() {
        val prefs = DiscoveryPreferences(eu.kanade.tachiyomi.test.PersistingPreferenceStore())
        prefs.homeManualRefreshAt(DiscoveryMediaType.ANIME).set(120L)
        prefs.feedManualRefreshAt(DiscoveryMediaType.MANGA).set(240L)
        prefs.homeManualRefreshAt(DiscoveryMediaType.NOVEL).set(360L)

        prefs.clearManualRefreshAt(DiscoveryMediaType.MANGA)

        prefs.manualRefreshAt(DiscoveryMediaType.MANGA).get() shouldBe 0L
        prefs.manualRefreshAt(DiscoveryMediaType.ANIME).get() shouldBe 120L
        prefs.manualRefreshAt(DiscoveryMediaType.NOVEL).get() shouldBe 360L
    }

    @Test
    fun `manual cooldown admits one refresh then allows a new refresh at five minutes`() {
        val prefs = DiscoveryPreferences(eu.kanade.tachiyomi.test.PersistingPreferenceStore())
        prefs.tryStartManualRefresh(DiscoveryMediaType.ANIME, 1_000_000L) shouldBe true
        prefs.tryStartManualRefresh(DiscoveryMediaType.ANIME, 1_000_000L) shouldBe false
        prefs.tryStartManualRefresh(DiscoveryMediaType.ANIME, 1_299_999L) shouldBe false
        prefs.tryStartManualRefresh(DiscoveryMediaType.ANIME, 1_300_000L) shouldBe true
        prefs.manualRefreshAt(DiscoveryMediaType.ANIME).get() shouldBe 1_300_000L
        prefs.tryStartManualRefresh(DiscoveryMediaType.MANGA, 1_000_000L) shouldBe true
        prefs.tryStartManualRefresh(DiscoveryMediaType.NOVEL, 1_000_000L) shouldBe true
        prefs.clearManualRefreshAt(DiscoveryMediaType.ANIME)
        prefs.tryStartManualRefresh(DiscoveryMediaType.ANIME, 1_300_001L) shouldBe true
        prefs.manualRefreshAt(DiscoveryMediaType.MANGA).get() shouldBe 1_000_000L
        prefs.manualRefreshAt(DiscoveryMediaType.NOVEL).get() shouldBe 1_000_000L
    }

    @Test
    fun `manual refresh work IDs persist independently for each media`() {
        val store = eu.kanade.tachiyomi.test.PersistingPreferenceStore()
        val prefs = DiscoveryPreferences(store)
        prefs.manualRefreshWorkId(DiscoveryMediaType.NOVEL).get() shouldBe ""
        prefs.manualRefreshWorkId(DiscoveryMediaType.NOVEL).set("novel-work")
        prefs.manualRefreshWorkId(DiscoveryMediaType.ANIME).set("anime-work")
        val reloaded = DiscoveryPreferences(store)
        reloaded.manualRefreshWorkId(DiscoveryMediaType.NOVEL).get() shouldBe "novel-work"
        reloaded.manualRefreshWorkId(DiscoveryMediaType.ANIME).get() shouldBe "anime-work"
        reloaded.manualRefreshWorkId(DiscoveryMediaType.MANGA).get() shouldBe ""
    }

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
