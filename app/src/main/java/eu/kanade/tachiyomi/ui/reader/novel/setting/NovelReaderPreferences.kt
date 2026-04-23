package eu.kanade.tachiyomi.ui.reader.novel.setting

import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
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
    val paragraphSpacing: Int,
    val forceParagraphIndent: Boolean,
    val preserveSourceTextAlignInNative: Boolean,
    val fontFamily: String,
    val forceBoldText: Boolean,
    val forceItalicText: Boolean,
    val textShadow: Boolean,
    val textShadowColor: String,
    val textShadowBlur: Float,
    val textShadowX: Float,
    val textShadowY: Float,
    val pageEdgeShadow: Boolean,
    val pageEdgeShadowAlpha: Float,

    // Theme
    val theme: NovelReaderTheme,
    val backgroundColor: String?,
    val textColor: String?,
    val backgroundTexture: NovelReaderBackgroundTexture,
    val nativeTextureStrengthPercent: Int,
    val appearanceMode: NovelReaderAppearanceMode,
    val backgroundSource: NovelReaderBackgroundSource,
    val backgroundPresetId: String,
    val customBackgroundPath: String,
    val customBackgroundId: String = "",
    val oledEdgeGradient: Boolean,
    val customThemes: List<NovelReaderColorTheme>,

    // Navigation
    val useVolumeButtons: Boolean,
    val swipeGestures: Boolean,
    val pageReader: Boolean,
    val showPageChapterTitle: Boolean = true,
    val preferWebViewRenderer: Boolean,
    val richNativeRendererExperimental: Boolean,
    val pageTransitionStyle: NovelPageTransitionStyle = NovelPageTransitionStyle.SLIDE,
    val bookFlipAnimationSpeed: NovelBookFlipAnimationSpeed = NovelBookFlipAnimationSpeed.SLOW,
    val pageTurnSpeed: NovelPageTurnSpeed = NovelPageTurnSpeed.NORMAL,
    val pageTurnIntensity: NovelPageTurnIntensity = NovelPageTurnIntensity.MEDIUM,
    val pageTurnShadowIntensity: NovelPageTurnShadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
    val pageTurnActivationZone: NovelPageTurnActivationZone = NovelPageTurnActivationZone.WIDE,
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
    val showKindleInfoBlock: Boolean,
    val showTimeToEnd: Boolean,
    val showWordCount: Boolean,
    val bionicReading: Boolean,

    // Advanced
    val customCSS: String,
    val customJS: String,

    // Selected text translation
    val selectedTextTranslationEnabled: Boolean = true,
    val selectedTextTranslationTargetLanguage: String = "Russian",

    // Gemini Translation
    val geminiEnabled: Boolean = false,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.1-flash-lite-preview",
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
    val geminiStylePreset: NovelTranslationStylePreset = NovelTranslationStylePreset.PROFESSIONAL,
    val geminiPromptModifiers: String = "",
    val geminiAutoTranslateEnglishSource: Boolean = false,
    val geminiPrefetchNextChapterTranslation: Boolean = false,
    val geminiPrivateUnlocked: Boolean = false,
    val geminiPrivatePythonLikeMode: Boolean = false,
    val translationProvider: NovelTranslationProvider = NovelTranslationProvider.GEMINI,
    val openRouterBaseUrl: String = "https://openrouter.ai/api/v1",
    val openRouterApiKey: String = "",
    val openRouterModel: String = "",
    val deepSeekBaseUrl: String = "https://api.deepseek.com",
    val deepSeekApiKey: String = "",
    val deepSeekModel: String = "deepseek-chat",
    val mistralBaseUrl: String = "https://api.mistral.ai/v1",
    val mistralApiKey: String = "",
    val mistralModel: String = "mistral-large-latest",
    val nvidiaBaseUrl: String = "https://integrate.api.nvidia.com/v1",
    val nvidiaApiKey: String = "",
    val nvidiaModel: String = "",

    // Google Translation
    val googleTranslationEnabled: Boolean = false,
    val googleTranslationSourceLang: String = "auto",
    val googleTranslationTargetLang: String = "Russian",
    val googleTranslationAutoStart: Boolean = false,

    // TTS
    val ttsEnabled: Boolean = false,
    val ttsEnginePackage: String = "",
    val ttsVoiceId: String = "",
    val ttsLocaleTag: String = "",
    val ttsSpeechRate: Float = 1f,
    val ttsPitch: Float = 1f,
    val ttsHighlightMode: NovelTtsHighlightMode = NovelTtsHighlightMode.AUTO,
    val ttsWordHighlightEnabled: Boolean = true,
    val ttsAutoAdvanceChapter: Boolean = false,
    val ttsFollowAlong: Boolean = true,
    val ttsPauseOnManualNavigation: Boolean = true,
    val ttsKeepScreenOnDuringPlayback: Boolean = false,
    val ttsPreferTranslatedText: Boolean = false,
    val ttsReadChapterTitle: Boolean = true,
)

enum class NovelReaderTheme {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class NovelReaderBackgroundTexture {
    NONE,
    PAPER_GRAIN,
    LINEN,
    PARCHMENT,
}

enum class NovelReaderAppearanceMode {
    THEME,
    BACKGROUND,
}

enum class NovelReaderBackgroundSource {
    PRESET,
    CUSTOM,
}

enum class TextAlign {
    SOURCE,
    LEFT,
    CENTER,
    JUSTIFY,
    RIGHT,
}

enum class NovelReaderParagraphSpacing {
    COMPACT,
    NORMAL,
    SPACIOUS,
}

enum class NovelPageTransitionStyle {
    INSTANT,
    SLIDE,
    DEPTH,
    BOOK,
    CURL,
    BOOK_FLIP,
}

enum class NovelPageTurnSpeed {
    SLOWER,
    SLOW,
    NORMAL,
    FAST,
    FASTER,
}

enum class NovelBookFlipAnimationSpeed {
    SLOW,
    NORMAL,
    FAST,
}

enum class NovelPageTurnIntensity {
    SOFTER,
    LOW,
    MEDIUM,
    HIGH,
    STRONGER,
}

enum class NovelPageTurnShadowIntensity {
    SOFTER,
    LOW,
    MEDIUM,
    HIGH,
    STRONGER,
}

enum class NovelPageTurnActivationZone {
    NARROWER,
    NARROW,
    NORMAL,
    WIDE,
    WIDER,
}

enum class GeminiPromptMode {
    CLASSIC,
    ADULT_18,
}

enum class NovelTranslationStylePreset {
    PROFESSIONAL,
    LITERARY,
    CONVERSATIONAL,
    VULGAR_18,
    MINIMAL,
}

enum class NovelTranslationProvider {
    GEMINI,
    GEMINI_PRIVATE,
    OPENROUTER,
    DEEPSEEK,
    MISTRAL,
    NVIDIA,
}

enum class NovelTtsHighlightMode {
    AUTO,
    EXACT,
    ESTIMATED,
    OFF,
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
    val paragraphSpacingDp: Int? = null,
    val forceParagraphIndent: Boolean? = null,
    val preserveSourceTextAlignInNative: Boolean? = null,
    val fontFamily: String? = null,
    val forceBoldText: Boolean? = null,
    val forceItalicText: Boolean? = null,
    val textShadow: Boolean? = null,
    val textShadowColor: String? = null,
    val textShadowBlur: Float? = null,
    val textShadowX: Float? = null,
    val textShadowY: Float? = null,
    val pageEdgeShadow: Boolean? = null,
    val pageEdgeShadowAlpha: Float? = null,

