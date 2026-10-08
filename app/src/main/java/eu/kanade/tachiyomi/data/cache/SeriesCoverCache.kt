package eu.kanade.tachiyomi.data.cache

import android.content.Context
import eu.kanade.tachiyomi.util.storage.DiskUtil
import java.io.File
import java.io.IOException
import java.io.InputStream

class SeriesCoverCache private constructor(
    private val mangaCacheDir: File,
    private val novelCacheDir: File,
    private val animeCacheDir: File,
) {

    companion object {
        private const val MANGA_COVERS_DIR = "series_covers/manga"
        private const val NOVEL_COVERS_DIR = "series_covers/novel"
        private const val ANIME_COVERS_DIR = "series_covers/anime"
    }

    constructor(context: Context) : this(
        context.getExternalFilesDir(MANGA_COVERS_DIR)
            ?: File(context.filesDir, MANGA_COVERS_DIR).also { it.mkdirs() },
        context.getExternalFilesDir(NOVEL_COVERS_DIR)
            ?: File(context.filesDir, NOVEL_COVERS_DIR).also { it.mkdirs() },
        context.getExternalFilesDir(ANIME_COVERS_DIR)
            ?: File(context.filesDir, ANIME_COVERS_DIR).also { it.mkdirs() },
    )

    internal constructor(
        mangaRootDir: File,
        novelRootDir: File,
        animeRootDir: File = File(mangaRootDir.parentFile ?: mangaRootDir, "anime"),
        createDir: Boolean,
    ) : this(
        if (createDir) mangaRootDir.also { it.mkdirs() } else mangaRootDir,
        if (createDir) novelRootDir.also { it.mkdirs() } else novelRootDir,
        if (createDir) animeRootDir.also { it.mkdirs() } else animeRootDir,
    )

    fun getMangaSeriesCoverFile(seriesId: Long): File {
        return File(mangaCacheDir, DiskUtil.hashKeyForDisk(seriesId.toString()))
    }

    fun getNovelSeriesCoverFile(seriesId: Long): File {
        return File(novelCacheDir, DiskUtil.hashKeyForDisk(seriesId.toString()))
    }

    fun getAnimeSeriesCoverFile(seriesId: Long): File {
        return File(animeCacheDir, DiskUtil.hashKeyForDisk(seriesId.toString()))
    }

    @Throws(IOException::class)
    fun setMangaSeriesCoverToCache(seriesId: Long, inputStream: InputStream) {
        getMangaSeriesCoverFile(seriesId).outputStream().use {
            inputStream.copyTo(it)
        }
    }

    @Throws(IOException::class)
    fun setNovelSeriesCoverToCache(seriesId: Long, inputStream: InputStream) {
        getNovelSeriesCoverFile(seriesId).outputStream().use {
            inputStream.copyTo(it)
        }
    }

    @Throws(IOException::class)
    fun setAnimeSeriesCoverToCache(seriesId: Long, inputStream: InputStream) {
        getAnimeSeriesCoverFile(seriesId).outputStream().use {
            inputStream.copyTo(it)
        }
    }

    fun deleteMangaSeriesCover(seriesId: Long): Boolean {
        return getMangaSeriesCoverFile(seriesId).let {
            it.exists() && it.delete()
        }
    }

    fun deleteNovelSeriesCover(seriesId: Long): Boolean {
        return getNovelSeriesCoverFile(seriesId).let {
            it.exists() && it.delete()
        }
    }

    fun deleteAnimeSeriesCover(seriesId: Long): Boolean {
        return getAnimeSeriesCoverFile(seriesId).let {
            it.exists() && it.delete()
        }
    }
}
