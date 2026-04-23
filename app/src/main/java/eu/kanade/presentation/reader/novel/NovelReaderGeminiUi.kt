package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
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