    // Theme
    val theme: NovelReaderTheme? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val backgroundTexture: NovelReaderBackgroundTexture? = null,
    val nativeTextureStrengthPercent: Int? = null,
    val appearanceMode: NovelReaderAppearanceMode? = null,
    val backgroundSource: NovelReaderBackgroundSource? = null,
    val backgroundPresetId: String? = null,
    val customBackgroundPath: String? = null,
    val customBackgroundId: String? = null,
    val oledEdgeGradient: Boolean? = null,
    val customThemes: List<NovelReaderColorTheme>? = null,

    // Navigation
    val useVolumeButtons: Boolean? = null,
    val swipeGestures: Boolean? = null,
    val pageReader: Boolean? = null,
    val showPageChapterTitle: Boolean? = null,
    val preferWebViewRenderer: Boolean? = null,
    val richNativeRendererExperimental: Boolean? = null,
    val pageTransitionStyle: NovelPageTransitionStyle? = null,
    val bookFlipAnimationSpeed: NovelBookFlipAnimationSpeed? = null,
    val pageTurnSpeed: NovelPageTurnSpeed? = null,
    val pageTurnIntensity: NovelPageTurnIntensity? = null,
    val pageTurnShadowIntensity: NovelPageTurnShadowIntensity? = null,
    val pageTurnActivationZone: NovelPageTurnActivationZone? = null,
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
    val showKindleInfoBlock: Boolean? = null,
    val showTimeToEnd: Boolean? = null,
    val showWordCount: Boolean? = null,
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
    val geminiStylePreset: NovelTranslationStylePreset? = null,
    val geminiPromptModifiers: String? = null,
    val geminiAutoTranslateEnglishSource: Boolean? = null,
    val geminiPrefetchNextChapterTranslation: Boolean? = null,
    val geminiPrivateUnlocked: Boolean? = null,
    val geminiPrivatePythonLikeMode: Boolean? = null,
    val translationProvider: NovelTranslationProvider? = null,
    val openRouterBaseUrl: String? = null,
    val openRouterApiKey: String? = null,
    val openRouterModel: String? = null,
    val deepSeekBaseUrl: String? = null,
    val deepSeekApiKey: String? = null,
    val deepSeekModel: String? = null,
    val mistralBaseUrl: String? = null,
    val mistralApiKey: String? = null,
    val mistralModel: String? = null,
    val nvidiaBaseUrl: String? = null,
    val nvidiaApiKey: String? = null,
    val nvidiaModel: String? = null,

    // Google Translation
    val googleTranslationEnabled: Boolean? = null,
    val googleTranslationSourceLang: String? = null,
    val googleTranslationTargetLang: String? = null,
    val googleTranslationAutoStart: Boolean? = null,

    // TTS
    val ttsEnabled: Boolean? = null,
    val ttsEnginePackage: String? = null,
    val ttsVoiceId: String? = null,
    val ttsLocaleTag: String? = null,
    val ttsSpeechRate: Float? = null,
    val ttsPitch: Float? = null,
    val ttsHighlightMode: NovelTtsHighlightMode? = null,
    val ttsWordHighlightEnabled: Boolean? = null,
    val ttsAutoAdvanceChapter: Boolean? = null,
    val ttsFollowAlong: Boolean? = null,
    val ttsPauseOnManualNavigation: Boolean? = null,
    val ttsKeepScreenOnDuringPlayback: Boolean? = null,
    val ttsPreferTranslatedText: Boolean? = null,
    val ttsReadChapterTitle: Boolean? = null,
)

class NovelReaderPreferences(
    private val preferenceStore: PreferenceStore,
    private val json: Json = Injekt.get(),
) {
    init {
        migrateLegacyParagraphSpacingIfNeeded()
        migrateLegacyBackgroundSelectionIfNeeded()
        migrateLegacyPageTransitionStyleIfNeeded()
    }

    // Display
    fun fontSize() = preferenceStore.getInt("novel_reader_font_size", DEFAULT_FONT_SIZE)

    fun lineHeight() = preferenceStore.getFloat("novel_reader_line_height", DEFAULT_LINE_HEIGHT)

    fun margin() = preferenceStore.getInt("novel_reader_margins", DEFAULT_MARGIN)

    fun textAlign() = preferenceStore.getEnum("novel_reader_text_align", TextAlign.SOURCE)

    fun paragraphSpacing() =
        preferenceStore.getInt("novel_reader_paragraph_spacing_dp", DEFAULT_PARAGRAPH_SPACING_DP)

    private fun migrateLegacyParagraphSpacingIfNeeded() {
        val legacyValue = preferenceStore
            .getString("novel_reader_paragraph_spacing", "")
            .get()
            .ifBlank { return }
        paragraphSpacing().set(resolveLegacyParagraphSpacingDp(legacyValue))
    }

    fun forceParagraphIndent() = preferenceStore.getBoolean("novel_reader_force_paragraph_indent", true)

    fun fontFamily() = preferenceStore.getString("novel_reader_font_family", "")

    fun forceBoldText() = preferenceStore.getBoolean("novel_reader_force_bold_text", false)

    fun forceItalicText() = preferenceStore.getBoolean("novel_reader_force_italic_text", false)

    fun textShadow() = preferenceStore.getBoolean("novel_reader_text_shadow", false)

    fun textShadowColor() = preferenceStore.getString("novel_reader_text_shadow_color", "")

    fun textShadowBlur() = preferenceStore.getFloat("novel_reader_text_shadow_blur", 4f)

    fun textShadowX() = preferenceStore.getFloat("novel_reader_text_shadow_x", 0f)

    fun textShadowY() = preferenceStore.getFloat("novel_reader_text_shadow_y", 0f)

    fun pageEdgeShadow() = preferenceStore.getBoolean("novel_reader_page_edge_shadow", false)

    fun pageEdgeShadowAlpha() = preferenceStore.getFloat("novel_reader_page_edge_shadow_alpha", 0.25f)

    // Theme
    fun theme() = preferenceStore.getEnum("novel_reader_theme", NovelReaderTheme.SYSTEM)

    fun backgroundColor() = preferenceStore.getString("novel_reader_bg_color", "")

    fun textColor() = preferenceStore.getString("novel_reader_text_color", "")

    fun backgroundTexture() =
        preferenceStore.getEnum("novel_reader_background_texture", NovelReaderBackgroundTexture.PAPER_GRAIN)

    fun nativeTextureStrengthPercent() = preferenceStore.getInt("novel_reader_native_texture_strength_percent", 50)

    fun appearanceMode() =
        preferenceStore.getEnum("novel_reader_appearance_mode", NovelReaderAppearanceMode.THEME)

    fun backgroundSource() =
        preferenceStore.getEnum("novel_reader_background_source", NovelReaderBackgroundSource.PRESET)

    fun backgroundPresetId() =
        preferenceStore.getString("novel_reader_background_preset_id", DEFAULT_BACKGROUND_PRESET_ID)

    fun customBackgroundPath() = preferenceStore.getString("novel_reader_custom_background_path", "")

    fun customBackgroundId() = preferenceStore.getString("novel_reader_custom_background_id", "")

    fun oledEdgeGradient() = preferenceStore.getBoolean("novel_reader_oled_edge_gradient", false)

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

    fun showPageChapterTitle() =
        preferenceStore.getBoolean("novel_reader_show_page_chapter_title", true)

    fun preferWebViewRenderer() = preferenceStore.getBoolean("novel_reader_prefer_webview_renderer", false)

    fun richNativeRendererExperimental() =
        preferenceStore.getBoolean("novel_reader_rich_native_renderer_experimental", true)

    fun pageTransitionStyle(): Preference<NovelPageTransitionStyle> {
        val preference = preferenceStore.getEnum(
            "novel_reader_page_transition_style",
            NovelPageTransitionStyle.SLIDE,
        )
        return object : Preference<NovelPageTransitionStyle> by preference {
            override fun get(): NovelPageTransitionStyle {
                val current = preference.get()
                val normalized = current.normalizeLegacyPageTransitionStyle()
                if (normalized != current) {
                    preference.set(normalized)
                }
                return normalized
            }

            override fun set(value: NovelPageTransitionStyle) {
                preference.set(value.normalizeLegacyPageTransitionStyle())
            }

            override fun changes() = preference.changes().map { it.normalizeLegacyPageTransitionStyle() }

            override fun stateIn(scope: CoroutineScope) =
                changes().stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, get())
        }
    }

