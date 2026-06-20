package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme

@Composable
fun AuroraHeroScaffold(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val panelShape = shape

    val cardModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(resolveAuroraHeroOverlayBrush(colors)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .then(
                    if (colors.isDark) {
                        Modifier
                    } else if (colors.isEInk) {
                        Modifier
                            .clip(panelShape)
                            .background(resolveAuroraHeroPanelContainerColor(colors))
                            .border(1.dp, resolveAuroraHeroPanelBorderColor(colors), panelShape)
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    } else {
                        Modifier
                            .auroraCoverHeroCardStyle(
                                colors = colors,
                                shape = panelShape,
                                cornerRadius = 24.dp,
                            )
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    },
                )
                .then(cardModifier),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuroraHeroStatsRow(
    ratingValue: String,
    modifier: Modifier = Modifier,
    secondValue: String? = null,
    thirdValue: String? = null,
) {
    val colors = AuroraTheme.colors

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = colors.ratingStar,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = ratingValue,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (secondValue != null) {
            HeroStatDivider()
            Text(
                text = secondValue,
                color = colors.textSecondary.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (thirdValue != null) {
            HeroStatDivider()
            Text(
                text = thirdValue,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun HeroStatDivider() {
    val colors = AuroraTheme.colors
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(10.dp)
            .background(colors.textSecondary.copy(alpha = 0.3f)),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuroraHeroGenreChips(
    genres: List<String>?,
    modifier: Modifier = Modifier,
    max: Int = 3,
) {
    val normalized = remember(genres) { normalizeAuroraHeroGenres(genres) }
    if (normalized.isEmpty()) return

    val colors = AuroraTheme.colors
    val chipShape = RoundedCornerShape(12.dp)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        normalized.take(max).forEach { genre ->
            Box(
                modifier = Modifier
                    .clip(chipShape)
                    .background(resolveAuroraHeroChipContainerColor(colors))
                    .then(
                        if (colors.isDark) {
                            Modifier
                        } else {
                            Modifier.border(1.dp, resolveAuroraHeroChipBorderColor(colors), chipShape)
                        },
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = genre,
                    color = resolveAuroraHeroChipTextColor(colors),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

internal fun normalizeAuroraHeroGenres(genres: List<String>?): List<String> {
    val seen = LinkedHashSet<String>()
    return genres.orEmpty()
        .flatMap { value ->
            value
                .split(Regex("[,;/|\\n\\r\\t•·]+"))
                .map {
                    it.trim().trim('-', '–', '—', ',', ';', '/', '|', '•', '·')
                }
        }
        .filter { it.isNotBlank() }
        .filter { seen.add(it.lowercase()) }
}
