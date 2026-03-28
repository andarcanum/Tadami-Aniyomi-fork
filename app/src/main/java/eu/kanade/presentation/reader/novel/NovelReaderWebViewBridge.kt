package eu.kanade.presentation.reader.novel

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import org.json.JSONObject
import java.io.File
import java.net.URLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

internal const val WEB_READER_STYLE_ELEMENT_ID = "__an_reader_style__"
internal const val WEB_READER_BOOTSTRAP_STYLE_ELEMENT_ID = "__an_reader_bootstrap_style__"
internal const val FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM = 2f
private const val EARLY_WEBVIEW_REVEAL_IMAGE_THRESHOLD = 6

private val webViewHtmlImageTagRegex = Regex("<img\\b", RegexOption.IGNORE_CASE)
private val hexNovelsPluginImageUrlRegex = Regex("""(?:novelimg|heximg)://hexnovels\b""", RegexOption.IGNORE_CASE)
internal val novelWordRegex = Regex("""[\p{L}\p{N}']+""")

internal fun buildInitialWebReaderHtml(
    rawHtml: String,
    readerCss: String,
): String {
    val injection = buildString {
        append("<style id=\"")
        append(WEB_READER_STYLE_ELEMENT_ID)
        append("\">")
        append(escapeCssForInlineStyleTag(readerCss))
        append("</style>")
        append("<style id=\"")
        append(WEB_READER_BOOTSTRAP_STYLE_ELEMENT_ID)
        append("\">")
        append(buildWebReaderBootstrapCss())
        append("</style>")
    }
    return injectHtmlFragmentIntoHead(rawHtml, injection)
}

internal fun escapeCssForInlineStyleTag(css: String): String {
    return css.replace("</style", "<\\\\/style", ignoreCase = true)
}

private fun buildWebReaderBootstrapCss(): String {
    return "html, body { visibility: hidden !important; }"
}

internal fun shouldUseEarlyWebViewReveal(rawHtml: String): Boolean {
    if (rawHtml.isBlank()) return false
    if (hexNovelsPluginImageUrlRegex.containsMatchIn(rawHtml)) return true

    val imageCount = webViewHtmlImageTagRegex
        .findAll(rawHtml)
        .take(EARLY_WEBVIEW_REVEAL_IMAGE_THRESHOLD)
        .count()
    return imageCount >= EARLY_WEBVIEW_REVEAL_IMAGE_THRESHOLD
}

internal fun shouldEnableJavaScriptInReaderWebView(
    pluginRequestsJavaScript: Boolean,
): Boolean {
    return pluginRequestsJavaScript
}

internal fun WebView.resolveWebViewContentHeightPx(): Int {
    val childHeight = getChildAt(0)?.height ?: 0
    val scaledContentHeight = (contentHeight * scale).roundToInt()
    return maxOf(childHeight, scaledContentHeight)
}

internal fun WebView.resolveCurrentWebViewProgressPercent(
    scrollYOverride: Int? = null,
): Int {
    val contentHeight = resolveWebViewContentHeightPx()
    val totalScrollable = resolveWebViewTotalScrollablePx(
        contentHeightPx = contentHeight,
        viewHeightPx = height,
    )
    return resolveWebViewScrollProgressPercent(
        scrollY = scrollYOverride ?: scrollY,
        totalScrollable = totalScrollable,
    )
}

internal fun WebView.restoreWebViewScroll(
    progressPercent: Int,
    maxAttempts: Int = 14,
    onComplete: (() -> Unit)? = null,
) {
    if (progressPercent <= 0) {
        onComplete?.invoke()
        return
    }

    fun attemptRestore(attempt: Int) {
        val totalScrollable = resolveWebViewTotalScrollablePx(
            contentHeightPx = resolveWebViewContentHeightPx(),
            viewHeightPx = height,
        )
        if (totalScrollable <= 0 && attempt < maxAttempts) {
            postDelayed({ attemptRestore(attempt + 1) }, 42L)
            return
        }
        if (totalScrollable > 0) {
            val targetY = ((progressPercent.toFloat() / 100f) * totalScrollable.toFloat()).roundToInt()
            scrollTo(0, targetY.coerceIn(0, totalScrollable))
        }
        onComplete?.invoke()
    }

    post { attemptRestore(0) }
}

internal fun WebView.revealReaderDocumentAndWebView() {
    evaluateJavascript(
        """
        (function() {
            const bootstrapStyle = document.getElementById('__an_reader_bootstrap_style__');
            if (bootstrapStyle) {
                bootstrapStyle.remove();
            }
        })();
        """.trimIndent(),
        { _ ->
            val reveal = {
                animate().cancel()
                if (alpha >= 1f) {
                    alpha = 1f
                } else {
                    animate()
                        .alpha(1f)
                        .setDuration(120L)
                        .start()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                postVisualStateCallback(
                    SystemClock.uptimeMillis(),
                    object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            post(reveal)
                        }
                    },
                )
            } else {
                post(reveal)
            }
        },
    )
}

