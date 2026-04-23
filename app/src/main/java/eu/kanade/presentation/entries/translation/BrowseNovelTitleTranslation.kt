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

@Composable
fun rememberBrowseNovelTitleTranslation(
    title: String,
    sourceLanguage: String?,
    enabled: Boolean,
    allowedSourceFamilies: Set<String>,
): String {
    val networkHelper = remember { Injekt.get<NetworkHelper>() }
    val translationService = remember(networkHelper) {
        GoogleTranslationService(client = networkHelper.client)
    }
    val targetLanguage = remember { resolveCurrentGoogleTranslationTargetLanguageForBrowse() }
    val originalTitle = remember(title) { title }

    return produceState(
        originalTitle,
        title,
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
            value = originalTitle
            return@produceState
        }

        if (title.isBlank()) {
            value = originalTitle
            return@produceState
        }

        val translatedTitle = try {
            translationService.translateBatch(
                texts = listOf(title),
                params = GoogleTranslationParams(
                    sourceLang = "auto",
                    targetLang = targetLanguage,
                ),
            ).translatedByText[title]
                ?.takeIf { it.isNotBlank() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

        value = translatedTitle ?: originalTitle
    }.value
}

private fun resolveCurrentGoogleTranslationTargetLanguageForBrowse(): String {
    return resolveGoogleTranslationTargetLanguage(
        appLocale = AppCompatDelegate.getApplicationLocales().get(0),
        systemLocale = LocaleListCompat.getDefault()[0] ?: Locale.getDefault(),
    )
}
