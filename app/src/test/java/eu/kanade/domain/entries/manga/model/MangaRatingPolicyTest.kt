package eu.kanade.domain.entries.manga.model

import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga

class MangaRatingPolicyTest {

    @Test
    fun `toDomainManga keeps unknown rating sentinel`() {
        val remote = remoteManga(rating = -1f)

        remote.toDomainManga(sourceId = 1L).rating shouldBe -1f
    }

    @Test
    fun `toSManga keeps unknown rating sentinel`() {
        val local = Manga.create().copy(
            id = 1L,
            source = 1L,
            url = "/manga",
            title = "Title",
            rating = -1f,
        )

        local.toSManga().rating shouldBe -1f
    }

    @Test
    fun `copyFrom keeps the best known rating between local and remote`() {
        val local = Manga.create().copy(
            id = 1L,
            source = 1L,
            url = "/manga",
            title = "Title",
            rating = 0.82f,
        )

        val remote = remoteManga(rating = 7.5f)

        local.copyFrom(remote).rating shouldBe 0.82f
    }

    @Test
    fun `copyFrom takes remote rating when local is unknown`() {
        val local = Manga.create().copy(
            id = 1L,
            source = 1L,
            url = "/manga",
            title = "Title",
            rating = -1f,
        )

        val remote = remoteManga(rating = 8.6f)

        local.copyFrom(remote).rating shouldBe 0.86f
    }

    private fun remoteManga(rating: Float): SManga {
        return SManga.create().apply {
            url = "/manga"
            title = "Title"
            this.rating = rating
        }
    }
}
