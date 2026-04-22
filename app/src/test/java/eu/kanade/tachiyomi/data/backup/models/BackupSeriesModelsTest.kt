package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BackupSeriesModelsTest {

    @Test
    fun `backup stores manga and novel series payload`() {
        val mangaSeries = BackupMangaSeries(
            title = "Manga Series",
            categoryName = "Reading",
            entries = listOf(BackupSeriesEntryRef(source = 1L, url = "/manga/1", position = 0)),
            customCover = byteArrayOf(1, 2, 3),
        )
        val novelSeries = BackupNovelSeries(
            title = "Novel Series",
            categoryName = "Plan to read",
            entries = listOf(BackupSeriesEntryRef(source = 2L, url = "/novel/1", position = 0)),
            customCover = byteArrayOf(7, 8),
        )

        val backup = Backup(
            backupMangaSeries = listOf(mangaSeries),
            backupNovelSeries = listOf(novelSeries),
        )

        backup.backupMangaSeries.first().title shouldBe "Manga Series"
        backup.backupMangaSeries.first().entries.first().url shouldBe "/manga/1"
        backup.backupMangaSeries.first().customCover?.contentEquals(byteArrayOf(1, 2, 3)) shouldBe true
        backup.backupNovelSeries.first().title shouldBe "Novel Series"
        backup.backupNovelSeries.first().entries.first().url shouldBe "/novel/1"
        backup.backupNovelSeries.first().customCover?.contentEquals(byteArrayOf(7, 8)) shouldBe true
    }

    @Test
    fun `legacy backup maps series payload to modern backup`() {
        val legacy = LegacyBackup(
            backupMangaSeries = listOf(
                BackupMangaSeries(
                    title = "Legacy Manga Series",
                    entries = listOf(BackupSeriesEntryRef(source = 1L, url = "/manga/legacy", position = 0)),
                ),
            ),
            backupNovelSeries = listOf(
                BackupNovelSeries(
                    title = "Legacy Novel Series",
                    entries = listOf(BackupSeriesEntryRef(source = 2L, url = "/novel/legacy", position = 0)),
                ),
            ),
        )

        val backup = legacy.toBackup()

        backup.backupMangaSeries.map { it.title } shouldBe listOf("Legacy Manga Series")
        backup.backupNovelSeries.map { it.title } shouldBe listOf("Legacy Novel Series")
    }
}
