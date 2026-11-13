package eu.kanade.presentation.more.settings.screen

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class SettingsDiscoveryGenreDialogLogicTest {

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
