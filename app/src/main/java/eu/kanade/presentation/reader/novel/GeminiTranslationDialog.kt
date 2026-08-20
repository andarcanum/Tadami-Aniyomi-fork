@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.presentation.reader.novel

import android.content.ClipData
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.reader.settings.AuroraChipFlow
import eu.kanade.presentation.reader.settings.AuroraFieldLabel
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraTabRow
import eu.kanade.presentation.reader.settings.AuroraToggleRow
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.rememberSupportsBlurBehind
import eu.kanade.tachiyomi.ui.reader.novel.ProviderApiTestStatus
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptModifiers
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationStylePresets
import eu.kanade.tachiyomi.ui.reader.novel.translation.OLLAMA_CLOUD_FREE_MODELS
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveTranslationReasoningOptions
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

internal enum class TranslationKind {
    Gemini,
    Google,
}

internal data class TranslationSwitchRequest(
    val from: TranslationKind,
    val to: TranslationKind,
)

@Composable
internal fun GeminiTranslationDialog(
    readerSettings: NovelReaderSettings,
    isTranslating: Boolean,
    translationProgress: Int,
    isVisible: Boolean,
    hasCache: Boolean,
    logs: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleVisibility: () -> Unit,
    onClear: () -> Unit,
    onClearAllCache: () -> Unit,
    onAddLog: (String) -> Unit,
    onClearLogs: () -> Unit,
    onSetGeminiApiKey: (String) -> Unit,
    onSetGeminiModel: (String) -> Unit,
    onSetGeminiBatchSize: (Int) -> Unit,
    onSetGeminiConcurrency: (Int) -> Unit,
    onSetGeminiRelaxedMode: (Boolean) -> Unit,
    onSetGeminiDisableCache: (Boolean) -> Unit,
    onSetGeminiReasoningEffort: (String) -> Unit,
    onSetGeminiBudgetTokens: (Int) -> Unit,
    onSetGeminiTemperature: (Float) -> Unit,
    onSetGeminiTopP: (Float) -> Unit,
    onSetGeminiTopK: (Int) -> Unit,
    onSetGeminiPromptMode: (GeminiPromptMode) -> Unit,
    onSetGeminiSourceLang: (String) -> Unit,
    onSetGeminiTargetLang: (String) -> Unit,
    onSetGeminiStylePreset: (NovelTranslationStylePreset) -> Unit,
    onSetGeminiEnabledPromptModifiers: (List<String>) -> Unit,
    onSetGeminiCustomPromptModifier: (String) -> Unit,
    onSetGeminiAutoTranslateEnglishSource: (Boolean) -> Unit,
    onSetGeminiPrefetchNextChapterTranslation: (Boolean) -> Unit,
    onSetGeminiPrivateUnlocked: (Boolean) -> Unit,
    onSetGeminiPrivatePythonLikeMode: (Boolean) -> Unit,
    onSetTranslationProvider: (NovelTranslationProvider) -> Unit,
    onSetOpenRouterBaseUrl: (String) -> Unit,
    onSetOpenRouterApiKey: (String) -> Unit,
    onSetOpenRouterModel: (String) -> Unit,
    onRefreshOpenRouterModels: () -> Unit,
    onTestOpenRouterConnection: () -> Unit,
    onSetDeepSeekBaseUrl: (String) -> Unit,
    onSetDeepSeekApiKey: (String) -> Unit,
    onSetDeepSeekModel: (String) -> Unit,
    onRefreshDeepSeekModels: () -> Unit,
    onTestDeepSeekConnection: () -> Unit,
    onSetMistralBaseUrl: (String) -> Unit,
    onSetMistralApiKey: (String) -> Unit,
    onSetMistralModel: (String) -> Unit,
    onRefreshMistralModels: () -> Unit,
    onTestMistralConnection: () -> Unit,
    onSetNvidiaBaseUrl: (String) -> Unit,
    onSetNvidiaApiKey: (String) -> Unit,
    onSetNvidiaModel: (String) -> Unit,
    onRefreshNvidiaModels: () -> Unit,
    onTestNvidiaConnection: () -> Unit,
    onSetOllamaCloudBaseUrl: (String) -> Unit,
    onSetOllamaCloudApiKey: (String) -> Unit,
    onSetOllamaCloudModel: (String) -> Unit,
    onRefreshOllamaCloudModels: () -> Unit,
    onTestOllamaCloudConnection: () -> Unit,
    openRouterModels: List<String>,
    isOpenRouterModelsLoading: Boolean,
    isTestingOpenRouterConnection: Boolean,
    openRouterApiTestStatus: ProviderApiTestStatus,
    openRouterApiTestMessage: String?,
    deepSeekModels: List<String>,
    isDeepSeekModelsLoading: Boolean,
    isTestingDeepSeekConnection: Boolean,
    deepSeekApiTestStatus: ProviderApiTestStatus,
    deepSeekApiTestMessage: String?,
    mistralModels: List<String>,
    isMistralModelsLoading: Boolean,
    isTestingMistralConnection: Boolean,
    mistralApiTestStatus: ProviderApiTestStatus,
    mistralApiTestMessage: String?,
    nvidiaModels: List<String>,
    isNvidiaModelsLoading: Boolean,
    isTestingNvidiaConnection: Boolean,
    nvidiaApiTestStatus: ProviderApiTestStatus,
    nvidiaApiTestMessage: String?,
    ollamaCloudModels: List<String>,
    isOllamaCloudModelsLoading: Boolean,
    isTestingOllamaCloudConnection: Boolean,
    ollamaCloudApiTestStatus: ProviderApiTestStatus,
    ollamaCloudApiTestMessage: String?,
    onDismiss: () -> Unit,
) {
    val modelEntries = remember {
        listOf(
            "gemini-3-flash-preview" to "Gemini 3 Flash",
            "gemini-3-pro-preview" to "Gemini 3 Pro",
            "gemini-3.1-flash-lite-preview" to "Gemini 3.1 Flash Lite",
        )
    }
    val modelMap = remember(modelEntries) { modelEntries.toMap() }
    val speedPresets = remember {
        listOf(
            "100-1" to (100 to 1),
            "40-2" to (40 to 2),
            "50-2" to (50 to 2),
            "30-3" to (30 to 3),
        )
    }
    val openRouterAllModelEntries = remember(openRouterModels) {
        openRouterModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.endsWith(":free", ignoreCase = true) }
            .distinct()
            .sorted()
            .associateWith { it }
    }
    val deepSeekAllModelEntries = remember(deepSeekModels) {
        deepSeekModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .associateWith { it }
    }
    val mistralAllModelEntries = remember(mistralModels) {
        mistralModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .associateWith { it }
    }
    val nvidiaAllModelEntries = remember(nvidiaModels) {
        nvidiaModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .associateWith { it }
    }
    val ollamaCloudAllModelEntries = remember(ollamaCloudModels) {
        ollamaCloudModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .associateWith { name ->
                if (name in OLLAMA_CLOUD_FREE_MODELS) "$name (Free)" else name
            }
    }

    var tempKey by remember(readerSettings.geminiApiKey) { mutableStateOf(readerSettings.geminiApiKey) }
    var tempModel by remember(readerSettings.geminiModel) {
        mutableStateOf(
            when (readerSettings.geminiModel) {
                "gemini-3-flash" -> "gemini-3-flash-preview"
                "gemini-2.5-flash" -> "gemini-3.1-flash-lite-preview"
                else -> readerSettings.geminiModel
            },
        )
    }
    var tempBatch by remember(readerSettings.geminiBatchSize) {
        mutableStateOf(readerSettings.geminiBatchSize.toString())
    }
    var tempConcurrency by remember(readerSettings.geminiConcurrency) {
        mutableStateOf(readerSettings.geminiConcurrency.toString())
    }
    var tempRelaxed by remember(readerSettings.geminiRelaxedMode) { mutableStateOf(readerSettings.geminiRelaxedMode) }
    var tempDisableCache by remember(readerSettings.geminiDisableCache) {
        mutableStateOf(readerSettings.geminiDisableCache)
    }
    var tempReasoning by remember(readerSettings.geminiReasoningEffort) {
        mutableStateOf(readerSettings.geminiReasoningEffort)
    }
    var tempBudget by remember(readerSettings.geminiBudgetTokens) { mutableStateOf(readerSettings.geminiBudgetTokens) }
    var tempTemperature by remember(readerSettings.geminiTemperature) {
        mutableStateOf(readerSettings.geminiTemperature.toString())
    }
    var tempTopP by remember(readerSettings.geminiTopP) { mutableStateOf(readerSettings.geminiTopP.toString()) }
    var tempTopK by remember(readerSettings.geminiTopK) { mutableStateOf(readerSettings.geminiTopK.toString()) }
    var tempPromptMode by remember(readerSettings.geminiPromptMode) { mutableStateOf(readerSettings.geminiPromptMode) }
    var tempSourceLang by remember(readerSettings.geminiSourceLang) { mutableStateOf(readerSettings.geminiSourceLang) }
    var tempTargetLang by remember(readerSettings.geminiTargetLang) { mutableStateOf(readerSettings.geminiTargetLang) }
    var tempStylePreset by remember(readerSettings.geminiStylePreset) {
        mutableStateOf(readerSettings.geminiStylePreset)
    }
    var tempEnabledModifiers by remember(readerSettings.geminiEnabledPromptModifiers) {
        mutableStateOf(readerSettings.geminiEnabledPromptModifiers.toSet())
    }
    var tempCustomModifier by remember(readerSettings.geminiCustomPromptModifier) {
        mutableStateOf(readerSettings.geminiCustomPromptModifier)
    }
    var tempAutoTranslateEnglish by remember(readerSettings.geminiAutoTranslateEnglishSource) {
        mutableStateOf(readerSettings.geminiAutoTranslateEnglishSource)
    }
    var tempPrefetchNextChapterTranslation by remember(readerSettings.geminiPrefetchNextChapterTranslation) {
        mutableStateOf(readerSettings.geminiPrefetchNextChapterTranslation)
    }
    var tempProvider by remember(readerSettings.translationProvider) {
        mutableStateOf(readerSettings.translationProvider)
    }
    var tempPrivatePythonLikeMode by remember(readerSettings.geminiPrivatePythonLikeMode) {
        mutableStateOf(readerSettings.geminiPrivatePythonLikeMode)
    }
    val isPrivateProviderInstalled = remember { GeminiPrivateBridge.isInstalled() }
    val privateProviderFallbackLabel = stringResource(
        AYMR.strings.novel_reader_translation_provider_gemini_private,
    )
    val privateProviderLabel = remember(isPrivateProviderInstalled, privateProviderFallbackLabel) {
        if (isPrivateProviderInstalled) GeminiPrivateBridge.providerLabel() else privateProviderFallbackLabel
    }

    val visibilityOnLabel = stringResource(AYMR.strings.novel_reader_gemini_visibility_on)
    val visibilityOffLabel = stringResource(AYMR.strings.novel_reader_gemini_visibility_off)
    val reasoningMinimalLabel = stringResource(AYMR.strings.novel_reader_gemini_reasoning_minimal)
    val reasoningLowLabel = stringResource(AYMR.strings.novel_reader_gemini_reasoning_low)
    val reasoningMediumLabel = stringResource(AYMR.strings.novel_reader_gemini_reasoning_medium)
    val reasoningHighLabel = stringResource(AYMR.strings.novel_reader_gemini_reasoning_high)
    val providerLabel = stringResource(AYMR.strings.novel_reader_translation_provider)
    val geminiModelLabel = stringResource(AYMR.strings.novel_reader_gemini_model)
    val openRouterModelLabel = stringResource(AYMR.strings.novel_reader_openrouter_model)
    val deepSeekModelLabel = stringResource(AYMR.strings.novel_reader_deepseek_model)
    val mistralModelLabel = stringResource(AYMR.strings.novel_reader_mistral_model)
    val nvidiaModelLabel = stringResource(AYMR.strings.novel_reader_nvidia_model)
    val ollamaCloudModelLabel = stringResource(AYMR.strings.novel_reader_ollama_cloud_model)
    val promptModeLabel = stringResource(AYMR.strings.novel_reader_gemini_prompt_mode)
    val styleLabel = stringResource(AYMR.strings.novel_reader_ai_translator_style_title)
    val speedLabel = stringResource(AYMR.strings.novel_reader_ai_translator_speed_batch_parallelism)
    val reasoningLabel = stringResource(AYMR.strings.novel_reader_gemini_reasoning_effort)
    val autoEnglishLabel = stringResource(AYMR.strings.novel_reader_translation_auto_english_title)
    val prefetchNextLabel = stringResource(AYMR.strings.novel_reader_translation_prefetch_next_title)
    val privatePythonLikeLabel = stringResource(AYMR.strings.novel_reader_gemini_private_python_like_mode)
    val generationLabel = stringResource(AYMR.strings.novel_reader_ai_translator_generation_title)
    val temperatureLabel = stringResource(AYMR.strings.novel_reader_gemini_temperature)
    val topPLabel = stringResource(AYMR.strings.novel_reader_gemini_top_p)
    val topKLabel = stringResource(AYMR.strings.novel_reader_gemini_top_k)
    val relaxedStateLabel = stringResource(AYMR.strings.novel_reader_ai_translator_relaxed_state)
    val cacheStateLabel = stringResource(AYMR.strings.novel_reader_ai_translator_cache_state)
    val bridgeLockedLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_bridge_locked)
    val bridgeEnterPasswordLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_bridge_enter_password)
    val bridgeUnlockedLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_bridge_unlocked)
    val bridgeDebugLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_bridge_debug)
    val invalidBridgePasswordLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_invalid_bridge_password)
    val cacheClearedLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_cache_cleared)
    val customPromptUpdatedLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_custom_prompt_updated)

    fun visibilityStateLabel(enabled: Boolean): String = if (enabled) {
        visibilityOnLabel
    } else {
        visibilityOffLabel
    }

    fun reasoningDisplayLabel(option: String): String = when (option) {
        "none" -> "OFF"
        "max" -> "MAX"
        "minimal" -> reasoningMinimalLabel
        "low" -> reasoningLowLabel
        "medium" -> reasoningMediumLabel
        "high" -> reasoningHighLabel
        else -> option.uppercase()
    }

    fun logPair(prefix: String, value: String) {
        onAddLog("$prefix: $value")
    }

    fun logState(prefix: String, enabled: Boolean) {
        onAddLog("$prefix: ${visibilityStateLabel(enabled)}")
    }

    fun logTemplate(template: String, vararg args: Any?) {
        onAddLog(template.format(*args))
    }

    var tempPrivatePassword by remember { mutableStateOf("") }
    var isPrivateProviderUnlocked by remember(isPrivateProviderInstalled, readerSettings.geminiPrivateUnlocked) {
        mutableStateOf(
            isPrivateProviderInstalled &&
                (readerSettings.geminiPrivateUnlocked || GeminiPrivateBridge.isUnlocked()),
        )
    }
    var tempOpenRouterBaseUrl by remember(readerSettings.openRouterBaseUrl) {
        mutableStateOf(readerSettings.openRouterBaseUrl)
    }
    var tempOpenRouterModel by remember(readerSettings.openRouterModel) {
        mutableStateOf(readerSettings.openRouterModel)
    }
    var tempDeepSeekBaseUrl by remember(readerSettings.deepSeekBaseUrl) {
        mutableStateOf(readerSettings.deepSeekBaseUrl)
    }
    var tempDeepSeekModel by remember(readerSettings.deepSeekModel) {
        mutableStateOf(readerSettings.deepSeekModel)
    }
    var tempMistralBaseUrl by remember(readerSettings.mistralBaseUrl) {
        mutableStateOf(readerSettings.mistralBaseUrl)
    }
    var tempNvidiaBaseUrl by remember(readerSettings.nvidiaBaseUrl) {
        mutableStateOf(readerSettings.nvidiaBaseUrl)
    }
    var tempMistralModel by remember(readerSettings.mistralModel) {
        mutableStateOf(readerSettings.mistralModel)
    }
    var tempNvidiaModel by remember(readerSettings.nvidiaModel) {
        mutableStateOf(readerSettings.nvidiaModel)
    }
    var tempOllamaCloudBaseUrl by remember(readerSettings.ollamaCloudBaseUrl) {
        mutableStateOf(readerSettings.ollamaCloudBaseUrl)
    }
    var tempOllamaCloudModel by remember(readerSettings.ollamaCloudModel) {
        mutableStateOf(readerSettings.ollamaCloudModel)
    }
    var showGenerationConfig by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showCustomPromptDialog by remember { mutableStateOf(false) }

    data class GenerationPreset(
        val id: String,
        val title: String,
        val temperature: Float,
        val topP: Float,
        val topK: Int?,
        val scenario: String,
        val advantage: String,
    )

    val defaultGenerationPresets = listOf(
        GenerationPreset(
            id = "anchor_plus",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_anchor_plus_title),
            temperature = 0.62f,
            topP = 0.9f,
            topK = 36,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_anchor_plus_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_anchor_plus_advantage),
        ),
        GenerationPreset(
            id = "authorial",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_authorial_title),
            temperature = 0.76f,
            topP = 0.93f,
            topK = 48,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_authorial_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_authorial_advantage),
        ),
        GenerationPreset(
            id = "dialogue_plus",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_dialogue_plus_title),
            temperature = 0.88f,
            topP = 0.95f,
            topK = 56,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_dialogue_plus_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_dialogue_plus_advantage),
        ),
        GenerationPreset(
            id = "private_pulse",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_private_pulse_title),
            temperature = 0.98f,
            topP = 0.97f,
            topK = 72,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_private_pulse_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_private_pulse_advantage),
        ),
        GenerationPreset(
            id = "unbound",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_unbound_title),
            temperature = 1.08f,
            topP = 0.985f,
            topK = 96,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_unbound_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_unbound_advantage),
        ),
    )
    val deepSeekGenerationPresets = listOf(
        GenerationPreset(
            id = "deepseek_balanced",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_balanced_title),
            temperature = 1.3f,
            topP = 0.9f,
            topK = null,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_balanced_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_balanced_advantage),
        ),
        GenerationPreset(
            id = "deepseek_expressive",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_expressive_title),
            temperature = 1.4f,
            topP = 0.93f,
            topK = null,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_expressive_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_expressive_advantage),
        ),
        GenerationPreset(
            id = "deepseek_creative",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_creative_title),
            temperature = 1.5f,
            topP = 0.95f,
            topK = null,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_creative_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_creative_advantage),
        ),
    )
    val stylePresets = remember { NovelTranslationStylePresets.all }
    fun resolveSelectedGenerationPresetId(
        provider: NovelTranslationProvider,
        temperature: Float,
        topP: Float,
        topK: Int,
    ): String {
        val presets = if (provider == NovelTranslationProvider.DEEPSEEK) {
            deepSeekGenerationPresets
        } else {
            defaultGenerationPresets
        }
        if (presets.isEmpty()) return ""
        val epsilon = 0.0001f
        presets.firstOrNull { preset ->
            val tempMatch = abs(preset.temperature - temperature) <= epsilon
            val topPMatch = abs(preset.topP - topP) <= epsilon
            val topKMatch = when {
                provider == NovelTranslationProvider.DEEPSEEK -> true
                preset.topK == null -> true
                else -> preset.topK == topK
            }
            tempMatch && topPMatch && topKMatch
        }?.let { return it.id }

        return presets.minByOrNull { preset ->
            val topKDistance = when {
                provider == NovelTranslationProvider.DEEPSEEK -> 0f
                preset.topK == null -> 0f
                else -> abs((topK - preset.topK).toFloat()) / 100f
            }
            abs(preset.temperature - temperature) + abs(preset.topP - topP) + topKDistance
        }?.id ?: presets.first().id
    }
    var selectedGenerationPresetId by remember(
        tempProvider,
        readerSettings.geminiTemperature,
        readerSettings.geminiTopP,
        readerSettings.geminiTopK,
    ) {
        mutableStateOf(
            resolveSelectedGenerationPresetId(
                provider = tempProvider,
                temperature = readerSettings.geminiTemperature,
                topP = readerSettings.geminiTopP,
                topK = readerSettings.geminiTopK,
            ),
        )
    }

    fun applyBatchAndConcurrency() {
        tempBatch.toIntOrNull()?.let {
            onSetGeminiBatchSize(it.coerceIn(1, 100))
        }
        val maxConcurrency = if (tempProvider == NovelTranslationProvider.DEEPSEEK) 32 else 8
        tempConcurrency.toIntOrNull()?.let {
            onSetGeminiConcurrency(it.coerceIn(1, maxConcurrency))
        }
    }

    val progressValue = translationProgress.coerceIn(0, 100) / 100f
    val uiState = resolveGeminiTranslationUiState(
        isTranslating = isTranslating,
        hasCache = hasCache,
        isVisible = isVisible,
        translationProgress = translationProgress,
    )
    val status = geminiStatusPresentation(uiState)
    val hasTranslationResult = hasCache || translationProgress >= 100
    val isGeminiSelected = tempProvider == NovelTranslationProvider.GEMINI
    val isGeminiPrivateSelected = tempProvider == NovelTranslationProvider.GEMINI_PRIVATE
    val isPrivateSingleRequestMode =
        isGeminiPrivateSelected &&
            isPrivateProviderInstalled &&
            GeminiPrivateBridge.forceSingleChapterRequest()
    val privateBridgeInstalled = isGeminiPrivateSelected && isPrivateProviderInstalled
    val privateBridgeRequiresUnlock = privateBridgeInstalled
    val privateBridgeUnlocked = !privateBridgeRequiresUnlock || isPrivateProviderUnlocked
    val isOpenRouterSelected = tempProvider == NovelTranslationProvider.OPENROUTER
    val isDeepSeekSelected = tempProvider == NovelTranslationProvider.DEEPSEEK
    val isMistralSelected = tempProvider == NovelTranslationProvider.MISTRAL
    val isNvidiaSelected = tempProvider == NovelTranslationProvider.NVIDIA
    val isOllamaCloudSelected = tempProvider == NovelTranslationProvider.OLLAMA_CLOUD
    val activeReasoningModel = when (tempProvider) {
        NovelTranslationProvider.GEMINI,
        NovelTranslationProvider.GEMINI_PRIVATE,
        -> tempModel
        NovelTranslationProvider.OPENROUTER -> tempOpenRouterModel
        NovelTranslationProvider.MISTRAL -> tempMistralModel
        NovelTranslationProvider.DEEPSEEK -> tempDeepSeekModel
        NovelTranslationProvider.NVIDIA -> tempNvidiaModel
        NovelTranslationProvider.OLLAMA_CLOUD -> tempOllamaCloudModel
    }
    val reasoningOptions = remember(tempProvider, activeReasoningModel) {
        resolveTranslationReasoningOptions(tempProvider, activeReasoningModel)
    }
    val activeGenerationPresets = if (isDeepSeekSelected) {
        deepSeekGenerationPresets
    } else {
        defaultGenerationPresets
    }
    val tabTitles = persistentListOf(
        stringResource(MR.strings.ai_translator_tab_basics),
        stringResource(MR.strings.ai_translator_tab_prompt),
        stringResource(MR.strings.ai_translator_tab_more),
    )

    LaunchedEffect(isPrivateProviderInstalled, readerSettings.geminiPrivateUnlocked) {
        isPrivateProviderUnlocked = isPrivateProviderInstalled &&
            (readerSettings.geminiPrivateUnlocked || GeminiPrivateBridge.isUnlocked())
    }

    LaunchedEffect(isOpenRouterSelected, openRouterModels.size) {
        if (isOpenRouterSelected && openRouterModels.isEmpty()) {
            onRefreshOpenRouterModels()
        }
    }

    LaunchedEffect(isDeepSeekSelected, deepSeekModels.size) {
        if (isDeepSeekSelected && deepSeekModels.isEmpty()) {
            onRefreshDeepSeekModels()
        }
    }

    LaunchedEffect(isMistralSelected, mistralModels.size) {
        if (isMistralSelected && mistralModels.isEmpty()) {
            onRefreshMistralModels()
        }
    }

    LaunchedEffect(isNvidiaSelected, nvidiaModels.size) {
        if (isNvidiaSelected && nvidiaModels.isEmpty()) {
            onRefreshNvidiaModels()
        }
    }

    LaunchedEffect(isOllamaCloudSelected, ollamaCloudModels.size) {
        if (isOllamaCloudSelected && ollamaCloudModels.isEmpty()) {
            onRefreshOllamaCloudModels()
        }
    }

    val pagerState = rememberPagerState { tabTitles.size }
    val scope = rememberCoroutineScope()
    val aurora = AuroraTheme.colors
    val baseScheme = MaterialTheme.colorScheme
    var sheetReveal by remember { mutableFloatStateOf(0f) }
    val supportsBlurBehind = rememberSupportsBlurBehind(aurora.isEInk)

    val sheetContainer = remember(aurora.isDark, aurora.isEInk, supportsBlurBehind) {
        when {
            aurora.isEInk -> baseScheme.surfaceContainerHigh
            !supportsBlurBehind -> aurora.surface
            aurora.isDark -> Color.Black.copy(alpha = 0.70f)
            else -> Color.White.copy(alpha = 0.88f)
        }
    }
    val auroraScheme = remember(baseScheme, aurora, sheetContainer) {
        baseScheme.copy(
            primary = aurora.accent,
            onPrimary = if (aurora.isDark) aurora.background else Color.White,
            surfaceContainerHigh = sheetContainer,
            surfaceContainerHighest = sheetContainer,
            secondaryContainer = aurora.accent.copy(alpha = 0.22f),
            onSecondaryContainer = aurora.accent,
        )
    }
    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = ZeroCornerSize,
        bottomEnd = ZeroCornerSize,
    )
    val pageMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp

    MaterialTheme(
        colorScheme = auroraScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        AdaptiveSheet(
            onDismissRequest = onDismiss,
            modifier = Modifier.border(
                width = 1.dp,
                color = auroraRimColor(),
                shape = sheetShape,
            ),
            containerColor = sheetContainer,
            scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
            applyStatusBarsPadding = false,
            onRevealChange = { sheetReveal = it },
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            val revealState = rememberUpdatedState(sheetReveal)

            DisposableEffect(window, supportsBlurBehind) {
                val w = window
                if (w != null && supportsBlurBehind) {
                    w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                    w.setDimAmount(0f)
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                }
                onDispose {
                    if (w != null && supportsBlurBehind) {
                        w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                        w.setDimAmount(0f)
                        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
            }

            LaunchedEffect(window, supportsBlurBehind) {
                val w = window ?: return@LaunchedEffect
                if (!supportsBlurBehind) return@LaunchedEffect
                snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                    .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                    .distinctUntilChanged()
                    .collect { step ->
                        applyNovelSheetWindowFx(
                            window = w,
                            reveal = step / 20f,
                        )
                    }
            }

            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (aurora.isDark) {
                                    Color.White.copy(alpha = 0.22f)
                                } else {
                                    Color.Black.copy(alpha = 0.18f)
                                },
                            ),
                    )
                }

                AuroraTabRow(
                    titles = tabTitles,
                    selectedIndex = pagerState.currentPage,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                )

                HorizontalPager(
                    modifier = Modifier.heightIn(max = pageMaxHeight),
                    state = pagerState,
                    verticalAlignment = Alignment.Top,
                    beyondViewportPageCount = 0,
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = pageMaxHeight)
                            .padding(vertical = 6.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        when (page) {
                            0 -> {
                                // -------------------------------------------------------------
                                // TAB 0: ОСНОВНЫЕ
                                // -------------------------------------------------------------
                                // 1. Hero Status & Actions Card
                                AuroraGlassSection(
                                    title = stringResource(AYMR.strings.novel_reader_ai_translator_status_title),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isTranslating) aurora.accent else MaterialTheme.colorScheme.outline,
                                                        ),
                                                )
                                                Text(
                                                    text = stringResource(status.titleRes),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                            Text(
                                                text = "$translationProgress%",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = aurora.accent,
                                            )
                                        }

                                        Text(
                                            text = stringResource(status.subtitleRes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = getAiTranslatorProviderLabel(tempProvider),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = "${tempSourceLang.ifBlank {
                                                    "?"
                                                }} → ${tempTargetLang.ifBlank { "?" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }

                                        LinearProgressIndicator(
                                            progress = { progressValue },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = aurora.accent,
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (isTranslating) {
                                                        onStop()
                                                    } else {
                                                        if (privateBridgeUnlocked) {
                                                            onStart()
                                                        } else {
                                                            logTemplate(bridgeLockedLabel, privateProviderLabel)
                                                        }
                                                    }
                                                },
                                                enabled = isTranslating || privateBridgeUnlocked,
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = aurora.accent,
                                                    contentColor = if (aurora.isDark) Color.Black else Color.White,
                                                ),
                                            ) {
                                                Icon(
                                                    imageVector = if (isTranslating) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(Modifier.size(6.dp))
                                                Text(
                                                    text = if (isTranslating) {
                                                        stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_action_stop,
                                                        )
                                                    } else {
                                                        stringResource(AYMR.strings.novel_reader_gemini_action_start)
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = onToggleVisibility,
                                                enabled = hasTranslationResult,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(
                                                    imageVector = if (isVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(Modifier.size(6.dp))
                                                Text(
                                                    if (isVisible) {
                                                        stringResource(AYMR.strings.novel_reader_gemini_show_original)
                                                    } else {
                                                        stringResource(
                                                            AYMR.strings.novel_reader_gemini_show_translation,
                                                        )
                                                    },
                                                )
                                            }
                                        }

                                        if (hasTranslationResult) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                TextButton(onClick = onClear) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Delete,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.error,
                                                    )
                                                    Spacer(Modifier.size(4.dp))
                                                    Text(
                                                        stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_clear_chapter_cache,
                                                        ),
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Language Pair Bridge
                                AuroraGlassSection(
                                    title = stringResource(AYMR.strings.novel_reader_translation_languages),
                                ) {
                                    AuroraFieldLabel(
                                        stringResource(AYMR.strings.novel_reader_translation_languages_summary),
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AuroraOutlinedTextField(
                                            value = tempSourceLang,
                                            onValueChange = {
                                                tempSourceLang = it
                                                onSetGeminiSourceLang(it)
                                            },
                                            label = {
                                                Text(stringResource(AYMR.strings.novel_reader_gemini_source_lang))
                                            },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = if (aurora.isDark) {
                                                Color.White.copy(
                                                    alpha = 0.06f,
                                                )
                                            } else {
                                                Color.Black.copy(alpha = 0.04f)
                                            },
                                            border = BorderStroke(1.dp, auroraRimColor()),
                                            modifier = Modifier
                                                .offset(y = 4.dp)
                                                .size(40.dp),
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val oldSource = tempSourceLang
                                                    tempSourceLang = tempTargetLang
                                                    tempTargetLang = oldSource
                                                    onSetGeminiSourceLang(tempSourceLang)
                                                    onSetGeminiTargetLang(tempTargetLang)
                                                },
                                                modifier = Modifier.size(40.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.SwapHoriz,
                                                    contentDescription = "Swap languages",
                                                    tint = aurora.accent,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                        AuroraOutlinedTextField(
                                            value = tempTargetLang,
                                            onValueChange = {
                                                tempTargetLang = it
                                                onSetGeminiTargetLang(it)
                                            },
                                            label = {
                                                Text(stringResource(AYMR.strings.novel_reader_gemini_target_lang))
                                            },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                        )
                                    }
                                }

                                // 3. Provider Selection 2x2 Grid
                                AuroraGlassSection(
                                    title = stringResource(AYMR.strings.novel_reader_translation_provider),
                                ) {
                                    AuroraFieldLabel(
                                        stringResource(AYMR.strings.novel_reader_ai_translator_provider_summary),
                                    )
                                    val providerCards = listOf(
                                        NovelTranslationProvider.GEMINI to
                                            stringResource(AYMR.strings.novel_reader_translation_provider_gemini),
                                        NovelTranslationProvider.GEMINI_PRIVATE to privateProviderLabel,
                                        NovelTranslationProvider.OPENROUTER to
                                            stringResource(AYMR.strings.novel_reader_translation_provider_openrouter),
                                        NovelTranslationProvider.DEEPSEEK to
                                            stringResource(AYMR.strings.novel_reader_translation_provider_deepseek),
                                        NovelTranslationProvider.MISTRAL to
                                            stringResource(AYMR.strings.novel_reader_translation_provider_mistral),
                                        NovelTranslationProvider.NVIDIA to
                                            stringResource(AYMR.strings.novel_reader_translation_provider_nvidia),
                                        NovelTranslationProvider.OLLAMA_CLOUD to
                                            stringResource(AYMR.strings.novel_reader_translation_provider_ollama_cloud),
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        providerCards.chunked(2).forEach { rowProviders ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                rowProviders.forEach { option ->
                                                    val selected = tempProvider == option.first
                                                    val apiConfigured = when (option.first) {
                                                        NovelTranslationProvider.GEMINI -> tempKey.isNotBlank()
                                                        NovelTranslationProvider.GEMINI_PRIVATE -> tempKey.isNotBlank() &&
                                                            isPrivateProviderUnlocked
                                                        NovelTranslationProvider.OPENROUTER -> tempOpenRouterBaseUrl.isNotBlank() &&
                                                            readerSettings.openRouterApiKey.isNotBlank() &&
                                                            tempOpenRouterModel.isNotBlank()
                                                        NovelTranslationProvider.DEEPSEEK -> tempDeepSeekBaseUrl.isNotBlank() &&
                                                            readerSettings.deepSeekApiKey.isNotBlank() &&
                                                            tempDeepSeekModel.isNotBlank()
                                                        NovelTranslationProvider.MISTRAL -> tempMistralBaseUrl.isNotBlank() &&
                                                            readerSettings.mistralApiKey.isNotBlank() &&
                                                            tempMistralModel.isNotBlank()
                                                        NovelTranslationProvider.NVIDIA -> tempNvidiaBaseUrl.isNotBlank() &&
                                                            readerSettings.nvidiaApiKey.isNotBlank() &&
                                                            tempNvidiaModel.isNotBlank()
                                                        NovelTranslationProvider.OLLAMA_CLOUD -> tempOllamaCloudBaseUrl.isNotBlank() &&
                                                            readerSettings.ollamaCloudApiKey.isNotBlank() &&
                                                            tempOllamaCloudModel.isNotBlank()
                                                    }
                                                    AiTranslatorProviderCard(
                                                        title = option.second,
                                                        apiConfigured = apiConfigured,
                                                        selected = selected,
                                                        modifier = Modifier.weight(1f),
                                                        onClick = {
                                                            tempProvider = option.first
                                                            onSetTranslationProvider(option.first)
                                                            logPair(providerLabel, option.second)
                                                            when (option.first) {
                                                                NovelTranslationProvider.GEMINI, NovelTranslationProvider.GEMINI_PRIVATE -> Unit
                                                                NovelTranslationProvider.OPENROUTER -> onRefreshOpenRouterModels()
                                                                NovelTranslationProvider.DEEPSEEK -> onRefreshDeepSeekModels()
                                                                NovelTranslationProvider.MISTRAL -> onRefreshMistralModels()
                                                                NovelTranslationProvider.NVIDIA -> onRefreshNvidiaModels()
                                                                NovelTranslationProvider.OLLAMA_CLOUD -> onRefreshOllamaCloudModels()
                                                            }
                                                        },
                                                    )
                                                }
                                                if (rowProviders.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }

                                    // Private Bridge Unlock Box
                                    if (privateBridgeInstalled) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Text(
                                                text = if (isPrivateProviderUnlocked) {
                                                    stringResource(
                                                        AYMR.strings.novel_reader_gemini_private_bridge_connected_unlocked,
                                                    ).format(privateProviderLabel)
                                                } else {
                                                    stringResource(
                                                        AYMR.strings.novel_reader_gemini_private_bridge_connected_unlock_required,
                                                    ).format(privateProviderLabel)
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }

                                    if (privateBridgeRequiresUnlock && !isPrivateProviderUnlocked) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            AuroraOutlinedTextField(
                                                value = tempPrivatePassword,
                                                onValueChange = { tempPrivatePassword = it },
                                                label = {
                                                    Text(
                                                        stringResource(
                                                            AYMR.strings.novel_reader_gemini_private_bridge_password_label,
                                                        ).format(privateProviderLabel),
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        val password = tempPrivatePassword.trim()
                                                        if (password.isBlank()) {
                                                            logTemplate(bridgeEnterPasswordLabel, privateProviderLabel)
                                                        } else {
                                                            val unlocked = GeminiPrivateBridge.unlock(password)
                                                            if (unlocked) {
                                                                isPrivateProviderUnlocked = true
                                                                onSetGeminiPrivateUnlocked(true)
                                                                tempPrivatePassword = ""
                                                                logTemplate(bridgeUnlockedLabel, privateProviderLabel)
                                                            } else {
                                                                logTemplate(
                                                                    bridgeDebugLabel,
                                                                    privateProviderLabel,
                                                                    GeminiPrivateBridge.debugInfo(),
                                                                )
                                                                onAddLog(invalidBridgePasswordLabel)
                                                            }
                                                        }
                                                    },
                                                ) {
                                                    Text(stringResource(AYMR.strings.novel_reader_gemini_action_unlock))
                                                }
                                                OutlinedButton(onClick = { tempPrivatePassword = "" }) {
                                                    Text(stringResource(AYMR.strings.novel_reader_gemini_action_clear))
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. Models Selection Block
                                AuroraGlassSection(
                                    title = when (tempProvider) {
                                        NovelTranslationProvider.GEMINI, NovelTranslationProvider.GEMINI_PRIVATE -> stringResource(
                                            AYMR.strings.novel_reader_gemini_model,
                                        )
                                        NovelTranslationProvider.OPENROUTER -> stringResource(
                                            AYMR.strings.novel_reader_ai_translator_openrouter_models_title,
                                        )
                                        NovelTranslationProvider.DEEPSEEK -> stringResource(
                                            AYMR.strings.novel_reader_ai_translator_deepseek_models_title,
                                        )
                                        NovelTranslationProvider.MISTRAL -> stringResource(
                                            AYMR.strings.novel_reader_ai_translator_mistral_models_title,
                                        )
                                        NovelTranslationProvider.NVIDIA -> stringResource(
                                            AYMR.strings.novel_reader_nvidia_section_title,
                                        )
                                        NovelTranslationProvider.OLLAMA_CLOUD -> stringResource(
                                            AYMR.strings.novel_reader_ai_translator_ollama_cloud_models_title,
                                        )
                                    },
                                ) {
                                    AuroraFieldLabel(
                                        stringResource(AYMR.strings.novel_reader_ai_translator_model_summary),
                                    )
                                    when (tempProvider) {
                                        NovelTranslationProvider.GEMINI, NovelTranslationProvider.GEMINI_PRIVATE -> {
                                            ListPreferenceWidget(
                                                value = tempModel,
                                                title = stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_current_model,
                                                ),
                                                subtitle = modelMap[tempModel] ?: tempModel,
                                                icon = null,
                                                entries = modelMap,
                                                onValueChange = { selected ->
                                                    tempModel = selected
                                                    onSetGeminiModel(selected)
                                                    logPair(geminiModelLabel, modelMap[selected] ?: selected)
                                                },
                                            )
                                        }
                                        NovelTranslationProvider.OPENROUTER -> {
                                            if (openRouterAllModelEntries.isNotEmpty()) {
                                                ListPreferenceWidget(
                                                    value = tempOpenRouterModel,
                                                    title = stringResource(
                                                        AYMR.strings.novel_reader_ai_translator_openrouter_models_count,
                                                    ).format(openRouterAllModelEntries.size),
                                                    subtitle = tempOpenRouterModel.ifBlank {
                                                        stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_choose_free_model,
                                                        )
                                                    },
                                                    icon = null,
                                                    entries = openRouterAllModelEntries,
                                                    onValueChange = { selected ->
                                                        tempOpenRouterModel = selected
                                                        onSetOpenRouterModel(selected)
                                                        logPair(openRouterModelLabel, selected)
                                                    },
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                OutlinedButton(onClick = onRefreshOpenRouterModels) {
                                                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.size(4.dp))
                                                    Text(
                                                        if (isOpenRouterModelsLoading) {
                                                            stringResource(
                                                                AYMR.strings.novel_reader_ai_translator_loading_models,
                                                            )
                                                        } else {
                                                            stringResource(
                                                                AYMR.strings.novel_reader_ai_translator_refresh_list,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                            AuroraOutlinedTextField(
                                                value = tempOpenRouterModel,
                                                onValueChange = {
                                                    tempOpenRouterModel = it
                                                    onSetOpenRouterModel(it)
                                                },
                                                label = {
                                                    Text(
                                                        stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_model_id_free_only,
                                                        ),
                                                    )
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                            )
                                        }
                                        NovelTranslationProvider.DEEPSEEK -> {
                                            if (deepSeekAllModelEntries.isNotEmpty()) {
                                                ListPreferenceWidget(
                                                    value = tempDeepSeekModel,
                                                    title = stringResource(
                                                        AYMR.strings.novel_reader_ai_translator_models_count,
                                                    ).format(deepSeekAllModelEntries.size),
                                                    subtitle = tempDeepSeekModel.ifBlank {
                                                        stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_choose_model,
                                                        )
                                                    },
                                                    icon = null,
                                                    entries = deepSeekAllModelEntries,
                                                    onValueChange = { selected ->
                                                        tempDeepSeekModel = selected
                                                        onSetDeepSeekModel(selected)
                                                        logPair(deepSeekModelLabel, selected)
                                                    },
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                OutlinedButton(onClick = onRefreshDeepSeekModels) {
                                                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.size(4.dp))
                                                    Text(
                                                        if (isDeepSeekModelsLoading) {
                                                            stringResource(
                                                                AYMR.strings.novel_reader_ai_translator_loading_models,
                                                            )
                                                        } else {
                                                            stringResource(
                                                                AYMR.strings.novel_reader_ai_translator_refresh_list,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                            AuroraOutlinedTextField(
                                                value = tempDeepSeekModel,
                                                onValueChange = {
                                                    tempDeepSeekModel = it
                                                    onSetDeepSeekModel(it)
                                                },
                                                label = {
                                                    Text(
                                                        stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_model_id,
                                                        ),
                                                    )
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                            )
                                        }
                                        NovelTranslationProvider.MISTRAL -> {
                                            if (mistralAllModelEntries.isNotEmpty()) {
                                                ListPreferenceWidget(
                                                    value = tempMistralModel,
                                                    title = stringResource(
                                                        AYMR.strings.novel_reader_ai_translator_models_count,
                                                    ).format(mistralAllModelEntries.size),
                                                    subtitle = tempMistralModel.ifBlank {
                                                        stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_choose_model,
                                                        )
                                                    },
                                                    icon = null,
                                                    entries = mistralAllModelEntries,
                                                    onValueChange = { selected ->
                                                        tempMistralModel = selected
                                                        onSetMistralModel(selected)
                                                        logPair(mistralModelLabel, selected)
                                                    },
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                OutlinedButton(onClick = onRefreshMistralModels) {
                                                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.size(4.dp))
                                                    Text(
                                                        if (isMistralModelsLoading) {
                                                            stringResource(
                                                                AYMR.strings.novel_reader_ai_translator_loading_models,
                                                            )
                                                        } else {
                                                            stringResource(
                                                                AYMR.strings.novel_reader_ai_translator_refresh_list,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                            AuroraOutlinedTextField(
                                                value = tempMistralModel,
                                                onValueChange = {
                                                    tempMistralModel = it
                                                    onSetMistralModel(it)
                                                },
                                                label = {
                                                    Text(
                                                        stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_model_id,
                                                        ),
                                                    )
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                            )
                                        }
                                        NovelTranslationProvider.NVIDIA -> {
                                            if (nvidiaAllModelEntries.isNotEmpty()) {
                                                ListPreferenceWidget(
                                                    value = tempNvidiaModel,
                                                    title = stringResource(
                                                        AYMR.strings.novel_reader_ai_translator_models_count,
                                                    ).format(nvidiaAllModelEntries.size),
                                                    subtitle = tempNvidiaModel.ifBlank {
                                                        stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_choose_model,
                                                        )
                                                    },
                                                    icon = null,
                                                    entries = nvidiaAllModelEntries,
                                                    onValueChange = { selected ->
                                                        tempNvidiaModel = selected
                                                        onSetNvidiaModel(selected)
                                                        logPair(nvidiaModelLabel, selected)
                                                    },
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                OutlinedButton(onClick = onRefreshNvidiaModels) {
                                                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.size(4.dp))
                                                    Text(
                                                        if (isNvidiaModelsLoading) {
                                                            stringResource(
                                                                AYMR.strings.novel_reader_ai_translator_loading_models,
                                                            )
                                                        } else {
                                                            stringResource(
                                                                AYMR.strings.novel_reader_ai_translator_refresh_list,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                            AuroraOutlinedTextField(
                                                value = tempNvidiaModel,
                                                onValueChange = {
                                                    tempNvidiaModel = it
                                                    onSetNvidiaModel(it)
                                                },
                                                label = {
                                                    Text(stringResource(AYMR.strings.novel_reader_nvidia_model))
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                            )
                                        }
                                        NovelTranslationProvider.OLLAMA_CLOUD -> {
                                            if (ollamaCloudAllModelEntries.isNotEmpty()) {
                                                ListPreferenceWidget(
                                                    value = tempOllamaCloudModel,
                                                    title = stringResource(
                                                        AYMR.strings.novel_reader_ai_translator_models_count,
                                                    ).format(ollamaCloudAllModelEntries.size),
                                                    subtitle = when {
                                                        tempOllamaCloudModel in OLLAMA_CLOUD_FREE_MODELS -> "$tempOllamaCloudModel (Free)"
                                                        tempOllamaCloudModel.isNotBlank() -> tempOllamaCloudModel
                                                        else -> stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_choose_model,
                                                        )
                                                    },
                                                    icon = null,
                                                    entries = ollamaCloudAllModelEntries,
                                                    onValueChange = { selected ->
                                                        tempOllamaCloudModel = selected
                                                        onSetOllamaCloudModel(selected)
                                                        logPair(ollamaCloudModelLabel, selected)
                                                    },
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                OutlinedButton(onClick = onRefreshOllamaCloudModels) {
                                                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.size(4.dp))
                                                    Text(
                                                        if (isOllamaCloudModelsLoading) {
                                                            stringResource(
                                                                AYMR.strings.novel_reader_ai_translator_loading_models,
                                                            )
                                                        } else {
                                                            stringResource(
                                                                AYMR.strings.novel_reader_ai_translator_refresh_list,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                            AuroraOutlinedTextField(
                                                value = tempOllamaCloudModel,
                                                onValueChange = {
                                                    tempOllamaCloudModel = it
                                                    onSetOllamaCloudModel(it)
                                                },
                                                label = {
                                                    Text(stringResource(AYMR.strings.novel_reader_ollama_cloud_model))
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }

                                // 5. Speed & Parallelism
                                if (!isPrivateSingleRequestMode) {
                                    AuroraGlassSection(
                                        title = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_speed_batch_parallelism,
                                        ),
                                    ) {
                                        AuroraChipFlow {
                                            speedPresets.forEach { preset ->
                                                val label = preset.first
                                                val batch = preset.second.first
                                                val concurrency = preset.second.second
                                                val selected =
                                                    tempBatch == batch.toString() &&
                                                        tempConcurrency == concurrency.toString()
                                                AiTranslatorChoiceChip(
                                                    text = label,
                                                    selected = selected,
                                                    onClick = {
                                                        tempBatch = batch.toString()
                                                        tempConcurrency = concurrency.toString()
                                                        onSetGeminiBatchSize(batch)
                                                        onSetGeminiConcurrency(concurrency)
                                                        logPair(speedLabel, label)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    AuroraGlassSection(
                                        title = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_speed_batch_parallelism,
                                        ),
                                    ) {
                                        Text(
                                            text = "$privateProviderLabel: отправка идёт одним запросом на главу. При ошибке включается fallback (batch=40, concurrency=1).",
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                // 6. Reasoning Effort
                                if (reasoningOptions.isNotEmpty()) {
                                    AuroraGlassSection(
                                        title = reasoningLabel,
                                    ) {
                                        if (isDeepSeekSelected && tempReasoning != "none") {
                                            AuroraFieldLabel(
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_deepseek_reasoning_hint,
                                                ),
                                            )
                                        }
                                        AuroraChipFlow {
                                            reasoningOptions.forEach { option ->
                                                AiTranslatorChoiceChip(
                                                    text = reasoningDisplayLabel(option),
                                                    selected = tempReasoning == option,
                                                    onClick = {
                                                        tempReasoning = option
                                                        onSetGeminiReasoningEffort(option)
                                                        logPair(reasoningLabel, reasoningDisplayLabel(option))
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                // -------------------------------------------------------------
                                // TAB 1: ПРОМПТ
                                // -------------------------------------------------------------
                                if (!isPrivateSingleRequestMode) {
                                    // 1. Prompt Filtering Mode
                                    AuroraGlassSection(
                                        title = stringResource(AYMR.strings.novel_reader_gemini_prompt_mode),
                                    ) {
                                        AuroraFieldLabel(
                                            stringResource(AYMR.strings.novel_reader_ai_translator_prompt_mode_summary),
                                        )
                                        val promptModeClassicLabel =
                                            stringResource(AYMR.strings.novel_reader_gemini_prompt_mode_classic)
                                        val promptModeAdultLabel =
                                            stringResource(AYMR.strings.novel_reader_gemini_prompt_mode_adult_short)
                                        AuroraChipFlow {
                                            listOf(
                                                GeminiPromptMode.CLASSIC to promptModeClassicLabel,
                                                GeminiPromptMode.ADULT_18 to promptModeAdultLabel,
                                            ).forEach { option ->
                                                AiTranslatorChoiceChip(
                                                    text = option.second,
                                                    selected = tempPromptMode == option.first,
                                                    onClick = {
                                                        tempPromptMode = option.first
                                                        onSetGeminiPromptMode(option.first)
                                                        logPair(promptModeLabel, option.second)
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    // 2. Style Persona Presets
                                    AuroraGlassSection(
                                        title = stringResource(AYMR.strings.novel_reader_ai_translator_style_title),
                                    ) {
                                        AuroraFieldLabel(
                                            stringResource(AYMR.strings.novel_reader_ai_translator_style_summary),
                                        )
                                        AuroraChipFlow {
                                            stylePresets.forEach { preset ->
                                                val selected = tempStylePreset == preset.id
                                                val presetTitle = stringResource(preset.titleRes)
                                                AiTranslatorChoiceChip(
                                                    text = presetTitle,
                                                    selected = selected,
                                                    onClick = {
                                                        tempStylePreset = preset.id
                                                        onSetGeminiStylePreset(preset.id)
                                                        logPair(styleLabel, presetTitle)
                                                    },
                                                )
                                            }
                                        }
                                        val selectedStylePreset = stylePresets.firstOrNull { it.id == tempStylePreset }
                                        if (selectedStylePreset != null) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                                color = if (aurora.isDark) {
                                                    Color.White.copy(
                                                        alpha = 0.04f,
                                                    )
                                                } else {
                                                    Color.Black.copy(alpha = 0.03f)
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(1.dp, auroraRimColor()),
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(aurora.accent),
                                                        )
                                                        Text(
                                                            text = stringResource(selectedStylePreset.titleRes),
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                    }
                                                    Text(
                                                        text = stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_style_scenario_prefix,
                                                        ).format(stringResource(selectedStylePreset.scenarioRes)),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = aurora.textSecondary,
                                                    )
                                                    Text(
                                                        text = stringResource(
                                                            AYMR.strings.novel_reader_ai_translator_style_advantage_prefix,
                                                        ).format(stringResource(selectedStylePreset.advantageRes)),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = aurora.textSecondary,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 3. Prompt Modifiers
                                    AuroraGlassSection(
                                        title = stringResource(AYMR.strings.novel_reader_gemini_prompt_modifiers),
                                    ) {
                                        AuroraFieldLabel(
                                            stringResource(AYMR.strings.novel_reader_gemini_prompt_modifiers_hint),
                                        )
                                        AuroraChipFlow {
                                            GeminiPromptModifiers.all.forEach { modifier ->
                                                val selected = tempEnabledModifiers.contains(modifier.id)
                                                val modifierLabel = stringResource(modifier.labelRes)
                                                AiTranslatorFilterChip(
                                                    label = modifierLabel,
                                                    selected = selected,
                                                    onClick = {
                                                        tempEnabledModifiers = if (selected) {
                                                            tempEnabledModifiers - modifier.id
                                                        } else {
                                                            tempEnabledModifiers + modifier.id
                                                        }
                                                        onSetGeminiEnabledPromptModifiers(tempEnabledModifiers.toList())
                                                    },
                                                )
                                            }
                                            AiTranslatorFilterChip(
                                                label = if (tempCustomModifier.isBlank()) {
                                                    stringResource(
                                                        AYMR.strings.novel_reader_ai_translator_custom_modifier_add,
                                                    )
                                                } else {
                                                    stringResource(
                                                        AYMR.strings.novel_reader_ai_translator_custom_modifier_active,
                                                    )
                                                },
                                                selected = tempCustomModifier.isNotBlank(),
                                                onClick = { showCustomPromptDialog = true },
                                            )
                                        }
                                    }

                                    // 4. Generation Sampling Presets & Sliders
                                    AuroraGlassSection(
                                        title = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_generation_title,
                                        ),
                                    ) {
                                        AuroraFieldLabel(
                                            stringResource(AYMR.strings.novel_reader_ai_translator_generation_summary),
                                        )
                                        AuroraChipFlow {
                                            activeGenerationPresets.forEach { preset ->
                                                AiTranslatorChoiceChip(
                                                    text = preset.title,
                                                    selected = preset.id == selectedGenerationPresetId,
                                                    onClick = {
                                                        selectedGenerationPresetId = preset.id
                                                        val name = preset.title
                                                        val t = preset.temperature
                                                        val p = preset.topP
                                                        tempTemperature = t.toString()
                                                        tempTopP = p.toString()
                                                        onSetGeminiTemperature(t)
                                                        onSetGeminiTopP(p)
                                                        val k = preset.topK
                                                        if (k != null) {
                                                            tempTopK = k.toString()
                                                            onSetGeminiTopK(k)
                                                            onAddLog("$generationLabel: $name (T:$t P:$p K:$k)")
                                                        } else {
                                                            onAddLog("$generationLabel: $name (T:$t P:$p)")
                                                        }
                                                    },
                                                )
                                            }
                                        }

                                        val selectedPreset =
                                            activeGenerationPresets.firstOrNull { it.id == selectedGenerationPresetId }
                                                ?: activeGenerationPresets.first()
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            color = if (aurora.isDark) {
                                                Color.White.copy(
                                                    alpha = 0.04f,
                                                )
                                            } else {
                                                Color.Black.copy(alpha = 0.03f)
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, auroraRimColor()),
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(aurora.accent),
                                                    )
                                                    Text(
                                                        text = selectedPreset.title,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                                Text(
                                                    text = stringResource(
                                                        AYMR.strings.novel_reader_ai_translator_generation_scenario_prefix,
                                                    ).format(selectedPreset.scenario),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = aurora.textSecondary,
                                                )
                                                Text(
                                                    text = stringResource(
                                                        AYMR.strings.novel_reader_ai_translator_generation_advantage_prefix,
                                                    ).format(selectedPreset.advantage),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = aurora.textSecondary,
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = stringResource(AYMR.strings.novel_reader_gemini_temperature),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (aurora.isDark) Color(0xFFD1D5DB) else Color(0xFF374151),
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = if (aurora.isDark) {
                                                    Color.White.copy(
                                                        alpha = 0.08f,
                                                    )
                                                } else {
                                                    Color.Black.copy(alpha = 0.06f)
                                                },
                                                border = BorderStroke(1.dp, auroraRimColor()),
                                            ) {
                                                Text(
                                                    text = tempTemperature,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = aurora.accent,
                                                )
                                            }
                                        }
                                        Slider(
                                            value = tempTemperature.toFloatOrNull() ?: 0.7f,
                                            onValueChange = {
                                                val rounded = (it * 100).roundToInt() / 100f
                                                tempTemperature = rounded.toString()
                                                onSetGeminiTemperature(rounded)
                                            },
                                            valueRange = if (isDeepSeekSelected) 1.3f..1.5f else 0.0f..2.0f,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            colors = SliderDefaults.colors(
                                                thumbColor = aurora.accent,
                                                activeTrackColor = aurora.accent,
                                                inactiveTrackColor = if (aurora.isDark) {
                                                    Color.White.copy(
                                                        alpha = 0.12f,
                                                    )
                                                } else {
                                                    Color.Black.copy(alpha = 0.10f)
                                                },
                                            ),
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = stringResource(AYMR.strings.novel_reader_gemini_top_p),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (aurora.isDark) Color(0xFFD1D5DB) else Color(0xFF374151),
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = if (aurora.isDark) {
                                                    Color.White.copy(
                                                        alpha = 0.08f,
                                                    )
                                                } else {
                                                    Color.Black.copy(alpha = 0.06f)
                                                },
                                                border = BorderStroke(1.dp, auroraRimColor()),
                                            ) {
                                                Text(
                                                    text = tempTopP,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = aurora.accent,
                                                )
                                            }
                                        }
                                        Slider(
                                            value = tempTopP.toFloatOrNull() ?: 0.9f,
                                            onValueChange = {
                                                val rounded = (it * 100).roundToInt() / 100f
                                                tempTopP = rounded.toString()
                                                onSetGeminiTopP(rounded)
                                            },
                                            valueRange = if (isDeepSeekSelected) 0.9f..0.95f else 0.0f..1.0f,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            colors = SliderDefaults.colors(
                                                thumbColor = aurora.accent,
                                                activeTrackColor = aurora.accent,
                                                inactiveTrackColor = if (aurora.isDark) {
                                                    Color.White.copy(
                                                        alpha = 0.12f,
                                                    )
                                                } else {
                                                    Color.Black.copy(alpha = 0.10f)
                                                },
                                            ),
                                        )

                                        if (!isDeepSeekSelected) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = stringResource(AYMR.strings.novel_reader_gemini_top_k),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (aurora.isDark) Color(0xFFD1D5DB) else Color(0xFF374151),
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = if (aurora.isDark) {
                                                        Color.White.copy(
                                                            alpha = 0.08f,
                                                        )
                                                    } else {
                                                        Color.Black.copy(alpha = 0.06f)
                                                    },
                                                    border = BorderStroke(1.dp, auroraRimColor()),
                                                ) {
                                                    Text(
                                                        text = tempTopK,
                                                        modifier = Modifier.padding(
                                                            horizontal = 10.dp,
                                                            vertical = 2.dp,
                                                        ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = aurora.accent,
                                                    )
                                                }
                                            }
                                            Slider(
                                                value = tempTopK.toFloatOrNull() ?: 40f,
                                                onValueChange = {
                                                    val intVal = it.roundToInt()
                                                    tempTopK = intVal.toString()
                                                    onSetGeminiTopK(intVal)
                                                },
                                                valueRange = 1f..100f,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = aurora.accent,
                                                    activeTrackColor = aurora.accent,
                                                    inactiveTrackColor = if (aurora.isDark) {
                                                        Color.White.copy(
                                                            alpha = 0.12f,
                                                        )
                                                    } else {
                                                        Color.Black.copy(alpha = 0.10f)
                                                    },
                                                ),
                                            )
                                        }
                                    }
                                } else {
                                    AuroraGlassSection(
                                        title = stringResource(MR.strings.ai_translator_tab_prompt),
                                    ) {
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.novel_reader_gemini_private_bridge_auto_rules,
                                            ).format(privateProviderLabel),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }

                            2 -> {
                                // -------------------------------------------------------------
                                // TAB 2: ЕЩЕ (Automation, API Connections & Logs)
                                // -------------------------------------------------------------
                                var apiKeyVisible by remember { mutableStateOf(false) }
                                val apiTestStatus = when (tempProvider) {
                                    NovelTranslationProvider.OPENROUTER -> openRouterApiTestStatus
                                    NovelTranslationProvider.DEEPSEEK -> deepSeekApiTestStatus
                                    NovelTranslationProvider.MISTRAL -> mistralApiTestStatus
                                    NovelTranslationProvider.NVIDIA -> nvidiaApiTestStatus
                                    NovelTranslationProvider.OLLAMA_CLOUD -> ollamaCloudApiTestStatus
                                    NovelTranslationProvider.GEMINI, NovelTranslationProvider.GEMINI_PRIVATE -> ProviderApiTestStatus.Idle
                                }
                                val apiTestMessage = when (tempProvider) {
                                    NovelTranslationProvider.OPENROUTER -> openRouterApiTestMessage
                                    NovelTranslationProvider.DEEPSEEK -> deepSeekApiTestMessage
                                    NovelTranslationProvider.MISTRAL -> mistralApiTestMessage
                                    NovelTranslationProvider.NVIDIA -> nvidiaApiTestMessage
                                    NovelTranslationProvider.OLLAMA_CLOUD -> ollamaCloudApiTestMessage
                                    NovelTranslationProvider.GEMINI, NovelTranslationProvider.GEMINI_PRIVATE -> null
                                }

                                // 1. Automation Section
                                AuroraGlassSection(
                                    title = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_more_automation_title,
                                    ),
                                ) {
                                    AuroraToggleRow(
                                        label = stringResource(
                                            AYMR.strings.novel_reader_translation_auto_english_title,
                                        ),
                                        subtitle = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_more_auto_english_summary,
                                        ),
                                        checked = tempAutoTranslateEnglish,
                                        onClick = {
                                            tempAutoTranslateEnglish = !tempAutoTranslateEnglish
                                            onSetGeminiAutoTranslateEnglishSource(tempAutoTranslateEnglish)
                                            logState(autoEnglishLabel, tempAutoTranslateEnglish)
                                        },
                                    )
                                    AuroraToggleRow(
                                        label = stringResource(
                                            AYMR.strings.novel_reader_translation_prefetch_next_title,
                                        ),
                                        subtitle = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_more_prefetch_summary,
                                        ),
                                        checked = tempPrefetchNextChapterTranslation,
                                        onClick = {
                                            tempPrefetchNextChapterTranslation = !tempPrefetchNextChapterTranslation
                                            onSetGeminiPrefetchNextChapterTranslation(
                                                tempPrefetchNextChapterTranslation,
                                            )
                                            logState(prefetchNextLabel, tempPrefetchNextChapterTranslation)
                                        },
                                    )
                                    AuroraToggleRow(
                                        label = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_more_cache_title,
                                        ),
                                        subtitle = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_more_cache_summary,
                                        ),
                                        checked = !tempDisableCache,
                                        onClick = {
                                            tempDisableCache = !tempDisableCache
                                            onSetGeminiDisableCache(tempDisableCache)
                                            onAddLog(cacheStateLabel.format(visibilityStateLabel(!tempDisableCache)))
                                        },
                                    )
                                    if (isGeminiPrivateSelected) {
                                        AuroraToggleRow(
                                            label = stringResource(
                                                AYMR.strings.novel_reader_gemini_private_python_like_mode,
                                            ),
                                            subtitle = stringResource(
                                                AYMR.strings.novel_reader_ai_translator_more_private_python_summary,
                                            ),
                                            checked = tempPrivatePythonLikeMode,
                                            onClick = {
                                                tempPrivatePythonLikeMode = !tempPrivatePythonLikeMode
                                                onSetGeminiPrivatePythonLikeMode(tempPrivatePythonLikeMode)
                                                logState(privatePythonLikeLabel, tempPrivatePythonLikeMode)
                                            },
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onClearAllCache()
                                                onAddLog(cacheClearedLabel)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                            Spacer(Modifier.size(6.dp))
                                            Text(
                                                stringResource(AYMR.strings.novel_reader_ai_translator_clear_all_cache),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }

                                // 2. API Connection Section
                                AuroraGlassSection(
                                    title = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_more_connection_title,
                                    ),
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = aurora.accent.copy(alpha = 0.10f),
                                        border = BorderStroke(1.dp, aurora.accent.copy(alpha = 0.35f)),
                                    ) {
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.novel_reader_ai_translator_more_active_provider,
                                            ).format(getAiTranslatorProviderLabel(tempProvider)),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = aurora.accent,
                                        )
                                    }

                                    if (isOpenRouterSelected || isDeepSeekSelected || isMistralSelected ||
                                        isNvidiaSelected ||
                                        isOllamaCloudSelected
                                    ) {
                                        AuroraOutlinedTextField(
                                            value = when {
                                                isOpenRouterSelected -> tempOpenRouterBaseUrl
                                                isDeepSeekSelected -> tempDeepSeekBaseUrl
                                                isMistralSelected -> tempMistralBaseUrl
                                                isNvidiaSelected -> tempNvidiaBaseUrl
                                                else -> tempOllamaCloudBaseUrl
                                            },
                                            onValueChange = {
                                                if (isOpenRouterSelected) {
                                                    tempOpenRouterBaseUrl = it
                                                    onSetOpenRouterBaseUrl(it)
                                                } else if (isDeepSeekSelected) {
                                                    tempDeepSeekBaseUrl = it
                                                    onSetDeepSeekBaseUrl(it)
                                                } else if (isMistralSelected) {
                                                    tempMistralBaseUrl = it
                                                    onSetMistralBaseUrl(it)
                                                } else if (isNvidiaSelected) {
                                                    tempNvidiaBaseUrl = it
                                                    onSetNvidiaBaseUrl(it)
                                                } else {
                                                    tempOllamaCloudBaseUrl = it
                                                    onSetOllamaCloudBaseUrl(it)
                                                }
                                            },
                                            label = {
                                                Text(
                                                    when {
                                                        isOpenRouterSelected -> stringResource(
                                                            AYMR.strings.novel_reader_openrouter_base_url,
                                                        )
                                                        isDeepSeekSelected -> stringResource(
                                                            AYMR.strings.novel_reader_deepseek_base_url,
                                                        )
                                                        isMistralSelected -> stringResource(
                                                            AYMR.strings.novel_reader_mistral_base_url,
                                                        )
                                                        isNvidiaSelected -> stringResource(
                                                            AYMR.strings.novel_reader_nvidia_base_url,
                                                        )
                                                        else -> stringResource(
                                                            AYMR.strings.novel_reader_ollama_cloud_base_url,
                                                        )
                                                    },
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            singleLine = true,
                                        )
                                    }

                                    val apiKeyUrl = getApiKeyUrl(tempProvider)
                                    if (apiKeyUrl != null) {
                                        val uriHandler = LocalUriHandler.current
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.End,
                                        ) {
                                            TextButton(onClick = { uriHandler.openUri(apiKeyUrl) }) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = aurora.accent,
                                                )
                                                Spacer(Modifier.size(4.dp))
                                                Text(
                                                    stringResource(AYMR.strings.novel_reader_ai_translator_get_api_key),
                                                    color = aurora.accent,
                                                )
                                            }
                                        }
                                    }

                                    AuroraOutlinedTextField(
                                        value = when {
                                            isOpenRouterSelected -> readerSettings.openRouterApiKey
                                            isDeepSeekSelected -> readerSettings.deepSeekApiKey
                                            isMistralSelected -> readerSettings.mistralApiKey
                                            isNvidiaSelected -> readerSettings.nvidiaApiKey
                                            isOllamaCloudSelected -> readerSettings.ollamaCloudApiKey
                                            else -> tempKey
                                        },
                                        onValueChange = {
                                            if (isOpenRouterSelected) {
                                                onSetOpenRouterApiKey(it)
                                            } else if (isDeepSeekSelected) {
                                                onSetDeepSeekApiKey(it)
                                            } else if (isMistralSelected) {
                                                onSetMistralApiKey(it)
                                            } else if (isNvidiaSelected) {
                                                onSetNvidiaApiKey(it)
                                            } else if (isOllamaCloudSelected) {
                                                onSetOllamaCloudApiKey(it)
                                            } else {
                                                tempKey = it
                                                onSetGeminiApiKey(it)
                                            }
                                        },
                                        label = {
                                            Text(
                                                when {
                                                    isOpenRouterSelected -> stringResource(
                                                        AYMR.strings.novel_reader_openrouter_api_key,
                                                    )
                                                    isDeepSeekSelected -> stringResource(
                                                        AYMR.strings.novel_reader_deepseek_api_key,
                                                    )
                                                    isMistralSelected -> stringResource(
                                                        AYMR.strings.novel_reader_mistral_api_key,
                                                    )
                                                    isNvidiaSelected -> stringResource(
                                                        AYMR.strings.novel_reader_nvidia_api_key,
                                                    )
                                                    isOllamaCloudSelected -> stringResource(
                                                        AYMR.strings.novel_reader_ollama_cloud_api_key,
                                                    )
                                                    else -> stringResource(AYMR.strings.novel_reader_gemini_api_key)
                                                },
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        singleLine = true,
                                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                                Icon(
                                                    imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                    contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key",
                                                    tint = if (apiKeyVisible) aurora.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        },
                                    )

                                    AuroraToggleRow(
                                        label = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_more_relaxed_title,
                                        ),
                                        subtitle = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_more_relaxed_summary,
                                        ),
                                        checked = tempRelaxed,
                                        onClick = {
                                            tempRelaxed = !tempRelaxed
                                            onSetGeminiRelaxedMode(tempRelaxed)
                                            onAddLog(relaxedStateLabel.format(visibilityStateLabel(tempRelaxed)))
                                        },
                                    )

                                    if (isOpenRouterSelected || isDeepSeekSelected || isMistralSelected ||
                                        isNvidiaSelected ||
                                        isOllamaCloudSelected
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            AiTranslatorApiTestButton(
                                                status = apiTestStatus,
                                                onClick = when {
                                                    isOpenRouterSelected -> onTestOpenRouterConnection
                                                    isDeepSeekSelected -> onTestDeepSeekConnection
                                                    isMistralSelected -> onTestMistralConnection
                                                    isNvidiaSelected -> onTestNvidiaConnection
                                                    else -> onTestOllamaCloudConnection
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (!apiTestMessage.isNullOrBlank() &&
                                            apiTestStatus == ProviderApiTestStatus.Error
                                        ) {
                                            Text(
                                                text = apiTestMessage,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }

                                // 3. Debug Terminal Logs
                                val clipboard = LocalClipboard.current
                                AuroraGlassSection(
                                    title = stringResource(AYMR.strings.novel_reader_ai_translator_logs_title),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.novel_reader_ai_translator_logs_count,
                                            ).format(logs.size),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = {
                                                    val text = logs.joinToString("\n")
                                                    scope.launch {
                                                        clipboard.setClipEntry(
                                                            ClipEntry(ClipData.newPlainText(null, text)),
                                                        )
                                                    }
                                                },
                                            ) {
                                                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text("Копировать")
                                            }
                                            TextButton(onClick = onClearLogs) {
                                                Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text(stringResource(AYMR.strings.novel_reader_gemini_action_clear))
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (aurora.isDark) Color(0xFF07090F) else Color(0xFF1E232E))
                                            .padding(10.dp)
                                            .heightIn(max = 140.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        if (logs.isEmpty()) {
                                            Text(
                                                text = stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_logs_empty,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF9CA3AF),
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        } else {
                                            logs.forEach { log ->
                                                Text(
                                                    text = log,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFFA7F3D0),
                                                    fontFamily = FontFamily.Monospace,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (showCustomPromptDialog) {
        AlertDialog(
            onDismissRequest = { showCustomPromptDialog = false },
            title = { Text(stringResource(AYMR.strings.novel_reader_ai_translator_custom_modifier_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuroraOutlinedTextField(
                        value = tempCustomModifier,
                        onValueChange = { tempCustomModifier = it },
                        label = {
                            Text(stringResource(AYMR.strings.novel_reader_ai_translator_custom_instructions))
                        },
                        minLines = 4,
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(AYMR.strings.novel_reader_ai_translator_custom_modifier_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetGeminiCustomPromptModifier(tempCustomModifier)
                    onAddLog(customPromptUpdatedLabel)
                    showCustomPromptDialog = false
                }) {
                    Text(stringResource(AYMR.strings.novel_reader_ai_translator_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        tempCustomModifier = ""
                        onSetGeminiCustomPromptModifier("")
                        showCustomPromptDialog = false
                    }) { Text(stringResource(AYMR.strings.novel_reader_gemini_action_clear)) }
                    TextButton(onClick = { showCustomPromptDialog = false }) {
                        Text(stringResource(AYMR.strings.novel_reader_ai_translator_cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun AuroraOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val aurora = AuroraTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = aurora.accent,
            unfocusedBorderColor = auroraRimColor(),
            focusedLabelColor = aurora.accent,
            unfocusedLabelColor = if (aurora.isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
            cursorColor = aurora.accent,
            focusedContainerColor = if (aurora.isDark) {
                Color.White.copy(
                    alpha = 0.04f,
                )
            } else {
                Color.Black.copy(alpha = 0.02f)
            },
            unfocusedContainerColor = if (aurora.isDark) {
                Color.White.copy(
                    alpha = 0.02f,
                )
            } else {
                Color.Black.copy(alpha = 0.01f)
            },
        ),
    )
}

@Composable
private fun AiTranslatorChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aurora = AuroraTheme.colors
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                aurora.accent.copy(alpha = 0.55f)
            } else {
                auroraRimColor()
            },
        ),
        color = if (selected) {
            aurora.accent.copy(alpha = 0.16f)
        } else {
            if (aurora.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (selected) {
                aurora.accent
            } else {
                if (aurora.isDark) Color(0xFFD1D5DB) else Color(0xFF374151)
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AiTranslatorFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aurora = AuroraTheme.colors
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                aurora.accent.copy(alpha = 0.50f)
            } else {
                auroraRimColor()
            },
        ),
        color = if (selected) {
            if (aurora.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
        } else {
            if (aurora.isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = aurora.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    if (aurora.isDark) Color(0xFFD1D5DB) else Color(0xFF374151)
                },
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun AiTranslatorProviderCard(
    title: String,
    apiConfigured: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val aurora = AuroraTheme.colors
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            aurora.accent.copy(alpha = 0.10f)
        } else {
            if (aurora.isDark) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.025f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                aurora.accent.copy(alpha = 0.45f)
            } else {
                auroraRimColor()
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (selected) {
                        Icons.Filled.CheckCircle
                    } else if (apiConfigured) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = if (selected) {
                        aurora.accent
                    } else if (apiConfigured) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = stringResource(
                    if (apiConfigured) {
                        AYMR.strings.novel_reader_ai_translator_provider_api_ready
                    } else {
                        AYMR.strings.novel_reader_ai_translator_provider_api_missing
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiTranslatorApiTestButton(
    status: ProviderApiTestStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelRes = when (status) {
        ProviderApiTestStatus.Idle -> AYMR.strings.novel_reader_ai_translator_api_test_idle
        ProviderApiTestStatus.Loading -> AYMR.strings.novel_reader_ai_translator_api_test_loading
        ProviderApiTestStatus.Success -> AYMR.strings.novel_reader_ai_translator_api_test_success
        ProviderApiTestStatus.Error -> AYMR.strings.novel_reader_ai_translator_api_test_error
    }
    val aurora = AuroraTheme.colors
    Button(
        onClick = onClick,
        enabled = status != ProviderApiTestStatus.Loading,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = when (status) {
                ProviderApiTestStatus.Error -> MaterialTheme.colorScheme.errorContainer
                else -> aurora.accent
            },
            contentColor = when (status) {
                ProviderApiTestStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
                else -> if (aurora.isDark) Color.Black else Color.White
            },
        ),
    ) {
        when (status) {
            ProviderApiTestStatus.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            ProviderApiTestStatus.Success -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            ProviderApiTestStatus.Error -> {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            ProviderApiTestStatus.Idle -> {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
        }
        Text(stringResource(labelRes), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun getApiKeyUrl(provider: NovelTranslationProvider): String? {
    return when (provider) {
        NovelTranslationProvider.GEMINI ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_gemini)
        NovelTranslationProvider.GEMINI_PRIVATE -> null
        NovelTranslationProvider.OPENROUTER ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_openrouter)
        NovelTranslationProvider.DEEPSEEK ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_deepseek)
        NovelTranslationProvider.MISTRAL ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_mistral)
        NovelTranslationProvider.NVIDIA ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_nvidia)
        NovelTranslationProvider.OLLAMA_CLOUD ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_ollama_cloud)
    }
}

@Composable
private fun getAiTranslatorProviderLabel(provider: NovelTranslationProvider): String {
    return when (provider) {
        NovelTranslationProvider.GEMINI ->
            stringResource(AYMR.strings.novel_reader_translation_provider_gemini)
        NovelTranslationProvider.GEMINI_PRIVATE ->
            if (GeminiPrivateBridge.isInstalled()) {
                GeminiPrivateBridge.providerLabel()
            } else {
                stringResource(AYMR.strings.novel_reader_translation_provider_gemini_private)
            }
        NovelTranslationProvider.OPENROUTER ->
            stringResource(AYMR.strings.novel_reader_translation_provider_openrouter)
        NovelTranslationProvider.DEEPSEEK ->
            stringResource(AYMR.strings.novel_reader_translation_provider_deepseek)
        NovelTranslationProvider.MISTRAL ->
            stringResource(AYMR.strings.novel_reader_translation_provider_mistral)
        NovelTranslationProvider.NVIDIA ->
            stringResource(AYMR.strings.novel_reader_translation_provider_nvidia)
        NovelTranslationProvider.OLLAMA_CLOUD ->
            stringResource(AYMR.strings.novel_reader_translation_provider_ollama_cloud)
    }
}

private fun applyNovelSheetWindowFx(
    window: Window,
    reveal: Float,
) {
    val glass = ((reveal - 0.18f) / 0.82f).coerceIn(0f, 1f)
    val radius = if (glass <= 0.02f) {
        0
    } else {
        (44f * glass).roundToInt().coerceIn(1, 48)
    }
    val attrs = window.attributes
    if (attrs.blurBehindRadius != radius) {
        window.attributes = attrs.apply { blurBehindRadius = radius }
    }
    window.setDimAmount(0.18f * glass)
}