internal fun WebView.applyReaderCss(
    fontFaceCss: String,
    paddingTop: Int,
    paddingBottom: Int,
    paddingHorizontal: Int,
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    paragraphSpacingPx: Int,
    textAlignCss: String?,
    firstLineIndentCss: String?,
    textColorHex: String,
    backgroundHex: String,
    appearanceMode: NovelReaderAppearanceMode,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    backgroundImageUrl: String?,
    fontFamilyName: String?,
    customCss: String,
    textShadowCss: String?,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
    bionicReadingEnabled: Boolean,
) {
    val css = buildWebReaderCssText(
        fontFaceCss = fontFaceCss,
        paddingTop = paddingTop,
        paddingBottom = paddingBottom,
        paddingHorizontal = paddingHorizontal,
        fontSizePx = fontSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        paragraphSpacingPx = paragraphSpacingPx,
        textAlignCss = textAlignCss,
        firstLineIndentCss = firstLineIndentCss,
        textColorHex = textColorHex,
        backgroundHex = backgroundHex,
        appearanceMode = appearanceMode,
        backgroundTexture = backgroundTexture,
        oledEdgeGradient = oledEdgeGradient,
        backgroundImageUrl = backgroundImageUrl,
        fontFamilyName = fontFamilyName,
        customCss = customCss,
        textShadowCss = textShadowCss,
        forceBoldText = forceBoldText,
        forceItalicText = forceItalicText,
    )
    val escapedFontFamily = fontFamilyName
        ?.replace("\\", "\\\\")
        ?.replace("'", "\\'")
    val shouldForceFontFamily = escapedFontFamily != null
    val quotedCss = JSONObject.quote(css)
    val fontFlag = if (shouldForceFontFamily) "true" else "false"
    val alignFlag = if (textAlignCss.isNullOrBlank()) "false" else "true"
    val firstLineIndentFlag = if (firstLineIndentCss.isNullOrBlank()) "false" else "true"
    evaluateJavascript(
        """
        (function() {
            const styleId = '$WEB_READER_STYLE_ELEMENT_ID';
            let style = document.getElementById(styleId);
            if (!style) {
                style = document.createElement('style');
                style.id = styleId;
                document.head.appendChild(style);
            }
            style.textContent = $quotedCss;

            const shouldForceFont = $fontFlag;
            const shouldForceAlign = $alignFlag;
            const shouldForceFirstLineIndent = $firstLineIndentFlag;
            const firstLineIndentTags = new Set(['p', 'div', 'article', 'section']);
            const emptyBlockTags = new Set(['p', 'div', 'article', 'section', 'li', 'blockquote', 'pre']);
            const invisibleSpaceRegex = /[\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]/g;
            const mediaContentSelector = 'img,svg,video,canvas,iframe,picture,hr';
            const root = document.body;
            if (!root) return;
            const nodes = root.querySelectorAll('*');
            for (const node of nodes) {
                if (!(node instanceof HTMLElement)) continue;
                if (shouldForceAlign) {
                    node.removeAttribute('align');
                }
                node.removeAttribute('bgcolor');
                node.removeAttribute('color');
                const tag = node.tagName.toLowerCase();
                if (
                    tag === 'img' || tag === 'svg' || tag === 'video' ||
                    tag === 'canvas' || tag === 'iframe' || tag === 'source' || tag === 'picture'
                ) {
                    continue;
                }
                if (emptyBlockTags.has(tag)) {
                    const normalizedText = (node.textContent || '')
                        .replace(invisibleSpaceRegex, ' ')
                        .trim();
                    const hasMediaContent = node.querySelector(mediaContentSelector) !== null;
                    if (!hasMediaContent && normalizedText.length === 0) {
                        node.style.setProperty('display', 'none', 'important');
                        node.style.setProperty('margin', '0', 'important');
                        node.style.setProperty('padding', '0', 'important');
                        continue;
                    }
                }
                node.style.setProperty('background-color', 'transparent', 'important');
                node.style.setProperty('color', 'var(--an-reader-fg)', 'important');
                if (shouldForceAlign) {
                    node.style.setProperty('text-align', 'var(--an-reader-align)', 'important');
                } else if (node.style.getPropertyValue('text-align').includes('--an-reader-align')) {
                    node.style.removeProperty('text-align');
                }
                if (shouldForceFirstLineIndent && firstLineIndentTags.has(tag)) {
                    node.style.setProperty('text-indent', 'var(--an-reader-first-line-indent)', 'important');
                } else if (node.style.getPropertyValue('text-indent').includes('--an-reader-first-line-indent')) {
                    node.style.removeProperty('text-indent');
                }
                node.style.setProperty('line-height', 'var(--an-reader-line-height)', 'important');
                if (shouldForceFont) {
                    node.style.setProperty('font-family', 'var(--an-reader-font)', 'important');
                }
            }
        })();
        """.trimIndent(),
        null,
    )
    val bionicJavascript = buildWebReaderBionicJavascript(bionicReadingEnabled)
    evaluateJavascript(
        if (bionicJavascript.isBlank()) buildWebReaderBionicResetJavascript() else bionicJavascript,
        null,
    )
}

