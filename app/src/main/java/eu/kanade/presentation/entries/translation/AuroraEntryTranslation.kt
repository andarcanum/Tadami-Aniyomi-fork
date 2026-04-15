package eu.kanade.presentation.entries.translation

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.core.os.LocaleListCompat
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import kotlinx.coroutines.CancellationException
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

data class AuroraEntryTranslationState(
    val title: String,
    val description: String?,
    val titleTranslated: Boolean = false,
    val descriptionTranslated: Boolean = false,
)

@Composable
fun rememberAuroraEntryTranslation(
    title: String,
    description: String?,
    sourceLanguage: String?,
    enabled: Boolean,
    allowedSourceFamilies: Set<String>,
): AuroraEntryTranslationState {
    val networkHelper = remember { Injekt.get<NetworkHelper>() }
    val translationService = remember(networkHelper) {
        GoogleTranslationService(client = networkHelper.client)
    }
    val targetLanguage = remember { resolveCurrentGoogleTranslationTargetLanguage() }
    val originalState = remember(title, description) {
        AuroraEntryTranslationState(
            title = title,
            description = description,
        )
    }

    return produceState(
        originalState,
        title,
        description,
        sourceLanguage,
        enabled,
        allowedSourceFamilies,
        targetLanguage,
    ) {
        if (!shouldTranslateAuroraEntry(
                enabled = enabled,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                allowedSourceFamilies = allowedSourceFamilies,
            )
        ) {
            value = originalState
            return@produceState
        }

        val textsToTranslate = buildList {
            if (title.isNotBlank()) {
                add(title)
            }
            if (description != null && description.isNotBlank() && description != title) {
                add(description)
            }
        }

        if (textsToTranslate.isEmpty()) {
            value = originalState
            return@produceState
        }

        val translatedTexts = try {
            translationService.translateBatch(
                texts = textsToTranslate,
                params = GoogleTranslationParams(
                    sourceLang = "auto",
                    targetLang = targetLanguage,
                ),
            ).translatedByText
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

        if (translatedTexts == null || translatedTexts.isEmpty()) {
            value = originalState
            return@produceState
        }

        val translatedTitle = translatedTexts[title]
            ?.takeIf { it.isNotBlank() }
            ?: title
        val translatedDescription = description?.let { originalDescription ->
            translatedTexts[originalDescription]
                ?.takeIf { it.isNotBlank() }
                ?: originalDescription
        }

        value = originalState.copy(
            title = translatedTitle,
            description = translatedDescription,
            titleTranslated = translatedTitle != title,
            descriptionTranslated = description != null && translatedDescription != description,
        )
    }.value
}

fun resolveCurrentGoogleTranslationTargetLanguage(): String {
    return resolveGoogleTranslationTargetLanguage(
        appLocale = AppCompatDelegate.getApplicationLocales().get(0),
        systemLocale = LocaleListCompat.getDefault()[0] ?: Locale.getDefault(),
    )
}

fun resolveGoogleTranslationTargetLanguage(
    appLocale: Locale?,
    systemLocale: Locale,
): String {
    val locale = appLocale ?: systemLocale
    val languageTag = locale.toLanguageTag()
    return resolveGoogleTranslationLanguageTag(languageTag).ifBlank { "en" }
}

private fun resolveGoogleTranslationLanguageTag(languageTag: String): String {
    return languageTag
        .trim()
        .takeIf { it.isNotBlank() && it.lowercase(Locale.ROOT) != "und" }
        ?.lowercase(Locale.ROOT)
        .orEmpty()
}