    fun bookFlipAnimationSpeed() =
        preferenceStore.getEnum("novel_reader_book_flip_animation_speed", NovelBookFlipAnimationSpeed.SLOW)

    fun pageTurnSpeed() =
        preferenceStore.getEnum("novel_reader_page_turn_speed", NovelPageTurnSpeed.NORMAL)

    fun pageTurnIntensity() =
        preferenceStore.getEnum("novel_reader_page_turn_intensity", NovelPageTurnIntensity.MEDIUM)

    fun pageTurnShadowIntensity() =
        preferenceStore.getEnum("novel_reader_page_turn_shadow_intensity", NovelPageTurnShadowIntensity.MEDIUM)

    fun pageTurnActivationZone() =
        preferenceStore.getEnum("novel_reader_page_turn_activation_zone", NovelPageTurnActivationZone.WIDE)

    fun preserveSourceTextAlignInNative() =
        preferenceStore.getBoolean("novel_reader_preserve_source_text_align_in_native", true)

    fun verticalSeekbar() = preferenceStore.getBoolean("novel_reader_vertical_seekbar", true)

    fun swipeToNextChapter() = preferenceStore.getBoolean("novel_reader_swipe_to_next_chapter", false)

    fun swipeToPrevChapter() = preferenceStore.getBoolean("novel_reader_swipe_to_prev_chapter", false)

    fun tapToScroll() = preferenceStore.getBoolean("novel_reader_tap_to_scroll", true)

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

    fun showKindleInfoBlock() = preferenceStore.getBoolean("novel_reader_show_kindle_info_block", true)

    fun showTimeToEnd() = preferenceStore.getBoolean("novel_reader_show_time_to_end", true)

    fun showWordCount() = preferenceStore.getBoolean("novel_reader_show_word_count", true)

    fun bionicReading() = preferenceStore.getBoolean("novel_reader_bionic_reading", false)

    // Advanced
    fun customCSS() = preferenceStore.getString("novel_reader_custom_css", "")

    fun customJS() = preferenceStore.getString("novel_reader_custom_js", "")

    fun selectedTextTranslationEnabled() =
        preferenceStore.getBoolean("novel_reader_selected_text_translation_enabled", false)

    fun selectedTextTranslationTargetLanguage() =
        preferenceStore.getString("novel_reader_selected_text_translation_target_language", "Russian")

    fun ttsEnabled() = preferenceStore.getBoolean("novel_reader_tts_enabled", false)

    fun ttsEnginePackage() = preferenceStore.getString("novel_reader_tts_engine_package", "")

    fun ttsVoiceId() = preferenceStore.getString("novel_reader_tts_voice_id", "")

    fun ttsLocaleTag() = preferenceStore.getString("novel_reader_tts_locale_tag", "")

    fun ttsRecentLanguageTags() = preferenceStore.getString("novel_reader_tts_recent_language_tags", "")

    fun ttsSpeechRate() = preferenceStore.getFloat("novel_reader_tts_speech_rate", 1f)

    fun ttsPitch() = preferenceStore.getFloat("novel_reader_tts_pitch", 1f)

    fun ttsHighlightMode() =
        preferenceStore.getEnum("novel_reader_tts_highlight_mode", NovelTtsHighlightMode.AUTO)

    fun ttsWordHighlightEnabled() =
        preferenceStore.getBoolean("novel_reader_tts_word_highlight_enabled", true)

    fun ttsAutoAdvanceChapter() =
        preferenceStore.getBoolean("novel_reader_tts_auto_advance_chapter", false)

    fun ttsFollowAlong() = preferenceStore.getBoolean("novel_reader_tts_follow_along", true)

    fun ttsPauseOnManualNavigation() =
        preferenceStore.getBoolean("novel_reader_tts_pause_on_manual_navigation", true)

    fun ttsKeepScreenOnDuringPlayback() =
        preferenceStore.getBoolean("novel_reader_tts_keep_screen_on_during_playback", false)

    fun ttsPreferTranslatedText() =
        preferenceStore.getBoolean("novel_reader_tts_prefer_translated_text", false)

    fun ttsReadChapterTitle() =
        preferenceStore.getBoolean("novel_reader_tts_read_chapter_title", true)

    // Gemini Translation
    fun geminiEnabled() = preferenceStore.getBoolean("novel_reader_gemini_enabled", false)

    fun geminiApiKey() = preferenceStore.getString("novel_reader_gemini_api_key", "")

    fun geminiModel() = preferenceStore.getString("novel_reader_gemini_model", "gemini-3.1-flash-lite-preview")

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

    fun geminiStylePreset() =
        preferenceStore.getEnum("novel_reader_gemini_style_preset", NovelTranslationStylePreset.PROFESSIONAL)

    fun geminiPromptModifiers() = preferenceStore.getString("novel_reader_gemini_prompt_modifiers", "")

    fun geminiAutoTranslateEnglishSource() =
        preferenceStore.getBoolean("novel_reader_gemini_auto_translate_english_source", false)

    fun geminiPrefetchNextChapterTranslation() =
        preferenceStore.getBoolean("novel_reader_gemini_prefetch_next_chapter_translation", false)

    fun geminiPrivateUnlocked() = preferenceStore.getBoolean("novel_reader_gemini_private_unlocked", false)

    fun geminiPrivatePythonLikeMode() = preferenceStore.getBoolean(
        "novel_reader_gemini_private_python_like_mode",
        false,
    )

    fun translationProvider() =
        preferenceStore.getEnum("novel_reader_translation_provider", NovelTranslationProvider.GEMINI)

    fun translatedDownloadFormat(novelId: Long): NovelTranslatedDownloadFormat {
        val stored = preferenceStore.getString(
            translatedDownloadFormatKey(novelId),
            NovelTranslatedDownloadFormat.TXT.name,
        ).get()
        return runCatching { NovelTranslatedDownloadFormat.valueOf(stored) }
            .getOrDefault(NovelTranslatedDownloadFormat.TXT)
    }

    fun setTranslatedDownloadFormat(
        novelId: Long,
        format: NovelTranslatedDownloadFormat,
    ) {
        preferenceStore.getString(translatedDownloadFormatKey(novelId), NovelTranslatedDownloadFormat.TXT.name)
            .set(format.name)
    }

    fun openRouterBaseUrl() = preferenceStore.getString(
        "novel_reader_openrouter_base_url",
        "https://openrouter.ai/api/v1",
    )

    fun openRouterApiKey() = preferenceStore.getString("novel_reader_openrouter_api_key", "")

    fun openRouterModel() = preferenceStore.getString("novel_reader_openrouter_model", "")

