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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun AuroraPosterActionPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    val colors = AuroraTheme.colors
    val isEInk = colors.isEInk
    val titleHeroMode = rememberAuroraTitleHeroCtaMode().toPresentationMode()
    val surfaceSpec = remember(titleHeroMode, colors.isDark, isEInk) {
        resolveAuroraHeroCtaSurfaceSpec(
            mode = titleHeroMode,
            isDark = colors.isDark,
            isHome = false,
        )
    }

    val contentColor = when {
        isEInk -> colors.textPrimary
        titleHeroMode == AuroraHeroCtaMode.Aurora -> Color.White
        else -> colors.textOnAccent
    }

    val islandContainerColor = if (isEInk) {
        resolveAuroraHeroPanelContainerColor(colors)
    } else {
        colors.accent.copy(alpha = surfaceSpec.containerAlpha)
    }
    val closeContainerColor = if (isEInk) {
        resolveAuroraHeroPanelContainerColor(colors)
    } else {
        colors.accent.copy(alpha = surfaceSpec.containerAlpha * 0.9f)
    }

    val borderBrush = remember(colors.accent, surfaceSpec.borderAlpha, isEInk) {
        if (isEInk) {
            SolidColor(resolveAuroraHeroPanelBorderColor(colors))
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = surfaceSpec.borderAlpha * 1.8f),
                    Color.White.copy(alpha = surfaceSpec.borderAlpha * 0.2f),
                ),
            )
        }
    }

    val innerGlowBrush = remember(colors.accent, surfaceSpec.innerGlowAlpha, isEInk) {
        if (isEInk) {
            SolidColor(Color.Transparent)
        } else {
            Brush.radialGradient(
                colors = listOf(
                    colors.accent.copy(alpha = surfaceSpec.innerGlowAlpha * 0.4f),
                    Color.Transparent,
                ),
                center = Offset(0.5f, 1.2f),
            )
        }
    }

    val showBorder = isEInk || surfaceSpec.borderAlpha > 0f
    val islandShape = RoundedCornerShape(28.dp)
    val closeLabel = stringResource(MR.strings.action_close)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(islandShape)
                .background(islandContainerColor)
                .background(innerGlowBrush)
                .let { base ->
                    if (showBorder) {
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

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(closeContainerColor)
                .let { base ->
                    if (showBorder) {
                        base.border(BorderStroke(1.dp, borderBrush), CircleShape)
                    } else {
                        base
                    }
                }
                .clickable(
                    role = Role.Button,
                    onClickLabel = closeLabel,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                tint = contentColor,
                contentDescription = closeLabel,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
