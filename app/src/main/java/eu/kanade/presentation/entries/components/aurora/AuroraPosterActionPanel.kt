package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.domain.ui.model.AuroraTitleHeroCtaMode
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
private fun rememberAuroraPosterActionMode(): AuroraTitleHeroCtaMode {
    val userProfilePreferences = remember { Injekt.get<UserProfilePreferences>() }
    val titleHeroModeKey by userProfilePreferences.auroraTitleHeroCtaMode().collectAsState()
    return remember(titleHeroModeKey) {
        AuroraTitleHeroCtaMode.fromKey(titleHeroModeKey)
    }
}

@Composable
internal fun AuroraPosterActionPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    val colors = AuroraTheme.colors
    val titleHeroMode = rememberAuroraPosterActionMode()
    val visualMode = remember(titleHeroMode) {
        resolveAuroraTitleHeroCtaVisualMode(titleHeroMode)
    }
    val surfaceSpec = remember(titleHeroMode, colors.isDark) {
        resolveAuroraTitleHeroCtaSurfaceSpec(
            mode = titleHeroMode,
            isDark = colors.isDark,
        )
    }
    val contentColor = when (visualMode) {
        AuroraTitleHeroCtaVisualMode.AuroraGlass -> Color.White
        AuroraTitleHeroCtaVisualMode.ClassicSolid -> colors.textOnAccent
    }

    // Эффект "фаски" на стекле - светится сверху, затухает снизу
    val borderBrush = remember(colors.accent, surfaceSpec.borderAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = surfaceSpec.borderAlpha * 1.8f),
                Color.White.copy(alpha = surfaceSpec.borderAlpha * 0.2f),
            ),
        )
    }

    // Внутреннее мягкое свечение снизу
    val innerGlowBrush = remember(colors.accent, surfaceSpec.innerGlowAlpha) {
        Brush.radialGradient(
            colors = listOf(
                colors.accent.copy(alpha = surfaceSpec.innerGlowAlpha * 0.4f),
                Color.Transparent,
            ),
            center = Offset(0.5f, 1.2f),
        )
    }

    val islandShape = RoundedCornerShape(28.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Основной остров действий
        Row(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(islandShape)
                .background(colors.accent.copy(alpha = surfaceSpec.containerAlpha))
                .background(innerGlowBrush)
                .let { base ->
                    if (surfaceSpec.borderAlpha > 0f) {
                        base.border(BorderStroke(1.dp, borderBrush), islandShape)
                    } else {
                        base
                    }
                }
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content(contentColor)
        }

        // Отдельный круглый остров для закрытия (Асимметрия)
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = surfaceSpec.containerAlpha * 0.9f))
                .let { base ->
                    if (surfaceSpec.borderAlpha > 0f) {
                        base.border(BorderStroke(1.dp, borderBrush), CircleShape)
                    } else {
                        base
                    }
                }
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                tint = contentColor,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