    fun deepSeekBaseUrl() = preferenceStore.getString("novel_reader_deepseek_base_url", "https://api.deepseek.com")

    fun deepSeekApiKey() = preferenceStore.getString("novel_reader_deepseek_api_key", "")

    fun deepSeekModel() = preferenceStore.getString("novel_reader_deepseek_model", "deepseek-chat")

    fun mistralBaseUrl() = preferenceStore.getString("novel_reader_mistral_base_url", "https://api.mistral.ai/v1")

    fun mistralApiKey() = preferenceStore.getString("novel_reader_mistral_api_key", "")

    fun mistralModel() = preferenceStore.getString("novel_reader_mistral_model", "mistral-large-latest")

    fun nvidiaBaseUrl() = preferenceStore.getString(
        "novel_reader_nvidia_base_url",
        "https://integrate.api.nvidia.com/v1",
    )

    fun nvidiaApiKey() = preferenceStore.getString("novel_reader_nvidia_api_key", "")

    fun nvidiaModel() = preferenceStore.getString("novel_reader_nvidia_model", "")

    private fun translatedDownloadFormatKey(novelId: Long): String {
        return "novel_reader_translated_download_format_$novelId"
    }

    // Google Translation
    fun googleTranslationEnabled() = preferenceStore.getBoolean("novel_reader_google_translation_enabled", false)

    fun googleTranslationSourceLang() = preferenceStore.getString("novel_reader_google_translation_source_lang", "auto")

    fun googleTranslationTargetLang() = preferenceStore.getString(
        "novel_reader_google_translation_target_lang",
        "Russian",
    )

