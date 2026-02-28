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
    val forceParagraphIndent: Boolean,
    val preserveSourceTextAlignInNative: Boolean,
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
    val richNativeRendererExperimental: Boolean,
    val verticalSeekbar: Boolean,
    val swipeToNextChapter: Boolean,
    val swipeToPrevChapter: Boolean,
    val tapToScroll: Boolean,
    val autoScroll: Boolean,
    val autoScrollInterval: Int,
    val autoScrollOffset: Int,
    val prefetchNextChapter: Boolean,

    // Accessibility
    val fullScreenMode: Boolean,
    val keepScreenOn: Boolean,
    val showScrollPercentage: Boolean,
    val showBatteryAndTime: Boolean,
    val bionicReading: Boolean,

    // Advanced
    val customCSS: String,
    val customJS: String,

    // Gemini Translation
    val geminiEnabled: Boolean = false,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash",
    val geminiBatchSize: Int = 40,
    val geminiConcurrency: Int = 2,
    val geminiDisableCache: Boolean = false,
    val geminiRelaxedMode: Boolean = true,
    val geminiReasoningEffort: String = "minimal",
    val geminiBudgetTokens: Int = 8192,
    val geminiTemperature: Float = 0.7f,
    val geminiTopP: Float = 0.95f,
    val geminiTopK: Int = 40,
    val geminiSourceLang: String = "English",
    val geminiTargetLang: String = "Russian",
    val geminiPromptMode: GeminiPromptMode = GeminiPromptMode.ADULT_18,
    val geminiEnabledPromptModifiers: List<String> = emptyList(),
    val geminiCustomPromptModifier: String = "",
    val geminiPromptModifiers: String = "",
    val geminiAutoTranslateEnglishSource: Boolean = false,
    val geminiPrefetchNextChapterTranslation: Boolean = false,
    val translationProvider: NovelTranslationProvider = NovelTranslationProvider.GEMINI,
    val airforceBaseUrl: String = "https://api.airforce",
    val airforceApiKey: String = "",
    val airforceModel: String = "",
    val openRouterBaseUrl: String = "https://openrouter.ai/api/v1",
    val openRouterApiKey: String = "",
    val openRouterModel: String = "",
    val deepSeekBaseUrl: String = "https://api.deepseek.com",
    val deepSeekApiKey: String = "",
    val deepSeekModel: String = "deepseek-chat",
)

enum class NovelReaderTheme {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class TextAlign {
    SOURCE,
    LEFT,
    CENTER,
    JUSTIFY,
    RIGHT,
}

enum class GeminiPromptMode {
    CLASSIC,
    ADULT_18,
}

enum class NovelTranslationProvider {
    GEMINI,
    AIRFORCE,
    OPENROUTER,
    DEEPSEEK,
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
    val forceParagraphIndent: Boolean? = null,
    val preserveSourceTextAlignInNative: Boolean? = null,
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
    val richNativeRendererExperimental: Boolean? = null,
    val verticalSeekbar: Boolean? = null,
    val swipeToNextChapter: Boolean? = null,
    val swipeToPrevChapter: Boolean? = null,
    val tapToScroll: Boolean? = null,
    val autoScroll: Boolean? = null,
    val autoScrollInterval: Int? = null,
    val autoScrollOffset: Int? = null,
    val prefetchNextChapter: Boolean? = null,

    // Accessibility
    val fullScreenMode: Boolean? = null,
    val keepScreenOn: Boolean? = null,
    val showScrollPercentage: Boolean? = null,
    val showBatteryAndTime: Boolean? = null,
    val bionicReading: Boolean? = null,

    // Advanced
    val customCSS: String? = null,
    val customJS: String? = null,

