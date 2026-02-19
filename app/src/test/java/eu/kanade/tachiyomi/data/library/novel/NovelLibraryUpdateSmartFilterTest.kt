package eu.kanade.tachiyomi.data.library.novel

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD

class NovelLibraryUpdateSmartFilterTest {

    @Test
    fun `always update strategy is required`() {
        val item = libraryNovel(
            novel = baseNovel().copy(updateStrategy = UpdateStrategy.ONLY_FETCH_ONCE),
        )

        isNovelEligibleForAutoUpdate(
            item = item,
            restrictions = emptySet(),
            fetchWindowUpperBound = Long.MAX_VALUE,
        ) shouldBe false
    }

    @Test
    fun `completed novel is skipped when restriction enabled`() {
        val item = libraryNovel(
            novel = baseNovel().copy(status = SManga.COMPLETED.toLong()),
        )

        isNovelEligibleForAutoUpdate(
            item = item,
            restrictions = setOf(ENTRY_NON_COMPLETED),
            fetchWindowUpperBound = Long.MAX_VALUE,
        ) shouldBe false
    }

    @Test
    fun `novel with unread chapters is skipped when caught up restriction enabled`() {
        val item = libraryNovel(
            totalChapters = 20,
            readCount = 5,
        )

        isNovelEligibleForAutoUpdate(
            item = item,
            restrictions = setOf(ENTRY_HAS_UNVIEWED),
            fetchWindowUpperBound = Long.MAX_VALUE,
        ) shouldBe false
    }

    @Test
    fun `not started novel is skipped when started restriction enabled`() {
        val item = libraryNovel(
            totalChapters = 20,
            readCount = 0,
        )

        isNovelEligibleForAutoUpdate(
            item = item,
            restrictions = setOf(ENTRY_NON_VIEWED),
            fetchWindowUpperBound = Long.MAX_VALUE,
        ) shouldBe false
    }

    @Test
    fun `novel outside release period is skipped when release restriction enabled`() {
        val item = libraryNovel(
            novel = baseNovel().copy(nextUpdate = 5_000L),
        )

        isNovelEligibleForAutoUpdate(
            item = item,
            restrictions = setOf(ENTRY_OUTSIDE_RELEASE_PERIOD),
            fetchWindowUpperBound = 4_000L,
        ) shouldBe false
    }

    @Test
    fun `novel is eligible when no restriction blocks it`() {
        val item = libraryNovel(
            novel = baseNovel().copy(nextUpdate = 3_000L),
            totalChapters = 20,
            readCount = 20,
        )

        isNovelEligibleForAutoUpdate(
            item = item,
            restrictions = setOf(
                ENTRY_NON_COMPLETED,
                ENTRY_HAS_UNVIEWED,
                ENTRY_NON_VIEWED,
                ENTRY_OUTSIDE_RELEASE_PERIOD,
            ),
            fetchWindowUpperBound = 4_000L,
        ) shouldBe true
    }

    private fun baseNovel(): Novel {
        return Novel.create().copy(
            id = 1L,
            title = "Novel",
            source = 1L,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            status = SManga.ONGOING.toLong(),
            nextUpdate = 0L,
        )
    }

    private fun libraryNovel(
        novel: Novel = baseNovel(),
        totalChapters: Long = 0L,
        readCount: Long = 0L,
    ): LibraryNovel {
        return LibraryNovel(
            novel = novel,
            category = 0L,
            totalChapters = totalChapters,
            readCount = readCount,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }
}
