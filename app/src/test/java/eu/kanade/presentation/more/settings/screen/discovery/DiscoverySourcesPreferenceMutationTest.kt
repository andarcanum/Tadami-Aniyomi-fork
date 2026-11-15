package eu.kanade.presentation.more.settings.screen.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.PluginKnownReconciliation
import eu.kanade.tachiyomi.data.discovery.parseKeyCsv
import eu.kanade.tachiyomi.data.discovery.reconcileDiscoverySourcePreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import java.util.concurrent.BrokenBarrierException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class DiscoverySourcesPreferenceMutationTest {

    @Test
    fun `concurrent manga toggle and reconciliation preserve both exclusions`() {
        val backing = PersistingPreferenceStore()
        val readBarrier = CyclicBarrier(2)
        val coordinateReads = AtomicBoolean(false)
        val store = object : PreferenceStore by backing {
            override fun getString(key: String, defaultValue: String): Preference<String> {
                val preference = synchronized(backing) { backing.getString(key, defaultValue) }
                if (key != "discovery_source_excluded_manga") return preference
                return object : Preference<String> by preference {
                    override fun get(): String {
                        val value = preference.get()
                        if (coordinateReads.get()) {
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
        first.discoverySourceMode(DiscoveryMediaType.MANGA).set("manual")
        first.discoverySourceKnown(DiscoveryMediaType.MANGA).set("ext.a,ext.b")
        first.discoverySourceExcluded(DiscoveryMediaType.MANGA).set("ext.removed")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        coordinateReads.set(true)
        try {
            val reconciliation = executor.submit<PluginKnownReconciliation> {
                start.await()
                reconcileDiscoverySourcePreferences(first, DiscoveryMediaType.MANGA, listOf("ext.a", "ext.b", "ext.c"))
            }
            val toggle = executor.submit<Boolean?> {
                start.await()
                toggleDiscoverySource(second, DiscoveryMediaType.MANGA, "ext.a", setOf("ext.a", "ext.b"))
            }
            start.countDown()
            reconciliation.get(10, TimeUnit.SECONDS)
            toggle.get(10, TimeUnit.SECONDS) shouldBe true
            coordinateReads.set(false)

            parseKeyCsv(first.discoverySourceExcluded(DiscoveryMediaType.MANGA).get()) shouldBe
                setOf("ext.removed", "ext.a", "ext.c")
            parseKeyCsv(second.discoverySourceKnown(DiscoveryMediaType.MANGA).get()) shouldBe
                setOf("ext.a", "ext.b", "ext.c")
        } finally {
            coordinateReads.set(false)
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `undo manga exclusion retains later reconciliation and other consumer choices`() {
        val backing = PersistingPreferenceStore()
        val first = DiscoveryPreferences(backing)
        val second = DiscoveryPreferences(backing)
        first.discoverySourceMode(DiscoveryMediaType.MANGA).set("manual")
        first.discoverySourceKnown(DiscoveryMediaType.MANGA).set("ext.a,ext.b,ext.d")
        first.discoverySourceExcluded(DiscoveryMediaType.MANGA).set("ext.removed")

        toggleDiscoverySource(first, DiscoveryMediaType.MANGA, "ext.a", setOf("ext.a", "ext.b", "ext.d")) shouldBe true
        reconcileDiscoverySourcePreferences(
            second,
            DiscoveryMediaType.MANGA,
            listOf("ext.a", "ext.b", "ext.c", "ext.d"),
        )
        toggleDiscoverySource(
            second,
            DiscoveryMediaType.MANGA,
            "ext.d",
            setOf("ext.a", "ext.b", "ext.c", "ext.d"),
        ) shouldBe true

        undoDiscoverySourceExclusion(first, DiscoveryMediaType.MANGA, "ext.a")

        parseKeyCsv(first.discoverySourceExcluded(DiscoveryMediaType.MANGA).get()) shouldBe
            setOf("ext.removed", "ext.c", "ext.d")
        parseKeyCsv(second.discoverySourceKnown(DiscoveryMediaType.MANGA).get()) shouldBe
            setOf("ext.a", "ext.b", "ext.c", "ext.d")
    }

    @Test
    fun `select all manga sources clears displayed keys without clearing newly discovered or removed keys`() {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.discoverySourceMode(DiscoveryMediaType.MANGA).set("manual")
        preferences.discoverySourceKnown(DiscoveryMediaType.MANGA).set("ext.a,ext.b")
        preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).set("ext.removed,ext.b,ext.d")
        reconcileDiscoverySourcePreferences(preferences, DiscoveryMediaType.MANGA, listOf("ext.a", "ext.b", "ext.c"))

        selectAllDiscoverySources(preferences, DiscoveryMediaType.MANGA, listOf("ext.a", "ext.b"))

        parseKeyCsv(preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).get()) shouldBe
            setOf("ext.removed", "ext.c", "ext.d")
        parseKeyCsv(preferences.discoverySourceKnown(DiscoveryMediaType.MANGA).get()) shouldBe
            setOf("ext.a", "ext.b", "ext.c")

        selectAllDiscoverySources(preferences, DiscoveryMediaType.MANGA, listOf("ext.a", "ext.b", "ext.c"))
        parseKeyCsv(preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).get()) shouldBe
            setOf("ext.removed", "ext.d")
    }

    @Test
    fun `manual manga mode snapshots installed keys and guards the last source through persisted mutations`() {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).set("ext.removed")
        val keys = listOf("ext.a", "ext.b", "ext.c")

        setDiscoverySourceMode(preferences, DiscoveryMediaType.MANGA, "manual", keys) shouldBe true
        setDiscoverySourceMode(preferences, DiscoveryMediaType.MANGA, "manual", keys) shouldBe false
        parseKeyCsv(preferences.discoverySourceKnown(DiscoveryMediaType.MANGA).get()) shouldBe
            setOf("ext.a", "ext.b", "ext.c")
        toggleDiscoverySource(preferences, DiscoveryMediaType.MANGA, "ext.a", keys.toSet()) shouldBe true
        toggleDiscoverySource(preferences, DiscoveryMediaType.MANGA, "ext.b", keys.toSet()) shouldBe true
        toggleDiscoverySource(preferences, DiscoveryMediaType.MANGA, "ext.c", keys.toSet()) shouldBe null
        parseKeyCsv(preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).get()) shouldBe
            setOf("ext.removed", "ext.a", "ext.b")

        undoDiscoverySourceExclusion(preferences, DiscoveryMediaType.MANGA, "ext.a")
        deselectAllDiscoverySources(preferences, DiscoveryMediaType.MANGA, keys) shouldBe "ext.a"
        parseKeyCsv(preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).get()) shouldBe
            setOf("ext.removed", "ext.b", "ext.c")
        parseKeyCsv(preferences.discoverySourceKnown(DiscoveryMediaType.MANGA).get()) shouldBe
            setOf("ext.a", "ext.b", "ext.c")
    }
}
