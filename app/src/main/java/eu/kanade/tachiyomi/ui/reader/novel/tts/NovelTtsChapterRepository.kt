package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.app.Application
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.prefetch.AllowAllContentPrefetchEnvironment
import eu.kanade.tachiyomi.data.prefetch.AndroidContentPrefetchEnvironment
import eu.kanade.tachiyomi.data.prefetch.ContentPrefetchService
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage
import eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.PageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.decodeNativeScrollProgress
import eu.kanade.tachiyomi.ui.reader.novel.decodePageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.decodeWebScrollProgressPercent
import eu.kanade.tachiyomi.ui.reader.novel.parseNovelRichContent
import eu.kanade.tachiyomi.ui.reader.novel.prependChapterHeadingIfMissing
import eu.kanade.tachiyomi.ui.reader.novel.resolveNovelChapterWebUrl
import eu.kanade.tachiyomi.ui.reader.novel.sanitizeChapterHtmlForReader
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class NovelTtsChapterSnapshot(
    val novel: Novel,
    val chapter: NovelChapter,
    val chapterOrderList: List<NovelChapter>,
    val rawHtml: String,
    val customCss: String?,
    val customJs: String?,
    val pluginSite: String?,
    val chapterWebUrl: String?,
    val contentBlocks: List<NovelReaderScreenModel.ContentBlock>,
    val richContentBlocks: List<NovelRichContentBlock>,
    val richContentUnsupportedFeaturesDetected: Boolean,
    val lastSavedIndex: Int,
    val lastSavedScrollOffsetPx: Int,
    val lastSavedWebProgressPercent: Int,
    val lastSavedPageReaderProgress: PageReaderProgress?,
    val previousChapterId: Long?,
    val previousChapterName: String?,
    val nextChapterId: Long?,
    val nextChapterName: String?,
)

