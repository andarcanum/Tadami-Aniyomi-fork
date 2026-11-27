package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType

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

    @Test
    fun `empty profile with NOVEL mediaType falls back to default novel starter genres`() {
        val policy = DiscoveryCandidatePolicy()
        resolveDiscoveryTasteProfile(emptyList(), policy, DiscoveryMediaType.NOVEL) shouldBe
            listOf("Fantasy" to 1.0, "Action" to 1.0, "Adventure" to 1.0, "Mystery" to 1.0, "Sci-Fi" to 1.0)
    }

    @Test
    fun `empty profile with MANGA mediaType falls back to default manga starter genres`() {
        val policy = DiscoveryCandidatePolicy()
        resolveDiscoveryTasteProfile(emptyList(), policy, DiscoveryMediaType.MANGA) shouldBe
            listOf("Action" to 1.0, "Adventure" to 1.0, "Fantasy" to 1.0, "Comedy" to 1.0, "Drama" to 1.0)
    }

    @Test
    fun `empty profile with ANIME mediaType falls back to default anime starter genres`() {
        val policy = DiscoveryCandidatePolicy()
        resolveDiscoveryTasteProfile(emptyList(), policy, DiscoveryMediaType.ANIME) shouldBe
            listOf("Action" to 1.0, "Comedy" to 1.0, "Fantasy" to 1.0, "Romance" to 1.0, "Sci-Fi" to 1.0)
    }

    @Test
    fun `starter genres respect candidatePolicy isForbidden filter`() {
        val policy = DiscoveryCandidatePolicy(ignoredGenres = setOf("Action", "Romance"))
        resolveDiscoveryTasteProfile(emptyList(), policy, DiscoveryMediaType.ANIME) shouldBe
            listOf("Comedy" to 1.0, "Fantasy" to 1.0, "Sci-Fi" to 1.0)
    }
}
