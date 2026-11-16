package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class DiscoveryConfigurationFingerprintTest {
    @Test
    fun `settings-session comparison includes every similar provider flag used for novel generation`() {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        val sources = SourcePreferences(PersistingPreferenceStore())
        val before = eu.kanade.presentation.more.settings.screen.computeDiscoverySessionFingerprint(
            preferences,
            sources,
            DiscoveryMediaType.NOVEL,
        )
        sources.suggestionsUseMangaUpdatesNovel().set(false)
        val after = eu.kanade.presentation.more.settings.screen.computeDiscoverySessionFingerprint(
            preferences,
            sources,
            DiscoveryMediaType.NOVEL,
        )
        after shouldNotBe before
    }

    @Test
    fun `disabling a similar works provider changes the generation fingerprint`() {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        val sources = SourcePreferences(PersistingPreferenceStore())
        val switches = listOf(
            sources.suggestionsUseShikimori(),
            sources.suggestionsUseMangaUpdatesNovel(),
            sources.suggestionsUseNovelUpdates(),
        )
        switches.forEach { toggle ->
            toggle.set(true)
            val before = computeDiscoveryConfigurationKey(
                preferences,
                DiscoveryMediaType.NOVEL,
                sources,
                "complete-topology",
            )
            toggle.set(false)
            val after = computeDiscoveryConfigurationKey(
                preferences,
                DiscoveryMediaType.NOVEL,
                sources,
                "complete-topology",
            )

            after shouldNotBe before
            toggle.set(true)
        }
    }

    @Test
    fun `changing the authorized source topology changes the generation fingerprint`() {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        val sources = SourcePreferences(PersistingPreferenceStore())
        val before = computeDiscoveryConfigurationKey(preferences, DiscoveryMediaType.MANGA, sources, "plugin-a:7")
        val after = computeDiscoveryConfigurationKey(preferences, DiscoveryMediaType.MANGA, sources, "plugin-a:8")

        after shouldNotBe before
    }
}
