package eu.kanade.presentation.entries.components

import eu.kanade.domain.metadata.model.MetadataLoadError
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource

class ExternalMetadataUiTest {

    @Test
    fun `resolveExternalMetadataCover keeps metadata fallback after metadata error`() {
        val metadata = ExternalMetadata(
            contentType = MetadataContentType.MANGA,
            source = MetadataSource.SHIKIMORI,
            mediaId = 1L,
            remoteId = 2L,
            score = 8.4,
            format = "manga",
            status = "ongoing",
            coverUrl = "https://example.org/metadata-cover.jpg",
            coverUrlFallback = null,
            searchQuery = "Test title",
            updatedAt = 0L,
        )

        val resolved = resolveExternalMetadataCover(
            baseCoverUrl = "https://example.org/source-cover.jpg",
            metadata = metadata,
            isMetadataLoading = false,
            metadataError = MetadataLoadError.NotAuthenticated,
            useMetadataCovers = true,
        )

        resolved.coverUrl shouldBe "https://example.org/metadata-cover.jpg"
        resolved.coverUrlFallback shouldBe "https://example.org/source-cover.jpg"
    }

    @Test
    fun `resolveExternalMetadataCover keeps source cover while metadata is loading`() {
        val resolved = resolveExternalMetadataCover(
            baseCoverUrl = "https://example.org/source-cover.jpg",
            metadata = null,
            isMetadataLoading = true,
            metadataError = null,
            useMetadataCovers = true,
        )

        resolved.coverUrl shouldBe "https://example.org/source-cover.jpg"
        resolved.coverUrlFallback shouldBe null
    }
}
