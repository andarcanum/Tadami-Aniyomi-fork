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
        score: Double = 1.0,
    ) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = reason,
        seedTitle = null,
        provider = "test",
        score = score,
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
    fun `filterRowCandidates ranks items with priority genres ahead when base score is zero`() {
        val policy = DiscoveryCandidatePolicy(
            priorityGenres = setOf("Cyberpunk"),
        )

        // All items have score = 0.0 (like in TREND / SOURCE rows)
        val items = listOf(
            testRowItem("Normal Item 1", genres = listOf("Drama"), score = 0.0),
            testRowItem("Priority Item 1", genres = listOf("Cyberpunk"), score = 0.0),
            testRowItem("Normal Item 2", genres = listOf("Action"), score = 0.0),
            testRowItem("Priority Item 2", genres = listOf("Cyberpunk", "Sci-Fi"), score = 0.0),
        )

        val filtered = policy.filterRowCandidates(items, { it.genres })
        // Priority items sort ahead, preserving their relative order; followed by normal items preserving order
        filtered.map { it.title } shouldBe listOf(
            "Priority Item 1",
            "Priority Item 2",
            "Normal Item 1",
            "Normal Item 2",
        )
    }

    @Test
    fun `filterRowCandidates ranks items with priority genres ahead preserving base score order within groups`() {
        val policy = DiscoveryCandidatePolicy(
            priorityGenres = setOf("Romance"),
        )

        val items = listOf(
            testRowItem("Non-priority High Score", genres = listOf("Action"), score = 0.9),
            testRowItem("Priority Low Score", genres = listOf("Romance"), score = 0.2),
            testRowItem("Non-priority Low Score", genres = listOf("Comedy"), score = 0.1),
            testRowItem("Priority High Score", genres = listOf("Romance", "Drama"), score = 0.8),
        )

        val filtered = policy.filterRowCandidates(items, { it.genres })
        // Priority items are first (high score 0.8 then low score 0.2), then non-priority (0.9 then 0.1)
        filtered.map { it.title } shouldBe listOf(
            "Priority High Score",
            "Priority Low Score",
            "Non-priority High Score",
            "Non-priority Low Score",
        )
    }

    @Test
    fun `filterRowCandidates preserves original order when no priority genres defined and no scores provided`() {
        val policy = DiscoveryCandidatePolicy(
            priorityGenres = emptySet(),
        )

        val items = listOf(
            testRowItem("Item A", genres = listOf("Action"), score = 0.0),
            testRowItem("Item B", genres = listOf("Drama"), score = 0.0),
            testRowItem("Item C", genres = listOf("Comedy"), score = 0.0),
        )

        val filtered = policy.filterRowCandidates(items, { it.genres })
        filtered.map { it.title } shouldBe listOf("Item A", "Item B", "Item C")
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

        // Candidate with empty genres and explanation reason is not fabricated into blacklisted genre
        val tasteUnknownItem =
            testSuggestion(
                "Taste Sad",
                genres = emptyList(),
                rowType = DiscoveryRowType.TASTE,
                reason = "Tragedy, Romance",
            )
        policy.isAllowed(tasteUnknownItem) shouldBe true
        policy.isForbidden(tasteUnknownItem) shouldBe false

        // Non-TASTE reason (e.g. TREND season label "Tragedy" or "current") should not be misidentified if genres is empty
        val trendItem =
            testSuggestion("Trend Current", genres = emptyList(), rowType = DiscoveryRowType.TREND, reason = "Tragedy")
        policy.isAllowed(trendItem) shouldBe true
        policy.isForbidden(trendItem) shouldBe false
    }

    // ═══════════ G01: Режимы сопоставления ANY vs ALL и строгость STRICT ═══════════

    @Test
    fun `requiredMatchMode ALL requires every required genre`() {
        val spec = GenreFilterSpec(
            requiredGenres = setOf("Action", "Comedy"),
            requiredMatchMode = RequiredMatchMode.ALL,
            requiredEnforcement = RequiredEnforcement.STRICT,
        )
        val policy = DiscoveryCandidatePolicy(spec)

        val bothItem = testSuggestion("Action Comedy", genres = listOf("Action", "Comedy"))
        val actionOnlyItem = testSuggestion("Just Action", genres = listOf("Action", "Drama"))
        val noneItem = testSuggestion("Just Drama", genres = listOf("Drama"))

        policy.isAllowed(bothItem) shouldBe true
        policy.isAllowed(actionOnlyItem) shouldBe false
        policy.isAllowed(noneItem) shouldBe false
    }

    @Test
    fun `requiredMatchMode ANY requires at least one required genre`() {
        val spec = GenreFilterSpec(
            requiredGenres = setOf("Action", "Comedy"),
            requiredMatchMode = RequiredMatchMode.ANY,
            requiredEnforcement = RequiredEnforcement.STRICT,
        )
        val policy = DiscoveryCandidatePolicy(spec)

        val actionOnlyItem = testSuggestion("Just Action", genres = listOf("Action", "Drama"))
        val comedyOnlyItem = testSuggestion("Just Comedy", genres = listOf("Comedy", "Slice of Life"))
        val noneItem = testSuggestion("Just Drama", genres = listOf("Drama"))

        policy.isAllowed(actionOnlyItem) shouldBe true
        policy.isAllowed(comedyOnlyItem) shouldBe true
        policy.isAllowed(noneItem) shouldBe false
    }

    @Test
    fun `strict required rejects items with unknown genres even if incomplete is normally allowed`() {
        val spec = GenreFilterSpec(
            requiredGenres = setOf("Action"),
            requiredMatchMode = RequiredMatchMode.ALL,
            requiredEnforcement = RequiredEnforcement.STRICT,
            unknownGenrePolicy = UnknownGenrePolicy.ALLOW_AS_INCOMPLETE,
        )
        val policy = DiscoveryCandidatePolicy(spec)

        val unknownItem = testSuggestion("Mystery Novel", genres = emptyList())
        policy.isAllowed(unknownItem) shouldBe false
        val verdict = policy.evaluate(emptyList())
        verdict.shouldBeInstanceOf<CandidateVerdict.Rejected>()
        verdict.reason.shouldBeInstanceOf<RejectionReason.MissingRequiredGenre>()
    }

    @Test
    fun `strict required filterRowCandidates returns emptyList when no candidates match and never relaxes`() {
        val spec = GenreFilterSpec(
            requiredGenres = setOf("Horror"),
            requiredMatchMode = RequiredMatchMode.ALL,
            requiredEnforcement = RequiredEnforcement.STRICT,
        )
        val policy = DiscoveryCandidatePolicy(spec)

        val items = listOf(
            testRowItem("Happy Romance", genres = listOf("Romance")),
            testRowItem("Exciting Action", genres = listOf("Action")),
        )

        policy.filterRowCandidates(items, { it.genres }) shouldBe emptyList()
    }
}
