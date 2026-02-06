package eu.kanade.tachiyomi.ui.reader.novel

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.domain.items.novelchapter.model.toSNovelChapter
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import android.app.Application
import eu.kanade.tachiyomi.util.system.isNightMode
import kotlinx.coroutines.Job
import org.jsoup.Jsoup
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate

class NovelReaderScreenModel(
    private val chapterId: Long,
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val pluginStorage: NovelPluginStorage = Injekt.get(),
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    private val isSystemDark: () -> Boolean = { Injekt.get<Application>().isNightMode() },
) : StateScreenModel<NovelReaderScreenModel.State>(State.Loading) {

    private var settingsJob: Job? = null
    private var rawHtml: String? = null
    private var currentNovel: Novel? = null
    private var currentChapter: NovelChapter? = null
    private var chapterOrderList: List<NovelChapter> = emptyList()
    private var customCss: String? = null
    private var customJs: String? = null
    private var lastSavedProgress: Long? = null
    private var lastSavedRead: Boolean? = null

    init {
        screenModelScope.launch {
            loadChapter()
        }
    }

    private suspend fun loadChapter() {
        val chapter = novelChapterRepository.getChapterById(chapterId)
            ?: return setError("Chapter not found")
        val novel = getNovel.await(chapter.novelId)
            ?: return setError("Novel not found")
        val source = sourceManager.get(novel.source)
            ?: return setError("Source not found")
        chapterOrderList = novelChapterRepository.getChapterByNovelId(novel.id)
            .sortedBy { it.sourceOrder }

        val html = try {
            source.getChapterText(chapter.toSNovelChapter())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load novel chapter text" }
            return setError(e.message)
        }

        val pluginPackage = pluginStorage.getAll()
            .firstOrNull { it.entry.id.hashCode().toLong() == novel.source }
        rawHtml = html
        currentNovel = novel
        currentChapter = chapter
        lastSavedProgress = chapter.lastPageRead
        lastSavedRead = chapter.read
        customCss = pluginPackage?.customCss?.toString(Charsets.UTF_8)
        customJs = pluginPackage?.customJs?.toString(Charsets.UTF_8)

        settingsJob?.cancel()
        settingsJob = screenModelScope.launch {
            novelReaderPreferences.settingsFlow(novel.source).collect { settings ->
                updateContent(settings)
            }
        }
        updateContent(novelReaderPreferences.resolveSettings(novel.source))
    }

    private fun setError(message: String?) {
        mutableState.value = State.Error(message)
    }

    private fun updateContent(settings: NovelReaderSettings) {
        val html = rawHtml ?: return
        val novel = currentNovel ?: return
        val chapter = currentChapter ?: return
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
        val textBlocks = extractTextBlocks(html)
        mutableState.value = State.Success(
            novel = novel,
            chapter = chapter,
            html = content,
            enableJs = !pluginJs.isNullOrBlank(),
            textBlocks = textBlocks,
            lastSavedIndex = chapter.lastPageRead.toInt(),
            previousChapterId = chapterNavigation.first,
            nextChapterId = chapterNavigation.second,
        )
    }

    fun updateReadingProgress(currentIndex: Int, totalItems: Int) {
        val chapter = currentChapter ?: return
        if (totalItems <= 0 || currentIndex < 0) return

        val shouldMarkRead = ((currentIndex + 1).toFloat() / totalItems.toFloat()) >= 0.95f
        val newProgress = if (shouldMarkRead) 0L else currentIndex.toLong()

        if (lastSavedRead == shouldMarkRead && lastSavedProgress == newProgress) {
            return
        }

        lastSavedRead = shouldMarkRead
        lastSavedProgress = newProgress

        screenModelScope.launch {
            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = chapter.id,
                    read = shouldMarkRead,
                    lastPageRead = newProgress,
                ),
            )
        }
    }

    private fun extractTextBlocks(rawHtml: String): List<String> {
        val text = Jsoup.parse(rawHtml)
            .text()
            .replace("\r", "")
            .trim()
        if (text.isBlank()) return emptyList()
        return text
            .split(Regex("\n{2,}"))
            .flatMap { it.split('\n') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
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

    sealed interface State {
        data object Loading : State
        data class Error(val message: String?) : State
        data class Success(
            val novel: Novel,
            val chapter: NovelChapter,
            val html: String,
            val enableJs: Boolean,
            val textBlocks: List<String>,
            val lastSavedIndex: Int,
            val previousChapterId: Long?,
            val nextChapterId: Long?,
        ) : State
    }
}
