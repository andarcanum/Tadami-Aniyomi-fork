package eu.kanade.tachiyomi.ui.reader.novel.setting

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class NovelReaderSettings(
    // Display
    val fontSize: Int,
    val lineHeight: Float,
    val margin: Int,
    val textAlign: TextAlign,
    val fontFamily: String,

    // Theme
    val theme: NovelReaderTheme,
    val backgroundColor: String?,
    val textColor: String?,
    val customThemes: List<NovelReaderColorTheme>,

    // Navigation
    val useVolumeButtons: Boolean,
    val swipeGestures: Boolean,
    val pageReader: Boolean,
    val preferWebViewRenderer: Boolean,
    val verticalSeekbar: Boolean,
    val swipeToNextChapter: Boolean,
    val swipeToPrevChapter: Boolean,
    val tapToScroll: Boolean,
    val autoScroll: Boolean,
    val autoScrollInterval: Int,
    val autoScrollOffset: Int,

    // Accessibility
    val fullScreenMode: Boolean,
    val keepScreenOn: Boolean,
    val showScrollPercentage: Boolean,
    val showBatteryAndTime: Boolean,
    val bionicReading: Boolean,

    // Advanced
    val customCSS: String,
    val customJS: String,
)

enum class NovelReaderTheme {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class TextAlign {
    LEFT,
    CENTER,
    JUSTIFY,
    RIGHT,
}

@Serializable
data class NovelReaderColorTheme(
    val backgroundColor: String,
    val textColor: String,
)

@Serializable
data class NovelReaderOverride(
    // Display
    val fontSize: Int? = null,
    val lineHeight: Float? = null,
    val margin: Int? = null,
    val textAlign: TextAlign? = null,
    val fontFamily: String? = null,

    // Theme
    val theme: NovelReaderTheme? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val customThemes: List<NovelReaderColorTheme>? = null,

    // Navigation
    val useVolumeButtons: Boolean? = null,
    val swipeGestures: Boolean? = null,
    val pageReader: Boolean? = null,
    val preferWebViewRenderer: Boolean? = null,
    val verticalSeekbar: Boolean? = null,
    val swipeToNextChapter: Boolean? = null,
    val swipeToPrevChapter: Boolean? = null,
    val tapToScroll: Boolean? = null,
    val autoScroll: Boolean? = null,
    val autoScrollInterval: Int? = null,
    val autoScrollOffset: Int? = null,

    // Accessibility
    val fullScreenMode: Boolean? = null,
    val keepScreenOn: Boolean? = null,
    val showScrollPercentage: Boolean? = null,
    val showBatteryAndTime: Boolean? = null,
    val bionicReading: Boolean? = null,

    // Advanced
    val customCSS: String? = null,
    val customJS: String? = null,
)

class NovelReaderPreferences(
    private val preferenceStore: PreferenceStore,
    private val json: Json = Injekt.get(),
) {
    // Display
    fun fontSize() = preferenceStore.getInt("novel_reader_font_size", DEFAULT_FONT_SIZE)

    fun lineHeight() = preferenceStore.getFloat("novel_reader_line_height", DEFAULT_LINE_HEIGHT)

    fun margin() = preferenceStore.getInt("novel_reader_margins", DEFAULT_MARGIN)

    fun textAlign() = preferenceStore.getEnum("novel_reader_text_align", TextAlign.LEFT)

    fun fontFamily() = preferenceStore.getString("novel_reader_font_family", "")

    // Theme
    fun theme() = preferenceStore.getEnum("novel_reader_theme", NovelReaderTheme.SYSTEM)

    fun backgroundColor() = preferenceStore.getString("novel_reader_bg_color", "")

    fun textColor() = preferenceStore.getString("novel_reader_text_color", "")

    fun customThemes() = preferenceStore.getObject(
        "novel_reader_custom_themes",
        emptyList(),
        serializer = { json.encodeToString(customThemesSerializer, it) },
        deserializer = { json.decodeFromString(customThemesSerializer, it) },
    )

    // Navigation
    fun useVolumeButtons() = preferenceStore.getBoolean("novel_reader_volume_buttons", false)

    fun swipeGestures() = preferenceStore.getBoolean("novel_reader_swipe_gestures", false)

    fun pageReader() = preferenceStore.getBoolean("novel_reader_page_mode", false)

    fun preferWebViewRenderer() = preferenceStore.getBoolean("novel_reader_prefer_webview_renderer", true)

    fun verticalSeekbar() = preferenceStore.getBoolean("novel_reader_vertical_seekbar", true)

    fun swipeToNextChapter() = preferenceStore.getBoolean("novel_reader_swipe_to_next_chapter", false)

    fun swipeToPrevChapter() = preferenceStore.getBoolean("novel_reader_swipe_to_prev_chapter", false)

    fun tapToScroll() = preferenceStore.getBoolean("novel_reader_tap_to_scroll", false)

    fun autoScroll() = preferenceStore.getBoolean("novel_reader_auto_scroll", false)

    fun autoScrollInterval() = preferenceStore.getInt("novel_reader_auto_scroll_interval", DEFAULT_AUTO_SCROLL_INTERVAL)

    fun autoScrollOffset() = preferenceStore.getInt("novel_reader_auto_scroll_offset", DEFAULT_AUTO_SCROLL_OFFSET)

    // Accessibility
    fun fullScreenMode() = preferenceStore.getBoolean("novel_reader_fullscreen", true)

    fun keepScreenOn() = preferenceStore.getBoolean("novel_reader_keep_screen_on", false)

    fun showScrollPercentage() = preferenceStore.getBoolean(
        "novel_reader_show_scroll_percentage",
        true,
    )

    fun showBatteryAndTime() = preferenceStore.getBoolean("novel_reader_show_battery_time", false)

    fun bionicReading() = preferenceStore.getBoolean("novel_reader_bionic_reading", false)

    // Advanced
    fun customCSS() = preferenceStore.getString("novel_reader_custom_css", "")

    fun customJS() = preferenceStore.getString("novel_reader_custom_js", "")

    // EPUB export
    fun epubExportLocation() = preferenceStore.getString("novel_epub_export_location", "")

    fun epubExportUseReaderTheme() = preferenceStore.getBoolean("novel_epub_export_use_reader_theme", false)

    fun epubExportUseCustomCSS() = preferenceStore.getBoolean("novel_epub_export_use_custom_css", false)

    fun epubExportUseCustomJS() = preferenceStore.getBoolean("novel_epub_export_use_custom_js", false)

    fun sourceOverrides() = preferenceStore.getObject(
        "novel_reader_source_overrides",
        emptyMap(),
        serializer = { json.encodeToString(overrideSerializer, it) },
        deserializer = { json.decodeFromString(overrideSerializer, it) },
    )

    fun getSourceOverride(sourceId: Long): NovelReaderOverride? = sourceOverrides().get()[sourceId]

    fun setSourceOverride(sourceId: Long, override: NovelReaderOverride?) {
        val updated = sourceOverrides().get().toMutableMap()
        if (override == null) {
            updated.remove(sourceId)
        } else {
            updated[sourceId] = override
        }
        sourceOverrides().set(updated)
    }

    fun enableSourceOverride(sourceId: Long) {
        if (getSourceOverride(sourceId) != null) return
        setSourceOverride(
            sourceId,
            NovelReaderOverride(
                fontSize = fontSize().get(),
                lineHeight = lineHeight().get(),
                margin = margin().get(),
                textAlign = textAlign().get(),
                fontFamily = fontFamily().get(),
                theme = theme().get(),
                backgroundColor = backgroundColor().get(),
                textColor = textColor().get(),
                customThemes = customThemes().get(),
                useVolumeButtons = useVolumeButtons().get(),
                swipeGestures = swipeGestures().get(),
                pageReader = pageReader().get(),
                preferWebViewRenderer = preferWebViewRenderer().get(),
                verticalSeekbar = verticalSeekbar().get(),
                swipeToNextChapter = swipeToNextChapter().get(),
                swipeToPrevChapter = swipeToPrevChapter().get(),
                tapToScroll = tapToScroll().get(),
                autoScroll = autoScroll().get(),
                autoScrollInterval = autoScrollInterval().get(),
                autoScrollOffset = autoScrollOffset().get(),
                fullScreenMode = fullScreenMode().get(),
                keepScreenOn = keepScreenOn().get(),
                showScrollPercentage = showScrollPercentage().get(),
                showBatteryAndTime = showBatteryAndTime().get(),
                bionicReading = bionicReading().get(),
                customCSS = customCSS().get(),
                customJS = customJS().get(),
            ),
        )
    }

    fun updateSourceOverride(
        sourceId: Long,
        update: (NovelReaderOverride) -> NovelReaderOverride,
    ) {
        val current = getSourceOverride(sourceId) ?: NovelReaderOverride()
        setSourceOverride(sourceId, update(current))
    }

    fun resolveSettings(sourceId: Long): NovelReaderSettings {
        val override = getSourceOverride(sourceId)
        return NovelReaderSettings(
            fontSize = override?.fontSize ?: fontSize().get(),
            lineHeight = override?.lineHeight ?: lineHeight().get(),
            margin = override?.margin ?: margin().get(),
            textAlign = override?.textAlign ?: textAlign().get(),
            fontFamily = override?.fontFamily ?: fontFamily().get(),
            theme = override?.theme ?: theme().get(),
            backgroundColor = override?.backgroundColor ?: backgroundColor().get(),
            textColor = override?.textColor ?: textColor().get(),
            customThemes = override?.customThemes ?: customThemes().get(),
            useVolumeButtons = override?.useVolumeButtons ?: useVolumeButtons().get(),
            swipeGestures = override?.swipeGestures ?: swipeGestures().get(),
            pageReader = override?.pageReader ?: pageReader().get(),
            preferWebViewRenderer = override?.preferWebViewRenderer ?: preferWebViewRenderer().get(),
            verticalSeekbar = override?.verticalSeekbar ?: verticalSeekbar().get(),
            swipeToNextChapter = override?.swipeToNextChapter ?: swipeToNextChapter().get(),
            swipeToPrevChapter = override?.swipeToPrevChapter ?: swipeToPrevChapter().get(),
            tapToScroll = override?.tapToScroll ?: tapToScroll().get(),
            autoScroll = override?.autoScroll ?: autoScroll().get(),
            autoScrollInterval = override?.autoScrollInterval ?: autoScrollInterval().get(),
            autoScrollOffset = override?.autoScrollOffset ?: autoScrollOffset().get(),
            fullScreenMode = override?.fullScreenMode ?: fullScreenMode().get(),
            keepScreenOn = override?.keepScreenOn ?: keepScreenOn().get(),
            showScrollPercentage = override?.showScrollPercentage ?: showScrollPercentage().get(),
            showBatteryAndTime = override?.showBatteryAndTime ?: showBatteryAndTime().get(),
            bionicReading = override?.bionicReading ?: bionicReading().get(),
            customCSS = override?.customCSS ?: customCSS().get(),
            customJS = override?.customJS ?: customJS().get(),
        )
    }

    fun settingsFlow(sourceId: Long): Flow<NovelReaderSettings> {
        // Группируем настройки для избежания лимита combine()
        val displayFlow = combine(
            fontSize().changes(),
            lineHeight().changes(),
            margin().changes(),
            textAlign().changes(),
            fontFamily().changes(),
        ) { values: Array<Any?> ->
            DisplaySettings(
                values[0] as Int,
                values[1] as Float,
                values[2] as Int,
                values[3] as TextAlign,
                values[4] as String,
            )
        }

        val themeFlow = combine(
            theme().changes(),
            backgroundColor().changes(),
            textColor().changes(),
            customThemes().changes(),
        ) { values: Array<Any?> ->
            ThemeSettings(
                values[0] as NovelReaderTheme,
                values[1] as String,
                values[2] as String,
                values[3] as List<NovelReaderColorTheme>,
            )
        }

        val navigationFlow = combine(
            useVolumeButtons().changes(),
            swipeGestures().changes(),
            pageReader().changes(),
            preferWebViewRenderer().changes(),
            verticalSeekbar().changes(),
            swipeToNextChapter().changes(),
            swipeToPrevChapter().changes(),
            tapToScroll().changes(),
            autoScroll().changes(),
            autoScrollInterval().changes(),
            autoScrollOffset().changes(),
        ) { values: Array<Any?> ->
            NavigationSettings(
                values[0] as Boolean,
                values[1] as Boolean,
                values[2] as Boolean,
                values[3] as Boolean,
                values[4] as Boolean,
                values[5] as Boolean,
                values[6] as Boolean,
                values[7] as Boolean,
                values[8] as Boolean,
                values[9] as Int,
                values[10] as Int,
            )
        }

        val accessibilityFlow = combine(
            fullScreenMode().changes(),
            keepScreenOn().changes(),
            showScrollPercentage().changes(),
            showBatteryAndTime().changes(),
            bionicReading().changes(),
        ) { values: Array<Any?> ->
            AccessibilitySettings(
                fullScreenMode = values[0] as Boolean,
                keepScreenOn = values[1] as Boolean,
                showScrollPercentage = values[2] as Boolean,
                showBatteryAndTime = values[3] as Boolean,
                bionicReading = values[4] as Boolean,
            )
        }

        val advancedFlow = combine(
            customCSS().changes(),
            customJS().changes(),
        ) { customCSS, customJS ->
            AdvancedSettings(customCSS, customJS)
        }

        return combine(
            displayFlow,
            themeFlow,
            navigationFlow,
            accessibilityFlow,
            advancedFlow,
            sourceOverrides().changes(),
        ) { values: Array<Any?> ->
            val display = values[0] as DisplaySettings
            val theme = values[1] as ThemeSettings
            val navigation = values[2] as NavigationSettings
            val accessibility = values[3] as AccessibilitySettings
            val advanced = values[4] as AdvancedSettings
            val overrides = values[5] as Map<Long, NovelReaderOverride>

            val override = overrides[sourceId]
            NovelReaderSettings(
                fontSize = override?.fontSize ?: display.fontSize,
                lineHeight = override?.lineHeight ?: display.lineHeight,
                margin = override?.margin ?: display.margin,
                textAlign = override?.textAlign ?: display.textAlign,
                fontFamily = override?.fontFamily ?: display.fontFamily,
                theme = override?.theme ?: theme.theme,
                backgroundColor = override?.backgroundColor ?: theme.backgroundColor,
                textColor = override?.textColor ?: theme.textColor,
                customThemes = override?.customThemes ?: theme.customThemes,
                useVolumeButtons = override?.useVolumeButtons ?: navigation.useVolumeButtons,
                swipeGestures = override?.swipeGestures ?: navigation.swipeGestures,
                pageReader = override?.pageReader ?: navigation.pageReader,
                preferWebViewRenderer = override?.preferWebViewRenderer ?: navigation.preferWebViewRenderer,
                verticalSeekbar = override?.verticalSeekbar ?: navigation.verticalSeekbar,
                swipeToNextChapter = override?.swipeToNextChapter ?: navigation.swipeToNextChapter,
                swipeToPrevChapter = override?.swipeToPrevChapter ?: navigation.swipeToPrevChapter,
                tapToScroll = override?.tapToScroll ?: navigation.tapToScroll,
                autoScroll = override?.autoScroll ?: navigation.autoScroll,
                autoScrollInterval = override?.autoScrollInterval ?: navigation.autoScrollInterval,
                autoScrollOffset = override?.autoScrollOffset ?: navigation.autoScrollOffset,
                fullScreenMode = override?.fullScreenMode ?: accessibility.fullScreenMode,
                keepScreenOn = override?.keepScreenOn ?: accessibility.keepScreenOn,
                showScrollPercentage = override?.showScrollPercentage ?: accessibility.showScrollPercentage,
                showBatteryAndTime = override?.showBatteryAndTime ?: accessibility.showBatteryAndTime,
                bionicReading = override?.bionicReading ?: accessibility.bionicReading,
                customCSS = override?.customCSS ?: advanced.customCSS,
                customJS = override?.customJS ?: advanced.customJS,
            )
        }
    }

    // Вспомогательные data classes для группировки в Flow
    private data class DisplaySettings(
        val fontSize: Int,
        val lineHeight: Float,
        val margin: Int,
        val textAlign: TextAlign,
        val fontFamily: String,
    )

    private data class ThemeSettings(
        val theme: NovelReaderTheme,
        val backgroundColor: String,
        val textColor: String,
        val customThemes: List<NovelReaderColorTheme>,
    )

    private data class NavigationSettings(
        val useVolumeButtons: Boolean,
        val swipeGestures: Boolean,
        val pageReader: Boolean,
        val preferWebViewRenderer: Boolean,
        val verticalSeekbar: Boolean,
        val swipeToNextChapter: Boolean,
        val swipeToPrevChapter: Boolean,
        val tapToScroll: Boolean,
        val autoScroll: Boolean,
        val autoScrollInterval: Int,
        val autoScrollOffset: Int,
    )

    private data class AccessibilitySettings(
        val fullScreenMode: Boolean,
        val keepScreenOn: Boolean,
        val showScrollPercentage: Boolean,
        val showBatteryAndTime: Boolean,
        val bionicReading: Boolean,
    )

    private data class AdvancedSettings(
        val customCSS: String,
        val customJS: String,
    )

    companion object {
        const val DEFAULT_FONT_SIZE = 16
        const val DEFAULT_LINE_HEIGHT = 1.6f
        const val DEFAULT_MARGIN = 16
        const val DEFAULT_AUTO_SCROLL_INTERVAL = 10
        const val DEFAULT_AUTO_SCROLL_OFFSET = 0

        private val overrideSerializer = MapSerializer(
            Long.serializer(),
            NovelReaderOverride.serializer(),
        )
        private val customThemesSerializer = ListSerializer(NovelReaderColorTheme.serializer())
    }
}