class NovelTtsChapterRepository internal constructor(
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val novelDownloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val pluginStorage: NovelPluginStorage = Injekt.get(),
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    private val contentPrefetchService: ContentPrefetchService = ContentPrefetchService(
        environment = runCatching {
            AndroidContentPrefetchEnvironment(Injekt.get<Application>())
        }.getOrElse {
            AllowAllContentPrefetchEnvironment
        },
    ),
) {
    suspend fun loadChapterSnapshot(chapterId: Long): NovelTtsChapterSnapshot {
        val chapter = withContext(Dispatchers.IO) {
            novelChapterRepository.getChapterById(chapterId)
        } ?: error("Chapter not found")
        val novel = withContext(Dispatchers.IO) {
            getNovel.await(chapter.novelId)
        } ?: error("Novel not found")
        val source = sourceManager.get(novel.source) ?: error("Source not found")
        val chapterOrderList = loadChapterOrderList(novel.id)
        val html = withContext(Dispatchers.IO) {
            contentPrefetchService.resolveNovelChapterText(
                novel = novel,
                chapter = chapter,
                source = source,
                downloadManager = novelDownloadManager,
                cacheReadChapters = novelReaderPreferences.cacheReadChapters().get(),
            )
        }
        val pluginPackage = withContext(Dispatchers.IO) {
            pluginStorage.getAll().firstOrNull { it.entry.id.hashCode().toLong() == novel.source }
        }
        val sourceSiteUrl = (source as? NovelSiteSource)?.siteUrl
        val pluginSite = pluginPackage?.entry?.site ?: sourceSiteUrl
        val chapterWebUrl = withContext(Dispatchers.IO) {
            resolveChapterWebUrl(
                source = source,
                chapterUrl = chapter.url,
                novelUrl = novel.url,
                pluginSite = pluginSite,
            )
        }
        val normalizedChapterHtml = prependChapterHeadingIfMissing(
            rawHtml = html,
            chapterName = chapter.name,
        )
        val sanitizedChapterHtml = sanitizeChapterHtmlForReader(normalizedChapterHtml)
        val readerHtml = if (sanitizedChapterHtml.isBlank()) normalizedChapterHtml else sanitizedChapterHtml
        val contentBlocks = withContext(Dispatchers.Default) {
            extractSnapshotContentBlocks(
                rawHtml = readerHtml,
                chapterWebUrl = chapterWebUrl,
                novelUrl = novel.url,
                pluginSite = pluginSite,
            )
        }
        val richContentResult = withContext(Dispatchers.Default) {
            parseNovelRichContent(readerHtml)
        }
        val richContentBlocks = withContext(Dispatchers.Default) {
            resolveSnapshotRichContentBlocks(
                blocks = richContentResult.blocks,
                chapterWebUrl = chapterWebUrl,
                novelUrl = novel.url,
                pluginSite = pluginSite,
            )
        }
        val decodedNativeProgress = decodeNativeScrollProgress(chapter.lastPageRead)
        val decodedWebProgressPercent = decodeWebScrollProgressPercent(chapter.lastPageRead)
        val decodedPageReaderProgress = decodePageReaderProgress(chapter.lastPageRead)
        val lastSavedIndex = when {
            decodedNativeProgress != null -> decodedNativeProgress.index
            decodedPageReaderProgress != null -> decodedPageReaderProgress.index
            decodedWebProgressPercent != null -> decodedWebProgressPercent
            else -> chapter.lastPageRead.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val lastSavedScrollOffsetPx = decodedNativeProgress?.offsetPx ?: 0
        val lastSavedWebProgressPercent = when {
            decodedWebProgressPercent != null -> decodedWebProgressPercent
            decodedNativeProgress != null || decodedPageReaderProgress != null -> 0
            else -> chapter.lastPageRead.coerceIn(0L, 100L).toInt()
        }
        val chapterIndex = chapterOrderList.indexOfFirst { it.id == chapter.id }
        val previousChapter = chapterOrderList.getOrNull(chapterIndex - 1)
        val nextChapter = chapterOrderList.getOrNull(chapterIndex + 1)
        return NovelTtsChapterSnapshot(
            novel = novel,
            chapter = chapter,
            chapterOrderList = chapterOrderList,
            rawHtml = html,
            customCss = pluginPackage?.customCss?.toString(Charsets.UTF_8),
            customJs = pluginPackage?.customJs?.toString(Charsets.UTF_8),
            pluginSite = pluginSite,
            chapterWebUrl = chapterWebUrl,
            contentBlocks = contentBlocks,
            richContentBlocks = richContentBlocks,
            richContentUnsupportedFeaturesDetected = richContentResult.unsupportedFeaturesDetected,
            lastSavedIndex = lastSavedIndex,
            lastSavedScrollOffsetPx = lastSavedScrollOffsetPx,
            lastSavedWebProgressPercent = lastSavedWebProgressPercent,
            lastSavedPageReaderProgress = decodedPageReaderProgress,
            previousChapterId = previousChapter?.id,
            previousChapterName = previousChapter?.name,
            nextChapterId = nextChapter?.id,
            nextChapterName = nextChapter?.name,
        )
    }

    private suspend fun loadChapterOrderList(novelId: Long): List<NovelChapter> {
        return withContext(Dispatchers.IO) {
            val chapters = novelChapterRepository.getChapterByNovelId(novelId, applyScanlatorFilter = true)
            chapters.sortedWith(
                compareBy<NovelChapter> { it.sourceOrder }
                    .thenBy { it.chapterNumber }
                    .thenBy { it.id },
            )
        }
    }

    private suspend fun resolveChapterWebUrl(
        source: NovelSource,
        chapterUrl: String,
        novelUrl: String,
        pluginSite: String?,
    ): String? {
        val sourceResolved = (source as? NovelWebUrlSource)
            ?.getChapterWebUrl(chapterPath = chapterUrl, novelPath = novelUrl)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (sourceResolved != null) {
            sourceResolved.toHttpUrlOrNull()?.let { return it.toString() }
            resolveNovelChapterWebUrl(
                chapterUrl = sourceResolved,
                pluginSite = pluginSite,
                novelUrl = novelUrl,
            )?.let { return it }
        }
        return resolveNovelChapterWebUrl(
            chapterUrl = chapterUrl,
            pluginSite = pluginSite,
            novelUrl = novelUrl,
        )
    }
}

private fun extractSnapshotContentBlocks(
    rawHtml: String,
    chapterWebUrl: String?,
    novelUrl: String,
    pluginSite: String?,
): List<NovelReaderScreenModel.ContentBlock> {
    val document = Jsoup.parse(rawHtml)
    val paragraphLikeNodes = document.select("p, li, blockquote, h1, h2, h3, h4, h5, h6, pre, img")
        .filterNot { node ->
            node.tagName().equals("p", ignoreCase = true) &&
                node.parent()?.tagName()?.equals("li", ignoreCase = true) == true
        }
    if (paragraphLikeNodes.isNotEmpty()) {
        return paragraphLikeNodes.mapNotNull { element ->
            if (element.tagName().equals("img", ignoreCase = true)) {
                val rawUrl = element.attr("src")
                    .ifBlank { element.attr("data-src") }
                    .ifBlank { element.attr("data-original") }
                    .trim()
                val resolvedUrl = resolveSnapshotContentResourceUrl(
                    rawUrl = rawUrl,
                    chapterWebUrl = chapterWebUrl,
                    novelUrl = novelUrl,
                    pluginSite = pluginSite,
                ) ?: return@mapNotNull null
                NovelReaderScreenModel.ContentBlock.Image(
                    url = resolvedUrl,
                    alt = element.attr("alt").sanitizeSnapshotTextBlock().ifBlank { null },
                )
            } else {
                val text = element.text().sanitizeSnapshotTextBlock()
                if (text.isBlank()) {
                    null
                } else {
                    NovelReaderScreenModel.ContentBlock.Text(
                        if (element.tagName().equals("li", ignoreCase = true)) "• $text" else text,
                    )
                }
            }
        }
    }
    val text = document.body().wholeText().sanitizeSnapshotTextBlock()
    if (text.isBlank()) return emptyList()
    return text.split(Regex("\n{2,}"))
        .flatMap { block -> block.split('\n') }
        .map { it.sanitizeSnapshotTextBlock() }
        .filter { it.isNotBlank() }
        .map(NovelReaderScreenModel.ContentBlock::Text)
}

private fun resolveSnapshotRichContentBlocks(
    blocks: List<NovelRichContentBlock>,
    chapterWebUrl: String?,
    novelUrl: String,
    pluginSite: String?,
): List<NovelRichContentBlock> {
    return blocks.map { block ->
        when (block) {
            is NovelRichContentBlock.Image -> {
                val resolvedUrl = resolveSnapshotContentResourceUrl(
                    rawUrl = block.url,
                    chapterWebUrl = chapterWebUrl,
                    novelUrl = novelUrl,
                    pluginSite = pluginSite,
                ) ?: block.url
                block.copy(url = resolvedUrl)
            }
            else -> block
        }
    }
}

private fun resolveSnapshotContentResourceUrl(
    rawUrl: String,
    chapterWebUrl: String?,
    novelUrl: String,
    pluginSite: String?,
): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.startsWith("data:image/", ignoreCase = true)) return trimmed
    if (NovelPluginImage.isSupported(trimmed)) return trimmed
    if (trimmed.startsWith("blob:", ignoreCase = true)) return null
    trimmed.toHttpUrlOrNull()?.let { return it.toString() }
    chapterWebUrl
        ?.let { resolveUrl(trimmed, it).trim().toHttpUrlOrNull() }
        ?.let { return it.toString() }
    return resolveNovelChapterWebUrl(
        chapterUrl = trimmed,
        pluginSite = pluginSite,
        novelUrl = novelUrl,
    )
}

private fun String.sanitizeSnapshotTextBlock(): String {
    return replace('\u00A0', ' ')
        .replace("\r", "")
        .trim()
}
