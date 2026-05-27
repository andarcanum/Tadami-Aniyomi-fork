package mihon.core.migration.migrations

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.domain.source.model.SourceType

class MoveLatestToFeedMigrationTest {

    @Test
    fun `mapLegacyFeedEntries produces LATEST listing type for resolved sources`() {
        val entries = mapLegacyFeedEntries(
            mangaFeedSources = setOf("42"),
            animeFeedSources = setOf("7"),
            novelFeedSources = setOf("3"),
            mangaResolve = { it == 42L },
            animeResolve = { it == 7L },
            novelResolve = { it == 3L },
        )

        entries.size shouldBe 3
        entries.all { it.listingType == FeedListingType.LATEST } shouldBe true
    }

    @Test
    fun `mapLegacyFeedEntries skips unresolved source IDs`() {
        val entries = mapLegacyFeedEntries(
            mangaFeedSources = setOf("42"),
            animeFeedSources = emptySet(),
            novelFeedSources = emptySet(),
            mangaResolve = { false },
            animeResolve = { true },
            novelResolve = { true },
        )

        entries.size shouldBe 0
    }

    @Test
    fun `mapLegacyFeedEntries assigns correct source types`() {
        val entries = mapLegacyFeedEntries(
            mangaFeedSources = setOf("1"),
            animeFeedSources = setOf("2"),
            novelFeedSources = setOf("3"),
            mangaResolve = { it == 1L },
            animeResolve = { it == 2L },
            novelResolve = { it == 3L },
        )

        entries.size shouldBe 3
        entries[0].sourceType shouldBe SourceType.MANGA
        entries[1].sourceType shouldBe SourceType.ANIME
        entries[2].sourceType shouldBe SourceType.NOVEL
    }

    @Test
    fun `mapLegacyFeedEntries ignores non-numeric source IDs`() {
        val entries = mapLegacyFeedEntries(
            mangaFeedSources = setOf("not_a_number", "42"),
            animeFeedSources = emptySet(),
            novelFeedSources = emptySet(),
            mangaResolve = { true },
            animeResolve = { false },
            novelResolve = { false },
        )

        entries.size shouldBe 1
        entries.single().source shouldBe 42L
    }
}
