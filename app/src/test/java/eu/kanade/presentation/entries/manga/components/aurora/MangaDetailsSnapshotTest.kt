package eu.kanade.presentation.entries.manga.components.aurora

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataContentType
import tachiyomi.domain.metadata.model.MetadataSource

class MangaDetailsSnapshotTest {

    @Test
    fun `progress snapshot keeps total chapters even without read progress`() {
        val chapters = listOf(
            Chapter.create().copy(id = 1L, mangaId = 1L, name = "Chapter 1"),
            Chapter.create().copy(id = 2L, mangaId = 1L, name = "Chapter 2"),
        )

        val snapshot = resolveMangaProgressSnapshot(chapters)

        snapshot.shouldNotBeNull()
        snapshot.currentChapterIndex shouldBe null
        snapshot.totalChapters shouldBe 2
        snapshot.hasProgress shouldBe false
        snapshot.progressText shouldBe "0 / 2"
        snapshot.chaptersText shouldBe "2"
    }

    @Test
    fun `translation text ignores scanlator names without locale`() {
        resolveMangaTranslationText(
            selectedScanlator = "Dynasty Scans",
            scanlatorChapterCounts = mapOf("Dynasty Scans" to 12),
            sourceLanguage = null,
        ).shouldBeNull()
    }

    @Test
    fun `translation text prefers source language when available`() {
        resolveMangaTranslationText(
            selectedScanlator = "Dynasty Scans",
            scanlatorChapterCounts = mapOf("Dynasty Scans" to 12),
            sourceLanguage = "en",
        ) shouldBe "English"
    }

    @Test
    fun `snapshot combines metadata and chapter progress`() {
        val manga = Manga.create().copy(
            title = "Colorist",
            rating = 0.86f,
            status = SManga.PUBLISHING_FINISHED.toLong(),
            description = "A test title",
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        )
        val chapters = listOf(
            Chapter.create().copy(id = 1L, mangaId = 1L, name = "Chapter 1", read = false, lastPageRead = 0),
            Chapter.create().copy(id = 2L, mangaId = 1L, name = "Chapter 2", read = true, lastPageRead = 0),
        )
        val metadata = ExternalMetadata(
            contentType = MetadataContentType.MANGA,
            source = MetadataSource.SHIKIMORI,
            mediaId = 1L,
            remoteId = 42L,
            score = 8.6,
            format = "manga",
            status = "ongoing",
            coverUrl = "https://example.com/cover.jpg",
            coverUrlFallback = null,
            searchQuery = "Colorist",
            updatedAt = 0L,
        )

        val snapshot = resolveMangaDetailsSnapshot(
            sourceTitle = "Shikimori",
            sourceName = "Shikimori",
            sourceLanguage = "en",
            manga = manga,
            chapters = chapters,
            selectedScanlator = "Dynasty Scans",
            scanlatorChapterCounts = mapOf("Dynasty Scans" to 2),
            mangaMetadata = metadata,
            isMetadataLoading = true,
            metadataError = null,
        )

        snapshot.sourceTitle shouldBe "Shikimori"
        snapshot.translationText shouldBe "English"
        snapshot.ratingValue shouldBe 0.86f
        snapshot.ratingText shouldBe "8.6"
        snapshot.formatText shouldBe "..."
        snapshot.statusText shouldBe "..."
        snapshot.progress.shouldNotBeNull().progressText shouldBe "2 / 2 (100%)"
        snapshot.progress?.chaptersText shouldBe "2"
        snapshot.isCompleted shouldBe true
    }

    @Test
    fun `rating falls back to external metadata when manga rating is unknown`() {
        val manga = Manga.create().copy(
            title = "Colorist",
            status = SManga.PUBLISHING_FINISHED.toLong(),
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        )
        val metadata = ExternalMetadata(
            contentType = MetadataContentType.MANGA,
            source = MetadataSource.SHIKIMORI,
            mediaId = 1L,
            remoteId = 42L,
            score = 8.6,
            format = "manga",
            status = "ongoing",
            coverUrl = "https://example.com/cover.jpg",
            coverUrlFallback = null,
            searchQuery = "Colorist",
            updatedAt = 0L,
        )

        val snapshot = resolveMangaDetailsSnapshot(
            sourceTitle = "Shikimori",
            sourceName = "Shikimori",
            sourceLanguage = "en",
            manga = manga,
            chapters = emptyList(),
            selectedScanlator = null,
            scanlatorChapterCounts = emptyMap(),
            mangaMetadata = metadata,
            isMetadataLoading = false,
            metadataError = null,
        )

        snapshot.ratingValue shouldBe 0.86f
        snapshot.ratingText shouldBe "8.6"
    }

    @Test
    fun `groupLe rating stays visible while metadata is loading`() {
        val manga = Manga.create().copy(
            title = "Почему у меня никого нет?",
            description = "***** 9.3[4.65] (votes: 23)\nОписание",
            status = SManga.PUBLISHING_FINISHED.toLong(),
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        )

        val snapshot = resolveMangaDetailsSnapshot(
            sourceTitle = "ReadManga",
            sourceName = "ReadManga",
            sourceLanguage = "ru",
            manga = manga,
            chapters = emptyList(),
            selectedScanlator = null,
            scanlatorChapterCounts = emptyMap(),
            mangaMetadata = null,
            isMetadataLoading = true,
            metadataError = null,
        )

        snapshot.ratingValue shouldBe 0.93f
        snapshot.ratingText shouldBe "9.3"
    }
}
