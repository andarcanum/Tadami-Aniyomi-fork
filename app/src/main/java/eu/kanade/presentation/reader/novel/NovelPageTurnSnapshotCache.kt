package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign

internal data class NovelPageTurnSnapshotKey(
    val style: NovelPageTransitionStyle,
    val pageIndex: Int,
    val pageCount: Int,
    val pageContentHash: Int,
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val fontFamilyKey: String,
    val chapterTitleFontFamilyKey: String,
    val chapterTitleTextColorArgb: Int,
    val fontSize: Int,
    val lineHeightBits: Int,
    val margin: Int,
    val contentPaddingPx: Int,
    val statusBarTopPaddingPx: Int,
    val textAlign: TextAlign,
    val textColorArgb: Int,
    val textBackgroundArgb: Int,
    val pageSurfaceColorArgb: Int,
    val isBackgroundMode: Boolean,
    val backgroundImageIdentity: String,
    val backgroundTextureName: String,
    val nativeTextureStrengthPercentEffective: Int,
    val oledEdgeGradient: Boolean,
    val isDarkTheme: Boolean,
    val pageEdgeShadow: Boolean,
    val pageEdgeShadowAlphaBits: Int,
    val backgroundTexture: NovelReaderBackgroundTexture,
    val nativeTextureStrengthPercent: Int,
    val forceBoldText: Boolean,
    val forceItalicText: Boolean,
    val textShadow: Boolean,
    val textShadowColor: String,
    val textShadowBlurBits: Int,
    val textShadowXBits: Int,
    val textShadowYBits: Int,
    val bionicReading: Boolean,
)

internal class NovelPageTurnSnapshotCache<T>(
    private val maxSize: Int = 3,
) {
    private val entries = LinkedHashMap<NovelPageTurnSnapshotKey, T>(maxSize, 0.75f, true)

    operator fun get(key: NovelPageTurnSnapshotKey): T? = entries[key]

    fun put(key: NovelPageTurnSnapshotKey, value: T) {
        entries[key] = value
        trimToSize()
    }

    fun clear() {
        entries.clear()
    }

    private fun trimToSize() {
        while (entries.size > maxSize) {
            val eldestKey = entries.entries.iterator().next().key
            entries.remove(eldestKey)
        }
    }
}

internal fun resolveNovelPageTurnSnapshotKey(
    style: NovelPageTransitionStyle,
    pageIndex: Int,
    pageCount: Int,
    pageContentHash: Int,
    pageSize: IntSize,
    fontFamilyKey: String,
    chapterTitleFontFamilyKey: String,
    chapterTitleTextColor: Color,
    fontSize: Int,
    lineHeight: Float,
    margin: Int,
    contentPaddingPx: Int,
    statusBarTopPaddingPx: Int,
    textAlign: TextAlign,
    textColor: Color,
    textBackground: Color,
    pageSurfaceColor: Color,
    isBackgroundMode: Boolean,
    backgroundImageIdentity: String,
    backgroundTextureName: String,
    nativeTextureStrengthPercentEffective: Int,
    oledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    pageEdgeShadow: Boolean,
    pageEdgeShadowAlpha: Float,
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
    textShadow: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
    bionicReading: Boolean,
): NovelPageTurnSnapshotKey {
    return NovelPageTurnSnapshotKey(
        style = style,
        pageIndex = pageIndex.coerceAtLeast(0),
        pageCount = pageCount.coerceAtLeast(1),
        pageContentHash = pageContentHash,
        pageWidthPx = pageSize.width.coerceAtLeast(1),
        pageHeightPx = pageSize.height.coerceAtLeast(1),
        fontFamilyKey = fontFamilyKey,
        chapterTitleFontFamilyKey = chapterTitleFontFamilyKey,
        chapterTitleTextColorArgb = chapterTitleTextColor.toArgb(),
        fontSize = fontSize,
        lineHeightBits = lineHeight.toBits(),
        margin = margin,
        contentPaddingPx = contentPaddingPx,
        statusBarTopPaddingPx = statusBarTopPaddingPx,
        textAlign = textAlign,
        textColorArgb = textColor.toArgb(),
        textBackgroundArgb = textBackground.toArgb(),
        pageSurfaceColorArgb = pageSurfaceColor.toArgb(),
        isBackgroundMode = isBackgroundMode,
        backgroundImageIdentity = backgroundImageIdentity,
        backgroundTextureName = backgroundTextureName,
        nativeTextureStrengthPercentEffective = nativeTextureStrengthPercentEffective,
        oledEdgeGradient = oledEdgeGradient,
        isDarkTheme = isDarkTheme,
        pageEdgeShadow = pageEdgeShadow,
        pageEdgeShadowAlphaBits = pageEdgeShadowAlpha.toBits(),
        backgroundTexture = backgroundTexture,
        nativeTextureStrengthPercent = nativeTextureStrengthPercent,
        forceBoldText = forceBoldText,
        forceItalicText = forceItalicText,
        textShadow = textShadow,
        textShadowColor = textShadowColor,
        textShadowBlurBits = textShadowBlur.toBits(),
        textShadowXBits = textShadowX.toBits(),
        textShadowYBits = textShadowY.toBits(),
        bionicReading = bionicReading,
    )
}
