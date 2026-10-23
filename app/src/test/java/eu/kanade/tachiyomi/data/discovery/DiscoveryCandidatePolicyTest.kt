package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class DiscoveryCandidatePolicyTest {

    private fun testSuggestion(
        title: String,
        genres: List<String> = emptyList(),
        rowType: DiscoveryRowType = DiscoveryRowType.TASTE,
        reason: String? = null,
    ) = DiscoverySuggestion(
        id = 1L,
        mediaType = DiscoveryMediaType.MANGA,
        rowType = rowType,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = reason,
        seedTitle = null,
        provider = "test",
        score = 1.0,
        position = 0L,
        createdAt = 0L,
        sourceId = null,
        sourceUrl = null,
        genres = genres,
    )

    private fun testRowItem(
        title: String,
        genres: List<String> = emptyList(),
        reason: String? = null,
    ) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = reason,
        seedTitle = null,
        provider = "test",
        score = 1.0,
        genres = genres,
    )

    @Test
    fun `blacklisted tag strictly rejects candidate with matching genre`() {
        val policy = DiscoveryCandidatePolicy(
            blacklistedTags = setOf("экшен"),
        )

        // Matches English equivalent "Action"
        val verdict = policy.evaluate(listOf("Action", "Comedy"))
        verdict.shouldBeInstanceOf<CandidateVerdict.Rejected>()
        verdict.reason.shouldBeInstanceOf<RejectionReason.Blacklisted>()

        // Allowed if no match
        val allowedVerdict = policy.evaluate(listOf("Romance", "Drama"))
        allowedVerdict.shouldBeInstanceOf<CandidateVerdict.Admitted>()
    }

    @Test
    fun `globally ignored genre strictly rejects candidate with matching genre`() {
        val policy = DiscoveryCandidatePolicy(
            ignoredGenres = setOf("ecchi", "yaoi"),
        )

        val rejected = policy.evaluate(listOf("Ecchi", "Comedy"))
        rejected.shouldBeInstanceOf<CandidateVerdict.Rejected>()
        rejected.reason.shouldBeInstanceOf<RejectionReason.GloballyIgnored>()

        val allowed = policy.evaluate(listOf("Adventure", "Fantasy"))
        allowed.shouldBeInstanceOf<CandidateVerdict.Admitted>()
    }

    @Test
    fun `blacklist and global ignore are never relaxed, even in best-effort fallback`() {
        val policy = DiscoveryCandidatePolicy(
            blacklistedTags = setOf("Hentai"),
            ignoredGenres = setOf("Horror"),
            requiredGenres = setOf("Mecha"), // No item will match Mecha!
            strictRequired = false, // Best effort mode
        )

        val items = listOf(
            testRowItem("Good Fantasy", genres = listOf("Fantasy")),
            testRowItem("Bad Hentai", genres = listOf("Hentai")),
            testRowItem("Bad Horror", genres = listOf("Horror")),
        )

        // Best-effort required relaxes Mecha requirement, but Blacklist & Ignored MUST remain excluded!
        val filtered = policy.filterRowCandidates(items, { it.genres })
        filtered.map { it.title } shouldBe listOf("Good Fantasy")
    }

    @Test
    fun `priority genre grants score boost and never rejects candidate`() {
        val policy = DiscoveryCandidatePolicy(
            priorityGenres = setOf("Cyberpunk"),
        )

        val boostItem = policy.evaluate(listOf("Cyberpunk", "Action"))
        boostItem.shouldBeInstanceOf<CandidateVerdict.Admitted>()
        boostItem.hasPriorityBoost shouldBe true
        boostItem.scoreMultiplier shouldBe (1.5 plusOrMinus 0.001)

        val normalItem = policy.evaluate(listOf("Slice of Life"))
        normalItem.shouldBeInstanceOf<CandidateVerdict.Admitted>()
        normalItem.hasPriorityBoost shouldBe false
        normalItem.scoreMultiplier shouldBe (1.0 plusOrMinus 0.001)
    }

    @Test
    fun `required genre in best-effort mode selects matching items when available`() {
        val policy = DiscoveryCandidatePolicy(
            requiredGenres = setOf("Romance"),
            strictRequired = false,
        )

        val items = listOf(
            testRowItem("Romance Novel", genres = listOf("Romance")),
            testRowItem("Action Novel", genres = listOf("Action")),
        )

        val filtered = policy.filterRowCandidates(items, { it.genres })
        filtered.map { it.title } shouldBe listOf("Romance Novel")
    }

    @Test
    fun `required genre in best-effort mode relaxes when no candidates match, but still enforces blacklist`() {
        val policy = DiscoveryCandidatePolicy(
            blacklistedTags = setOf("Gore"),
            requiredGenres = setOf("Sci-Fi"), // Nobody has Sci-Fi
            strictRequired = false,
        )

        val items = listOf(
            testRowItem("Action Tale", genres = listOf("Action")),
            testRowItem("Comedy Tale", genres = listOf("Comedy")),
            testRowItem("Gore Tale", genres = listOf("Gore")),
        )

        val filtered = policy.filterRowCandidates(items, { it.genres })
        // Gore Tale is strictly excluded; Action Tale and Comedy Tale kept via best-effort relaxation
        filtered.map { it.title } shouldBe listOf("Action Tale", "Comedy Tale")
    }

    @Test
    fun `required genre in strict mode rejects candidates that do not match`() {
        val policy = DiscoveryCandidatePolicy(
            requiredGenres = setOf("Isekai"),
            strictRequired = true,
        )

        val items = listOf(
            testRowItem("Isekai Hero", genres = listOf("Isekai")),
            testRowItem("Real Life Hero", genres = listOf("Drama")),
        )

        val filtered = policy.filterRowCandidates(items, { it.genres })
        filtered.map { it.title } shouldBe listOf("Isekai Hero")

        // When none match, strict mode returns empty list
        val noneMatch = listOf(testRowItem("Real Life Hero", genres = listOf("Drama")))
        policy.filterRowCandidates(noneMatch, { it.genres }) shouldBe emptyList()
    }

    @Test
    fun `unknown genre policy ALLOW_AS_INCOMPLETE admits candidate with incomplete metadata flag`() {
        val policy = DiscoveryCandidatePolicy(
            unknownGenrePolicy = UnknownGenrePolicy.ALLOW_AS_INCOMPLETE,
        )

        val verdict = policy.evaluate(emptyList())
        verdict.shouldBeInstanceOf<CandidateVerdict.Admitted>()
        verdict.hasIncompleteMetadata shouldBe true
        verdict.matchesRequired shouldBe false
    }

    @Test
    fun `unknown genre policy DISALLOW strictly rejects candidate without genres`() {
        val policy = DiscoveryCandidatePolicy(
            unknownGenrePolicy = UnknownGenrePolicy.DISALLOW,
        )

        val verdict = policy.evaluate(emptyList())
        verdict.shouldBeInstanceOf<CandidateVerdict.Rejected>()
        verdict.reason.shouldBeInstanceOf<RejectionReason.UnknownGenresDisallowed>()
    }

    @Test
    fun `genre aliases and translations match identically`() {
        val policy = DiscoveryCandidatePolicy(
            blacklistedTags = setOf("фэнтези"),
        )

        // Russian Cyrillic matches English Latin "Fantasy"
        val engMatch = policy.evaluate(listOf("Fantasy"))
        engMatch.shouldBeInstanceOf<CandidateVerdict.Rejected>()

        // Russian Cyrillic matches Russian "фэнтези"
        val ruMatch = policy.evaluate(listOf("фэнтези"))
        ruMatch.shouldBeInstanceOf<CandidateVerdict.Rejected>()
    }

    @Test
    fun `fallback tags from TASTE reason are evaluated when genres list is empty`() {
        val policy = DiscoveryCandidatePolicy(
            blacklistedTags = setOf("Drama"),
        )

        // Candidate has empty genres, but reason contains "Drama, Romance"
        val verdict = policy.evaluate(
            genres = emptyList(),
            fallbackTags = DiscoveryCandidatePolicy.extractFallbackTags("Drama, Romance"),
        )
        verdict.shouldBeInstanceOf<CandidateVerdict.Rejected>()
        verdict.reason.shouldBeInstanceOf<RejectionReason.Blacklisted>()
    }

    @Test
    fun `isAllowed and isForbidden check suggestions correctly across row types`() {
        val policy = DiscoveryCandidatePolicy(
            blacklistedTags = setOf("Tragedy"),
            ignoredGenres = setOf("Ecchi"),
        )

        val goodItem = testSuggestion("Good Manga", genres = listOf("Adventure"))
        policy.isAllowed(goodItem) shouldBe true
        policy.isForbidden(goodItem) shouldBe false

        val blacklistedItem = testSuggestion("Sad Manga", genres = listOf("Tragedy"))
        policy.isAllowed(blacklistedItem) shouldBe false
        policy.isForbidden(blacklistedItem) shouldBe true

        val ignoredItem = testSuggestion("Spicy Manga", genres = listOf("Ecchi"))
        policy.isAllowed(ignoredItem) shouldBe false
        policy.isForbidden(ignoredItem) shouldBe true

        // TASTE fallback reason matching
        val tasteFallbackBad =
            testSuggestion(
                "Taste Sad",
                genres = emptyList(),
                rowType = DiscoveryRowType.TASTE,
                reason = "Tragedy, Romance",
            )
        policy.isAllowed(tasteFallbackBad) shouldBe false
        policy.isForbidden(tasteFallbackBad) shouldBe true

        // Non-TASTE reason (e.g. TREND season label "Tragedy" or "current") should not be misidentified if genres is empty
        val trendItem =
            testSuggestion("Trend Current", genres = emptyList(), rowType = DiscoveryRowType.TREND, reason = "Tragedy")
        policy.isAllowed(trendItem) shouldBe true
    }
}
