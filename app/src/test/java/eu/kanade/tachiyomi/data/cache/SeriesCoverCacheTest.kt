package eu.kanade.tachiyomi.data.cache

import eu.kanade.tachiyomi.util.storage.DiskUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SeriesCoverCacheTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `getMangaSeriesCoverFile hashes series id into manga series cover directory`() {
        val cache = createCache()

        val file = cache.getMangaSeriesCoverFile(1L)

        assertTrue(file.parentFile!!.path.replace('\\', '/').endsWith("/series_covers/manga"))
        assertEquals(DiskUtil.hashKeyForDisk("1"), file.name)
    }

    @Test
    fun `getNovelSeriesCoverFile hashes series id into novel series cover directory`() {
        val cache = createCache()

        val file = cache.getNovelSeriesCoverFile(2L)

        assertTrue(file.parentFile!!.path.replace('\\', '/').endsWith("/series_covers/novel"))
        assertEquals(DiskUtil.hashKeyForDisk("2"), file.name)
    }

    @Test
    fun `set and delete custom manga series cover`() {
        val cache = createCache()
        val seriesId = 11L

        cache.setMangaSeriesCoverToCache(seriesId, "manga-cover".byteInputStream())
        val file = cache.getMangaSeriesCoverFile(seriesId)

        assertTrue(file.exists())
        assertEquals("manga-cover", file.readText())
        assertTrue(cache.deleteMangaSeriesCover(seriesId))
        assertFalse(file.exists())
    }

    @Test
    fun `set and delete custom novel series cover`() {
        val cache = createCache()
        val seriesId = 22L

        cache.setNovelSeriesCoverToCache(seriesId, "novel-cover".byteInputStream())
        val file = cache.getNovelSeriesCoverFile(seriesId)

        assertTrue(file.exists())
        assertEquals("novel-cover", file.readText())
        assertTrue(cache.deleteNovelSeriesCover(seriesId))
        assertFalse(file.exists())
    }

    private fun createCache(): SeriesCoverCache {
        return SeriesCoverCache(
            mangaRootDir = tempDir.resolve("series_covers/manga").toFile(),
            novelRootDir = tempDir.resolve("series_covers/novel").toFile(),
            createDir = true,
        )
    }
}
