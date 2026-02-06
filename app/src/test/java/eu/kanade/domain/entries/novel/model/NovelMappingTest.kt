package eu.kanade.domain.entries.novel.model

import eu.kanade.tachiyomi.novelsource.model.SNovel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel

class NovelMappingTest {

    @Test
    fun `toDomainNovel maps fields from source`() {
        val sNovel = SNovel.create().apply {
            url = "/novel/1"
            title = "Example"
            author = "Author"
            description = "Desc"
            genre = "Drama, Fantasy"
            status = SNovel.ONGOING
            thumbnail_url = "https://example.com/cover.jpg"
            initialized = true
        }

        val novel = sNovel.toDomainNovel(sourceId = 42L)

        novel.url shouldBe "/novel/1"
        novel.title shouldBe "Example"
        novel.author shouldBe "Author"
        novel.description shouldBe "Desc"
        novel.genre shouldBe listOf("Drama", "Fantasy")
        novel.status shouldBe SNovel.ONGOING.toLong()
        novel.thumbnailUrl shouldBe "https://example.com/cover.jpg"
        novel.source shouldBe 42L
        novel.initialized shouldBe true
    }

    @Test
    fun `copyFrom updates mutable fields from source`() {
        val base = Novel.create().copy(
            title = "Base",
            author = "Old",
            description = "Old desc",
            genre = listOf("Old"),
            status = SNovel.UNKNOWN.toLong(),
            thumbnailUrl = "old",
            initialized = false,
        )

        val sNovel = SNovel.create().apply {
            title = "New"
            author = "New Author"
            description = "New desc"
            genre = "Drama"
            status = SNovel.COMPLETED
            thumbnail_url = "new"
            initialized = true
        }

        val updated = base.copyFrom(sNovel)

        updated.title shouldBe "Base"
        updated.author shouldBe "New Author"
        updated.description shouldBe "New desc"
        updated.genre shouldBe listOf("Drama")
        updated.status shouldBe SNovel.COMPLETED.toLong()
        updated.thumbnailUrl shouldBe "new"
        updated.initialized shouldBe false
    }
}
