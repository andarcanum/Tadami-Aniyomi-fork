package eu.kanade.tachiyomi.data.backup.create

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BackupOptionsTest {

    @Test
    fun `fromBooleanArray supports legacy arrays and defaults type flags to true`() {
        val legacy = booleanArrayOf(
            false, // libraryEntries
            true, // categories
            false, // chapters
            true, // tracking
            false, // history
            true, // readEntries
            false, // appSettings
            true, // extensionRepoSettings
            false, // customButton
            true, // sourceSettings
            false, // privateSettings
            true, // extensions
            false, // achievements
            true, // stats
        )

        val options = BackupOptions.fromBooleanArray(legacy)

        options.libraryEntries shouldBe false
        options.categories shouldBe true
        options.chapters shouldBe false
        options.tracking shouldBe true
        options.history shouldBe false
        options.readEntries shouldBe true
        options.appSettings shouldBe false
        options.extensionRepoSettings shouldBe true
        options.customButton shouldBe false
        options.sourceSettings shouldBe true
        options.privateSettings shouldBe false
        options.extensions shouldBe true
        options.achievements shouldBe false
        options.stats shouldBe true
        options.backupManga shouldBe true
        options.backupAnime shouldBe true
        options.backupNovel shouldBe true
    }

    @Test
    fun `asBooleanArray and fromBooleanArray preserve new type flags`() {
        val options = BackupOptions(
            libraryEntries = true,
            backupManga = false,
            backupAnime = true,
            backupNovel = false,
            categories = false,
        )

        val restored = BackupOptions.fromBooleanArray(options.asBooleanArray())

        restored.backupManga shouldBe false
        restored.backupAnime shouldBe true
        restored.backupNovel shouldBe false
        restored.categories shouldBe false
    }

    @Test
    fun `canCreate returns false when library enabled and all content types disabled`() {
        val options = BackupOptions(
            libraryEntries = true,
            backupManga = false,
            backupAnime = false,
            backupNovel = false,
            categories = false,
            appSettings = false,
            extensionRepoSettings = false,
            customButton = false,
            sourceSettings = false,
            achievements = false,
            stats = false,
        )

        options.canCreate() shouldBe false
    }

    @Test
    fun `canCreate still allows non-library backup when library disabled`() {
        val options = BackupOptions(
            libraryEntries = false,
            backupManga = false,
            backupAnime = false,
            backupNovel = false,
            categories = true,
            appSettings = false,
            extensionRepoSettings = false,
            customButton = false,
            sourceSettings = false,
            achievements = false,
            stats = false,
        )

        options.canCreate() shouldBe true
    }
}
