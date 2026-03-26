package eu.kanade.tachiyomi.data.cache

import eu.kanade.tachiyomi.util.storage.DiskUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.model.Novel
import java.nio.file.Path

class NovelCoverCacheTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `getCoverFile hashes thumbnail url into novel cover directory`() {
        val cache = NovelCoverCache(tempDir.resolve("novelcovers").toFile(), createDir = true)
        val url = "https://example.org/covers/series-1.jpg"

        val file = cache.getCoverFile(url)

        assertNotNull(file)
        assertTrue(file!!.parentFile!!.path.replace('\\', '/').endsWith("/novelcovers"))
        assertEquals(DiskUtil.hashKeyForDisk(url), file.name)
    }

    @Test
    fun `deleteFromCache removes existing novel cover file and reports deletion count`() {
        val cache = NovelCoverCache(tempDir.resolve("novelcovers").toFile(), createDir = true)
        val novel = Novel.create().copy(
            id = 7L,
            thumbnailUrl = "https://example.org/covers/series-7.jpg",
        )
        val file = cache.getCoverFile(novel.thumbnailUrl)!!
        file.parentFile?.mkdirs()
        file.writeText("cover")

        val deleted = cache.deleteFromCache(novel)

        assertEquals(1, deleted)
        assertFalse(file.exists())
    }
}
