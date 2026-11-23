package eu.kanade.tachiyomi.data.library

import androidx.work.workDataOf
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LibraryUpdateEntryIdsTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `stores small entry id list inline and deduplicates`() {
        val data = LibraryUpdateEntryIds.buildInputData(
            cacheDir = tempDir,
            mediaTag = "MangaLibraryUpdate",
            entryIds = longArrayOf(3L, 1L, 3L, 2L),
        )

        LibraryUpdateEntryIds.hasTargetedEntryIds(data).shouldBeTrue()
        data.getString(LibraryUpdateEntryIds.KEY_ENTRY_IDS_FILE).shouldBeNull()

        val consumed = LibraryUpdateEntryIds.consumeEntryIds(data, tempDir)
        consumed.shouldNotBeNull()
        consumed.shouldContainExactlyInAnyOrder(1L, 2L, 3L)
    }

    @Test
    fun `empty entry id array produces untargeted input data`() {
        val data = LibraryUpdateEntryIds.buildInputData(
            cacheDir = tempDir,
            mediaTag = "MangaLibraryUpdate",
            entryIds = LongArray(0),
        )

        LibraryUpdateEntryIds.hasTargetedEntryIds(data).shouldBeFalse()
        LibraryUpdateEntryIds.consumeEntryIds(data, tempDir).shouldBeNull()
    }

    @Test
    fun `spills large selection above WorkManager limit to cache file and deletes after consume`() {
        val largeIds = LongArray(1_600) { (it + 1).toLong() }
        val data = LibraryUpdateEntryIds.buildInputData(
            cacheDir = tempDir,
            mediaTag = "AnimeLibraryUpdate",
            entryIds = largeIds,
        )

        LibraryUpdateEntryIds.hasTargetedEntryIds(data).shouldBeTrue()
        data.getLongArray(LibraryUpdateEntryIds.KEY_ENTRY_IDS).shouldBeNull()
        val spillPath = data.getString(LibraryUpdateEntryIds.KEY_ENTRY_IDS_FILE).shouldNotBeNull()
        File(spillPath).exists().shouldBeTrue()

        val consumed = LibraryUpdateEntryIds.consumeEntryIds(data, tempDir)
        consumed.shouldNotBeNull()
        consumed.size shouldBe 1_600
        consumed.contains(1L).shouldBeTrue()
        consumed.contains(1_600L).shouldBeTrue()
        File(spillPath).exists().shouldBeFalse()
    }

    @Test
    fun `cleanupSpillFile deletes spilled file when enqueue is rejected`() {
        val data = LibraryUpdateEntryIds.buildInputData(
            cacheDir = tempDir,
            mediaTag = "NovelLibraryUpdate",
            entryIds = longArrayOf(10L, 20L, 30L),
            inlineLimit = 2,
        )
        val spillFile = File(data.getString(LibraryUpdateEntryIds.KEY_ENTRY_IDS_FILE).shouldNotBeNull())
        spillFile.exists().shouldBeTrue()

        LibraryUpdateEntryIds.cleanupSpillFile(data, tempDir)
        spillFile.exists().shouldBeFalse()
    }

    @Test
    fun `consumeEntryIds rejects file paths outside allowed cache directory`() {
        val outsideFile = File(tempDir, "outside.txt").apply {
            writeText("42\n99\n")
        }
        val forgedData = workDataOf(LibraryUpdateEntryIds.KEY_ENTRY_IDS_FILE to outsideFile.absolutePath)

        LibraryUpdateEntryIds.consumeEntryIds(forgedData, tempDir).shouldBeNull()
        outsideFile.exists().shouldBeTrue()
    }

    @Test
    fun `shouldExpandAnimeSeasonsForUpdate always expands for explicit targeted updates`() {
        shouldExpandAnimeSeasonsForUpdate(
            hasTargetedEntryIds = true,
            updateSeasonOnLibraryUpdate = false,
        ).shouldBeTrue()
        shouldExpandAnimeSeasonsForUpdate(
            hasTargetedEntryIds = false,
            updateSeasonOnLibraryUpdate = false,
        ).shouldBeFalse()
        shouldExpandAnimeSeasonsForUpdate(
            hasTargetedEntryIds = false,
            updateSeasonOnLibraryUpdate = true,
        ).shouldBeTrue()
    }
}
