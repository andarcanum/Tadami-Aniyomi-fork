package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class DiscoveryDeferredSourceAdmissionTest {
    @Test
    fun `deferred metadata does not query a plugin excluded after its card was queued`() = runTest {
        val media = DiscoveryMediaType.MANGA
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoverySourceMode(media).set("manual")
            discoverySourceKnown(media).set("ext.native")
        }
        val inventory = MutableStateFlow(
            DiscoverySourceInventory(true, listOf(DiscoverySourcePlugin("ext.native", setOf(7L)))),
        )
        val sourcePolicy = DiscoverySourcePolicyResolver(
            preferences = preferences,
            sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
            inventorySourceLoader = { DiscoverySourceInventorySource({ inventory.value }, inventory) },
            scope = backgroundScope,
        )
        runCurrent()
        val candidate = DiscoverySuggestion(
            id = 0L, mediaType = media, rowType = DiscoveryRowType.SOURCE,
            title = "Queued metadata", cleanTitle = "queued metadata",
            coverUrl = null, reason = null, seedTitle = null, provider = "Native plugin",
            score = 1.0, position = 0L, createdAt = 1L,
            sourceId = 7L, sourceUrl = "/queued-metadata", genres = emptyList(),
        )
        sourcePolicy.current(media).allows(candidate) shouldBe true
        preferences.discoverySourceExcluded(media).set("ext.native")
        sourcePolicy.current(media).allows(candidate) shouldBe false
        val requests = mutableListOf<Pair<Long, String>>()

        TasteSignalRecorder.enrichLikeMetadata(
            repository = object : FakeDiscoveryRepositoryBase() {},
            item = candidate,
            sourceKey = "ext.native",
            expectedCreatedAt = 1L,
            sourceDetailsResolver = { _, id, url ->
                requests += id to url
                listOf("Action")
            },
            sourcePolicyProvider = sourcePolicy,
        )

        requests shouldBe emptyList()
    }
}
