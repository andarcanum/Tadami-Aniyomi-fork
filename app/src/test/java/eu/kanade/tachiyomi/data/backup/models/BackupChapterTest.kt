package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BackupChapterTest {

    @Test
    fun `toNovelChapterImpl preserves raw upload date`() {
        val backup = BackupChapter(
            url = "/c",
            name = "Chapter",
            dateUploadRaw = "Yesterday",
        )

        backup.toNovelChapterImpl().dateUploadRaw shouldBe "Yesterday"
    }
}
