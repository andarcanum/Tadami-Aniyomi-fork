package eu.kanade.presentation.reader.novel

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme

internal data class ThemeModeSelection(
    val theme: NovelReaderTheme,
    val backgroundColor: String,
    val textColor: String,
)

internal data class AppearanceControlState(
    val themeControlsEnabled: Boolean,
    val backgroundControlsEnabled: Boolean,
)

internal data class CustomBackgroundDeletionResolution(
    val nextCustomId: String,
    val keepCustomSource: Boolean,
    val fallbackPresetId: String,
)

internal fun resolveAppearanceControlState(
    appearanceMode: NovelReaderAppearanceMode,
): AppearanceControlState {
    return when (appearanceMode) {
        NovelReaderAppearanceMode.THEME -> AppearanceControlState(
            themeControlsEnabled = true,
            backgroundControlsEnabled = false,
        )
        NovelReaderAppearanceMode.BACKGROUND -> AppearanceControlState(
            themeControlsEnabled = false,
            backgroundControlsEnabled = true,
        )
    }
}

internal fun resolveThemeModeSelection(theme: NovelReaderTheme): ThemeModeSelection {
    return ThemeModeSelection(
        theme = theme,
        backgroundColor = "",
        textColor = "",
    )
}

internal fun resolveCustomBackgroundDeletion(
    selectedId: String,
    deletedId: String,
    remainingCustomIds: List<String>,
    fallbackPresetId: String,
): CustomBackgroundDeletionResolution {
    if (selectedId != deletedId) {
        return CustomBackgroundDeletionResolution(
            nextCustomId = selectedId,
            keepCustomSource = true,
            fallbackPresetId = fallbackPresetId,
        )
    }
    val nextCustomId = remainingCustomIds.firstOrNull().orEmpty()
    return CustomBackgroundDeletionResolution(
        nextCustomId = nextCustomId,
        keepCustomSource = nextCustomId.isNotBlank(),
        fallbackPresetId = fallbackPresetId,
    )
}

internal fun resolveCustomBackgroundReplacement(
    selectedId: String,
    replacedId: String,
): String {
    return if (selectedId == replacedId) replacedId else selectedId
}

internal enum class RendererSettingDisableReason {
    PAGE_MODE,
    WEBVIEW_ACTIVE,
    BIONIC_READING,
}

internal data class RendererSettingsAvailability(
    val preferWebViewEnabled: Boolean,
    val preferWebViewReason: RendererSettingDisableReason?,
    val richNativeEnabled: Boolean,
    val richNativeReason: RendererSettingDisableReason?,
)

internal fun resolveRendererSettingsAvailability(
    pageReaderEnabled: Boolean,
    showWebView: Boolean,
    bionicReadingEnabled: Boolean,
): RendererSettingsAvailability {
    val preferWebViewReason = if (pageReaderEnabled) RendererSettingDisableReason.PAGE_MODE else null
    val richNativeReason = when {
        showWebView -> RendererSettingDisableReason.WEBVIEW_ACTIVE
        bionicReadingEnabled -> RendererSettingDisableReason.BIONIC_READING
        else -> null
    }
    return RendererSettingsAvailability(
        preferWebViewEnabled = preferWebViewReason == null,
        preferWebViewReason = preferWebViewReason,
        richNativeEnabled = richNativeReason == null,
        richNativeReason = richNativeReason,
    )
}

internal fun areChapterSwipeControlsEnabled(
    swipeGesturesEnabled: Boolean,
    pageReaderEnabled: Boolean,
): Boolean {
    return swipeGesturesEnabled && !pageReaderEnabled
}

internal fun shouldDismissReaderSettingsDialogAfterFamilyChange(
    family: NovelReaderSettingsFamily,
): Boolean {
    return false
}

internal enum class NovelReaderSettingsFamily {
    SOURCE_ALIGNMENT_POLICY,
    CHAPTER_CACHE_POLICY,
    LIVE_TEXT_STYLING,
    RENDERER_TUNING,
}

internal data class NovelReaderSettingsSurfaceStrategy(
    val globalOnlyFamilies: Set<NovelReaderSettingsFamily>,
    val quickDialogOnlyFamilies: Set<NovelReaderSettingsFamily>,
)

internal fun resolveNovelReaderSettingsSurfaceStrategy(): NovelReaderSettingsSurfaceStrategy {
    return NovelReaderSettingsSurfaceStrategy(
        globalOnlyFamilies = setOf(
            NovelReaderSettingsFamily.SOURCE_ALIGNMENT_POLICY,
            NovelReaderSettingsFamily.CHAPTER_CACHE_POLICY,
        ),
        quickDialogOnlyFamilies = setOf(
            NovelReaderSettingsFamily.LIVE_TEXT_STYLING,
            NovelReaderSettingsFamily.RENDERER_TUNING,
        ),
    )
}

internal fun areQuickDialogKindleDependentControlsEnabled(
    showKindleInfoBlock: Boolean,
): Boolean {
    return showKindleInfoBlock
}

internal fun verticalSeekbarLabels(
    readingProgressPercent: Int,
    showScrollPercentage: Boolean,
): Pair<String?, String?> {
    if (!showScrollPercentage) return null to null
    val clamped = readingProgressPercent.coerceIn(0, 100)
    return clamped.toString() to "100"
}

internal fun parseNovelReaderColor(value: String): Color? {
    return runCatching { Color(AndroidColor.parseColor(value)) }.getOrNull()
}

internal fun resolveNovelReaderColorTheme(
    backgroundColor: String,
    textColor: String,
): NovelReaderColorTheme? {
    if (backgroundColor.isBlank() || textColor.isBlank()) return null
    if (!isValidNovelReaderColorOrBlank(backgroundColor) || !isValidNovelReaderColorOrBlank(textColor)) return null
    return NovelReaderColorTheme(backgroundColor = backgroundColor, textColor = textColor)
}

internal fun isValidNovelReaderColorOrBlank(value: String): Boolean {
    if (value.isBlank()) return true
    return value.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))
}
