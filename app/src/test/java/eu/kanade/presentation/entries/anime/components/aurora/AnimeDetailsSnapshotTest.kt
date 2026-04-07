package eu.kanade.presentation.entries.anime.components.aurora

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import eu.kanade.tachiyomi.animesource.model.SAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource

class AnimeDetailsSnapshotTest {

    @Test
    fun `source rating wins over metadata score`() {
        val anime = Anime.create().copy(
            title = "Black Clover",
            status = SAnime.ONGOING.toLong(),
        )
        val metadata = ExternalMetadata(
            contentType = MetadataContentType.ANIME,
            source = MetadataSource.SHIKIMORI,
            mediaId = 1L,
            remoteId = 42L,
            score = 7.2,
            format = "tv",
            status = "ongoing",
            coverUrl = "https://example.com/cover.jpg",
            coverUrlFallback = null,
            searchQuery = "Black Clover",
            updatedAt = 0L,
        )

        val snapshot = resolveAnimeDetailsSnapshot(
            anime = anime,
            watchedCount = 3,
            totalEpisodes = 12,
            sourceName = "AnimeGO",
            selectedDubbing = null,
            nextUpdate = null,
            sourceRating = 8.4f,
            animeMetadata = metadata,
            isMetadataLoading = true,
            metadataError = null,
        )

        snapshot.ratingValue shouldBe 8.4f
        snapshot.ratingText shouldBe "8.4"
    }

    @Test
    fun `metadata rating is used when source rating is unavailable`() {
        val anime = Anime.create().copy(
            title = "Black Clover",
            status = SAnime.ONGOING.toLong(),
        )
        val metadata = ExternalMetadata(
            contentType = MetadataContentType.ANIME,
            source = MetadataSource.SHIKIMORI,
            mediaId = 1L,
            remoteId = 42L,
            score = 7.2,
            format = "tv",
            status = "ongoing",
            coverUrl = "https://example.com/cover.jpg",
            coverUrlFallback = null,
            searchQuery = "Black Clover",
            updatedAt = 0L,
        )

        val snapshot = resolveAnimeDetailsSnapshot(
            anime = anime,
            watchedCount = 3,
            totalEpisodes = 12,
            sourceName = "AnimeGO",
            selectedDubbing = null,
            nextUpdate = null,
            sourceRating = null,
            animeMetadata = metadata,
            isMetadataLoading = false,
            metadataError = null,
        )

        snapshot.ratingValue shouldBe 7.2f
        snapshot.ratingText shouldBe "7.2"
    }

    @Test
    fun `rating falls back to nd when no source or metadata rating exists`() {
        val anime = Anime.create().copy(
            title = "Black Clover",
            status = SAnime.ONGOING.toLong(),
        )

        val snapshot = resolveAnimeDetailsSnapshot(
            anime = anime,
            watchedCount = 3,
            totalEpisodes = 12,
            sourceName = "AnimeGO",
            selectedDubbing = null,
            nextUpdate = null,
            sourceRating = null,
            animeMetadata = null,
            isMetadataLoading = false,
            metadataError = null,
        )

        snapshot.ratingValue.shouldBeNull()
        snapshot.ratingText shouldBe "N/D"
    }
}
