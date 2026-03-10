package eu.kanade.presentation.more.settings.screen.about

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val ABOUT_HIDDEN_FEATURE_ASSET_PATH = "local/about_hidden_feature.json"
private val ABOUT_HIDDEN_FEATURE_JSON = Json { ignoreUnknownKeys = true }

@Serializable
internal data class AboutHiddenFeatureConfig(
    val trigger: AboutHiddenFeatureTriggerConfig,
    val content: AboutHiddenFeatureContent,
)

@Serializable
internal data class AboutHiddenFeatureTriggerConfig(
    val requiredPrimarySignals: Int,
    val primedWindowMs: Long,
    val tapStreakWindowMs: Long,
)

@Serializable
internal data class AboutHiddenFeatureContent(
    val systemLabel: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val exitLabel: String,
    val systemLabelEn: String? = null,
    val titleEn: String? = null,
    val subtitleEn: String? = null,
    val bodyEn: String? = null,
    val exitLabelEn: String? = null,
)

internal data class AboutHiddenFeatureLocalizedContent(
    val systemLabel: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val exitLabel: String,
)

internal fun AboutHiddenFeatureContent.localizedForLanguage(languageCode: String): AboutHiddenFeatureLocalizedContent {
    val normalizedLanguage = languageCode.trim().lowercase()
    val useEnglish = normalizedLanguage == "en"
    return AboutHiddenFeatureLocalizedContent(
        systemLabel = localizedValue(base = systemLabel, english = systemLabelEn, useEnglish = useEnglish),
        title = localizedValue(base = title, english = titleEn, useEnglish = useEnglish),
        subtitle = localizedValue(base = subtitle, english = subtitleEn, useEnglish = useEnglish),
        body = localizedValue(base = body, english = bodyEn, useEnglish = useEnglish),
        exitLabel = localizedValue(base = exitLabel, english = exitLabelEn, useEnglish = useEnglish),
    )
}

private fun localizedValue(base: String, english: String?, useEnglish: Boolean): String {
    return if (useEnglish && !english.isNullOrBlank()) {
        english
    } else {
        base
    }
}

internal fun parseAboutHiddenFeatureConfig(json: String): AboutHiddenFeatureConfig {
    return ABOUT_HIDDEN_FEATURE_JSON.decodeFromString(json)
}

internal fun loadAboutHiddenFeatureConfig(context: Context): AboutHiddenFeatureConfig? {
    return runCatching {
        context.assets.open(ABOUT_HIDDEN_FEATURE_ASSET_PATH).bufferedReader().use { reader ->
            parseAboutHiddenFeatureConfig(reader.readText())
        }
    }.getOrNull()
}
