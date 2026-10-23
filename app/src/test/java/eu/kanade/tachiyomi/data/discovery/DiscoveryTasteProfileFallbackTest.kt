package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DiscoveryTasteProfileFallbackTest {
    @Test
    fun `excluded library profile falls back to explicit allowed interests`() {
        val policy = DiscoveryCandidatePolicy(
            ignoredGenres = setOf("Action"),
            requiredGenres = setOf("Romance"),
            priorityGenres = setOf("Fantasy"),
        )
        resolveDiscoveryTasteProfile(listOf("Action" to 5.0), policy) shouldBe
            listOf("Romance" to 1.0, "Fantasy" to 1.0)
    }

    @Test
    fun `fallback does not weaken excluded genres or duplicate interests`() {
        val policy = DiscoveryCandidatePolicy(
            ignoredGenres = setOf("Action"),
            requiredGenres = setOf("Action", "Romance"),
            priorityGenres = setOf("Romance"),
        )
        resolveDiscoveryTasteProfile(emptyList(), policy) shouldBe listOf("Romance" to 1.0)
    }

    @Test
    fun `an allowed library profile retains its weights and ordering`() {
        val policy = DiscoveryCandidatePolicy(ignoredGenres = setOf("Action"), priorityGenres = setOf("Fantasy"))
        resolveDiscoveryTasteProfile(listOf("Action" to 5.0, "Drama" to 2.0), policy) shouldBe
            listOf("Drama" to 2.0)
    }
}