private fun injectHtmlFragmentIntoHead(
    rawHtml: String,
    fragment: String,
): String {
    val headCloseRegex = Regex("</head>", RegexOption.IGNORE_CASE)
    if (headCloseRegex.containsMatchIn(rawHtml)) {
        return headCloseRegex.replaceFirst(rawHtml, "$fragment</head>")
    }

    val bodyOpenRegex = Regex("<body[^>]*>", RegexOption.IGNORE_CASE)
    val bodyOpenMatch = bodyOpenRegex.find(rawHtml)
    if (bodyOpenMatch != null) {
        val insertIndex = bodyOpenMatch.range.last + 1
        return rawHtml.substring(0, insertIndex) + fragment + rawHtml.substring(insertIndex)
    }

    return fragment + rawHtml
}

internal fun buildWebReaderCssText(
    fontFaceCss: String,
    paddingTop: Int,
    paddingBottom: Int,
    paddingHorizontal: Int,
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    paragraphSpacingPx: Int,
    textAlignCss: String?,
    firstLineIndentCss: String?,
    textColorHex: String,
    backgroundHex: String,
    appearanceMode: NovelReaderAppearanceMode,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    backgroundImageUrl: String?,
    fontFamilyName: String?,
    customCss: String,
    textShadowCss: String?,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
): String {
    val escapedFontFamily = fontFamilyName
        ?.replace("\\", "\\\\")
        ?.replace("'", "\\'")
    val fontVariable = escapedFontFamily?.let { "'$it', sans-serif" }.orEmpty()
    val resolvedParagraphSpacingPx = paragraphSpacingPx.coerceIn(0, 32)

    return buildString {
        append(fontFaceCss)
        append('\n')
        append(":root {\n")
        append("  --an-reader-bg: $backgroundHex;\n")
        append("  --an-reader-fg: $textColorHex;\n")
        if (!textAlignCss.isNullOrBlank()) {
            append("  --an-reader-align: $textAlignCss;\n")
        }
        if (!firstLineIndentCss.isNullOrBlank()) {
            append("  --an-reader-first-line-indent: $firstLineIndentCss;\n")
        }
        append("  --an-reader-size: ${fontSizePx}px;\n")
        append("  --an-reader-line-height: ${lineHeightMultiplier.coerceAtLeast(1f)};\n")
        append("  --an-reader-paragraph-spacing: ${resolvedParagraphSpacingPx}px;\n")
        if (!textShadowCss.isNullOrBlank()) {
            append("  --an-reader-text-shadow: $textShadowCss;\n")
        }
        if (fontVariable.isNotBlank()) {
            append("  --an-reader-font: $fontVariable;\n")
        }
        append("  --an-reader-font-weight: ${if (forceBoldText) "700" else "400"};\n")
        append("  --an-reader-font-style: ${if (forceItalicText) "italic" else "normal"};\n")
        append("}\n")
        append("html, body {\n")
        append("  margin: 0 !important;\n")
        append("  min-height: 0 !important;\n")
        append("  height: auto !important;\n")
        append("  background: var(--an-reader-bg) !important;\n")
        append("  color: var(--an-reader-fg) !important;\n")
        append("}\n")
        append("body {\n")
        append("  padding-top: ${paddingTop}px !important;\n")
        append("  padding-bottom: ${paddingBottom}px !important;\n")
        append("  padding-left: ${paddingHorizontal}px !important;\n")
        append("  padding-right: ${paddingHorizontal}px !important;\n")
        append("  font-size: var(--an-reader-size) !important;\n")
        append("  line-height: var(--an-reader-line-height) !important;\n")
        if (!textAlignCss.isNullOrBlank()) {
            append("  text-align: var(--an-reader-align) !important;\n")
        }
        append("  word-break: break-word !important;\n")
        append("  overflow-wrap: anywhere !important;\n")
        append("  -webkit-text-size-adjust: 100% !important;\n")
        if (!textShadowCss.isNullOrBlank()) {
            append("  text-shadow: var(--an-reader-text-shadow) !important;\n")
        }
        if (fontVariable.isNotBlank()) {
            append("  font-family: var(--an-reader-font) !important;\n")
        }
        append("  font-weight: var(--an-reader-font-weight) !important;\n")
        append("  font-style: var(--an-reader-font-style) !important;\n")
        append("}\n")
        append("body > * {\n")
        append("  margin-top: 0 !important;\n")
        append("  padding-top: 0 !important;\n")
        append("}\n")
        append("body h1, body h2, body h3, body h4, body h5, body h6 {\n")
        append("  line-height: 1.35 !important;\n")
        append("  font-weight: 600 !important;\n")
        append("}\n")
        append("body h1 {\n")
        append("  font-size: 1.24em !important;\n")
        append("  margin-top: 0 !important;\n")
        append("  margin-bottom: 0.7em !important;\n")
        append("}\n")
        append("body p, body div, body article, body section, body blockquote, body pre, body li {\n")
        append("  margin-bottom: ${resolvedParagraphSpacingPx}px !important;\n")
        append("}\n")
        append("body span.an-reader-bionic {\n")
        append("  font-weight: 600 !important;\n")
        append("}\n")
        append("body h2 {\n")
        append("  font-size: 1.12em !important;\n")
        append("}\n")
        append("body h3 {\n")
        append("  font-size: 1.06em !important;\n")
        append("}\n")
        append("body .an-reader-chapter-title {\n")
        append("  font-size: 1.16em !important;\n")
        append("  font-weight: 600 !important;\n")
        append("  margin-top: 0 !important;\n")
        append("  margin-bottom: 0.85em !important;\n")
        append("}\n")
        append("body > :first-child {\n")
        append("  margin-top: 0 !important;\n")
        append("  padding-top: 0 !important;\n")
        append("}\n")
        append("body > :first-child > :first-child,\n")
        append("body > :first-child > :first-child > :first-child {\n")
        append("  margin-top: 0 !important;\n")
        append("  padding-top: 0 !important;\n")
        append("}\n")
        append("body > :last-child {\n")
        append("  margin-bottom: 0 !important;\n")
        append("  padding-bottom: 0 !important;\n")
        append("}\n")
        append("body > :last-child > :last-child,\n")
        append("body > :last-child > :last-child > :last-child {\n")
        append("  margin-bottom: 0 !important;\n")
        append("  padding-bottom: 0 !important;\n")
        append("}\n")
        append(
            "body p:first-child, body h1:first-child, body h2:first-child, body h3:first-child, " +
                "body h4:first-child, body h5:first-child, body h6:first-child, body ul:first-child, " +
                "body ol:first-child, body blockquote:first-child, body pre:first-child {\n",
        )
        append("  margin-top: 0 !important;\n")
        append("}\n")
        append("body, body *:not(img):not(svg):not(video):not(canvas):not(iframe), body *::before, body *::after {\n")
        append("  color: var(--an-reader-fg) !important;\n")
        if (!textAlignCss.isNullOrBlank()) {
            append("  text-align: var(--an-reader-align) !important;\n")
        }
        append("  line-height: var(--an-reader-line-height) !important;\n")
        append("  background-color: transparent !important;\n")
        if (!textShadowCss.isNullOrBlank()) {
            append("  text-shadow: var(--an-reader-text-shadow) !important;\n")
        }
        if (fontVariable.isNotBlank()) {
            append("  font-family: var(--an-reader-font) !important;\n")
        }
        append("}\n")
        append("a {\n")
        append("  color: var(--an-reader-fg) !important;\n")
        if (fontVariable.isNotBlank()) {
            append("  font-family: var(--an-reader-font) !important;\n")
        }
        append("}\n")
        if (!firstLineIndentCss.isNullOrBlank()) {
            append("body p, body div, body article, body section {\n")
            append("  text-indent: var(--an-reader-first-line-indent) !important;\n")
            append("}\n")
            append("body .an-reader-chapter-title,\n")
            append("body h1, body h2, body h3, body h4, body h5, body h6 {\n")
            append("  text-indent: 0 !important;\n")
            append("}\n")
        }
        append(
            buildWebReaderAtmosphereCss(
                appearanceMode = appearanceMode,
                backgroundTexture = backgroundTexture,
                oledEdgeGradient = oledEdgeGradient,
                backgroundImageUrl = backgroundImageUrl,
            ),
        )
        append(customCss)
    }
}

