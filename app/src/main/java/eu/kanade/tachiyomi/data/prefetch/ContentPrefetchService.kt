package eu.kanade.tachiyomi.data.prefetch

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import eu.kanade.domain.items.novelchapter.model.toSNovelChapter
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderChapterDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderChapterPrefetchCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import java.util.concurrent.ConcurrentHashMap

internal interface ContentPrefetchEnvironment {
    fun isPowerSaveMode(): Boolean
    fun hasActiveNetwork(): Boolean
}

internal object AllowAllContentPrefetchEnvironment : ContentPrefetchEnvironment {
    override fun isPowerSaveMode(): Boolean = false
    override fun hasActiveNetwork(): Boolean = true
}

internal class AndroidContentPrefetchEnvironment(
    private val context: Context,
) : ContentPrefetchEnvironment {
    override fun isPowerSaveMode(): Boolean {
        val powerManager = runCatching { context.getSystemService(PowerManager::class.java) }.getOrNull()
        return powerManager?.isPowerSaveMode == true
    }

    override fun hasActiveNetwork(): Boolean {
        val connectivityManager = runCatching { context.getSystemService(ConnectivityManager::class.java) }.getOrNull()
            ?: return true
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

internal class ContentPrefetchService(
    private val environment: ContentPrefetchEnvironment = AllowAllContentPrefetchEnvironment,
) {
    private val inFlightChapterIds = ConcurrentHashMap.newKeySet<Long>()
    private val cacheMutex = Mutex()

    suspend fun resolveNovelChapterText(
        novel: Novel,
        chapter: NovelChapter,
        source: NovelSource,
        downloadManager: NovelDownloadManager,
        cacheReadChapters: Boolean,
    ): String {
        return readCachedNovelChapterText(
            novel = novel,
            chapter = chapter,
            downloadManager = downloadManager,
            cacheReadChapters = cacheReadChapters,
        ) ?: source.getChapterText(chapter.toSNovelChapter()).also { html ->
            if (cacheReadChapters) {
                NovelReaderChapterDiskCacheStore.put(chapter.id, html)
            }
        }
    }

    suspend fun prefetchNovelChapterText(
        prefetchEnabled: Boolean,
        novel: Novel,
        chapter: NovelChapter,
        source: NovelSource,
        downloadManager: NovelDownloadManager,
        cacheReadChapters: Boolean,
    ): Boolean {
        if (!prefetchEnabled) return false
        if (environment.isPowerSaveMode()) return false
        if (!environment.hasActiveNetwork()) return false
        if (!inFlightChapterIds.add(chapter.id)) return false

        return try {
            if (hasCachedNovelChapterText(
                    novel = novel,
                    chapter = chapter,
                    downloadManager = downloadManager,
                    cacheReadChapters = cacheReadChapters,
                )
            ) {
                false
            } else {
                val html = source.getChapterText(chapter.toSNovelChapter())
                cacheNovelChapterText(chapter = chapter, html = html, cacheReadChapters = cacheReadChapters)
                NovelReaderChapterPrefetchCache.put(chapter.id, html)
                true
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            inFlightChapterIds.remove(chapter.id)
        }
    }

    private suspend fun readCachedNovelChapterText(
        novel: Novel,
        chapter: NovelChapter,
        downloadManager: NovelDownloadManager,
        cacheReadChapters: Boolean,
    ): String? {
        val downloadedText = downloadManager.getDownloadedChapterText(novel, chapter.id)
        if (downloadedText != null) {
            return downloadedText
        }

        val cachedText = if (cacheReadChapters) {
            NovelReaderChapterDiskCacheStore.get(chapter.id)
        } else {
            null
        }
        if (cachedText != null) {
            return cachedText
        }

        return NovelReaderChapterPrefetchCache.get(chapter.id)?.also { html ->
            if (cacheReadChapters) {
                NovelReaderChapterDiskCacheStore.put(chapter.id, html)
            }
        }
    }

    private suspend fun hasCachedNovelChapterText(
        novel: Novel,
        chapter: NovelChapter,
        downloadManager: NovelDownloadManager,
        cacheReadChapters: Boolean,
    ): Boolean {
        return readCachedNovelChapterText(
            novel = novel,
            chapter = chapter,
            downloadManager = downloadManager,
            cacheReadChapters = cacheReadChapters,
        ) != null
    }

    private suspend fun cacheNovelChapterText(
        chapter: NovelChapter,
        html: String,
        cacheReadChapters: Boolean,
    ) {
        cacheMutex.withLock {
            NovelReaderChapterPrefetchCache.put(chapter.id, html)
            if (cacheReadChapters) {
                NovelReaderChapterDiskCacheStore.put(chapter.id, html)
            }
        }
    }
}
