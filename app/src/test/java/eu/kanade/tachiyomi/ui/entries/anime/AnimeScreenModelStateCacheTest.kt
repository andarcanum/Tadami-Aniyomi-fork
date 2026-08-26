package eu.kanade.tachiyomi.ui.entries.anime

import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel.Dialog
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel.State
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.model.StubAnimeSource

class AnimeScreenModelStateCacheTest {

    @BeforeEach
    fun setUp() {
        AnimeScreenModel.clearStateCacheForTest()
    }

    @Test
    fun `cacheState and restoreStateFromCache returns success state without dialogs or selection`() {
        val anime = Anime.create().copy(id = 100L, title = "Test Anime", url = "/anime/100", source = 1L)
        val episode = Episode.create().copy(id = 200L, animeId = 100L, name = "Ep 1", url = "/ep/1")
        val episodeItem = EpisodeList.Item(
            episode = episode,
            downloadState = AnimeDownload.State.NOT_DOWNLOADED,
            downloadProgress = 0,
            selected = true,
        )
        val source = StubAnimeSource(1L, "Source", "en")

        val state = State.Success(
            anime = anime,
            source = source,
            isFromSource = false,
            episodes = listOf(episodeItem),
            seasons = emptyList(),
            dialog = Dialog.DeleteEpisodes(listOf(episode)),
        )

        AnimeScreenModel.cacheStateForTest(state)

        val restored = AnimeScreenModel.restoreStateFromCacheForTest(100L)
        restored.shouldNotBeNull()
        restored.anime.id shouldBe 100L
        restored.episodes.size shouldBe 1
        restored.episodes.first().selected shouldBe false
        restored.dialog.shouldBeNull()
    }

    @Test
    fun `restoreStateFromCache returns null for uncached anime id`() {
        AnimeScreenModel.restoreStateFromCacheForTest(999L).shouldBeNull()
    }
}
