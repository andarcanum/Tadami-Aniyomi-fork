package eu.kanade.presentation.more.settings.screen.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.discovery.DiscoverySourceInventorySource
import eu.kanade.tachiyomi.data.discovery.DiscoverySourcePolicyResolver
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin

class DiscoverySourcesPolicyProjectionTest {

    @Test
    fun `automatic picker displays effective participants rather than raw library top three`() = runTest {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            sourceFailureHistory(DiscoveryMediaType.MANGA).set("33:1000:3")
        }
        val inventory = MutableStateFlow(
            DiscoverySourceInventory(
                complete = true,
                plugins = listOf(
                    DiscoverySourcePlugin("ext.a", listOf(11L), mapOf(11L to "en"), "1"),
                    DiscoverySourcePlugin("ext.b", listOf(22L), mapOf(22L to "en"), "1"),
                    DiscoverySourcePlugin("ext.c", listOf(33L), mapOf(33L to "en"), "1"),
                    DiscoverySourcePlugin("ext.d", listOf(44L), mapOf(44L to "en"), "1"),
                ),
            ),
        )
        val resolver = DiscoverySourcePolicyResolver(
            preferences = preferences,
            sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
            inventorySourceLoader = { DiscoverySourceInventorySource({ inventory.value }, inventory) },
            scope = backgroundScope,
            clock = { 1_000L },
        )
        runCurrent()
        resolver.updateRetrievalWeights(
            DiscoveryMediaType.MANGA,
            libraryWeights = mapOf(11L to 10, 22L to 9, 33L to 8, 44L to 0),
            sourceAffinity = emptyMap(),
            appLanguage = "en",
            capturedAt = 1_000L,
        )
        val policy = resolver.current(DiscoveryMediaType.MANGA)
        // C is penalized to 8 * .05 = .4; the unused D has a base weight of 1.
        policy.representativeSourceIds shouldBe listOf(11L, 22L, 44L)
        val rawLibraryOrder = listOf(
            entry(11L, "ext.a", 10),
            entry(22L, "ext.b", 9),
            entry(33L, "ext.c", 8),
            entry(44L, "ext.d", 0),
        )

        val displayed = projectDiscoverySourcePicks(rawLibraryOrder, policy).filter { it.isTop3Auto }

        displayed.map { it.pluginKey } shouldBe listOf("ext.a", "ext.b", "ext.d")
    }

    private fun entry(id: Long, key: String, weight: Int) = SourcePickUi(
        id = id,
        pluginKey = key,
        name = key,
        weight = weight,
        isLastUsed = false,
        isTop3Auto = false,
        excluded = false,
    )
}
