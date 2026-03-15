package eu.kanade.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import eu.kanade.presentation.reader.novel.NovelReaderFontOption
import eu.kanade.presentation.reader.novel.NovelReaderFontSource
import eu.kanade.presentation.reader.novel.buildNovelReaderFontCatalog
import eu.kanade.presentation.reader.novel.resolveNovelReaderSelectedFont
import java.io.File

val LocalCoverTitleFontFamily = staticCompositionLocalOf<FontFamily?> { null }

@Composable
fun rememberAppFontFamily(
    fontId: String,
): FontFamily? {
    val context = LocalContext.current
    val selectedFont = remember(context, fontId) {
        val catalog = buildNovelReaderFontCatalog(context)
        resolveNovelReaderSelectedFont(catalog, fontId)
    }
    return rememberAppFontFamily(selectedFont)
}

@Composable
fun rememberAppFontFamily(
    font: NovelReaderFontOption,
): FontFamily? {
    val context = LocalContext.current
    val typeface = remember(
        context,
        font.id,
        font.source,
        font.fontResId,
        font.assetPath,
        font.filePath,
    ) {
        loadUiTypeface(context, font)
    }
    return remember(
        font.id,
        font.source,
        font.fontResId,
        typeface,
    ) {
        resolveUiComposeFontFamily(font, typeface)
    }
}

internal fun Typography.withDefaultFontFamily(
    fontFamily: FontFamily?,
): Typography {
    if (fontFamily == null) return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily),
    )
}

private fun loadUiTypeface(
    context: android.content.Context,
    font: NovelReaderFontOption,
): android.graphics.Typeface? {
    return runCatching {
        when (font.source) {
            NovelReaderFontSource.BUILT_IN -> font.fontResId?.let { ResourcesCompat.getFont(context, it) }
            NovelReaderFontSource.LOCAL_PRIVATE -> {
                if (font.assetPath.isBlank()) {
                    null
                } else {
                    android.graphics.Typeface.createFromAsset(context.assets, font.assetPath)
                }
            }
            NovelReaderFontSource.USER_IMPORTED -> {
                font.filePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { android.graphics.Typeface.createFromFile(File(it)) }
            }
        }
    }.getOrNull()
}

private fun resolveUiComposeFontFamily(
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
