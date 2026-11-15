package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
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

class DiscoverySourcePreferencesConcurrencyTest {

    @Test
    fun `concurrent manga plugin reconciliation preserves both exclusions and known snapshots`() {
        val backing = PersistingPreferenceStore()
        val readBarrier = CyclicBarrier(2)
        val coordinateReads = AtomicBoolean(false)
        val store = object : PreferenceStore by backing {
            override fun getString(key: String, defaultValue: String): Preference<String> {
                val preference = synchronized(backing) { backing.getString(key, defaultValue) }
                if (key != "discovery_source_known_manga") return preference
                return object : Preference<String> by preference {
                    override fun get(): String {
                        val value = preference.get()
                        if (coordinateReads.get()) {
                            // Two unsynchronized consumers capture the same old snapshot.
                            // A shared mutation boundary admits one reader, whose bounded
                            // wait expires before the second consumer can read its new state.
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
            val snapshots = listOf(
                first to listOf("ext.a", "ext.b", "ext.c"),
                second to listOf("ext.a", "ext.b", "ext.d"),
            )
            val reconciliations = snapshots.map { (preferences, installedKeys) ->
                executor.submit<PluginKnownReconciliation> {
                    start.await()
                    reconcileDiscoverySourcePreferences(
                        preferences = preferences,
                        mediaType = DiscoveryMediaType.MANGA,
                        installedKeys = installedKeys,
                    )
                }
            }
            start.countDown()
            reconciliations.forEach { it.get(10, TimeUnit.SECONDS) }
            coordinateReads.set(false)

            parseKeyCsv(first.discoverySourceExcluded(DiscoveryMediaType.MANGA).get()) shouldBe
                setOf("ext.removed", "ext.c", "ext.d")
            parseKeyCsv(second.discoverySourceKnown(DiscoveryMediaType.MANGA).get()) shouldBe
                setOf("ext.a", "ext.b", "ext.c", "ext.d")
        } finally {
            coordinateReads.set(false)
            start.countDown()
            executor.shutdownNow()
        }
    }
}
