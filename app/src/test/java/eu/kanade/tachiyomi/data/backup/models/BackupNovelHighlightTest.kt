package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test

class BackupNovelHighlightTest {

    @Test
    fun `highlights survive a protobuf round trip`() {
        val novel = BackupNovel(
            source = 1L,
            url = "/novel",
            highlights = listOf(
                BackupNovelHighlight(
                    chapterUrl = "https://example.org/ch1",
                    blockIndex = 3,
                    charStart = 10,
                    charEndExclusive = 25,
                    normalizedText = "stored snippet",
                    colorArgb = 4294688813L,
                    note = "my note",
                    createdAt = 100L,
                    updatedAt = 200L,
                    pageIndex = 3,
                    pageCount = 42,
                ),
                BackupNovelHighlight(
                    chapterUrl = "https://example.org/ch2",
                    blockIndex = 0,
                    charStart = 0,
                    charEndExclusive = 5,
                    normalizedText = "hello",
                    colorArgb = 4293271909L,
                    note = "",
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
            ),
        )

        val bytes = ProtoBuf.encodeToByteArray(BackupNovel.serializer(), novel)
        val restored = ProtoBuf.decodeFromByteArray(BackupNovel.serializer(), bytes)

        restored.highlights shouldBe novel.highlights
        restored.highlights.first().pageIndex shouldBe 3
        restored.highlights.first().pageCount shouldBe 42
        // Второй хайлайт без страниц — дефолты 0/0 (совместимость со старыми бэкапами).
        restored.highlights.last().pageIndex shouldBe 0
        restored.highlights.last().pageCount shouldBe 0
    }

    @Test
    fun `novels without highlights decode with an empty list`() {
        val novel = BackupNovel(source = 1L, url = "/novel")

        val bytes = ProtoBuf.encodeToByteArray(BackupNovel.serializer(), novel)
        val restored = ProtoBuf.decodeFromByteArray(BackupNovel.serializer(), bytes)

        restored.highlights shouldBe emptyList()
    }
}
