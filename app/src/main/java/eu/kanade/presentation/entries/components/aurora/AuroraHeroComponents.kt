package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

@Composable
fun AuroraHeroScaffold(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    hazeState: HazeState? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
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
            .then(
                if (colors.isDark) {
                    Modifier.background(resolveAuroraHeroOverlayBrush(colors))
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (colors.isDark) {
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 24.dp, bottom = 24.dp + bottomPadding)
                    } else if (colors.isEInk) {
                        Modifier
                            .fillMaxWidth()
                            .auroraCenteredMaxWidth(420)
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp, bottom = 12.dp + bottomPadding)
                            .clip(panelShape)
                            .background(resolveAuroraHeroPanelContainerColor(colors))
                            .border(1.dp, resolveAuroraHeroPanelBorderColor(colors), panelShape)
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    } else {
                        val hazeModifier = if (hazeState != null) {
                            Modifier
                                .clip(panelShape)
                                .hazeEffect(
                                    state = hazeState,
                                    style = HazeStyle(
                                        backgroundColor = Color.Transparent,
                                        tint = HazeTint(Color.White.copy(alpha = 0.18f)),
                                        blurRadius = 20.dp,
                                        noiseFactor = 0f,
                                    ),
                                )
                        } else {
                            Modifier
                        }
                        Modifier
                            .fillMaxWidth()
                            .auroraCenteredMaxWidth(420)
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp, bottom = 12.dp + bottomPadding)
                            .then(hazeModifier)
                            .auroraCoverHeroCardStyle(
                                colors = colors,
                                shape = panelShape,
                                cornerRadius = 28.dp,
                            )
                            .padding(horizontal = 14.dp, vertical = 16.dp)
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
            modifier = Modifier.align(Alignment.CenterVertically),
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
            HeroStatDivider(modifier = Modifier.align(Alignment.CenterVertically))
            Text(
                text = secondValue,
                modifier = Modifier.align(Alignment.CenterVertically),
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (thirdValue != null) {
            HeroStatDivider(modifier = Modifier.align(Alignment.CenterVertically))
            Text(
                text = thirdValue,
                modifier = Modifier.align(Alignment.CenterVertically),
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
internal fun HeroStatDivider(modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    Box(
        modifier = modifier
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
    selectedGenres: Set<String> = emptySet(),
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    onSearchSelected: (() -> Unit)? = null,
    onClearSelected: (() -> Unit)? = null,
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
            val isSelected = genre in selectedGenres
            val baseModifier = Modifier
                .clip(chipShape)
                .background(
                    if (isSelected) {
                        colors.accent.copy(alpha = 0.24f)
                    } else {
                        resolveAuroraHeroChipContainerColor(colors)
                    },
                )
                .then(
                    if (colors.isDark) {
                        Modifier
                    } else {
                        Modifier.border(
                            1.dp,
                            if (isSelected) {
                                colors.accent.copy(
                                    alpha = 0.5f,
                                )
                            } else {
                                resolveAuroraHeroChipBorderColor(colors)
                            },
                            chipShape,
                        )
                    },
                )
                .sizeIn(minWidth = 40.dp, minHeight = 26.dp)

            val interactiveModifier = if (onGenreClick != null || onGenreLongClick != null) {
                Modifier.pointerInput(genre, selectedGenres) {
                    detectTapGestures(
                        onTap = {
                            if (selectedGenres.isNotEmpty()) {
                                onGenreLongClick?.invoke(genre)
                            } else {
                                onGenreClick?.invoke(genre)
                            }
                        },
                        onLongPress = {
                            onGenreLongClick?.invoke(genre)
                        },
                    )
                }
            } else {
                Modifier
            }

            Box(
                modifier = baseModifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .then(interactiveModifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = genre,
                    color = if (isSelected) colors.accent else resolveAuroraHeroChipTextColor(colors),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (selectedGenres.isNotEmpty()) {
            // Search Selected button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.8f))
                    .clickable { onSearchSelected?.invoke() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🔎 ${stringResource(MR.strings.action_search)} (${selectedGenres.size})",
                    color = colors.textOnAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Clear Selection button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(resolveAuroraHeroChipContainerColor(colors))
                    .then(
                        if (colors.isDark) {
                            Modifier
                        } else {
                            Modifier.border(1.dp, resolveAuroraHeroChipBorderColor(colors), CircleShape)
                        },
                    )
                    .clickable { onClearSelected?.invoke() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    color = colors.textPrimary,
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

/**
 * The copy-to-clipboard icon shown next to a title. Only rendered when [onCopyTitle] is set.
 */
@Composable
internal fun CopyTitleIcon(
    onCopyTitle: (() -> Unit)?,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    padding: androidx.compose.ui.unit.Dp = 8.dp,
) {
    if (onCopyTitle == null) return

    val appHaptics = LocalAppHaptics.current
    Icon(
        imageVector = Icons.Outlined.ContentCopy,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                appHaptics.tap()
                onCopyTitle()
            }
            .padding(padding),
    )
}

/**
 * Builds the inline content for a copy-to-clipboard icon appended right after the last
 * character of a title. The icon is only rendered when [onCopyTitle] is set.
 *
 * @return pair of the inline content id and the [InlineTextContent] map for [Text.inlineContent]
 */
@Composable
internal fun copyTitleInlineContent(
    onCopyTitle: (() -> Unit)?,
    tint: Color,
    contentDescription: String,
): Pair<String, Map<String, InlineTextContent>> {
    val iconId = "copy_title_icon"
    val content = if (onCopyTitle != null) {
        persistentMapOf(
            iconId to InlineTextContent(
                Placeholder(
                    width = 24.sp,
                    height = 20.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                CopyTitleIcon(
                    onCopyTitle = onCopyTitle,
                    tint = tint,
                    contentDescription = contentDescription,
                    size = 20.dp,
                    padding = 2.dp,
                )
            },
        )
    } else {
        persistentMapOf()
    }
    return iconId to content
}