internal fun buildWebReaderAtmosphereCss(
    appearanceMode: NovelReaderAppearanceMode,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    backgroundImageUrl: String?,
): String {
    if (appearanceMode == NovelReaderAppearanceMode.BACKGROUND) {
        if (backgroundImageUrl.isNullOrBlank()) return ""
        return buildString {
            append("html, body {\n")
            append("  background-color: var(--an-reader-bg) !important;\n")
            append("  background-image: url('$backgroundImageUrl') !important;\n")
            append("  background-repeat: no-repeat !important;\n")
            append("  background-size: cover !important;\n")
            append("  background-position: center center !important;\n")
            append("  background-attachment: fixed !important;\n")
            append("}\n")
        }
    }

    val textureLayers = when (backgroundTexture) {
        NovelReaderBackgroundTexture.NONE -> null
        NovelReaderBackgroundTexture.PAPER_GRAIN ->
            "url('file:///android_asset/textures/texture_paper.webp')"
        NovelReaderBackgroundTexture.LINEN ->
            "url('file:///android_asset/textures/texture_linen.webp')"
        NovelReaderBackgroundTexture.PARCHMENT ->
            "radial-gradient(circle at 20% 20%, rgba(255,255,255,0.14), transparent 45%), " +
                "radial-gradient(circle at 80% 75%, rgba(0,0,0,0.12), transparent 42%)"
    }
    val oledLayer = if (oledEdgeGradient) {
        "radial-gradient(circle at center, rgba(0,0,0,0.0) 38%, rgba(0,0,0,0.36) 100%)"
    } else {
        null
    }
    if (textureLayers == null && oledLayer == null) return ""

    return buildString {
        val layers = listOfNotNull(oledLayer, textureLayers)
        if (layers.isNotEmpty()) {
            val repeatValues = buildList {
                if (oledLayer != null) add("no-repeat")
                if (textureLayers != null) {
                    if (backgroundTexture == NovelReaderBackgroundTexture.PARCHMENT) {
                        add("no-repeat")
                    } else {
                        add("repeat")
                    }
                }
            }.joinToString(", ")
            append("html, body {\n")
            append("  background-color: var(--an-reader-bg) !important;\n")
            append("  background-image: ${layers.joinToString(", ")} !important;\n")
            append("  background-repeat: $repeatValues !important;\n")
            append("  background-attachment: fixed !important;\n")
            append("}\n")
        }
    }
}

