package eu.kanade.tachiyomi.data.backup.restore

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RestoreOptionsTest {

    @Test
    fun `fromBooleanArray supports legacy arrays and defaults type flags to true`() {
        val legacy = booleanArrayOf(
            false, // libraryEntries
            true, // categories
            false, // appSettings
            true, // extensionRepoSettings
            false, // customButtons
            true, // sourceSettings
            false, // extensions
            true, // achievements
            false, // stats
        )

        val options = RestoreOptions.fromBooleanArray(legacy)

        options.libraryEntries shouldBe false
        options.categories shouldBe true
        options.appSettings shouldBe false
        options.extensionRepoSettings shouldBe true
        options.customButtons shouldBe false
        options.sourceSettings shouldBe true
        options.extensions shouldBe false
        options.achievements shouldBe true
        options.stats shouldBe false
        options.restoreManga shouldBe true
        options.restoreAnime shouldBe true
        options.restoreNovel shouldBe true
    }

    @Test
    fun `asBooleanArray and fromBooleanArray preserve new type flags`() {
        val options = RestoreOptions(
            libraryEntries = true,
            restoreManga = false,
            restoreAnime = true,
            restoreNovel = false,
            categories = false,
        )

        val restored = RestoreOptions.fromBooleanArray(options.asBooleanArray())

        restored.restoreManga shouldBe false
        restored.restoreAnime shouldBe true
        restored.restoreNovel shouldBe false
        restored.categories shouldBe false
    }

    @Test
    fun `canRestore returns false when library enabled and all content types disabled`() {
        val options = RestoreOptions(
            libraryEntries = true,
            restoreManga = false,
            restoreAnime = false,
            restoreNovel = false,
            categories = false,
            appSettings = false,
            extensionRepoSettings = false,
            customButtons = false,
            sourceSettings = false,
            extensions = false,
            achievements = false,
            stats = false,
        )

        options.canRestore() shouldBe false
    }

    @Test
    fun `canRestore still allows non-library restore when library disabled`() {
        val options = RestoreOptions(
            libraryEntries = false,
            restoreManga = false,
            restoreAnime = false,
            restoreNovel = false,
            categories = true,
            appSettings = false,
            extensionRepoSettings = false,
            customButtons = false,
            sourceSettings = false,
            extensions = false,
            achievements = false,
            stats = false,
        )

        options.canRestore() shouldBe true
    }
}
