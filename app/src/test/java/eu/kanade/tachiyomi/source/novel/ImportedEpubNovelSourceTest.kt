package eu.kanade.tachiyomi.source.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ImportedEpubNovelSourceTest {

    @Test
    fun `imported epub source has correct id and name`() {
        val source = ImportedEpubNovelSource()

        source.id shouldBe IMPORTED_EPUB_NOVEL_SOURCE_ID
        source.name shouldBe IMPORTED_EPUB_NOVEL_SOURCE_NAME
    }

    // TODO: Add tests for getNovelDetails, getChapterList, getChapterText
}