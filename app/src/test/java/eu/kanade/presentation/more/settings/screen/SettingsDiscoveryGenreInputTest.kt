package eu.kanade.presentation.more.settings.screen

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.CandidateVerdict
import eu.kanade.tachiyomi.data.discovery.DiscoveryCandidatePolicy
import eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem
import eu.kanade.tachiyomi.data.discovery.GenreMatcher
import eu.kanade.tachiyomi.data.discovery.resolveUserGenreInput
import eu.kanade.tachiyomi.data.discovery.serializeGenreFilterCsv
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class SettingsDiscoveryGenreInputTest {
    @Test
    fun `literal raw prefix custom input keeps its preexisting meaning through the codec`() {
        val literal = "raw:Action"
        resolveUserGenreInput(literal) shouldBe (literal to false)
        GenreMatcher.identityKey(literal) shouldBe "raw action"
        val csv = serializeGenreFilterCsv(emptySet(), listOf(literal))
        csv shouldBe "raw:raw:Action"
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.requiredGenres(DiscoveryMediaType.NOVEL).set(csv)
        val policy = DiscoveryCandidatePolicy(preferences.genreFilterSpec(DiscoveryMediaType.NOVEL))
        (policy.evaluate(listOf(literal)) is CandidateVerdict.Admitted) shouldBe true
        (policy.evaluate(listOf("Action")) is CandidateVerdict.Rejected) shouldBe true
        preferences.requiredGenres(DiscoveryMediaType.NOVEL).get() shouldBe csv
    }

    @Test
    fun `moving canonical Action does not erase a restored literal prefixed custom tag`() {
        val canonical = List(3) { mutableSetOf<String>() }
        val raw = listOf(mutableListOf("raw:Action"), mutableListOf<String>(), mutableListOf<String>())
        moveDiscoveryGenreSelection("action", true, 2, canonical, raw)
        raw[0] shouldBe listOf("raw:Action")
        canonical[2] shouldBe setOf("action")
    }

    @Test
    fun `inert emoji and overlong raw input cannot create active constraints`() {
        resolveUserGenreInput("🔥") shouldBe ("" to false)
        resolveUserGenreInput("a".repeat(65)) shouldBe ("" to false)
    }

    @Test
    fun `a valid unknown tag at the matcher length boundary retains its observed spelling`() {
        val tag = "a".repeat(64)
        resolveUserGenreInput(tag) shouldBe (tag to false)
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.requiredGenres(DiscoveryMediaType.NOVEL).set(savedCsv(tag))
        (
            DiscoveryCandidatePolicy(
                preferences.genreFilterSpec(DiscoveryMediaType.NOVEL),
            ).evaluate(listOf(tag)) is CandidateVerdict.Admitted
            ) shouldBe
            true
    }

    @Test
    fun `saved ambiguous required genre keeps System and LitRPG titles eligible`() {
        val media = DiscoveryMediaType.NOVEL
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        val (genre, isCanonical) = resolveUserGenreInput("Game System")
        val csv = serializeGenreFilterCsv(
            canonical = if (isCanonical) setOf(genre) else emptySet(),
            raw = if (isCanonical) emptyList() else listOf(genre),
        )
        preferences.requiredGenres(media).set(csv)

        val system = DiscoveryRowItem(
            title = "System story",
            cleanTitle = "system story",
            coverUrl = null,
            reason = null,
            seedTitle = null,
            provider = "fixture",
            score = 1.0,
            genres = listOf("System"),
        )
        val litrpg = system.copy(
            title = "LitRPG story",
            cleanTitle = "litrpg story",
            genres = listOf("LitRPG"),
        )

        val visible = DiscoveryCandidatePolicy(preferences.genreFilterSpec(media))
            .filterRowCandidates(listOf(system, litrpg), { it.genres })

        visible.map { it.title } shouldBe listOf("System story", "LitRPG story")
    }

    @Test
    fun `saved ambiguous ignored genre vetoes either supported meaning`() {
        val media = DiscoveryMediaType.NOVEL
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.ignoredGenres(media).set(savedCsv("Game System"))

        val visible = DiscoveryCandidatePolicy(preferences.genreFilterSpec(media))
            .filterRowCandidates(
                listOf(
                    genreItem("System story", "System"),
                    genreItem("LitRPG story", "LitRPG"),
                    genreItem("Comedy story", "Comedy"),
                ),
                { it.genres },
            )

        visible.map { it.title } shouldBe listOf("Comedy story")
    }

    @Test
    fun `saved unambiguous required genre still filters ordinary titles`() {
        val media = DiscoveryMediaType.MANGA
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.requiredGenres(media).set(savedCsv("  Экшен  "))

        val visible = DiscoveryCandidatePolicy(preferences.genreFilterSpec(media))
            .filterRowCandidates(
                listOf(genreItem("Action story", "Action"), genreItem("Drama story", "Drama")),
                { it.genres },
            )

        visible.map { it.title } shouldBe listOf("Action story")
    }

    @Test
    fun `saved unknown required tag still matches its observed spelling`() {
        val media = DiscoveryMediaType.NOVEL
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.requiredGenres(media).set(savedCsv("Novel Custom Tag"))

        val visible = DiscoveryCandidatePolicy(preferences.genreFilterSpec(media))
            .filterRowCandidates(
                listOf(
                    genreItem("Custom story", "Novel Custom Tag"),
                    genreItem("Drama story", "Drama"),
                ),
                { it.genres },
            )

        visible.map { it.title } shouldBe listOf("Custom story")
    }

    private fun savedCsv(input: String): String {
        val (genre, isCanonical) = resolveUserGenreInput(input)
        return serializeGenreFilterCsv(
            canonical = if (isCanonical) setOf(genre) else emptySet(),
            raw = if (isCanonical) emptyList() else listOf(genre),
        )
    }

    private fun genreItem(title: String, genre: String) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "fixture",
        score = 1.0,
        genres = listOf(genre),
    )
}
