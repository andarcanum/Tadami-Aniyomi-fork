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
)

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
