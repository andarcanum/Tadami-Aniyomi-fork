package eu.kanade.presentation.more.settings.screen

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.CandidateVerdict
import eu.kanade.tachiyomi.data.discovery.DiscoveryCandidatePolicy
import eu.kanade.tachiyomi.data.discovery.serializeGenreFilterCsv
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class SettingsDiscoveryGenreDialogLogicTest {
    @Test
    fun `moving canonical Action removes equivalent restored raw veto before policy admission`() {
        val canonical = List(3) { mutableSetOf<String>() }
        val raw = listOf(mutableListOf("Action", "Экшен"), mutableListOf<String>(), mutableListOf<String>())
        moveDiscoveryGenreSelection("action", true, 2, canonical, raw)
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.requiredGenres(DiscoveryMediaType.NOVEL).set(serializeGenreFilterCsv(canonical[2], raw[2]))
        preferences.ignoredGenres(DiscoveryMediaType.NOVEL).set(serializeGenreFilterCsv(canonical[0], raw[0]))
        raw[0] shouldBe emptyList()
        (
            DiscoveryCandidatePolicy(
                preferences.genreFilterSpec(DiscoveryMediaType.NOVEL),
            ).evaluate(listOf("Action")) is CandidateVerdict.Admitted
            ) shouldBe
            true
    }

    @Test
    fun `moving raw Action also clears equivalent canonical selections in the other modes`() {
        val canonical = listOf(mutableSetOf("action"), mutableSetOf<String>(), mutableSetOf<String>())
        val raw = List(3) { mutableListOf<String>() }
        moveDiscoveryGenreSelection("Экшен", false, 2, canonical, raw)
        canonical[0] shouldBe emptySet()
        raw[2] shouldBe listOf("Экшен")
    }

    @Test
    fun `moving System does not erase the broader ambiguous Game System constraint`() {
        val canonical = List(3) { mutableSetOf<String>() }
        val raw = listOf(mutableListOf("Game System"), mutableListOf<String>(), mutableListOf<String>())
        moveDiscoveryGenreSelection("system", true, 2, canonical, raw)
        raw[0] shouldBe listOf("Game System")
        canonical[2] shouldBe setOf("system")
    }

    @Test
    fun `selected pills include both canonical and custom tags in full count`() {
        val selectedCanon = setOf("adventure", "action")
        val selectedRaw = listOf("LitRPG", "Boyar-anime", "RealRPG", "Cultivation", "System", "Academy")

        val pills = buildSelectedGenrePills(
            selectedCanonical = selectedCanon,
            selectedRaw = selectedRaw,
            labelForKey = { key ->
                when (key) {
                    "adventure" -> "Приключения"
                    "action" -> "Экшен"
                    else -> key
                }
            },
        )

        pills.size shouldBe 8
        pills.take(2) shouldBe listOf(
            SelectedGenrePill(keyOrRaw = "adventure", label = "Приключения", isCustom = false),
            SelectedGenrePill(keyOrRaw = "action", label = "Экшен", isCustom = false),
        )
        pills.drop(2).map { it.label } shouldBe selectedRaw
        pills.drop(2).all { it.isCustom } shouldBe true
    }

    @Test
    fun `tapping popular genre moves it out of popular list into selected pills`() {
        val popular = listOf("action", "adventure", "comedy", "drama", "fantasy")
        val selected = mutableSetOf("action")

        filterAvailablePopularGenres(popular, selected) { it } shouldBe listOf(
            "adventure",
            "comedy",
            "drama",
            "fantasy",
        )

        selected += "adventure"
        filterAvailablePopularGenres(popular, selected) { it } shouldBe listOf(
            "comedy",
            "drama",
            "fantasy",
        )
        buildSelectedGenrePills(selected, emptyList()) { it }.map { it.keyOrRaw } shouldBe listOf(
            "action",
            "adventure",
        )

        selected -= "adventure"
        filterAvailablePopularGenres(popular, selected) { it }.contains("adventure") shouldBe true
    }

    @Test
    fun `genre dialog media type persists across instances and falls back on invalid value`() {
        val store = PersistingPreferenceStore()
        val prefs = DiscoveryPreferences(store)

        resolveGenreDialogMediaType(prefs.lastGenreDialogMediaType().get()) shouldBe DiscoveryMediaType.ANIME

        prefs.lastGenreDialogMediaType().set(DiscoveryMediaType.MANGA.key)
        val reopenedPrefs = DiscoveryPreferences(store)
        resolveGenreDialogMediaType(reopenedPrefs.lastGenreDialogMediaType().get()) shouldBe DiscoveryMediaType.MANGA

        reopenedPrefs.lastGenreDialogMediaType().set(DiscoveryMediaType.NOVEL.key)
        resolveGenreDialogMediaType(reopenedPrefs.lastGenreDialogMediaType().get()) shouldBe DiscoveryMediaType.NOVEL

        resolveGenreDialogMediaType("unknown") shouldBe DiscoveryMediaType.ANIME
    }
}
