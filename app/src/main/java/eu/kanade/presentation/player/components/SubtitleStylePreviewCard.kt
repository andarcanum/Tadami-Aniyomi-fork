package eu.kanade.presentation.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.player.SubtitleStylePreset
import eu.kanade.tachiyomi.ui.player.SubtitleStyleSnapshot
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubtitlesBorderStyle
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun SubtitleStylePreviewAndPresets(
    subtitlePreferences: SubtitlePreferences,
    applyLiveToMpv: Boolean,
    modifier: Modifier = Modifier,
) {
    val fontSize by subtitlePreferences.subtitleFontSize().collectAsState()
    val bold by subtitlePreferences.boldSubtitles().collectAsState()
    val italic by subtitlePreferences.italicSubtitles().collectAsState()
    val textColor by subtitlePreferences.textColorSubtitles().collectAsState()
    val borderColor by subtitlePreferences.borderColorSubtitles().collectAsState()
    val backgroundColor by subtitlePreferences.backgroundColorSubtitles().collectAsState()
    val borderSize by subtitlePreferences.subtitleBorderSize().collectAsState()
    val borderStyle by subtitlePreferences.borderStyleSubtitles().collectAsState()

    val currentSnapshot = remember(
        fontSize,
        bold,
        italic,
        textColor,
        borderColor,
        backgroundColor,
        borderSize,
        borderStyle,
    ) {
        SubtitleStyleSnapshot(
            fontSize = fontSize,
            bold = bold,
            italic = italic,
            textColor = textColor,
            borderColor = borderColor,
            backgroundColor = backgroundColor,
            borderSize = borderSize,
            borderStyle = borderStyle,
        )
    }
    val activePreset = remember(currentSnapshot) {
        SubtitleStylePreset.resolveActive(currentSnapshot)
    }

    val previewTextSp = ((fontSize.coerceIn(24, 96) / 55f) * 16f).coerceIn(11f, 26f).sp
    val usesBoxBg = borderStyle != SubtitlesBorderStyle.OutlineAndShadow || Color(backgroundColor).alpha > 0.05f
    val boxBgColor = if (usesBoxBg) {
        Color(backgroundColor).let { if (it.alpha < 0.05f) Color.Black.copy(alpha = 0.62f) else it }
    } else {
        Color.Transparent
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1A2233),
                            Color(0xFF2B3A4E),
                            Color(0xFF131922),
                        ),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = stringResource(AYMR.strings.player_subtitle_preview_sample),
                color = Color(textColor),
                fontSize = previewTextSp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(borderColor),
                        offset = Offset(1f, 1f),
                        blurRadius = (borderSize.coerceIn(0, 8) * 1.5f).coerceAtLeast(1f),
                    ),
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(boxBgColor)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SubtitleStylePreset.entries.forEach { preset ->
                FilterChip(
                    selected = activePreset == preset,
                    onClick = { preset.applyTo(subtitlePreferences, applyLiveToMpv = applyLiveToMpv) },
                    label = { Text(text = stringResource(preset.titleRes)) },
                )
            }
            if (activePreset == null) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(text = stringResource(AYMR.strings.player_decoder_preset_custom)) },
                )
            }
        }
    }
}