internal fun buildWebReaderBionicJavascript(enabled: Boolean): String {
    if (!enabled) return ""
    return """
        (function() {
            const root = document.body;
            if (!root) return;
            const existing = Array.from(root.querySelectorAll('span.an-reader-bionic'));
            for (const span of existing) {
                span.replaceWith(document.createTextNode(span.textContent || ''));
            }
            root.normalize();
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
                acceptNode(node) {
                    if (!node || !node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
                    const parent = node.parentElement;
                    if (!parent) return NodeFilter.FILTER_REJECT;
                    if (parent.closest('script,style,noscript,textarea,svg,.an-reader-bionic')) {
                        return NodeFilter.FILTER_REJECT;
                    }
                    return NodeFilter.FILTER_ACCEPT;
                }
            });
            const nodes = [];
            while (walker.nextNode()) nodes.push(walker.currentNode);
            for (const textNode of nodes) {
                const text = textNode.nodeValue || '';
                const fragment = document.createDocumentFragment();
                const parts = text.match(/\S+|\s+/g) || [];
                for (const token of parts) {
                    if (/^\s+$/.test(token)) {
                        fragment.appendChild(document.createTextNode(token));
                        continue;
                    }
                    const emphasizeCount = Math.max(1, Math.ceil(token.length * 0.5));
                    const span = document.createElement('span');
                    span.className = 'an-reader-bionic';
                    span.textContent = token.slice(0, emphasizeCount);
                    fragment.appendChild(span);
                    const remainder = token.slice(emphasizeCount);
                    if (remainder) {
                        fragment.appendChild(document.createTextNode(remainder));
                    }
                }
                textNode.replaceWith(fragment);
            }
        })();
    """.trimIndent()
}

internal fun buildWebReaderBionicResetJavascript(): String {
    return """
        (function() {
            const root = document.body;
            if (!root) return;
            const spans = Array.from(root.querySelectorAll('span.an-reader-bionic'));
            for (const span of spans) {
                span.replaceWith(document.createTextNode(span.textContent || ''));
            }
            root.normalize();
        })();
    """.trimIndent()
}

internal data class ReaderBackgroundSelection(
    val source: NovelReaderBackgroundSource,
    val preset: NovelReaderBackgroundPreset,
    val customId: String? = null,
    val customPath: String? = null,
    val customIsDarkHint: Boolean? = null,
)

private const val READER_BACKGROUND_PRESET_URL_PREFIX = "https://reader-background.local/preset/"
private const val READER_BACKGROUND_CUSTOM_URL = "https://reader-background.local/custom"
private const val READER_FONT_USER_URL_PREFIX = "https://reader-font.local/user/"

