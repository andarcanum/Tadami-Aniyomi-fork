package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

internal data class GeminiStatusPresentation(
    val titleRes: StringResource,
    val subtitleRes: StringResource,
)

internal fun geminiStatusPresentation(uiState: GeminiTranslationUiState): GeminiStatusPresentation {
    return when (uiState) {
        GeminiTranslationUiState.Translating -> GeminiStatusPresentation(
            titleRes = MR.strings.reader_translation_running,
            subtitleRes = MR.strings.reader_translation_running_subtitle,
        )
        GeminiTranslationUiState.CachedVisible -> GeminiStatusPresentation(
            titleRes = MR.strings.reader_translation_visible,
            subtitleRes = MR.strings.reader_translation_visible_subtitle,
        )
        GeminiTranslationUiState.CachedHidden -> GeminiStatusPresentation(
            titleRes = MR.strings.reader_translation_cache_ready,
            subtitleRes = MR.strings.reader_translation_cache_ready_subtitle,
        )
        GeminiTranslationUiState.Ready -> GeminiStatusPresentation(
            titleRes = MR.strings.reader_translation_ready,
            subtitleRes = MR.strings.reader_translation_ready_subtitle,
        )
    }
}

@Composable
internal fun GeminiSettingsBlock(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            },
        )
    }
}
