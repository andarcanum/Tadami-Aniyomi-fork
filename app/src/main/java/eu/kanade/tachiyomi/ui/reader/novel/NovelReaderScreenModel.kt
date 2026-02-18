package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.items.novelchapter.model.toSNovelChapter
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.util.system.isNightMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import logcat.LogPriority
import org.jsoup.Jsoup
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

class NovelReaderScreenModel(
    private val chapterId: Long,
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val novelDownloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val pluginStorage: NovelPluginStorage = Injekt.get(),
    private val historyRepository: NovelHistoryRepository? = null,
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    private val eventBus: AchievementEventBus? = runCatching { Injekt.get<AchievementEventBus>() }.getOrNull(),
    private val isSystemDark: () -> Boolean = { Injekt.get<Application>().isNightMode() },
) : StateScreenModel<NovelReaderScreenModel.State>(State.Loading) {

    private var settingsJob: Job? = null
    private var rawHtml: String? = null
    private var currentNovel: Novel? = null
    private var currentChapter: NovelChapter? = null
    private var chapterOrderList: List<NovelChapter> = emptyList()
    private var customCss: String? = null
    private var customJs: String? = null
    private var pluginSite: String? = null
    private var chapterWebUrl: String? = null
    private var parsedContentBlocks: List<ContentBlock>? = null
    private var parsedTextBlocks: List<String>? = null
    private var lastSavedProgress: Long? = null
    private var lastSavedRead: Boolean? = null
    private var initialProgressIndex: Int = 0
    private var hasProgressChanged: Boolean = false
    private var chapterReadStartTimeMs: Long = System.currentTimeMillis()
    private val structuredJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val resolvedHistoryRepository by lazy {
        historyRepository ?: runCatching { Injekt.get<NovelHistoryRepository>() }.getOrNull()
    }

    init {
        screenModelScope.launch {
            loadChapter()
        }
    }

    private suspend fun loadChapter() {
        val chapter = withContext(Dispatchers.IO) {
            novelChapterRepository.getChapterById(chapterId)
        }
            ?: return setError("Chapter not found")
        val novel = withContext(Dispatchers.IO) {
            getNovel.await(chapter.novelId)
        }
            ?: return setError("Novel not found")
        val source = sourceManager.get(novel.source)
            ?: return setError("Source not found")
        chapterOrderList = withContext(Dispatchers.IO) {
            novelChapterRepository.getChapterByNovelId(novel.id, applyScanlatorFilter = true)
                .sortedBy { it.sourceOrder }
        }

        val html = try {
            withContext(Dispatchers.IO) {
                novelDownloadManager.getDownloadedChapterText(novel, chapter.id)
                    ?: source.getChapterText(chapter.toSNovelChapter())
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load novel chapter text" }
            return setError(e.message)
        }

        val pluginPackage = withContext(Dispatchers.IO) {
            pluginStorage.getAll()
                .firstOrNull { it.entry.id.hashCode().toLong() == novel.source }
        }
        val sourceSiteUrl = (source as? NovelSiteSource)?.siteUrl
        rawHtml = withContext(Dispatchers.Default) {
            html
                .normalizeStructuredChapterPayload()
                .prependChapterHeadingIfMissing(chapter.name)
        }
        currentNovel = novel
        currentChapter = chapter
        parsedContentBlocks = null
        parsedTextBlocks = null
        lastSavedProgress = chapter.lastPageRead
        lastSavedRead = chapter.read
        val savedNativeProgress = decodeNativeScrollProgress(chapter.lastPageRead)
        val savedWebProgress = decodeWebScrollProgressPercent(chapter.lastPageRead)
        initialProgressIndex = savedNativeProgress?.index
            ?: savedWebProgress
            ?: chapter.lastPageRead.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        hasProgressChanged = false
        customCss = pluginPackage?.customCss?.toString(Charsets.UTF_8)
        customJs = pluginPackage?.customJs?.toString(Charsets.UTF_8)
        pluginSite = pluginPackage?.entry?.site ?: sourceSiteUrl
        chapterWebUrl = withContext(Dispatchers.IO) {
            resolveChapterWebUrl(
                source = source,
                chapterUrl = chapter.url,
                novelUrl = novel.url,
                pluginSite = pluginSite,
            )
        }
        parseAndCacheContentBlocks(
            rawHtml = rawHtml ?: return setError("Chapter content is empty"),
            chapterWebUrl = chapterWebUrl,
            novelUrl = novel.url,
            pluginSite = pluginSite,
        )
        chapterReadStartTimeMs = System.currentTimeMillis()
        val initialSettings = novelReaderPreferences.resolveSettings(novel.source)

        settingsJob?.cancel()
        settingsJob = screenModelScope.launch {
            var skippedInitialEmission = false
            novelReaderPreferences.settingsFlow(novel.source)
                .distinctUntilChanged()
                .collect { settings ->
                    if (!skippedInitialEmission && settings == initialSettings) {
                        skippedInitialEmission = true
                        return@collect
                    }
                    skippedInitialEmission = true
                    updateContent(settings)
                }
        }
        saveHistorySnapshot(chapter.id, sessionReadDurationMs = 0L)
        updateContent(initialSettings)
    }

    private fun setError(message: String?) {
        mutableState.value = State.Error(message)
    }

    private fun updateContent(settings: NovelReaderSettings) {
        val html = rawHtml ?: return
        val novel = currentNovel ?: return
        val chapter = currentChapter ?: return
        val decodedNativeProgress = decodeNativeScrollProgress(chapter.lastPageRead)
        val decodedWebProgressPercent = decodeWebScrollProgressPercent(chapter.lastPageRead)
        val lastSavedIndex = when {
            decodedNativeProgress != null -> decodedNativeProgress.index
            decodedWebProgressPercent != null -> 0
            else -> chapter.lastPageRead.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val lastSavedScrollOffsetPx = decodedNativeProgress?.offsetPx ?: 0
        val lastSavedWebProgressPercent = when {
            decodedWebProgressPercent != null -> decodedWebProgressPercent
            decodedNativeProgress != null -> 0
            else -> chapter.lastPageRead.coerceIn(0L, 100L).toInt()
        }
        val chapterNavigation = chapterOrderList.let { chapters ->
            val index = chapters.indexOfFirst { it.id == chapter.id }
            val previousChapterId = chapters.getOrNull(index - 1)?.id
            val nextChapterId = chapters.getOrNull(index + 1)?.id
            previousChapterId to nextChapterId
        }
        val pluginCss = customCss
        val pluginJs = customJs
        val content = normalizeHtml(
            rawHtml = html,
            settings = settings,
            customCss = pluginCss,
            customJs = pluginJs,
        )
        val contentBlocks = parsedContentBlocks
            ?: extractContentBlocks(
                rawHtml = html,
                chapterWebUrl = chapterWebUrl,
                novelUrl = novel.url,
                pluginSite = pluginSite,
            ).ifEmpty {
                extractTextBlocks(html).map(ContentBlock::Text)
            }.also {
                parsedContentBlocks = it
            }
        val textBlocks = parsedTextBlocks
            ?: contentBlocks
                .filterIsInstance<ContentBlock.Text>()
                .map { it.text }
                .also { parsedTextBlocks = it }
        mutableState.value = State.Success(
            novel = novel,
            chapter = chapter,
            html = content,
            enableJs = !pluginJs.isNullOrBlank(),
            readerSettings = settings,
            contentBlocks = contentBlocks,
            textBlocks = textBlocks,
            lastSavedIndex = lastSavedIndex,
            lastSavedScrollOffsetPx = lastSavedScrollOffsetPx,
            lastSavedWebProgressPercent = lastSavedWebProgressPercent,
            previousChapterId = chapterNavigation.first,
            nextChapterId = chapterNavigation.second,
            chapterWebUrl = chapterWebUrl,
        )
    }

    private suspend fun parseAndCacheContentBlocks(
        rawHtml: String,
        chapterWebUrl: String?,
        novelUrl: String,
        pluginSite: String?,
    ) {
        val blocks = withContext(Dispatchers.Default) {
            extractContentBlocks(
                rawHtml = rawHtml,
                chapterWebUrl = chapterWebUrl,
                novelUrl = novelUrl,
                pluginSite = pluginSite,
            ).ifEmpty {
                extractTextBlocks(rawHtml).map(ContentBlock::Text)
            }
        }
        parsedContentBlocks = blocks
        parsedTextBlocks = blocks
            .filterIsInstance<ContentBlock.Text>()
            .map { it.text }
    }

    private suspend fun resolveChapterWebUrl(
        source: eu.kanade.tachiyomi.novelsource.NovelSource,
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

    fun updateReadingProgress(
        currentIndex: Int,
        totalItems: Int,
        persistedProgress: Long? = null,
    ) {
        val chapter = currentChapter ?: return
        if (totalItems <= 0 || currentIndex < 0) return
        val resolvedPersistedProgress = persistedProgress ?: currentIndex.toLong()
        if (!hasProgressChanged) {
            val isSameInitialIndex = currentIndex == initialProgressIndex
            val isSamePersistedProgress = lastSavedProgress == resolvedPersistedProgress
            if (isSameInitialIndex && isSamePersistedProgress) return
            hasProgressChanged = true
        }

        val readThreshold = when {
            totalItems == 100 -> 0.99f
            else -> 0.95f
        }
        val reachedReadThreshold = totalItems > 1 &&
            ((currentIndex + 1).toFloat() / totalItems.toFloat()) >= readThreshold
        val shouldPersistRead = (lastSavedRead == true) || chapter.read || reachedReadThreshold
        val newProgress = if (shouldPersistRead) 0L else resolvedPersistedProgress

        if (lastSavedRead == shouldPersistRead && lastSavedProgress == newProgress) {
            return
        }

        val becameRead = !chapter.read && shouldPersistRead
        lastSavedRead = shouldPersistRead
        lastSavedProgress = newProgress
        applyLocalChapterProgress(
            chapter = chapter,
            read = shouldPersistRead,
            progress = newProgress,
        )
        val shouldEmitNovelCompleted = becameRead && chapterOrderList.all { it.read }

        screenModelScope.launch(NonCancellable) {
            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = chapter.id,
                    read = shouldPersistRead,
                    lastPageRead = newProgress,
                ),
            )
            if (becameRead) {
                eventBus?.tryEmit(
                    AchievementEvent.NovelChapterRead(
                        novelId = chapter.novelId,
                        chapterNumber = chapter.chapterNumber.toInt(),
                    ),
                )
                if (shouldEmitNovelCompleted) {
                    eventBus?.tryEmit(AchievementEvent.NovelCompleted(chapter.novelId))
                }
            }
            val now = System.currentTimeMillis()
            saveHistorySnapshot(chapter.id, now - chapterReadStartTimeMs)
            chapterReadStartTimeMs = now
        }
    }

    private fun applyLocalChapterProgress(
        chapter: NovelChapter,
        read: Boolean,
        progress: Long,
    ) {
        val updatedChapter = chapter.copy(
            read = read,
            lastPageRead = progress,
        )
        currentChapter = updatedChapter
        chapterOrderList = chapterOrderList.map { existing ->
            if (existing.id == chapter.id) {
                existing.copy(
                    read = read,
                    lastPageRead = progress,
                )
            } else {
                existing
            }
        }
        val currentState = mutableState.value
        if (currentState is State.Success) {
            val decodedNativeProgress = decodeNativeScrollProgress(progress)
            val decodedWebProgressPercent = decodeWebScrollProgressPercent(progress)
            val lastSavedIndex = when {
                decodedNativeProgress != null -> decodedNativeProgress.index
                decodedWebProgressPercent != null -> currentState.lastSavedIndex
                else -> progress.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            val lastSavedScrollOffsetPx = decodedNativeProgress?.offsetPx ?: 0
            val lastSavedWebProgressPercent = when {
                decodedWebProgressPercent != null -> decodedWebProgressPercent
                decodedNativeProgress != null -> currentState.lastSavedWebProgressPercent
                else -> progress.coerceIn(0L, 100L).toInt()
            }
            mutableState.value = currentState.copy(
                chapter = updatedChapter,
                lastSavedIndex = lastSavedIndex,
                lastSavedScrollOffsetPx = lastSavedScrollOffsetPx,
                lastSavedWebProgressPercent = lastSavedWebProgressPercent,
            )
        }
    }

    fun toggleChapterBookmark() {
        val chapter = currentChapter ?: return
        val bookmarked = !chapter.bookmark
        val updatedChapter = chapter.copy(bookmark = bookmarked)
        currentChapter = updatedChapter
        lastSavedRead = updatedChapter.read
        lastSavedProgress = updatedChapter.lastPageRead
        val state = mutableState.value
        if (state is State.Success) {
            mutableState.value = state.copy(chapter = updatedChapter)
        }
        screenModelScope.launch {
            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = chapter.id,
                    bookmark = bookmarked,
                ),
            )
        }
    }

    private fun extractTextBlocks(rawHtml: String): List<String> {
        val document = Jsoup.parse(rawHtml)
        val paragraphLikeNodes = document.select("p, li, blockquote, h1, h2, h3, h4, h5, h6, pre")
            .filterNot { node -> node.tagName().equals("p", ignoreCase = true) && node.parent()?.tagName()?.equals("li", ignoreCase = true) == true }
            .map { element -> element.text().sanitizeTextBlock() }
            .filter { it.isNotBlank() }
        if (paragraphLikeNodes.isNotEmpty()) {
            return paragraphLikeNodes
        }

        val text = (document.body()?.wholeText() ?: document.text())
            .sanitizeTextBlock()
        if (text.isBlank()) return emptyList()

        return text.split(Regex("\n{2,}"))
            .flatMap { block -> block.split('\n') }
            .map { it.sanitizeTextBlock() }
            .filter { it.isNotBlank() }
    }

    private fun extractContentBlocks(
        rawHtml: String,
        chapterWebUrl: String?,
        novelUrl: String,
        pluginSite: String?,
    ): List<ContentBlock> {
        val document = Jsoup.parse(rawHtml)
        val blocks = mutableListOf<ContentBlock>()
        val candidates = document.body()?.select("p, li, blockquote, h1, h2, h3, h4, h5, h6, pre, img")
            ?.filterNot { node -> node.tagName().equals("p", ignoreCase = true) && node.parent()?.tagName()?.equals("li", ignoreCase = true) == true }
            .orEmpty()

        for (element in candidates) {
            if (element.tagName().equals("img", ignoreCase = true)) {
                val rawUrl = element.attr("src")
                    .ifBlank { element.attr("data-src") }
                    .ifBlank { element.attr("data-original") }
                    .trim()
                if (rawUrl.isBlank()) continue
                val resolvedUrl = resolveContentResourceUrl(
                    rawUrl = rawUrl,
                    chapterWebUrl = chapterWebUrl,
                    novelUrl = novelUrl,
                    pluginSite = pluginSite,
                ) ?: continue
                blocks += ContentBlock.Image(
                    url = resolvedUrl,
                    alt = element.attr("alt").sanitizeTextBlock().ifBlank { null },
                )
            } else {
                val text = element.text().sanitizeTextBlock()
                if (text.isNotBlank()) {
                    val structuredBlocks = parseStructuredFragmentToBlocks(
                        rawPayload = text,
                        chapterWebUrl = chapterWebUrl,
                        novelUrl = novelUrl,
                        pluginSite = pluginSite,
                    )
                    if (structuredBlocks.isNotEmpty()) {
                        blocks += structuredBlocks
                        continue
                    }
                    val normalizedText = if (element.tagName().equals("li", ignoreCase = true)) {
                        "• $text"
                    } else {
                        text
                    }
                    blocks += ContentBlock.Text(normalizedText)
                }
            }
        }

        return blocks
    }

    private fun parseStructuredFragmentToBlocks(
        rawPayload: String,
        chapterWebUrl: String?,
        novelUrl: String,
        pluginSite: String?,
    ): List<ContentBlock> {
        if (!looksLikeStructuredPayload(rawPayload)) return emptyList()

        val parsedRoot = parseStructuredRoot(rawPayload)
        val renderedHtml = if (parsedRoot != null) {
            val attachmentUrls = extractStructuredAttachmentUrls(parsedRoot)
            val structuredNode = findStructuredNode(parsedRoot) ?: return emptyList()
            renderStructuredElementAsHtml(structuredNode, attachmentUrls)
        } else {
            renderStructuredPayloadFallback(rawPayload).orEmpty()
        }.trim()
        if (renderedHtml.isBlank()) return emptyList()

        val renderedDoc = Jsoup.parse("<div>$renderedHtml</div>")
        val renderedCandidates = renderedDoc.select("p, li, blockquote, h1, h2, h3, h4, h5, h6, pre, img")
            .filterNot { node -> node.tagName().equals("p", ignoreCase = true) && node.parent()?.tagName()?.equals("li", ignoreCase = true) == true }

        return renderedCandidates.mapNotNull { candidate ->
            if (candidate.tagName().equals("img", ignoreCase = true)) {
                val rawUrl = candidate.attr("src")
                    .ifBlank { candidate.attr("data-src") }
                    .ifBlank { candidate.attr("data-original") }
                    .trim()
                val resolvedUrl = resolveContentResourceUrl(
                    rawUrl = rawUrl,
                    chapterWebUrl = chapterWebUrl,
                    novelUrl = novelUrl,
                    pluginSite = pluginSite,
                ) ?: return@mapNotNull null
                ContentBlock.Image(
                    url = resolvedUrl,
                    alt = candidate.attr("alt").sanitizeTextBlock().ifBlank { null },
                )
            } else {
                val candidateText = candidate.text().sanitizeTextBlock()
                if (candidateText.isBlank()) {
                    null
                } else {
                    val normalized = if (candidate.tagName().equals("li", ignoreCase = true)) {
                        "• $candidateText"
                    } else {
                        candidateText
                    }
                    ContentBlock.Text(normalized)
                }
            }
        }
    }

    private fun resolveContentResourceUrl(
        rawUrl: String,
        chapterWebUrl: String?,
        novelUrl: String,
        pluginSite: String?,
    ): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("data:image/", ignoreCase = true)) {
            return trimmed
        }
        if (NovelPluginImage.isSupported(trimmed)) {
            return trimmed
        }
        if (trimmed.startsWith("blob:", ignoreCase = true)) {
            return null
        }

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

    private fun normalizeHtml(
        rawHtml: String,
        settings: NovelReaderSettings,
        customCss: String?,
        customJs: String?,
    ): String {
        val css = customCss?.takeIf { it.isNotBlank() }
        val js = customJs?.takeIf { it.isNotBlank() }
        val isDarkTheme = when (settings.theme) {
            NovelReaderTheme.SYSTEM -> isSystemDark()
            NovelReaderTheme.DARK -> true
            NovelReaderTheme.LIGHT -> false
        }
        val background = if (isDarkTheme) "#121212" else "#FFFFFF"
        val textColor = if (isDarkTheme) "#EDEDED" else "#1A1A1A"
        val linkColor = if (isDarkTheme) "#80B4FF" else "#1E3A8A"
        val baseStyle = """
            body {
              padding: ${settings.margin}px;
              line-height: ${settings.lineHeight};
              font-size: ${settings.fontSize}px;
              background: $background;
              color: $textColor;
              word-break: break-word;
            }
            img { max-width: 100%; height: auto; }
            a { color: $linkColor; }
        """.trimIndent()
        val injection = buildString {
            append("<style>")
            append('\n')
            append(baseStyle)
            if (css != null) {
                append('\n')
                append(css)
            }
            append('\n')
            append("</style>")
            if (js != null) {
                append('\n')
                append("<script>")
                append('\n')
                append(js)
                append('\n')
                append("</script>")
            }
        }

        if (rawHtml.contains("<html", ignoreCase = true)) {
            return if (injection.isNotBlank()) injectIntoHtml(rawHtml, injection) else rawHtml
        }

        val style = buildString {
            append(baseStyle)
            if (css != null) {
                append('\n')
                append(css)
            }
        }

        return """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <style>$style</style>
                ${js?.let { "<script>\n$it\n</script>" } ?: ""}
              </head>
              <body>
                $rawHtml
              </body>
            </html>
        """.trimIndent()
    }

    private fun injectIntoHtml(rawHtml: String, injection: String): String {
        val headClose = Regex("</head>", RegexOption.IGNORE_CASE)
        if (headClose.containsMatchIn(rawHtml)) {
            return rawHtml.replaceFirst(headClose, "$injection</head>")
        }
        val headOpen = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
        val headMatch = headOpen.find(rawHtml)
        if (headMatch != null) {
            return rawHtml.replaceRange(headMatch.range, headMatch.value + injection)
        }
        val bodyClose = Regex("</body>", RegexOption.IGNORE_CASE)
        if (bodyClose.containsMatchIn(rawHtml)) {
            return rawHtml.replaceFirst(bodyClose, "$injection</body>")
        }
        return injection + rawHtml
    }

    private fun String.sanitizeTextBlock(): String {
        return this
            .replace('\u00A0', ' ')
            .replace("\r", "")
            .trim()
    }

    private fun String.prependChapterHeadingIfMissing(chapterName: String?): String {
        val headingText = chapterName.orEmpty().sanitizeTextBlock()
        if (headingText.isBlank()) return this

        return runCatching {
            val document = Jsoup.parseBodyFragment(this)
            val body = document.body() ?: return this
            if (body.select("h1, h2, h3").isNotEmpty()) {
                return body.html()
            }

            body.prependElement("h1")
                .addClass("an-reader-chapter-title")
                .text(headingText)
            body.html()
        }.getOrDefault(this)
    }

    private fun String.normalizeStructuredChapterPayload(): String {
        val trimmedPayload = trim()
        if (looksLikeHtmlPayload(trimmedPayload)) {
            return this
        }

        val parsedRoot = parseStructuredRoot(this)
        if (parsedRoot != null) {
            val attachmentUrls = extractStructuredAttachmentUrls(parsedRoot)
            val structuredNode = findStructuredNode(parsedRoot) ?: return this
            val rendered = renderStructuredElementAsHtml(
                element = structuredNode,
                attachmentUrls = attachmentUrls,
            ).trim()
            if (rendered.isNotBlank()) {
                return "<div>$rendered</div>"
            }
        }

        val fallbackRendered = renderStructuredPayloadFallback(this).orEmpty().trim()
        return if (fallbackRendered.isBlank()) this else "<div>$fallbackRendered</div>"
    }

    private fun parseStructuredRoot(rawPayload: String): JsonElement? {
        val trimmed = rawPayload
            .trim()
            .removePrefix("\uFEFF")
            .trim()
        if (!looksLikeStructuredPayload(trimmed)) return null

        val parseCandidates = linkedSetOf(trimmed)
        extractJsonCandidate(trimmed)?.let { parseCandidates += it }
        normalizeJsonLikePayload(trimmed)?.let { parseCandidates += it }

        parseCandidates.forEach { candidate ->
            val parsed = parseStructuredCandidate(candidate, decodeDepth = 0) ?: return@forEach
            if (parsed is JsonObject || parsed is JsonArray) {
                return parsed
            }
        }

        return null
    }

    private fun parseStructuredCandidate(
        candidate: String,
        decodeDepth: Int,
    ): JsonElement? {
        if (decodeDepth > 4) return null

        val trimmed = candidate.trim().trimEnd(';').trim()
        if (trimmed.isBlank()) return null

        val directParsed = runCatching { structuredJson.parseToJsonElement(trimmed) }.getOrNull()
        if (directParsed != null) {
            if (directParsed is JsonObject || directParsed is JsonArray) {
                return directParsed
            }

            val primitiveContent = (directParsed as? JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                .orEmpty()
            if (looksLikeStructuredPayload(primitiveContent)) {
                return parseStructuredCandidate(primitiveContent, decodeDepth + 1)
            }
        }

        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            val decoded = runCatching { structuredJson.decodeFromString<String>(trimmed) }.getOrNull()
            if (!decoded.isNullOrBlank()) {
                return parseStructuredCandidate(decoded, decodeDepth + 1)
            }
        }

        val normalizedCandidate = normalizeJsonLikePayload(trimmed)
            ?.takeIf { it != trimmed }
            ?: return null
        return parseStructuredCandidate(normalizedCandidate, decodeDepth + 1)
    }

    private fun looksLikeStructuredPayload(rawValue: String): Boolean {
        if (rawValue.isBlank()) return false
        val trimmed = rawValue.trim()
        return trimmed.startsWith("{") ||
            trimmed.startsWith("[") ||
            trimmed.startsWith("\"{") ||
            trimmed.startsWith("\"[") ||
            trimmed.startsWith("'{") ||
            trimmed.startsWith("'[") ||
            trimmed.startsWith("{\\\"") ||
            trimmed.startsWith("[\\\"") ||
            (trimmed.contains("\"type\"") && trimmed.contains("content")) ||
            (trimmed.contains("'type'") && trimmed.contains("content"))
    }

    private fun extractJsonCandidate(rawPayload: String): String? {
        val trimmed = rawPayload.trim()
        if (trimmed.startsWith("<")) {
            val htmlTextCandidate = Jsoup.parse(trimmed).body()?.wholeText().orEmpty().trim()
            if (looksLikeStructuredPayload(htmlTextCandidate)) {
                return htmlTextCandidate
            }
        }

        val objectStart = trimmed.indexOf('{').takeIf { it >= 0 } ?: trimmed.indexOf('[').takeIf { it >= 0 } ?: return null
        val objectEnd = trimmed.lastIndexOf('}').takeIf { it > objectStart }
            ?: trimmed.lastIndexOf(']').takeIf { it > objectStart }
            ?: return null
        return trimmed.substring(objectStart, objectEnd + 1).trim()
    }

    private fun normalizeJsonLikePayload(rawPayload: String): String? {
        var candidate = rawPayload
            .trim()
            .removePrefix("\uFEFF")
            .trim()

        if (candidate.startsWith("return ")) {
            candidate = candidate.removePrefix("return ").trim()
        }
        candidate = candidate.trimEnd(';').trim()
        if (!looksLikeStructuredPayload(candidate)) return null

        if (candidate.startsWith("'") && candidate.endsWith("'")) {
            val inner = candidate.substring(1, candidate.lastIndex).replace("\"", "\\\"")
            candidate = "\"$inner\""
        }

        if (candidate.contains("\\\"")) {
            candidate = candidate.replace("\\\"", "\"")
        }
        if (candidate.contains("\\n")) {
            candidate = candidate.replace("\\n", "\n")
        }
        if (candidate.contains("\\t")) {
            candidate = candidate.replace("\\t", "\t")
        }

        candidate = Regex("([\\{,]\\s*)([A-Za-z_][A-Za-z0-9_\\-]*)(\\s*:)").replace(candidate, "$1\"$2\"$3")
        candidate = Regex("\"([A-Za-z_][A-Za-z0-9_\\-]*)\\s*:\\s*\"").replace(candidate, "\"$1\":\"")
        candidate = Regex("'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'").replace(candidate) { match ->
            "\"${match.groupValues[1].replace("\"", "\\\"")}\""
        }
        candidate = Regex(",\\s*([}\\]])").replace(candidate, "$1")
        return candidate
    }

    private fun findStructuredNode(element: JsonElement): JsonElement? {
        return when (element) {
            is JsonObject -> {
                if (isStructuredNode(element)) {
                    element
                } else {
                    listOf("content", "data", "body", "result", "payload", "value", "chapter")
                        .firstNotNullOfOrNull { key ->
                            val nested = element[key] ?: return@firstNotNullOfOrNull null
                            findStructuredNode(nested)
                                ?: parseStructuredRoot(nested.asStringOrNull().orEmpty())?.let(::findStructuredNode)
                        }
                }
            }
            is JsonArray -> {
                val hasStructuredObjects = element.any {
                    (it as? JsonObject)?.let(::isStructuredNode) == true
                }
                if (hasStructuredObjects) element else null
            }
            else -> null
        }
    }

    private fun isStructuredNode(element: JsonObject): Boolean {
        val normalizedType = normalizeStructuredType(element["type"].asStringOrNull())
        if (normalizedType != null && normalizedType in STRUCTURED_NODE_TYPES) {
            return true
        }
        return (element["content"] is JsonArray) ||
            (element["content"] is JsonObject) ||
            (element["text"].asStringOrNull() != null) ||
            (element["attrs"] is JsonObject)
    }

    private fun extractStructuredAttachmentUrls(root: JsonElement): Map<String, String> {
        val rootObject = root as? JsonObject ?: return emptyMap()
        val mapping = mutableMapOf<String, String>()

        fun appendAttachmentMapping(attachment: JsonObject) {
            val url = attachment["url"].asStringOrNull()?.trim().orEmpty()
            if (url.isBlank()) return
            attachment["id"].asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { key ->
                mapping[key] = url
            }
            attachment["name"].asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { key ->
                mapping[key] = url
            }
        }

        when (val attachments = rootObject["attachments"]) {
            is JsonArray -> attachments.forEach { entry ->
                val attachment = entry as? JsonObject ?: return@forEach
                appendAttachmentMapping(attachment)
            }
            is JsonObject -> attachments.forEach { (key, value) ->
                val valueObject = value as? JsonObject
                val url = valueObject?.get("url").asStringOrNull()?.trim().orEmpty()
                    .ifBlank { value.asStringOrNull().orEmpty().trim() }
                if (url.isNotBlank()) {
                    mapping[key.trim()] = url
                }
            }
            else -> Unit
        }
        return mapping
    }

    private fun renderStructuredElementAsHtml(
        element: JsonElement,
        attachmentUrls: Map<String, String>,
    ): String {
        return when (element) {
            is JsonObject -> renderStructuredNodeAsHtml(element, attachmentUrls)
            is JsonArray -> buildString {
                element.forEach { node ->
                    append(renderStructuredElementAsHtml(node, attachmentUrls))
                }
            }
            else -> ""
        }
    }

    private fun renderStructuredNodeAsHtml(
        node: JsonObject,
        attachmentUrls: Map<String, String>,
    ): String {
        val type = normalizeStructuredType(node["type"].asStringOrNull()).orEmpty()
        val attrs = node["attrs"] as? JsonObject
        val children = node["content"] as? JsonArray

        fun renderChildren(): String {
            if (children == null) return ""
            return buildString {
                children.forEach { child ->
                    append(renderStructuredElementAsHtml(child, attachmentUrls))
                }
            }
        }

        return when (type) {
            "doc" -> renderChildren()
            "paragraph" -> "<p>${renderChildren()}</p>"
            "heading" -> {
                val level = attrs?.get("level").asIntOrNull()?.coerceIn(1, 6) ?: 1
                "<h$level>${renderChildren()}</h$level>"
            }
            "bulletlist" -> "<ul>${renderChildren()}</ul>"
            "orderedlist" -> "<ol>${renderChildren()}</ol>"
            "listitem" -> "<li>${renderChildren()}</li>"
            "blockquote" -> "<blockquote>${renderChildren()}</blockquote>"
            "hardbreak" -> "<br/>"
            "horizontalrule" -> "<hr/>"
            "image" -> renderStructuredImageNode(attrs, attachmentUrls)
            "text" -> {
                val escaped = node["text"].asStringOrNull().orEmpty().escapeHtml()
                applyStructuredMarks(escaped, node["marks"] as? JsonArray)
            }
            else -> {
                val inlineText = node["text"]
                    .asStringOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.escapeHtml()
                if (inlineText != null) {
                    applyStructuredMarks(inlineText, node["marks"] as? JsonArray)
                } else {
                    renderChildren()
                }
            }
        }
    }

    private fun renderStructuredImageNode(
        attrs: JsonObject?,
        attachmentUrls: Map<String, String>,
    ): String {
        if (attrs == null) return ""
        val directUrl = attrs["src"].asStringOrNull()?.trim().orEmpty()
        val altText = attrs["alt"].asStringOrNull().orEmpty().escapeHtml()
        if (directUrl.isNotBlank()) {
            return "<img src=\"${directUrl.escapeHtmlAttribute()}\" alt=\"$altText\" />"
        }

        val imageReferences = mutableListOf<String>()
        attrs["image"].asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { imageReferences += it }
        when (val imagesNode = attrs["images"]) {
            is JsonArray -> imagesNode.forEach { entry ->
                when (entry) {
                    is JsonObject -> {
                        entry["image"].asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { imageReferences += it }
                    }
                    is JsonPrimitive -> entry.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let { imageReferences += it }
                    else -> Unit
                }
            }
            is JsonObject -> {
                imagesNode["image"].asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { imageReferences += it }
            }
            else -> Unit
        }
        val resolvedUrls = imageReferences.mapNotNull { reference ->
            attachmentUrls[reference]
        }
        if (resolvedUrls.isEmpty()) return ""
        return resolvedUrls.joinToString(separator = "") { url ->
            "<img src=\"${url.escapeHtmlAttribute()}\" alt=\"$altText\" />"
        }
    }

    private fun applyStructuredMarks(
        text: String,
        marks: JsonArray?,
    ): String {
        if (marks == null || marks.isEmpty()) return text

        var rendered = text
        marks.forEach { markElement ->
            val mark = markElement as? JsonObject ?: return@forEach
            rendered = when (normalizeStructuredType(mark["type"].asStringOrNull())) {
                "bold", "strong" -> "<strong>$rendered</strong>"
                "italic", "em" -> "<em>$rendered</em>"
                "underline" -> "<u>$rendered</u>"
                "strike", "s" -> "<s>$rendered</s>"
                "code" -> "<code>$rendered</code>"
                "link" -> {
                    val href = (mark["attrs"] as? JsonObject)
                        ?.get("href")
                        .asStringOrNull()
                        .orEmpty()
                    if (href.isBlank()) rendered else "<a href=\"${href.escapeHtmlAttribute()}\">$rendered</a>"
                }
                else -> rendered
            }
        }
        return rendered
    }

    private fun JsonElement?.asStringOrNull(): String? {
        return (this as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        return (this as? JsonPrimitive)?.intOrNull
    }

    private fun String.escapeHtml(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun String.escapeHtmlAttribute(): String {
        return escapeHtml()
    }

    private fun renderStructuredPayloadFallback(rawPayload: String): String? {
        val candidate = extractJsonCandidate(rawPayload) ?: rawPayload.trim()
        if (!looksLikeStructuredPayload(candidate)) return null

        val normalized = normalizeJsonLikePayload(candidate) ?: candidate
        val textSegments = extractStructuredTextFallbackSegments(normalized)
        val imageSegments = extractStructuredImageFallbackUrls(normalized)

        if (textSegments.isEmpty() && imageSegments.isEmpty()) return null

        val html = buildString {
            textSegments.forEach { segment ->
                append("<p>${segment.escapeHtml()}</p>")
            }
            imageSegments.forEach { url ->
                append("<img src=\"${url.escapeHtmlAttribute()}\" alt=\"\" />")
            }
        }.trim()
        return html.takeIf { it.isNotBlank() }
    }

    private fun looksLikeHtmlPayload(rawPayload: String): Boolean {
        val trimmed = rawPayload.trim()
        if (!trimmed.contains('<') || !trimmed.contains('>')) return false
        return Regex("(?is)<\\s*(html|body|div|main|article|section|p|ul|ol|li|h1|h2|h3|h4|h5|h6|span)\\b")
            .containsMatchIn(trimmed)
    }

    private fun extractStructuredTextFallbackSegments(payload: String): List<String> {
        val results = mutableListOf<String>()
        val textRegex = Regex("(?is)\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        textRegex.findAll(payload).forEach { match ->
            val rawText = match.groupValues.getOrNull(1).orEmpty()
            val decodedText = rawText
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "")
                .replace("\\\"", "\"")
                .replace("\\u00A0", " ")
                .sanitizeTextBlock()
            if (decodedText.isBlank()) return@forEach

            val contextStart = (match.range.first - 220).coerceAtLeast(0)
            val context = payload.substring(contextStart, match.range.first).lowercase()
            val isListItemContext = context.contains("listitem") || context.contains("bulletlist")
            val normalized = if (isListItemContext && !decodedText.startsWith("•")) {
                "• $decodedText"
            } else {
                decodedText
            }
            results += normalized
        }
        return results
    }

    private fun extractStructuredImageFallbackUrls(payload: String): List<String> {
        val urlRegex = Regex("(?is)\"url\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val directHttpRegex = Regex("(?i)https?://[^\\s\"'<>]+\\.(?:png|jpe?g|gif|webp|bmp|svg)")
        val urls = linkedSetOf<String>()

        urlRegex.findAll(payload).forEach { match ->
            val url = match.groupValues.getOrNull(1).orEmpty()
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .trim()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls += url
            }
        }

        directHttpRegex.findAll(payload).forEach { match ->
            val url = match.value.trim()
            if (url.isNotBlank()) {
                urls += url
            }
        }

        return urls.toList()
    }

    private fun normalizeStructuredType(type: String?): String? {
        return type
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
    }

    private suspend fun saveHistorySnapshot(chapterId: Long, sessionReadDurationMs: Long) {
        runCatching {
            resolvedHistoryRepository?.upsertNovelHistory(
                NovelHistoryUpdate(
                    chapterId = chapterId,
                    readAt = Date(),
                    sessionReadDuration = sessionReadDurationMs.coerceAtLeast(0L),
                ),
            )
        }.onFailure { error ->
            logcat(LogPriority.ERROR, error) { "Failed to save novel history snapshot" }
        }
    }

    sealed interface State {
        data object Loading : State
        data class Error(val message: String?) : State
        data class Success(
            val novel: Novel,
            val chapter: NovelChapter,
            val html: String,
            val enableJs: Boolean,
            val readerSettings: NovelReaderSettings,
            val contentBlocks: List<ContentBlock>,
            val textBlocks: List<String>,
            val lastSavedIndex: Int,
            val lastSavedScrollOffsetPx: Int,
            val lastSavedWebProgressPercent: Int,
            val previousChapterId: Long?,
            val nextChapterId: Long?,
            val chapterWebUrl: String?,
        ) : State
    }

    sealed interface ContentBlock {
        data class Text(val text: String) : ContentBlock
        data class Image(val url: String, val alt: String?) : ContentBlock
    }

    companion object {
        private val STRUCTURED_NODE_TYPES = setOf(
            "doc",
            "paragraph",
            "heading",
            "bulletlist",
            "orderedlist",
            "listitem",
            "blockquote",
            "hardbreak",
            "horizontalrule",
            "image",
            "text",
        )
    }
}
