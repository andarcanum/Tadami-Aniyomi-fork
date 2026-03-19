package eu.kanade.presentation.entries.anime

import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime

class AnimeScreenAuroraLayoutTest {

    @Test
    fun `two pane aurora layout is enabled only for tablet expanded`() {
        shouldUseAnimeAuroraTwoPane(AuroraDeviceClass.Phone) shouldBe false
        shouldUseAnimeAuroraTwoPane(AuroraDeviceClass.TabletCompact) shouldBe false
        shouldUseAnimeAuroraTwoPane(AuroraDeviceClass.TabletExpanded) shouldBe true
    }

    @Test
    fun `fast scroller uses pane scoped placement only in two pane anime layout`() {
        shouldUseAnimeAuroraPaneScopedFastScroller(useTwoPaneLayout = false) shouldBe false
        shouldUseAnimeAuroraPaneScopedFastScroller(useTwoPaneLayout = true) shouldBe true
    }

    @Test
    fun `metadata loading keeps plugin cover as aurora background fallback`() {
        val state = AnimeScreenModel.State.Success(
            anime = testAnime(thumbnailUrl = "https://example.org/plugin-cover.jpg"),
            source = object : AnimeSource {
                override val id: Long = 1L
                override val name: String = "Test source"
                override val lang: String = "en"

                override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

                override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime

                override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()

                override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
            },
            isFromSource = false,
            episodes = emptyList(),
            seasons = emptyList(),
            isMetadataLoading = true,
        )

        val resolved = resolveCoverUrl(state, useMetadataCovers = true)

        resolved.url shouldBe "https://example.org/plugin-cover.jpg"
        resolved.fallbackUrl.shouldBeNull()
    }
}

private fun testAnime(thumbnailUrl: String?) = Anime(
    id = 1L,
    source = 1L,
    favorite = false,
    lastUpdate = 0L,
    nextUpdate = 0L,
    fetchInterval = 0,
    dateAdded = 0L,
    viewerFlags = 0L,
    episodeFlags = 0L,
    coverLastModified = 0L,
    backgroundLastModified = 0L,
    url = "/anime/test",
    title = "Test anime",
    artist = null,
    author = null,
    description = null,
    genre = null,
    status = 0L,
    thumbnailUrl = thumbnailUrl,
    backgroundUrl = null,
    updateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
    initialized = true,
    lastModifiedAt = 0L,
    favoriteModifiedAt = null,
    version = 0L,
    fetchType = FetchType.Episodes,
    parentId = null,
    seasonFlags = 0L,
    seasonNumber = -1.0,
    seasonSourceOrder = 0L,
)