    // Gemini Translation
    val geminiEnabled: Boolean? = null,
    val geminiApiKey: String? = null,
    val geminiModel: String? = null,
    val geminiBatchSize: Int? = null,
    val geminiConcurrency: Int? = null,
    val geminiDisableCache: Boolean? = null,
    val geminiRelaxedMode: Boolean? = null,
    val geminiReasoningEffort: String? = null,
    val geminiBudgetTokens: Int? = null,
    val geminiTemperature: Float? = null,
    val geminiTopP: Float? = null,
    val geminiTopK: Int? = null,
    val geminiSourceLang: String? = null,
    val geminiTargetLang: String? = null,
    val geminiPromptMode: GeminiPromptMode? = null,
    val geminiEnabledPromptModifiers: List<String>? = null,
    val geminiCustomPromptModifier: String? = null,
    val geminiPromptModifiers: String? = null,
    val geminiAutoTranslateEnglishSource: Boolean? = null,
    val geminiPrefetchNextChapterTranslation: Boolean? = null,
    val translationProvider: NovelTranslationProvider? = null,
    val airforceBaseUrl: String? = null,
    val airforceApiKey: String? = null,
    val airforceModel: String? = null,
    val openRouterBaseUrl: String? = null,
    val openRouterApiKey: String? = null,
    val openRouterModel: String? = null,
    val deepSeekBaseUrl: String? = null,
    val deepSeekApiKey: String? = null,
    val deepSeekModel: String? = null,
)

class NovelReaderPreferences(
    private val preferenceStore: PreferenceStore,
    private val json: Json = Injekt.get(),
) {
    // Display
    fun fontSize() = preferenceStore.getInt("novel_reader_font_size", DEFAULT_FONT_SIZE)

    fun lineHeight() = preferenceStore.getFloat("novel_reader_line_height", DEFAULT_LINE_HEIGHT)

    fun margin() = preferenceStore.getInt("novel_reader_margins", DEFAULT_MARGIN)

    fun textAlign() = preferenceStore.getEnum("novel_reader_text_align", TextAlign.SOURCE)

    fun forceParagraphIndent() = preferenceStore.getBoolean("novel_reader_force_paragraph_indent", true)

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

    fun preferWebViewRenderer() = preferenceStore.getBoolean("novel_reader_prefer_webview_renderer", false)

    fun richNativeRendererExperimental() =
        preferenceStore.getBoolean("novel_reader_rich_native_renderer_experimental", true)

    fun preserveSourceTextAlignInNative() =
        preferenceStore.getBoolean("novel_reader_preserve_source_text_align_in_native", true)

    fun verticalSeekbar() = preferenceStore.getBoolean("novel_reader_vertical_seekbar", true)

    fun swipeToNextChapter() = preferenceStore.getBoolean("novel_reader_swipe_to_next_chapter", false)

    fun swipeToPrevChapter() = preferenceStore.getBoolean("novel_reader_swipe_to_prev_chapter", false)

    fun tapToScroll() = preferenceStore.getBoolean("novel_reader_tap_to_scroll", false)

    fun autoScroll() = preferenceStore.getBoolean("novel_reader_auto_scroll", false)

    fun autoScrollInterval() = preferenceStore.getInt("novel_reader_auto_scroll_interval", DEFAULT_AUTO_SCROLL_INTERVAL)

    fun autoScrollOffset() = preferenceStore.getInt("novel_reader_auto_scroll_offset", DEFAULT_AUTO_SCROLL_OFFSET)

    fun prefetchNextChapter() = preferenceStore.getBoolean("novel_reader_prefetch_next_chapter", false)

    fun cacheReadChapters() = preferenceStore.getBoolean("novel_reader_cache_read_chapters", true)

    fun cacheReadChaptersUnlimited() = preferenceStore.getBoolean("novel_reader_cache_read_chapters_unlimited", false)

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

    // Gemini Translation
    fun geminiEnabled() = preferenceStore.getBoolean("novel_reader_gemini_enabled", false)

    fun geminiApiKey() = preferenceStore.getString("novel_reader_gemini_api_key", "")

    fun geminiModel() = preferenceStore.getString("novel_reader_gemini_model", "gemini-2.5-flash")

    fun geminiBatchSize() = preferenceStore.getInt("novel_reader_gemini_batch_size", 40)

    fun geminiConcurrency() = preferenceStore.getInt("novel_reader_gemini_concurrency", 2)

    fun geminiDisableCache() = preferenceStore.getBoolean("novel_reader_gemini_disable_cache", false)

    fun geminiRelaxedMode() = preferenceStore.getBoolean("novel_reader_gemini_relaxed_mode", true)

    fun geminiReasoningEffort() = preferenceStore.getString("novel_reader_gemini_reasoning_effort", "minimal")

    fun geminiBudgetTokens() = preferenceStore.getInt("novel_reader_gemini_budget_tokens", 8192)

    fun geminiTemperature() = preferenceStore.getFloat("novel_reader_gemini_temperature", 0.7f)

    fun geminiTopP() = preferenceStore.getFloat("novel_reader_gemini_top_p", 0.95f)

    fun geminiTopK() = preferenceStore.getInt("novel_reader_gemini_top_k", 40)

    fun geminiSourceLang() = preferenceStore.getString("novel_reader_gemini_source_lang", "English")

    fun geminiTargetLang() = preferenceStore.getString("novel_reader_gemini_target_lang", "Russian")

    fun geminiPromptMode() = preferenceStore.getEnum("novel_reader_gemini_prompt_mode", GeminiPromptMode.ADULT_18)

    fun geminiEnabledPromptModifiers() = preferenceStore.getObject(
        "novel_reader_gemini_enabled_prompt_modifiers",
        emptyList(),
        serializer = { json.encodeToString(stringListSerializer, it) },
        deserializer = { json.decodeFromString(stringListSerializer, it) },
    )

    fun geminiCustomPromptModifier() = preferenceStore.getString("novel_reader_gemini_custom_prompt_modifier", "")

    fun geminiPromptModifiers() = preferenceStore.getString("novel_reader_gemini_prompt_modifiers", "")

    fun geminiAutoTranslateEnglishSource() =
        preferenceStore.getBoolean("novel_reader_gemini_auto_translate_english_source", false)

    fun geminiPrefetchNextChapterTranslation() =
        preferenceStore.getBoolean("novel_reader_gemini_prefetch_next_chapter_translation", false)

    fun translationProvider() =
        preferenceStore.getEnum("novel_reader_translation_provider", NovelTranslationProvider.GEMINI)

    fun airforceBaseUrl() = preferenceStore.getString("novel_reader_airforce_base_url", "https://api.airforce")

    fun airforceApiKey() = preferenceStore.getString("novel_reader_airforce_api_key", "")

    fun airforceModel() = preferenceStore.getString("novel_reader_airforce_model", "")

    fun openRouterBaseUrl() = preferenceStore.getString(
        "novel_reader_openrouter_base_url",
        "https://openrouter.ai/api/v1",
    )

    fun openRouterApiKey() = preferenceStore.getString("novel_reader_openrouter_api_key", "")

    fun openRouterModel() = preferenceStore.getString("novel_reader_openrouter_model", "")

    fun deepSeekBaseUrl() = preferenceStore.getString("novel_reader_deepseek_base_url", "https://api.deepseek.com")

    fun deepSeekApiKey() = preferenceStore.getString("novel_reader_deepseek_api_key", "")

    fun deepSeekModel() = preferenceStore.getString("novel_reader_deepseek_model", "deepseek-chat")

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
                forceParagraphIndent = forceParagraphIndent().get(),
                preserveSourceTextAlignInNative = preserveSourceTextAlignInNative().get(),
                fontFamily = fontFamily().get(),
                theme = theme().get(),
                backgroundColor = backgroundColor().get(),
                textColor = textColor().get(),
                customThemes = customThemes().get(),
                useVolumeButtons = useVolumeButtons().get(),
                swipeGestures = swipeGestures().get(),
                pageReader = pageReader().get(),
                preferWebViewRenderer = preferWebViewRenderer().get(),
                richNativeRendererExperimental = richNativeRendererExperimental().get(),
                verticalSeekbar = verticalSeekbar().get(),
                swipeToNextChapter = swipeToNextChapter().get(),
                swipeToPrevChapter = swipeToPrevChapter().get(),
                tapToScroll = tapToScroll().get(),
                autoScroll = autoScroll().get(),
                autoScrollInterval = autoScrollInterval().get(),
                autoScrollOffset = autoScrollOffset().get(),
                prefetchNextChapter = prefetchNextChapter().get(),
                fullScreenMode = fullScreenMode().get(),
                keepScreenOn = keepScreenOn().get(),
                showScrollPercentage = showScrollPercentage().get(),
                showBatteryAndTime = showBatteryAndTime().get(),
                bionicReading = bionicReading().get(),
                customCSS = customCSS().get(),
                customJS = customJS().get(),
                geminiApiKey = geminiApiKey().get(),
                geminiModel = geminiModel().get(),
                geminiBatchSize = geminiBatchSize().get(),
                geminiConcurrency = geminiConcurrency().get(),
                geminiDisableCache = geminiDisableCache().get(),
                geminiRelaxedMode = geminiRelaxedMode().get(),
                geminiReasoningEffort = geminiReasoningEffort().get(),
                geminiBudgetTokens = geminiBudgetTokens().get(),
                geminiTemperature = geminiTemperature().get(),
                geminiTopP = geminiTopP().get(),
                geminiTopK = geminiTopK().get(),
                geminiSourceLang = geminiSourceLang().get(),
                geminiTargetLang = geminiTargetLang().get(),
                geminiPromptMode = geminiPromptMode().get(),
                geminiEnabledPromptModifiers = geminiEnabledPromptModifiers().get(),
                geminiCustomPromptModifier = geminiCustomPromptModifier().get(),
                geminiPromptModifiers = geminiPromptModifiers().get(),
                geminiAutoTranslateEnglishSource = geminiAutoTranslateEnglishSource().get(),
                geminiPrefetchNextChapterTranslation = geminiPrefetchNextChapterTranslation().get(),
                translationProvider = translationProvider().get(),
                airforceBaseUrl = airforceBaseUrl().get(),
                airforceApiKey = airforceApiKey().get(),
                airforceModel = airforceModel().get(),
                openRouterBaseUrl = openRouterBaseUrl().get(),
                openRouterApiKey = openRouterApiKey().get(),
                openRouterModel = openRouterModel().get(),
                deepSeekBaseUrl = deepSeekBaseUrl().get(),
                deepSeekApiKey = deepSeekApiKey().get(),
                deepSeekModel = deepSeekModel().get(),
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
            forceParagraphIndent = override?.forceParagraphIndent ?: forceParagraphIndent().get(),
            preserveSourceTextAlignInNative =
            override?.preserveSourceTextAlignInNative ?: preserveSourceTextAlignInNative().get(),
            fontFamily = override?.fontFamily ?: fontFamily().get(),
            theme = override?.theme ?: theme().get(),
            backgroundColor = override?.backgroundColor ?: backgroundColor().get(),
            textColor = override?.textColor ?: textColor().get(),
            customThemes = override?.customThemes ?: customThemes().get(),
            useVolumeButtons = override?.useVolumeButtons ?: useVolumeButtons().get(),
            swipeGestures = override?.swipeGestures ?: swipeGestures().get(),
            pageReader = override?.pageReader ?: pageReader().get(),
            preferWebViewRenderer = override?.preferWebViewRenderer ?: preferWebViewRenderer().get(),
            richNativeRendererExperimental =
            override?.richNativeRendererExperimental ?: richNativeRendererExperimental().get(),
            verticalSeekbar = override?.verticalSeekbar ?: verticalSeekbar().get(),
            swipeToNextChapter = override?.swipeToNextChapter ?: swipeToNextChapter().get(),
            swipeToPrevChapter = override?.swipeToPrevChapter ?: swipeToPrevChapter().get(),
            tapToScroll = override?.tapToScroll ?: tapToScroll().get(),
            autoScroll = override?.autoScroll ?: autoScroll().get(),
            autoScrollInterval = override?.autoScrollInterval ?: autoScrollInterval().get(),
            autoScrollOffset = override?.autoScrollOffset ?: autoScrollOffset().get(),
            prefetchNextChapter = override?.prefetchNextChapter ?: prefetchNextChapter().get(),
            fullScreenMode = override?.fullScreenMode ?: fullScreenMode().get(),
            keepScreenOn = override?.keepScreenOn ?: keepScreenOn().get(),
            showScrollPercentage = override?.showScrollPercentage ?: showScrollPercentage().get(),
            showBatteryAndTime = override?.showBatteryAndTime ?: showBatteryAndTime().get(),
            bionicReading = override?.bionicReading ?: bionicReading().get(),
            customCSS = override?.customCSS ?: customCSS().get(),
            customJS = override?.customJS ?: customJS().get(),
            geminiEnabled = geminiEnabled().get(),
            geminiApiKey = override?.geminiApiKey ?: geminiApiKey().get(),
            geminiModel = override?.geminiModel ?: geminiModel().get(),
            geminiBatchSize = override?.geminiBatchSize ?: geminiBatchSize().get(),
            geminiConcurrency = override?.geminiConcurrency ?: geminiConcurrency().get(),
            geminiDisableCache = override?.geminiDisableCache ?: geminiDisableCache().get(),
            geminiRelaxedMode = override?.geminiRelaxedMode ?: geminiRelaxedMode().get(),
            geminiReasoningEffort = override?.geminiReasoningEffort ?: geminiReasoningEffort().get(),
            geminiBudgetTokens = override?.geminiBudgetTokens ?: geminiBudgetTokens().get(),
            geminiTemperature = override?.geminiTemperature ?: geminiTemperature().get(),
            geminiTopP = override?.geminiTopP ?: geminiTopP().get(),
            geminiTopK = override?.geminiTopK ?: geminiTopK().get(),
            geminiSourceLang = override?.geminiSourceLang ?: geminiSourceLang().get(),
            geminiTargetLang = override?.geminiTargetLang ?: geminiTargetLang().get(),
            geminiPromptMode = override?.geminiPromptMode ?: geminiPromptMode().get(),
            geminiEnabledPromptModifiers =
            override?.geminiEnabledPromptModifiers ?: geminiEnabledPromptModifiers().get(),
            geminiCustomPromptModifier = override?.geminiCustomPromptModifier ?: geminiCustomPromptModifier().get(),
            geminiPromptModifiers = override?.geminiPromptModifiers ?: geminiPromptModifiers().get(),
            geminiAutoTranslateEnglishSource =
            override?.geminiAutoTranslateEnglishSource ?: geminiAutoTranslateEnglishSource().get(),
            geminiPrefetchNextChapterTranslation =
            override?.geminiPrefetchNextChapterTranslation ?: geminiPrefetchNextChapterTranslation().get(),
            translationProvider = override?.translationProvider ?: translationProvider().get(),
            airforceBaseUrl = override?.airforceBaseUrl ?: airforceBaseUrl().get(),
            airforceApiKey = override?.airforceApiKey ?: airforceApiKey().get(),
            airforceModel = override?.airforceModel ?: airforceModel().get(),
            openRouterBaseUrl = override?.openRouterBaseUrl ?: openRouterBaseUrl().get(),
            openRouterApiKey = override?.openRouterApiKey ?: openRouterApiKey().get(),
            openRouterModel = override?.openRouterModel ?: openRouterModel().get(),
            deepSeekBaseUrl = override?.deepSeekBaseUrl ?: deepSeekBaseUrl().get(),
            deepSeekApiKey = override?.deepSeekApiKey ?: deepSeekApiKey().get(),
            deepSeekModel = override?.deepSeekModel ?: deepSeekModel().get(),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun settingsFlow(sourceId: Long): Flow<NovelReaderSettings> {
        // Группируем настройки для избежания лимита combine()
        val displayFlow = combine(
            fontSize().changes(),
            lineHeight().changes(),
            margin().changes(),
            textAlign().changes(),
            forceParagraphIndent().changes(),
            preserveSourceTextAlignInNative().changes(),
            fontFamily().changes(),
        ) { values: Array<Any?> ->
            DisplaySettings(
                values[0] as Int,
                values[1] as Float,
                values[2] as Int,
                values[3] as TextAlign,
                values[4] as Boolean,
                values[5] as Boolean,
                values[6] as String,
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
            richNativeRendererExperimental().changes(),
            verticalSeekbar().changes(),
            swipeToNextChapter().changes(),
            swipeToPrevChapter().changes(),
            tapToScroll().changes(),
            autoScroll().changes(),
            autoScrollInterval().changes(),
            autoScrollOffset().changes(),
            prefetchNextChapter().changes(),
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
                values[9] as Boolean,
                values[10] as Int,
                values[11] as Int,
                values[12] as Boolean,
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

        val geminiFlow = combine(
            geminiEnabled().changes(),
            geminiApiKey().changes(),
            geminiModel().changes(),
            geminiBatchSize().changes(),
            geminiConcurrency().changes(),
            geminiDisableCache().changes(),
            geminiRelaxedMode().changes(),
            geminiReasoningEffort().changes(),
            geminiBudgetTokens().changes(),
            geminiTemperature().changes(),
            geminiTopP().changes(),
            geminiTopK().changes(),
            geminiSourceLang().changes(),
            geminiTargetLang().changes(),
            geminiPromptMode().changes(),
            geminiEnabledPromptModifiers().changes(),
            geminiCustomPromptModifier().changes(),
            geminiPromptModifiers().changes(),
            geminiAutoTranslateEnglishSource().changes(),
            geminiPrefetchNextChapterTranslation().changes(),
            translationProvider().changes(),
            airforceBaseUrl().changes(),
            airforceApiKey().changes(),
            airforceModel().changes(),
            openRouterBaseUrl().changes(),
            openRouterApiKey().changes(),
            openRouterModel().changes(),
            deepSeekBaseUrl().changes(),
            deepSeekApiKey().changes(),
            deepSeekModel().changes(),
        ) { values: Array<Any?> ->
            GeminiSettings(
                enabled = values[0] as Boolean,
                apiKey = values[1] as String,
                model = values[2] as String,
                batchSize = values[3] as Int,
                concurrency = values[4] as Int,
                disableCache = values[5] as Boolean,
                relaxedMode = values[6] as Boolean,
                reasoningEffort = values[7] as String,
                budgetTokens = values[8] as Int,
                temperature = values[9] as Float,
                topP = values[10] as Float,
                topK = values[11] as Int,
                sourceLang = values[12] as String,
                targetLang = values[13] as String,
                promptMode = values[14] as GeminiPromptMode,
                enabledPromptModifiers = values[15] as List<String>,
                customPromptModifier = values[16] as String,
                promptModifiers = values[17] as String,
                autoTranslateEnglishSource = values[18] as Boolean,
                prefetchNextChapterTranslation = values[19] as Boolean,
                translationProvider = values[20] as NovelTranslationProvider,
                airforceBaseUrl = values[21] as String,
                airforceApiKey = values[22] as String,
                airforceModel = values[23] as String,
                openRouterBaseUrl = values[24] as String,
                openRouterApiKey = values[25] as String,
                openRouterModel = values[26] as String,
                deepSeekBaseUrl = values[27] as String,
                deepSeekApiKey = values[28] as String,
                deepSeekModel = values[29] as String,
            )
        }

        return combine(
            displayFlow,
            themeFlow,
            navigationFlow,
            accessibilityFlow,
            advancedFlow,
            geminiFlow,
            sourceOverrides().changes(),
        ) { values: Array<Any?> ->
            val display = values[0] as DisplaySettings
            val theme = values[1] as ThemeSettings
            val navigation = values[2] as NavigationSettings
            val accessibility = values[3] as AccessibilitySettings
            val advanced = values[4] as AdvancedSettings
            val gemini = values[5] as GeminiSettings
            val overrides = values[6] as Map<Long, NovelReaderOverride>

            val override = overrides[sourceId]
            NovelReaderSettings(
                fontSize = override?.fontSize ?: display.fontSize,
                lineHeight = override?.lineHeight ?: display.lineHeight,
                margin = override?.margin ?: display.margin,
                textAlign = override?.textAlign ?: display.textAlign,
                forceParagraphIndent = override?.forceParagraphIndent ?: display.forceParagraphIndent,
                preserveSourceTextAlignInNative =
                override?.preserveSourceTextAlignInNative ?: display.preserveSourceTextAlignInNative,
                fontFamily = override?.fontFamily ?: display.fontFamily,
                theme = override?.theme ?: theme.theme,
                backgroundColor = override?.backgroundColor ?: theme.backgroundColor,
                textColor = override?.textColor ?: theme.textColor,
                customThemes = override?.customThemes ?: theme.customThemes,
                useVolumeButtons = override?.useVolumeButtons ?: navigation.useVolumeButtons,
                swipeGestures = override?.swipeGestures ?: navigation.swipeGestures,
                pageReader = override?.pageReader ?: navigation.pageReader,
                preferWebViewRenderer = override?.preferWebViewRenderer ?: navigation.preferWebViewRenderer,
                richNativeRendererExperimental =
                override?.richNativeRendererExperimental ?: navigation.richNativeRendererExperimental,
                verticalSeekbar = override?.verticalSeekbar ?: navigation.verticalSeekbar,
                swipeToNextChapter = override?.swipeToNextChapter ?: navigation.swipeToNextChapter,
                swipeToPrevChapter = override?.swipeToPrevChapter ?: navigation.swipeToPrevChapter,
                tapToScroll = override?.tapToScroll ?: navigation.tapToScroll,
                autoScroll = override?.autoScroll ?: navigation.autoScroll,
                autoScrollInterval = override?.autoScrollInterval ?: navigation.autoScrollInterval,
                autoScrollOffset = override?.autoScrollOffset ?: navigation.autoScrollOffset,
                prefetchNextChapter = override?.prefetchNextChapter ?: navigation.prefetchNextChapter,
                fullScreenMode = override?.fullScreenMode ?: accessibility.fullScreenMode,
                keepScreenOn = override?.keepScreenOn ?: accessibility.keepScreenOn,
                showScrollPercentage = override?.showScrollPercentage ?: accessibility.showScrollPercentage,
                showBatteryAndTime = override?.showBatteryAndTime ?: accessibility.showBatteryAndTime,
                bionicReading = override?.bionicReading ?: accessibility.bionicReading,
                customCSS = override?.customCSS ?: advanced.customCSS,
                customJS = override?.customJS ?: advanced.customJS,
                geminiEnabled = gemini.enabled,
                geminiApiKey = override?.geminiApiKey ?: gemini.apiKey,
                geminiModel = override?.geminiModel ?: gemini.model,
                geminiBatchSize = override?.geminiBatchSize ?: gemini.batchSize,
                geminiConcurrency = override?.geminiConcurrency ?: gemini.concurrency,
                geminiDisableCache = override?.geminiDisableCache ?: gemini.disableCache,
                geminiRelaxedMode = override?.geminiRelaxedMode ?: gemini.relaxedMode,
                geminiReasoningEffort = override?.geminiReasoningEffort ?: gemini.reasoningEffort,
                geminiBudgetTokens = override?.geminiBudgetTokens ?: gemini.budgetTokens,
                geminiTemperature = override?.geminiTemperature ?: gemini.temperature,
                geminiTopP = override?.geminiTopP ?: gemini.topP,
                geminiTopK = override?.geminiTopK ?: gemini.topK,
                geminiSourceLang = override?.geminiSourceLang ?: gemini.sourceLang,
                geminiTargetLang = override?.geminiTargetLang ?: gemini.targetLang,
                geminiPromptMode = override?.geminiPromptMode ?: gemini.promptMode,
                geminiEnabledPromptModifiers = override?.geminiEnabledPromptModifiers ?: gemini.enabledPromptModifiers,
                geminiCustomPromptModifier = override?.geminiCustomPromptModifier ?: gemini.customPromptModifier,
                geminiPromptModifiers = override?.geminiPromptModifiers ?: gemini.promptModifiers,
                geminiAutoTranslateEnglishSource =
                override?.geminiAutoTranslateEnglishSource ?: gemini.autoTranslateEnglishSource,
                geminiPrefetchNextChapterTranslation =
                override?.geminiPrefetchNextChapterTranslation ?: gemini.prefetchNextChapterTranslation,
                translationProvider = override?.translationProvider ?: gemini.translationProvider,
                airforceBaseUrl = override?.airforceBaseUrl ?: gemini.airforceBaseUrl,
                airforceApiKey = override?.airforceApiKey ?: gemini.airforceApiKey,
                airforceModel = override?.airforceModel ?: gemini.airforceModel,
                openRouterBaseUrl = override?.openRouterBaseUrl ?: gemini.openRouterBaseUrl,
                openRouterApiKey = override?.openRouterApiKey ?: gemini.openRouterApiKey,
                openRouterModel = override?.openRouterModel ?: gemini.openRouterModel,
                deepSeekBaseUrl = override?.deepSeekBaseUrl ?: gemini.deepSeekBaseUrl,
                deepSeekApiKey = override?.deepSeekApiKey ?: gemini.deepSeekApiKey,
                deepSeekModel = override?.deepSeekModel ?: gemini.deepSeekModel,
            )
        }
    }

    // Вспомогательные data classes для группировки в Flow
    private data class DisplaySettings(
        val fontSize: Int,
        val lineHeight: Float,
        val margin: Int,
        val textAlign: TextAlign,
        val forceParagraphIndent: Boolean,
        val preserveSourceTextAlignInNative: Boolean,
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
        val richNativeRendererExperimental: Boolean,
        val verticalSeekbar: Boolean,
        val swipeToNextChapter: Boolean,
        val swipeToPrevChapter: Boolean,
        val tapToScroll: Boolean,
        val autoScroll: Boolean,
        val autoScrollInterval: Int,
        val autoScrollOffset: Int,
        val prefetchNextChapter: Boolean,
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

    private data class GeminiSettings(
        val enabled: Boolean,
        val apiKey: String,
        val model: String,
        val batchSize: Int,
        val concurrency: Int,
        val disableCache: Boolean,
        val relaxedMode: Boolean,
        val reasoningEffort: String,
        val budgetTokens: Int,
        val temperature: Float,
        val topP: Float,
        val topK: Int,
        val sourceLang: String,
        val targetLang: String,
        val promptMode: GeminiPromptMode,
        val enabledPromptModifiers: List<String>,
        val customPromptModifier: String,
        val promptModifiers: String,
        val autoTranslateEnglishSource: Boolean,
        val prefetchNextChapterTranslation: Boolean,
        val translationProvider: NovelTranslationProvider,
        val airforceBaseUrl: String,
        val airforceApiKey: String,
        val airforceModel: String,
        val openRouterBaseUrl: String,
        val openRouterApiKey: String,
        val openRouterModel: String,
        val deepSeekBaseUrl: String,
        val deepSeekApiKey: String,
        val deepSeekModel: String,
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
        private val stringListSerializer = ListSerializer(String.serializer())
    }
}
