package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings

/**
 * Manages chapter text and translations as a single canonical source.
 *
 * Eliminates the duplication pattern where rawHtml, content blocks,
 * geminiTranslatedByIndex and googleTranslatedByIndex all held
 * separate copies of chapter text in parallel maps.
 *
 * The rawHtml (canonicalHtml) is the canonical source. Text blocks are lazily extracted once.
 */
class NovelReaderContentModel(
    val canonicalHtml: String,
    val chapterWebUrl: String? = null,
    val novelUrl: String = "",
    val pluginSite: String? = null,
) {
    /** Parsed content blocks — extracted once from canonicalHtml. */
    val contentBlocks: List<NovelReaderScreenModel.ContentBlock> by lazy {
        extractContentBlocks(
            rawHtml = canonicalHtml,
            chapterWebUrl = chapterWebUrl,
            novelUrl = novelUrl,
            pluginSite = pluginSite,
        ).ifEmpty {
            extractTextBlocks(canonicalHtml).map(NovelReaderScreenModel.ContentBlock::Text)
        }
    }

    /** Text-only blocks (no images), cached after contentBlocks init. */
    val textBlocks: List<String> by lazy {
        contentBlocks
            .filterIsInstance<NovelReaderScreenModel.ContentBlock.Text>()
            .map { it.text }
    }

    /** Rich content parse result — cached once. */
    internal var parsedRichContentResult: NovelRichContentParseResult? = null

    /** Cached normalized HTML by settings key to avoid recalculating */
    private var cachedNormalizedHtml: String? = null
    private var lastSettings: NovelReaderSettings? = null
    private var lastCustomCss: String? = null
    private var lastCustomJs: String? = null

    fun getNormalizedHtml(
        settings: NovelReaderSettings,
        customCss: String?,
        customJs: String?,
    ): String {
        val css = customCss
        val js = customJs
        val cached = cachedNormalizedHtml
        if (cached != null &&
            lastSettings == settings &&
            lastCustomCss == css &&
            lastCustomJs == js
        ) {
            return cached
        }
        val result = normalizeHtml(canonicalHtml, settings, css, js)
        cachedNormalizedHtml = result
        lastSettings = settings
        lastCustomCss = css
        lastCustomJs = js
        return result
    }
}