    fun googleTranslationAutoStart() = preferenceStore.getBoolean("novel_reader_google_translation_auto_start", false)

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
            updated[sourceId] = override.normalizeLegacyPageTransitionStyle()
        }
        sourceOverrides().set(updated)
    }

    fun migrateLegacyBackgroundSelectionIfNeeded() {
        val selectedCustomId = customBackgroundId().get()
        val legacyCustomPath = customBackgroundPath().get()
        if (selectedCustomId.isBlank() && legacyCustomPath.isNotBlank()) {
            customBackgroundId().set(legacyCustomPath)
        }

        val overrides = sourceOverrides().get()
        var hasChanges = false
        val migrated = overrides.mapValues { (_, value) ->
            if (value.customBackgroundId.isNullOrBlank() && !value.customBackgroundPath.isNullOrBlank()) {
                hasChanges = true
                value.copy(customBackgroundId = value.customBackgroundPath)
            } else {
                value
            }
        }
        if (hasChanges) {
            sourceOverrides().set(migrated)
        }
    }

    fun migrateLegacyPageTransitionStyleIfNeeded() {
        if (pageTransitionStyle().get() == NovelPageTransitionStyle.BOOK) {
            pageTransitionStyle().set(NovelPageTransitionStyle.CURL)
        }

        val overrides = sourceOverrides().get()
        var hasChanges = false
        val migrated = overrides.mapValues { (_, value) ->
            val normalized = value.normalizeLegacyPageTransitionStyle()
            if (normalized != value) {
                hasChanges = true
            }
            normalized
        }
        if (hasChanges) {
            sourceOverrides().set(migrated)
        }
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
                paragraphSpacingDp = paragraphSpacing().get(),
                forceParagraphIndent = forceParagraphIndent().get(),
                preserveSourceTextAlignInNative = preserveSourceTextAlignInNative().get(),
                fontFamily = fontFamily().get(),
                forceBoldText = forceBoldText().get(),
                forceItalicText = forceItalicText().get(),
                textShadow = textShadow().get(),
                theme = theme().get(),
                backgroundColor = backgroundColor().get(),
                textColor = textColor().get(),
                backgroundTexture = backgroundTexture().get(),
                nativeTextureStrengthPercent = nativeTextureStrengthPercent().get(),
                appearanceMode = appearanceMode().get(),
                backgroundSource = backgroundSource().get(),
                backgroundPresetId = backgroundPresetId().get(),
                customBackgroundPath = customBackgroundPath().get(),
                customBackgroundId = customBackgroundId().get(),
                oledEdgeGradient = oledEdgeGradient().get(),
                customThemes = customThemes().get(),
                useVolumeButtons = useVolumeButtons().get(),
                swipeGestures = swipeGestures().get(),
                pageReader = pageReader().get(),
                showPageChapterTitle = showPageChapterTitle().get(),
                preferWebViewRenderer = preferWebViewRenderer().get(),
                richNativeRendererExperimental = richNativeRendererExperimental().get(),
                pageTransitionStyle = pageTransitionStyle().get(),
                bookFlipAnimationSpeed = bookFlipAnimationSpeed().get(),
                pageTurnSpeed = pageTurnSpeed().get(),
                pageTurnIntensity = pageTurnIntensity().get(),
                pageTurnShadowIntensity = pageTurnShadowIntensity().get(),
                pageTurnActivationZone = pageTurnActivationZone().get(),
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
                showKindleInfoBlock = showKindleInfoBlock().get(),
                showTimeToEnd = showTimeToEnd().get(),
                showWordCount = showWordCount().get(),
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
                geminiStylePreset = geminiStylePreset().get(),
                geminiPromptModifiers = geminiPromptModifiers().get(),
                geminiAutoTranslateEnglishSource = geminiAutoTranslateEnglishSource().get(),
                geminiPrefetchNextChapterTranslation = geminiPrefetchNextChapterTranslation().get(),
                geminiPrivatePythonLikeMode = geminiPrivatePythonLikeMode().get(),
                translationProvider = translationProvider().get(),
                openRouterBaseUrl = openRouterBaseUrl().get(),
                openRouterApiKey = openRouterApiKey().get(),
                openRouterModel = openRouterModel().get(),
                deepSeekBaseUrl = deepSeekBaseUrl().get(),
                deepSeekApiKey = deepSeekApiKey().get(),
                deepSeekModel = deepSeekModel().get(),
                mistralBaseUrl = mistralBaseUrl().get(),
                mistralApiKey = mistralApiKey().get(),
                mistralModel = mistralModel().get(),
                nvidiaApiKey = nvidiaApiKey().get(),
                nvidiaModel = nvidiaModel().get(),
                googleTranslationEnabled = googleTranslationEnabled().get(),
                googleTranslationSourceLang = googleTranslationSourceLang().get(),
                googleTranslationTargetLang = googleTranslationTargetLang().get(),
                googleTranslationAutoStart = googleTranslationAutoStart().get(),
                ttsEnabled = ttsEnabled().get(),
                ttsEnginePackage = ttsEnginePackage().get(),
                ttsVoiceId = ttsVoiceId().get(),
                ttsLocaleTag = ttsLocaleTag().get(),
                ttsSpeechRate = ttsSpeechRate().get(),
                ttsPitch = ttsPitch().get(),
                ttsHighlightMode = ttsHighlightMode().get(),
                ttsWordHighlightEnabled = ttsWordHighlightEnabled().get(),
                ttsAutoAdvanceChapter = ttsAutoAdvanceChapter().get(),
                ttsFollowAlong = ttsFollowAlong().get(),
                ttsPauseOnManualNavigation = ttsPauseOnManualNavigation().get(),
                ttsKeepScreenOnDuringPlayback = ttsKeepScreenOnDuringPlayback().get(),
                ttsPreferTranslatedText = ttsPreferTranslatedText().get(),
                ttsReadChapterTitle = ttsReadChapterTitle().get(),
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
            paragraphSpacing = override?.paragraphSpacingDp ?: paragraphSpacing().get(),
            forceParagraphIndent = override?.forceParagraphIndent ?: forceParagraphIndent().get(),
            preserveSourceTextAlignInNative =
            override?.preserveSourceTextAlignInNative ?: preserveSourceTextAlignInNative().get(),
            fontFamily = override?.fontFamily ?: fontFamily().get(),
            forceBoldText = override?.forceBoldText ?: forceBoldText().get(),
            forceItalicText = override?.forceItalicText ?: forceItalicText().get(),
            textShadow = override?.textShadow ?: textShadow().get(),
            textShadowColor = override?.textShadowColor ?: textShadowColor().get(),
            textShadowBlur = override?.textShadowBlur ?: textShadowBlur().get(),
            textShadowX = override?.textShadowX ?: textShadowX().get(),
            textShadowY = override?.textShadowY ?: textShadowY().get(),
            pageEdgeShadow = override?.pageEdgeShadow ?: pageEdgeShadow().get(),
            pageEdgeShadowAlpha = override?.pageEdgeShadowAlpha ?: pageEdgeShadowAlpha().get(),
            theme = override?.theme ?: theme().get(),
            backgroundColor = override?.backgroundColor ?: backgroundColor().get(),
            textColor = override?.textColor ?: textColor().get(),
            backgroundTexture = override?.backgroundTexture ?: backgroundTexture().get(),
            nativeTextureStrengthPercent =
            override?.nativeTextureStrengthPercent ?: nativeTextureStrengthPercent().get(),
            appearanceMode = override?.appearanceMode ?: appearanceMode().get(),
            backgroundSource = override?.backgroundSource ?: backgroundSource().get(),
            backgroundPresetId = override?.backgroundPresetId ?: backgroundPresetId().get(),
            customBackgroundPath = override?.customBackgroundPath ?: customBackgroundPath().get(),
            customBackgroundId = override?.customBackgroundId ?: customBackgroundId().get(),
            oledEdgeGradient = override?.oledEdgeGradient ?: oledEdgeGradient().get(),
            customThemes = override?.customThemes ?: customThemes().get(),
            useVolumeButtons = override?.useVolumeButtons ?: useVolumeButtons().get(),
            swipeGestures = override?.swipeGestures ?: swipeGestures().get(),
            pageReader = override?.pageReader ?: pageReader().get(),
            showPageChapterTitle = override?.showPageChapterTitle ?: showPageChapterTitle().get(),
            preferWebViewRenderer = override?.preferWebViewRenderer ?: preferWebViewRenderer().get(),
            richNativeRendererExperimental =
            override?.richNativeRendererExperimental ?: richNativeRendererExperimental().get(),
            pageTransitionStyle = override?.pageTransitionStyle ?: pageTransitionStyle().get(),
            bookFlipAnimationSpeed = override?.bookFlipAnimationSpeed ?: bookFlipAnimationSpeed().get(),
            pageTurnSpeed = override?.pageTurnSpeed ?: pageTurnSpeed().get(),
            pageTurnIntensity = override?.pageTurnIntensity ?: pageTurnIntensity().get(),
            pageTurnShadowIntensity =
            override?.pageTurnShadowIntensity ?: pageTurnShadowIntensity().get(),
            pageTurnActivationZone =
            override?.pageTurnActivationZone ?: pageTurnActivationZone().get(),
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
            showKindleInfoBlock = override?.showKindleInfoBlock ?: showKindleInfoBlock().get(),
            showTimeToEnd = override?.showTimeToEnd ?: showTimeToEnd().get(),
            showWordCount = override?.showWordCount ?: showWordCount().get(),
            bionicReading = override?.bionicReading ?: bionicReading().get(),
            customCSS = override?.customCSS ?: customCSS().get(),
            customJS = override?.customJS ?: customJS().get(),
            selectedTextTranslationEnabled =
            selectedTextTranslationEnabled().get(),
            selectedTextTranslationTargetLanguage =
            selectedTextTranslationTargetLanguage().get(),
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
            geminiStylePreset = override?.geminiStylePreset ?: geminiStylePreset().get(),
            geminiPromptModifiers = override?.geminiPromptModifiers ?: geminiPromptModifiers().get(),
            geminiAutoTranslateEnglishSource =
            override?.geminiAutoTranslateEnglishSource ?: geminiAutoTranslateEnglishSource().get(),
            geminiPrefetchNextChapterTranslation =
            override?.geminiPrefetchNextChapterTranslation ?: geminiPrefetchNextChapterTranslation().get(),
            geminiPrivateUnlocked = geminiPrivateUnlocked().get(),
            geminiPrivatePythonLikeMode =
            override?.geminiPrivatePythonLikeMode ?: geminiPrivatePythonLikeMode().get(),
            translationProvider = override?.translationProvider ?: translationProvider().get(),
            openRouterBaseUrl = override?.openRouterBaseUrl ?: openRouterBaseUrl().get(),
            openRouterApiKey = override?.openRouterApiKey ?: openRouterApiKey().get(),
            openRouterModel = override?.openRouterModel ?: openRouterModel().get(),
            deepSeekBaseUrl = override?.deepSeekBaseUrl ?: deepSeekBaseUrl().get(),
            deepSeekApiKey = override?.deepSeekApiKey ?: deepSeekApiKey().get(),
            deepSeekModel = override?.deepSeekModel ?: deepSeekModel().get(),
            mistralBaseUrl = override?.mistralBaseUrl ?: mistralBaseUrl().get(),
            mistralApiKey = override?.mistralApiKey ?: mistralApiKey().get(),
            mistralModel = override?.mistralModel ?: mistralModel().get(),
            nvidiaApiKey = override?.nvidiaApiKey ?: nvidiaApiKey().get(),
            nvidiaModel = override?.nvidiaModel ?: nvidiaModel().get(),
            googleTranslationEnabled = override?.googleTranslationEnabled ?: googleTranslationEnabled().get(),
            googleTranslationSourceLang = override?.googleTranslationSourceLang ?: googleTranslationSourceLang().get(),
            googleTranslationTargetLang = override?.googleTranslationTargetLang ?: googleTranslationTargetLang().get(),
            googleTranslationAutoStart = override?.googleTranslationAutoStart ?: googleTranslationAutoStart().get(),
            ttsEnabled = override?.ttsEnabled ?: ttsEnabled().get(),
            ttsEnginePackage = override?.ttsEnginePackage ?: ttsEnginePackage().get(),
            ttsVoiceId = override?.ttsVoiceId ?: ttsVoiceId().get(),
            ttsLocaleTag = override?.ttsLocaleTag ?: ttsLocaleTag().get(),
            ttsSpeechRate = override?.ttsSpeechRate ?: ttsSpeechRate().get(),
            ttsPitch = override?.ttsPitch ?: ttsPitch().get(),
            ttsHighlightMode = override?.ttsHighlightMode ?: ttsHighlightMode().get(),
            ttsWordHighlightEnabled = override?.ttsWordHighlightEnabled ?: ttsWordHighlightEnabled().get(),
            ttsAutoAdvanceChapter = override?.ttsAutoAdvanceChapter ?: ttsAutoAdvanceChapter().get(),
            ttsFollowAlong = override?.ttsFollowAlong ?: ttsFollowAlong().get(),
            ttsPauseOnManualNavigation =
            override?.ttsPauseOnManualNavigation ?: ttsPauseOnManualNavigation().get(),
            ttsKeepScreenOnDuringPlayback =
            override?.ttsKeepScreenOnDuringPlayback ?: ttsKeepScreenOnDuringPlayback().get(),
            ttsPreferTranslatedText = override?.ttsPreferTranslatedText ?: ttsPreferTranslatedText().get(),
            ttsReadChapterTitle = override?.ttsReadChapterTitle ?: ttsReadChapterTitle().get(),
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
            paragraphSpacing().changes(),
            forceParagraphIndent().changes(),
            preserveSourceTextAlignInNative().changes(),
            fontFamily().changes(),
            forceBoldText().changes(),
            forceItalicText().changes(),
            textShadow().changes(),
            textShadowColor().changes(),
            textShadowBlur().changes(),
            textShadowX().changes(),
            textShadowY().changes(),
            pageEdgeShadow().changes(),
            pageEdgeShadowAlpha().changes(),
        ) { values: Array<Any?> ->
            DisplaySettings(
                values[0] as Int,
                values[1] as Float,
                values[2] as Int,
                values[3] as TextAlign,
                values[4] as Int,
                values[5] as Boolean,
                values[6] as Boolean,
                values[7] as String,
                values[8] as Boolean,
                values[9] as Boolean,
                values[10] as Boolean,
                values[11] as String,
                values[12] as Float,
                values[13] as Float,
                values[14] as Float,
                values[15] as Boolean,
                values[16] as Float,
            )
        }.distinctUntilChanged()

        val themeFlow = combine(
            theme().changes(),
            backgroundColor().changes(),
            textColor().changes(),
            backgroundTexture().changes(),
            nativeTextureStrengthPercent().changes(),
            appearanceMode().changes(),
            backgroundSource().changes(),
            backgroundPresetId().changes(),
            customBackgroundPath().changes(),
            customBackgroundId().changes(),
            oledEdgeGradient().changes(),
            customThemes().changes(),
        ) { values: Array<Any?> ->
            ThemeSettings(
                values[0] as NovelReaderTheme,
                values[1] as String,
                values[2] as String,
                values[3] as NovelReaderBackgroundTexture,
                values[4] as Int,
                values[5] as NovelReaderAppearanceMode,
                values[6] as NovelReaderBackgroundSource,
                values[7] as String,
                values[8] as String,
                values[9] as String,
                values[10] as Boolean,
                values[11] as List<NovelReaderColorTheme>,
            )
        }.distinctUntilChanged()

        val navigationFlow = combine(
            useVolumeButtons().changes(),
            swipeGestures().changes(),
            pageReader().changes(),
            showPageChapterTitle().changes(),
            preferWebViewRenderer().changes(),
            richNativeRendererExperimental().changes(),
            pageTransitionStyle().changes(),
            bookFlipAnimationSpeed().changes(),
            pageTurnSpeed().changes(),
            pageTurnIntensity().changes(),
            pageTurnShadowIntensity().changes(),
            pageTurnActivationZone().changes(),
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
                values[6] as NovelPageTransitionStyle,
                values[7] as NovelBookFlipAnimationSpeed,
                values[8] as NovelPageTurnSpeed,
                values[9] as NovelPageTurnIntensity,
                values[10] as NovelPageTurnShadowIntensity,
                values[11] as NovelPageTurnActivationZone,
                values[12] as Boolean,
                values[13] as Boolean,
                values[14] as Boolean,
                values[15] as Boolean,
                values[16] as Boolean,
                values[17] as Int,
                values[18] as Int,
                values[19] as Boolean,
            )
        }.distinctUntilChanged()

        val accessibilityFlow = combine(
            fullScreenMode().changes(),
            keepScreenOn().changes(),
            showScrollPercentage().changes(),
            showBatteryAndTime().changes(),
            showKindleInfoBlock().changes(),
            showTimeToEnd().changes(),
            showWordCount().changes(),
            bionicReading().changes(),
        ) { values: Array<Any?> ->
            AccessibilitySettings(
                fullScreenMode = values[0] as Boolean,
                keepScreenOn = values[1] as Boolean,
                showScrollPercentage = values[2] as Boolean,
                showBatteryAndTime = values[3] as Boolean,
                showKindleInfoBlock = values[4] as Boolean,
                showTimeToEnd = values[5] as Boolean,
                showWordCount = values[6] as Boolean,
                bionicReading = values[7] as Boolean,
            )
        }.distinctUntilChanged()

        val advancedFlow = combine(
            customCSS().changes(),
            customJS().changes(),
            selectedTextTranslationEnabled().changes(),
            selectedTextTranslationTargetLanguage().changes(),
        ) { values: Array<Any?> ->
            AdvancedSettings(
                customCSS = values[0] as String,
                customJS = values[1] as String,
                selectedTextTranslationEnabled = values[2] as Boolean,
                selectedTextTranslationTargetLanguage = values[3] as String,
            )
        }.distinctUntilChanged()

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
            geminiStylePreset().changes(),
            geminiPromptModifiers().changes(),
            geminiAutoTranslateEnglishSource().changes(),
            geminiPrefetchNextChapterTranslation().changes(),
            geminiPrivateUnlocked().changes(),
            geminiPrivatePythonLikeMode().changes(),
            translationProvider().changes(),
            openRouterBaseUrl().changes(),
            openRouterApiKey().changes(),
            openRouterModel().changes(),
            deepSeekBaseUrl().changes(),
            deepSeekApiKey().changes(),
            deepSeekModel().changes(),
            mistralBaseUrl().changes(),
            mistralApiKey().changes(),
            mistralModel().changes(),
            nvidiaBaseUrl().changes(),
            nvidiaApiKey().changes(),
            nvidiaModel().changes(),
            googleTranslationEnabled().changes(),
            googleTranslationSourceLang().changes(),
            googleTranslationTargetLang().changes(),
            googleTranslationAutoStart().changes(),
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
                stylePreset = values[17] as NovelTranslationStylePreset,
                promptModifiers = values[18] as String,
                autoTranslateEnglishSource = values[19] as Boolean,
                prefetchNextChapterTranslation = values[20] as Boolean,
                privateUnlocked = values[21] as Boolean,
                privatePythonLikeMode = values[22] as Boolean,
                translationProvider = values[23] as NovelTranslationProvider,
                openRouterBaseUrl = values[24] as String,
                openRouterApiKey = values[25] as String,
                openRouterModel = values[26] as String,
                deepSeekBaseUrl = values[27] as String,
                deepSeekApiKey = values[28] as String,
                deepSeekModel = values[29] as String,
                mistralBaseUrl = values[30] as String,
                mistralApiKey = values[31] as String,
                mistralModel = values[32] as String,
                nvidiaBaseUrl = values[33] as String,
                nvidiaApiKey = values[34] as String,
                nvidiaModel = values[35] as String,
                googleTranslationEnabled = values[36] as Boolean,
                googleTranslationSourceLang = values[37] as String,
                googleTranslationTargetLang = values[38] as String,
                googleTranslationAutoStart = values[39] as Boolean,
            )
        }.distinctUntilChanged()

        val ttsFlow = combine(
            ttsEnabled().changes(),
            ttsEnginePackage().changes(),
            ttsVoiceId().changes(),
            ttsLocaleTag().changes(),
            ttsSpeechRate().changes(),
            ttsPitch().changes(),
            ttsHighlightMode().changes(),
            ttsWordHighlightEnabled().changes(),
            ttsAutoAdvanceChapter().changes(),
            ttsFollowAlong().changes(),
            ttsPauseOnManualNavigation().changes(),
            ttsKeepScreenOnDuringPlayback().changes(),
            ttsPreferTranslatedText().changes(),
            ttsReadChapterTitle().changes(),
        ) { values: Array<Any?> ->
            TtsSettings(
                enabled = values[0] as Boolean,
                enginePackage = values[1] as String,
                voiceId = values[2] as String,
                localeTag = values[3] as String,
                speechRate = values[4] as Float,
                pitch = values[5] as Float,
                highlightMode = values[6] as NovelTtsHighlightMode,
                wordHighlightEnabled = values[7] as Boolean,
                autoAdvanceChapter = values[8] as Boolean,
                followAlong = values[9] as Boolean,
                pauseOnManualNavigation = values[10] as Boolean,
                keepScreenOnDuringPlayback = values[11] as Boolean,
                preferTranslatedText = values[12] as Boolean,
                readChapterTitle = values[13] as Boolean,
            )
        }.distinctUntilChanged()

        return combine(
            displayFlow,
            themeFlow,
            navigationFlow,
            accessibilityFlow,
            advancedFlow,
            geminiFlow,
            ttsFlow,
            sourceOverrides().changes(),
        ) { values: Array<Any?> ->
            val display = values[0] as DisplaySettings
            val theme = values[1] as ThemeSettings
            val navigation = values[2] as NavigationSettings
            val accessibility = values[3] as AccessibilitySettings
            val advanced = values[4] as AdvancedSettings
            val gemini = values[5] as GeminiSettings
            val tts = values[6] as TtsSettings
            val overrides = values[7] as Map<Long, NovelReaderOverride>

            val override = overrides[sourceId]
            NovelReaderSettings(
                fontSize = override?.fontSize ?: display.fontSize,
                lineHeight = override?.lineHeight ?: display.lineHeight,
                margin = override?.margin ?: display.margin,
                textAlign = override?.textAlign ?: display.textAlign,
                paragraphSpacing = override?.paragraphSpacingDp ?: display.paragraphSpacing,
                forceParagraphIndent = override?.forceParagraphIndent ?: display.forceParagraphIndent,
                preserveSourceTextAlignInNative =
                override?.preserveSourceTextAlignInNative ?: display.preserveSourceTextAlignInNative,
                fontFamily = override?.fontFamily ?: display.fontFamily,
                forceBoldText = override?.forceBoldText ?: display.forceBoldText,
                forceItalicText = override?.forceItalicText ?: display.forceItalicText,
                textShadow = override?.textShadow ?: display.textShadow,
                textShadowColor = override?.textShadowColor ?: display.textShadowColor,
                textShadowBlur = override?.textShadowBlur ?: display.textShadowBlur,
                textShadowX = override?.textShadowX ?: display.textShadowX,
                textShadowY = override?.textShadowY ?: display.textShadowY,
                pageEdgeShadow = override?.pageEdgeShadow ?: display.pageEdgeShadow,
                pageEdgeShadowAlpha = override?.pageEdgeShadowAlpha ?: display.pageEdgeShadowAlpha,
                theme = override?.theme ?: theme.theme,
                backgroundColor = override?.backgroundColor ?: theme.backgroundColor,
                textColor = override?.textColor ?: theme.textColor,
                backgroundTexture = override?.backgroundTexture ?: theme.backgroundTexture,
                nativeTextureStrengthPercent =
                override?.nativeTextureStrengthPercent ?: theme.nativeTextureStrengthPercent,
                appearanceMode = override?.appearanceMode ?: theme.appearanceMode,
                backgroundSource = override?.backgroundSource ?: theme.backgroundSource,
                backgroundPresetId = override?.backgroundPresetId ?: theme.backgroundPresetId,
                customBackgroundPath = override?.customBackgroundPath ?: theme.customBackgroundPath,
                customBackgroundId = override?.customBackgroundId ?: theme.customBackgroundId,
                oledEdgeGradient = override?.oledEdgeGradient ?: theme.oledEdgeGradient,
                customThemes = override?.customThemes ?: theme.customThemes,
                useVolumeButtons = override?.useVolumeButtons ?: navigation.useVolumeButtons,
                swipeGestures = override?.swipeGestures ?: navigation.swipeGestures,
                pageReader = override?.pageReader ?: navigation.pageReader,
                showPageChapterTitle = override?.showPageChapterTitle ?: navigation.showPageChapterTitle,
                preferWebViewRenderer = override?.preferWebViewRenderer ?: navigation.preferWebViewRenderer,
                richNativeRendererExperimental =
                override?.richNativeRendererExperimental ?: navigation.richNativeRendererExperimental,
                pageTransitionStyle = override?.pageTransitionStyle ?: navigation.pageTransitionStyle,
                bookFlipAnimationSpeed = override?.bookFlipAnimationSpeed ?: navigation.bookFlipAnimationSpeed,
                pageTurnSpeed = override?.pageTurnSpeed ?: navigation.pageTurnSpeed,
                pageTurnIntensity = override?.pageTurnIntensity ?: navigation.pageTurnIntensity,
                pageTurnShadowIntensity =
                override?.pageTurnShadowIntensity ?: navigation.pageTurnShadowIntensity,
                pageTurnActivationZone =
                override?.pageTurnActivationZone ?: navigation.pageTurnActivationZone,
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
                showKindleInfoBlock = override?.showKindleInfoBlock ?: accessibility.showKindleInfoBlock,
                showTimeToEnd = override?.showTimeToEnd ?: accessibility.showTimeToEnd,
                showWordCount = override?.showWordCount ?: accessibility.showWordCount,
                bionicReading = override?.bionicReading ?: accessibility.bionicReading,
                customCSS = override?.customCSS ?: advanced.customCSS,
                customJS = override?.customJS ?: advanced.customJS,
                selectedTextTranslationEnabled = advanced.selectedTextTranslationEnabled,
                selectedTextTranslationTargetLanguage = advanced.selectedTextTranslationTargetLanguage,
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
                geminiStylePreset = override?.geminiStylePreset ?: gemini.stylePreset,
                geminiPromptModifiers = override?.geminiPromptModifiers ?: gemini.promptModifiers,
                geminiAutoTranslateEnglishSource =
                override?.geminiAutoTranslateEnglishSource ?: gemini.autoTranslateEnglishSource,
                geminiPrefetchNextChapterTranslation =
                override?.geminiPrefetchNextChapterTranslation ?: gemini.prefetchNextChapterTranslation,
                geminiPrivateUnlocked = override?.geminiPrivateUnlocked ?: gemini.privateUnlocked,
                geminiPrivatePythonLikeMode =
                override?.geminiPrivatePythonLikeMode ?: gemini.privatePythonLikeMode,
                translationProvider = override?.translationProvider ?: gemini.translationProvider,
                openRouterBaseUrl = override?.openRouterBaseUrl ?: gemini.openRouterBaseUrl,
                openRouterApiKey = override?.openRouterApiKey ?: gemini.openRouterApiKey,
                openRouterModel = override?.openRouterModel ?: gemini.openRouterModel,
                deepSeekBaseUrl = override?.deepSeekBaseUrl ?: gemini.deepSeekBaseUrl,
                deepSeekApiKey = override?.deepSeekApiKey ?: gemini.deepSeekApiKey,
                deepSeekModel = override?.deepSeekModel ?: gemini.deepSeekModel,
                mistralBaseUrl = override?.mistralBaseUrl ?: gemini.mistralBaseUrl,
                mistralApiKey = override?.mistralApiKey ?: gemini.mistralApiKey,
                mistralModel = override?.mistralModel ?: gemini.mistralModel,
                googleTranslationEnabled = override?.googleTranslationEnabled ?: gemini.googleTranslationEnabled,
                googleTranslationSourceLang =
                override?.googleTranslationSourceLang ?: gemini.googleTranslationSourceLang,
                googleTranslationTargetLang =
                override?.googleTranslationTargetLang ?: gemini.googleTranslationTargetLang,
                googleTranslationAutoStart = override?.googleTranslationAutoStart ?: gemini.googleTranslationAutoStart,
                ttsEnabled = override?.ttsEnabled ?: tts.enabled,
                ttsEnginePackage = override?.ttsEnginePackage ?: tts.enginePackage,
                ttsVoiceId = override?.ttsVoiceId ?: tts.voiceId,
                ttsLocaleTag = override?.ttsLocaleTag ?: tts.localeTag,
                ttsSpeechRate = override?.ttsSpeechRate ?: tts.speechRate,
                ttsPitch = override?.ttsPitch ?: tts.pitch,
                ttsHighlightMode = override?.ttsHighlightMode ?: tts.highlightMode,
                ttsWordHighlightEnabled = override?.ttsWordHighlightEnabled ?: tts.wordHighlightEnabled,
                ttsAutoAdvanceChapter = override?.ttsAutoAdvanceChapter ?: tts.autoAdvanceChapter,
                ttsFollowAlong = override?.ttsFollowAlong ?: tts.followAlong,
                ttsPauseOnManualNavigation =
                override?.ttsPauseOnManualNavigation ?: tts.pauseOnManualNavigation,
                ttsKeepScreenOnDuringPlayback =
                override?.ttsKeepScreenOnDuringPlayback ?: tts.keepScreenOnDuringPlayback,
                ttsPreferTranslatedText =
                override?.ttsPreferTranslatedText ?: tts.preferTranslatedText,
                ttsReadChapterTitle = override?.ttsReadChapterTitle ?: tts.readChapterTitle,
            )
        }.distinctUntilChanged()
    }

    // Вспомогательные data classes для группировки в Flow
    private data class DisplaySettings(
        val fontSize: Int,
        val lineHeight: Float,
        val margin: Int,
        val textAlign: TextAlign,
        val paragraphSpacing: Int,
        val forceParagraphIndent: Boolean,
        val preserveSourceTextAlignInNative: Boolean,
        val fontFamily: String,
        val forceBoldText: Boolean,
        val forceItalicText: Boolean,
        val textShadow: Boolean,
        val textShadowColor: String,
        val textShadowBlur: Float,
        val textShadowX: Float,
        val textShadowY: Float,
        val pageEdgeShadow: Boolean,
        val pageEdgeShadowAlpha: Float,
    )

    private data class ThemeSettings(
        val theme: NovelReaderTheme,
        val backgroundColor: String,
        val textColor: String,
        val backgroundTexture: NovelReaderBackgroundTexture,
        val nativeTextureStrengthPercent: Int,
        val appearanceMode: NovelReaderAppearanceMode,
        val backgroundSource: NovelReaderBackgroundSource,
        val backgroundPresetId: String,
        val customBackgroundPath: String,
        val customBackgroundId: String,
        val oledEdgeGradient: Boolean,
        val customThemes: List<NovelReaderColorTheme>,
    )

    private data class NavigationSettings(
        val useVolumeButtons: Boolean,
        val swipeGestures: Boolean,
        val pageReader: Boolean,
        val showPageChapterTitle: Boolean,
        val preferWebViewRenderer: Boolean,
        val richNativeRendererExperimental: Boolean,
        val pageTransitionStyle: NovelPageTransitionStyle,
        val bookFlipAnimationSpeed: NovelBookFlipAnimationSpeed,
        val pageTurnSpeed: NovelPageTurnSpeed,
        val pageTurnIntensity: NovelPageTurnIntensity,
        val pageTurnShadowIntensity: NovelPageTurnShadowIntensity,
        val pageTurnActivationZone: NovelPageTurnActivationZone,
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
        val showKindleInfoBlock: Boolean,
        val showTimeToEnd: Boolean,
        val showWordCount: Boolean,
        val bionicReading: Boolean,
    )

    private data class AdvancedSettings(
        val customCSS: String,
        val customJS: String,
        val selectedTextTranslationEnabled: Boolean,
        val selectedTextTranslationTargetLanguage: String,
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
        val stylePreset: NovelTranslationStylePreset,
        val promptModifiers: String,
        val autoTranslateEnglishSource: Boolean,
        val prefetchNextChapterTranslation: Boolean,
        val privateUnlocked: Boolean,
        val privatePythonLikeMode: Boolean,
        val translationProvider: NovelTranslationProvider,
        val openRouterBaseUrl: String,
        val openRouterApiKey: String,
        val openRouterModel: String,
        val deepSeekBaseUrl: String,
        val deepSeekApiKey: String,
        val deepSeekModel: String,
        val mistralBaseUrl: String,
        val mistralApiKey: String,
        val mistralModel: String,
        val nvidiaBaseUrl: String,
        val nvidiaApiKey: String,
        val nvidiaModel: String,
        val googleTranslationEnabled: Boolean,
        val googleTranslationSourceLang: String,
        val googleTranslationTargetLang: String,
        val googleTranslationAutoStart: Boolean,
    )

    private data class TtsSettings(
        val enabled: Boolean,
        val enginePackage: String,
        val voiceId: String,
        val localeTag: String,
        val speechRate: Float,
        val pitch: Float,
        val highlightMode: NovelTtsHighlightMode,
        val wordHighlightEnabled: Boolean,
        val autoAdvanceChapter: Boolean,
        val followAlong: Boolean,
        val pauseOnManualNavigation: Boolean,
        val keepScreenOnDuringPlayback: Boolean,
        val preferTranslatedText: Boolean,
        val readChapterTitle: Boolean,
    )

    companion object {
        const val DEFAULT_FONT_SIZE = 16
        const val DEFAULT_LINE_HEIGHT = 1.6f
        const val DEFAULT_MARGIN = 16
        const val DEFAULT_PARAGRAPH_SPACING_DP = 12
        const val DEFAULT_AUTO_SCROLL_INTERVAL = 10
        const val DEFAULT_AUTO_SCROLL_OFFSET = 0
        const val DEFAULT_BACKGROUND_PRESET_ID = "linen_paper"

        private val overrideSerializer = MapSerializer(
            Long.serializer(),
            NovelReaderOverride.serializer(),
        )
        private val customThemesSerializer = ListSerializer(NovelReaderColorTheme.serializer())
        private val stringListSerializer = ListSerializer(String.serializer())
    }
}

private fun resolveLegacyParagraphSpacingDp(rawValue: String): Int {
    return when (rawValue) {
        NovelReaderParagraphSpacing.COMPACT.name -> 8
        NovelReaderParagraphSpacing.SPACIOUS.name -> 16
        else -> NovelReaderPreferences.DEFAULT_PARAGRAPH_SPACING_DP
    }
}

private fun NovelPageTransitionStyle.normalizeLegacyPageTransitionStyle(): NovelPageTransitionStyle {
    return if (this == NovelPageTransitionStyle.BOOK) {
        NovelPageTransitionStyle.CURL
    } else {
        this
    }
}

private fun NovelReaderOverride.normalizeLegacyPageTransitionStyle(): NovelReaderOverride {
    return if (pageTransitionStyle == NovelPageTransitionStyle.BOOK) {
        copy(pageTransitionStyle = NovelPageTransitionStyle.CURL)
    } else {
        this
    }
}
