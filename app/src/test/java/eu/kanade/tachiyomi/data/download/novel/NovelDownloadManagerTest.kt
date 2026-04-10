package eu.kanade.tachiyomi.data.download.novel

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelDownloadManagerTest {

    @Test
    fun `downloadChapter returns false when chapter fetch times out`() {
        runBlocking {
            val manager = NovelDownloadManager(
                application = null,
                sourceManager = null,
                storageManager = null,
                downloadCache = null,
                chapterFetchTimeoutMillis = 50L,
                fetchChapterText = { _, _ ->
                    delay(250L)
                    "chapter text"
                },
            )

            val result = manager.downloadChapter(
                novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel"),
                chapter = NovelChapter.create().copy(id = 2L, novelId = 1L, url = "/chapter-2"),
            )

            result shouldBe false
        }
    }
}
