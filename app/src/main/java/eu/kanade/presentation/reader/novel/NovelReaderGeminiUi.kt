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

internal data class GeminiStatusPresentation(
    val title: String,
    val subtitle: String,
)

internal fun geminiStatusPresentation(uiState: GeminiTranslationUiState): GeminiStatusPresentation {
    return when (uiState) {
        GeminiTranslationUiState.Translating -> GeminiStatusPresentation(
            title = "Перевод выполняется",
            subtitle = "Обновление текста в реальном времени",
        )
        GeminiTranslationUiState.CachedVisible -> GeminiStatusPresentation(
            title = "Показывается перевод",
            subtitle = "Можно быстро переключать оригинал и перевод",
        )
        GeminiTranslationUiState.CachedHidden -> GeminiStatusPresentation(
            title = "Кэш готов",
            subtitle = "Можно быстро переключать оригинал и перевод",
        )
        GeminiTranslationUiState.Ready -> GeminiStatusPresentation(
            title = "Готов к запуску",
            subtitle = "Выберите модель и запустите перевод главы",
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
