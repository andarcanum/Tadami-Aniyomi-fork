package tachiyomi.domain.discovery.model

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DiscoveryBatchSelectionTest {

    private val media = DiscoveryMediaType.MANGA
    private val noExclusions = DiscoveryEligibilitySnapshot(mediaType = media, capturedAt = 0L)

    private fun suggestion(
        number: Int,
        key: String = "key:$number",
        aliases: Set<String> = emptySet(),
        mediaType: DiscoveryMediaType = media,
        cleanTitle: String = "same title",
    ): DiscoverySuggestion = DiscoverySuggestion(
        id = number.toLong(),
        mediaType = mediaType,
        rowType = DiscoveryRowType.SOURCE,
        title = "Title $number",
        cleanTitle = cleanTitle,
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "source",
        score = 1.0,
        position = number.toLong(),
        createdAt = 0L,
        identityKey = key,
        trustedAliases = aliases,
    )

    private fun List<DiscoverySuggestion>.keys(): Set<String> = mapNotNull { it.identityKey }.toSet()

    @Test
    fun `consecutive batches never repeat titles`() {
        val ranked = (1..60).map(::suggestion)

        val first = selectNextDiscoveryBatch(ranked, emptySet(), emptySet(), noExclusions)
        val second = selectNextDiscoveryBatch(ranked, first.keys(), first.keys(), noExclusions)
        val third = selectNextDiscoveryBatch(ranked, second.keys(), first.keys() + second.keys(), noExclusions)

        first.keys() shouldBe (1..20).map { "key:$it" }.toSet()
        second.keys() shouldBe (21..40).map { "key:$it" }.toSet()
        third.keys() shouldBe (41..60).map { "key:$it" }.toSet()
    }

    @Test
    fun `leftover smaller than limit is returned without stale backfill`() {
        val ranked = (1..27).map(::suggestion)
        val first = selectNextDiscoveryBatch(ranked, emptySet(), emptySet(), noExclusions)
        val second = selectNextDiscoveryBatch(ranked, first.keys(), first.keys(), noExclusions)

        second.map { it.identityKey } shouldBe (21..27).map { "key:$it" }
    }

    @Test
    fun `alias of an already issued title is not selected again`() {
        val first = suggestion(1)
        val aliased = suggestion(2, key = "key:2", aliases = setOf("key:1"))

        val next = selectNextDiscoveryBatch(
            ranked = listOf(aliased, suggestion(3)),
            current = setOf("key:1"),
            issued = setOf("key:1"),
            exclusions = noExclusions,
        )

        next.map { it.identityKey } shouldBe listOf("key:3")
    }

    @Test
    fun `homonyms with different bindings stay distinct`() {
        val original = suggestion(1, key = "source:1:url:/a")
        val reprint = suggestion(2, key = "source:2:url:/b")

        val next =
            selectNextDiscoveryBatch(
                listOf(reprint),
                setOf(original.identityKey!!),
                setOf(original.identityKey!!),
                noExclusions,
            )

        next.map { it.identityKey } shouldBe listOf("source:2:url:/b")
    }

    @Test
    fun `eligibility exclusions are applied before the limit`() {
        val excluded = suggestion(1, key = "opened:1")
        val forbiddenLookup = suggestion(2, key = "key:2")
        val allowed = suggestion(3)
        val exclusions = noExclusions.copy(
            excludedIdentityKeys = setOf("opened:1"),
            excludedLookupKeys = setOf("legacy-title"),
        )

        val next = selectNextDiscoveryBatch(
            ranked = listOf(excluded, suggestion(2, key = "key:2", aliases = setOf("legacy-title")), allowed),
            current = emptySet(),
            issued = emptySet(),
            exclusions = exclusions,
        )

        next.map { it.identityKey } shouldBe listOf("key:3")
        forbiddenLookup.identityKey shouldBe "key:2"
    }

    @Test
    fun `candidates of another media type are ignored`() {
        val anime = suggestion(1, key = "anime:1", mediaType = DiscoveryMediaType.ANIME)
        val manga = suggestion(2)

        val next = selectNextDiscoveryBatch(listOf(anime, manga), emptySet(), emptySet(), noExclusions)

        next.map { it.identityKey } shouldBe listOf("key:2")
    }

    @Test
    fun `non positive limit returns empty batch`() {
        selectNextDiscoveryBatch(listOf(suggestion(1)), emptySet(), emptySet(), noExclusions, limit = 0)
            .shouldBeEmpty()
    }
}