internal fun resolveReaderBackgroundSelection(
    backgroundSource: NovelReaderBackgroundSource,
    backgroundPresetId: String,
    customBackgroundPath: String,
    customBackgroundExists: Boolean,
    customBackgroundId: String = "",
    customBackgroundItems: List<NovelReaderCustomBackgroundItem> = emptyList(),
): ReaderBackgroundSelection {
    val fallbackPreset = novelReaderBackgroundPresets.first()
    val preset = novelReaderBackgroundPresets
        .firstOrNull { it.id == backgroundPresetId }
        ?: fallbackPreset
    val selectedCustomFromCatalog = customBackgroundItems.firstOrNull { item ->
        item.id == customBackgroundId &&
            item.absolutePath.isNotBlank()
    }
    val hasLegacyCustom = backgroundSource == NovelReaderBackgroundSource.CUSTOM &&
        customBackgroundPath.isNotBlank() &&
        customBackgroundExists
    return if (backgroundSource == NovelReaderBackgroundSource.CUSTOM && selectedCustomFromCatalog != null) {
        ReaderBackgroundSelection(
            source = NovelReaderBackgroundSource.CUSTOM,
            preset = preset,
            customId = selectedCustomFromCatalog.id,
            customPath = selectedCustomFromCatalog.absolutePath,
            customIsDarkHint = selectedCustomFromCatalog.isDarkHint,
        )
    } else if (hasLegacyCustom) {
        ReaderBackgroundSelection(
            source = NovelReaderBackgroundSource.CUSTOM,
            preset = preset,
            customId = customBackgroundId.ifBlank { customBackgroundPath },
            customPath = customBackgroundPath,
            customIsDarkHint = null,
        )
    } else {
        ReaderBackgroundSelection(
            source = NovelReaderBackgroundSource.PRESET,
            preset = preset,
            customId = null,
            customPath = null,
            customIsDarkHint = null,
        )
    }
}

internal fun resolveReaderTextColorForBackgroundMode(averageLuminance: Float): Color {
    return if (averageLuminance >= 0.55f) {
        Color(0xFF111111)
    } else {
        Color(0xFFEDEDED)
    }
}

internal fun resolveReaderBackgroundWebImageUrl(selection: ReaderBackgroundSelection): String {
    return when (selection.source) {
        NovelReaderBackgroundSource.PRESET -> "$READER_BACKGROUND_PRESET_URL_PREFIX${selection.preset.id}"
        NovelReaderBackgroundSource.CUSTOM -> {
            val selectedId = selection.customId.orEmpty()
            if (selectedId.isBlank()) {
                READER_BACKGROUND_CUSTOM_URL
            } else {
                "$READER_BACKGROUND_CUSTOM_URL?id=${selectedId.encodeUrlParam()}"
            }
        }
    }
}

internal fun resolveReaderBackgroundIdentity(selection: ReaderBackgroundSelection): String {
    return when (selection.source) {
        NovelReaderBackgroundSource.PRESET -> "preset:${selection.preset.id}"
        NovelReaderBackgroundSource.CUSTOM -> "custom:${selection.customId ?: selection.customPath.orEmpty()}"
    }
}

internal fun resolveReaderBackgroundWebResourceResponse(
    requestUrl: String,
    context: Context,
    selection: ReaderBackgroundSelection,
): WebResourceResponse? {
    if (requestUrl.startsWith(READER_BACKGROUND_PRESET_URL_PREFIX)) {
        val requestedPresetId = requestUrl
            .removePrefix(READER_BACKGROUND_PRESET_URL_PREFIX)
            .substringBefore('?')
        val requestedPreset = novelReaderBackgroundPresets
            .firstOrNull { it.id == requestedPresetId }
            ?: selection.preset
        return runCatching {
            WebResourceResponse(
                "image/jpeg",
                null,
                context.resources.openRawResource(requestedPreset.imageResId),
            )
        }.getOrNull()
    }
    if (!requestUrl.startsWith(READER_BACKGROUND_CUSTOM_URL)) return null
    val customPath = selection.customPath ?: return null
    val customFile = File(customPath)
    if (!customFile.exists() || !customFile.isFile) return null
    val mimeType = URLConnection.guessContentTypeFromName(customFile.name) ?: "image/*"
    return runCatching {
        WebResourceResponse(
            mimeType,
            null,
            customFile.inputStream(),
        )
    }.getOrNull()
}

internal fun loadNovelReaderTypeface(
    context: Context,
    font: NovelReaderFontOption,
    forceBoldText: Boolean = false,
    forceItalicText: Boolean = false,
): android.graphics.Typeface? {
    val baseTypeface = runCatching {
        when (font.source) {
            NovelReaderFontSource.BUILT_IN -> font.fontResId?.let { ResourcesCompat.getFont(context, it) }
            NovelReaderFontSource.LOCAL_PRIVATE -> {
                if (font.assetPath.isBlank()) {
                    null
                } else {
                    android.graphics.Typeface.createFromAsset(context.assets, font.assetPath)
                }
            }
            NovelReaderFontSource.USER_IMPORTED ->
                font.filePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { android.graphics.Typeface.createFromFile(it) }
        }
    }.getOrNull()
    return resolveForcedReaderTypeface(
        typeface = baseTypeface,
        forceBoldText = forceBoldText,
        forceItalicText = forceItalicText,
    )
}

