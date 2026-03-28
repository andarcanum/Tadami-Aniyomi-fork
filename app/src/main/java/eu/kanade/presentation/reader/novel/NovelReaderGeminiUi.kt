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
            title = "РџРµСЂРµРІРѕРґ РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ",
            subtitle = "РћР±РЅРѕРІР»РµРЅРёРµ С‚РµРєСЃС‚Р° РІ СЂРµР°Р»СЊРЅРѕРј РІСЂРµРјРµРЅРё",
        )
        GeminiTranslationUiState.CachedVisible -> GeminiStatusPresentation(
            title = "РџРѕРєР°Р·С‹РІР°РµС‚СЃСЏ РїРµСЂРµРІРѕРґ",
            subtitle = "РњРѕР¶РЅРѕ Р±С‹СЃС‚СЂРѕ РїРµСЂРµРєР»СЋС‡Р°С‚СЊ РѕСЂРёРіРёРЅР°Р» Рё РїРµСЂРµРІРѕРґ",
        )
        GeminiTranslationUiState.CachedHidden -> GeminiStatusPresentation(
            title = "РљСЌС€ РіРѕС‚РѕРІ",
            subtitle = "РњРѕР¶РЅРѕ Р±С‹СЃС‚СЂРѕ РїРµСЂРµРєР»СЋС‡Р°С‚СЊ РѕСЂРёРіРёРЅР°Р» Рё РїРµСЂРµРІРѕРґ",
        )
        GeminiTranslationUiState.Ready -> GeminiStatusPresentation(
            title = "Р“РѕС‚РѕРІ Рє Р·Р°РїСѓСЃРєСѓ",
            subtitle = "Р’С‹Р±РµСЂРёС‚Рµ РјРѕРґРµР»СЊ Рё Р·Р°РїСѓСЃС‚РёС‚Рµ РїРµСЂРµРІРѕРґ РіР»Р°РІС‹",
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