internal fun resolveForcedReaderTypeface(
    typeface: android.graphics.Typeface?,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
): android.graphics.Typeface? {
    if (typeface == null) return null
    val style = resolveForcedReaderTypefaceStyle(forceBoldText, forceItalicText)
    return android.graphics.Typeface.create(typeface, style)
}

internal fun resolveForcedReaderTypefaceStyle(
    forceBoldText: Boolean,
    forceItalicText: Boolean,
): Int {
    return when {
        forceBoldText && forceItalicText -> android.graphics.Typeface.BOLD_ITALIC
        forceBoldText -> android.graphics.Typeface.BOLD
        forceItalicText -> android.graphics.Typeface.ITALIC
        else -> android.graphics.Typeface.NORMAL
    }
}

internal fun resolveNovelReaderComposeFontFamily(
    font: NovelReaderFontOption,
    typeface: android.graphics.Typeface?,
): FontFamily? {
    return when (font.source) {
        NovelReaderFontSource.BUILT_IN -> font.fontResId?.let { FontFamily(Font(it)) }
        NovelReaderFontSource.LOCAL_PRIVATE,
        NovelReaderFontSource.USER_IMPORTED,
        -> typeface?.let { FontFamily(it) }
    }
}

internal fun buildNovelReaderFontFaceCss(font: NovelReaderFontOption): String {
    val fontFamilyName = font.id.takeIf { it.isNotBlank() } ?: return ""
    val fontUrl = when (font.source) {
        NovelReaderFontSource.BUILT_IN,
        NovelReaderFontSource.LOCAL_PRIVATE,
        -> font.assetPath.takeIf {
            it.isNotBlank()
        }?.let { "file:///android_asset/${encodeReaderFontUrlPath(it, preserveSlashes = true)}" }
        NovelReaderFontSource.USER_IMPORTED ->
            font.filePath
                ?.takeIf { it.isNotBlank() }
                ?.let { "$READER_FONT_USER_URL_PREFIX${encodeReaderFontUrlPath(File(it).name)}" }
    } ?: return ""
    return """
        @font-face {
            font-family: '$fontFamilyName';
            src: url('$fontUrl');
        }
    """.trimIndent()
}

internal fun resolveReaderFontWebResourceResponse(
    requestUrl: String,
    selectedFont: NovelReaderFontOption,
): WebResourceResponse? {
    if (!requestUrl.startsWith(READER_FONT_USER_URL_PREFIX)) return null
    if (selectedFont.source != NovelReaderFontSource.USER_IMPORTED) return null
    val filePath = selectedFont.filePath ?: return null
    val fontFile = File(filePath)
    if (!fontFile.exists() || !fontFile.isFile) return null
    val requestedFileName = requestUrl
        .removePrefix(READER_FONT_USER_URL_PREFIX)
        .substringBefore('?')
    if (encodeReaderFontUrlPath(fontFile.name) != requestedFileName) return null
    val mimeType = when {
        fontFile.name.endsWith(".otf", ignoreCase = true) -> "font/otf"
        else -> "font/ttf"
    }
    return runCatching {
        WebResourceResponse(
            mimeType,
            null,
            fontFile.inputStream(),
        )
    }.getOrNull()
}

private fun encodeReaderFontUrlPath(
    value: String,
    preserveSlashes: Boolean = false,
): String {
    return if (!preserveSlashes) {
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
    } else {
        value.split('/')
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8).replace("+", "%20")
            }
    }
}

private fun String.encodeUrlParam(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

internal fun buildWebReaderCssFingerprint(
    chapterId: Long,
    paddingTop: Int,
    paddingBottom: Int,
    paddingHorizontal: Int,
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    paragraphSpacingPx: Int,
    textAlignCss: String?,
    firstLineIndentCss: String?,
    textColorHex: String,
    backgroundHex: String,
    appearanceMode: NovelReaderAppearanceMode,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    backgroundImageIdentity: String?,
    fontFamilyName: String?,
    customCss: String,
    textShadowCss: String?,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
): String {
    return buildString {
        append(chapterId)
        append('|').append(paddingTop)
        append('|').append(paddingBottom)
        append('|').append(paddingHorizontal)
        append('|').append(fontSizePx)
        append('|').append(lineHeightMultiplier)
        append('|').append(paragraphSpacingPx)
        append('|').append(textAlignCss ?: "<site>")
        append('|').append(firstLineIndentCss ?: "<site>")
        append('|').append(textColorHex)
        append('|').append(backgroundHex)
        append('|').append(appearanceMode.name)
        append('|').append(backgroundTexture.name)
        append('|').append(oledEdgeGradient)
        append('|').append(backgroundImageIdentity ?: "<none>")
        append('|').append(fontFamilyName.orEmpty())
        append('|').append(customCss)
        append('|').append(textShadowCss ?: "<none>")
        append('|').append(forceBoldText)
        append('|').append(forceItalicText)
    }
}

internal fun resolveWebViewTextAlignCss(
    textAlign: ReaderTextAlign,
): String? {
    return when (textAlign) {
        ReaderTextAlign.SOURCE -> null
        ReaderTextAlign.LEFT -> "left"
        ReaderTextAlign.CENTER -> "center"
        ReaderTextAlign.JUSTIFY -> "justify"
        ReaderTextAlign.RIGHT -> "right"
    }
}

internal fun resolveWebViewFirstLineIndentCss(
    forceParagraphIndent: Boolean,
): String? {
    return if (forceParagraphIndent) {
        "${FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM}em"
    } else {
        null
    }
}

internal fun resolveAutoReaderShadowColor(
    customShadowColor: Color?,
    textColor: Color,
    backgroundColor: Color,
): Color {
    if (customShadowColor != null) return customShadowColor
    val prefersLightShadow = backgroundColor.luminance() < 0.5f || textColor.luminance() > 0.5f
    return if (prefersLightShadow) {
        Color.White.copy(alpha = 0.55f)
    } else {
        Color.Black.copy(alpha = 0.55f)
    }
}

internal fun resolveWebReaderTextShadowCss(
    textShadowEnabled: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
    textColor: Color,
    backgroundColor: Color,
): String? {
    if (!textShadowEnabled) return null
    val customColor = parseReaderColor(textShadowColor)
    val shadowColor = resolveAutoReaderShadowColor(
        customShadowColor = customColor,
        textColor = textColor,
        backgroundColor = backgroundColor,
    )
    val blur = textShadowBlur.coerceAtLeast(0f)
    return "${formatCssPixel(
        textShadowX,
    )} ${formatCssPixel(textShadowY)} ${formatCssPixel(blur)} ${colorToCssRgba(shadowColor)}"
}

internal fun resolvePageEdgeShadowColor(
    pageEdgeShadowAlpha: Float,
    backgroundColor: Color,
): Color {
    val alpha = pageEdgeShadowAlpha.coerceIn(0.05f, 1f)
    return if (backgroundColor.luminance() < 0.5f) {
        Color.White.copy(alpha = alpha * 0.35f)
    } else {
        Color.Black.copy(alpha = alpha)
    }
}

private fun formatCssPixel(value: Float): String {
    val rounded = ((value * 10f).roundToInt()) / 10f
    return if (abs(rounded - rounded.roundToInt().toFloat()) < 0.001f) {
        "${rounded.roundToInt()}px"
    } else {
        String.format(Locale.US, "%.1fpx", rounded)
    }
}

private fun colorToCssRgba(color: Color): String {
    val red = (color.red * 255f).roundToInt().coerceIn(0, 255)
    val green = (color.green * 255f).roundToInt().coerceIn(0, 255)
    val blue = (color.blue * 255f).roundToInt().coerceIn(0, 255)
    val alpha = color.alpha.coerceIn(0f, 1f)
    return String.format(Locale.US, "rgba(%d,%d,%d,%.3f)", red, green, blue, alpha)
}

internal fun parseReaderColor(value: String?): Color? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) return null
    val hex = normalized.removePrefix("#")
    return when (hex.length) {
        6 -> runCatching {
            val rgb = hex.toLong(16).toInt()
            val argb = (0xFF shl 24) or rgb
            Color(argb)
        }.getOrNull()
        8 -> {
            runCatching {
                val packed = hex.toLong(16).toInt()
                val argbAlpha = (packed ushr 24) and 0xFF
                val rgbaAlpha = packed and 0xFF
                val shouldTreatAsArgb = when {
                    argbAlpha == 0 && rgbaAlpha > 0 -> false
                    rgbaAlpha == 0 && argbAlpha > 0 -> true
                    else -> true
                }
                if (shouldTreatAsArgb) {
                    Color(packed)
                } else {
                    val rr = (packed shr 24) and 0xFF
                    val gg = (packed shr 16) and 0xFF
                    val bb = (packed shr 8) and 0xFF
                    val aa = packed and 0xFF
                    Color((aa shl 24) or (rr shl 16) or (gg shl 8) or bb)
                }
            }.getOrNull()
        }
        else -> runCatching { Color(AndroidColor.parseColor(normalized)) }.getOrNull()
    }
}

internal fun parseReaderColorForTest(value: String?): Color? = parseReaderColor(value)
