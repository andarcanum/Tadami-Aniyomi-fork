package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.displayScore
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionButton
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroChipBorderColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroChipContainerColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroChipTextColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroOverlayBrush
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroPanelBorderColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroPanelContainerColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroPrimaryMetaColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroSecondaryButtonPalette
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroSecondaryMetaColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroTitleColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private fun parseOriginalTitle(description: String?): String? {
    if (description.isNullOrBlank()) return null

    val match = Regex(
        pattern = """(?:Original|Оригинал):\s*([^\n]+)""",
        options = setOf(RegexOption.IGNORE_CASE),
    ).find(description)

    return match?.groupValues?.get(1)?.trim()
}

internal data class AnimeHeroPrimaryActionLayoutSpec(
    val heightDp: Int,
    val horizontalPaddingDp: Int,
)

internal fun resolveAnimeHeroPrimaryActionLayoutSpec(): AnimeHeroPrimaryActionLayoutSpec {
    return AnimeHeroPrimaryActionLayoutSpec(
        heightDp = 52,
        horizontalPaddingDp = 14,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeHeroContent(
    anime: Anime,
    episodeCount: Int,
    hasWatchingProgress: Boolean,
    onContinueWatching: () -> Unit,
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
    animeMetadata: ExternalMetadata? = null,
    modifier: Modifier = Modifier,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val showOriginalTitle by uiPreferences.showOriginalTitle().collectAsState()
    val colors = AuroraTheme.colors
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val primaryActionLayoutSpec = remember {
        resolveAnimeHeroPrimaryActionLayoutSpec()
    }
    val originalTitle = remember(anime.description) {
        parseOriginalTitle(anime.description)
    }
    val metadataScore = animeMetadata?.displayScore()
    val heroPanelShape = RoundedCornerShape(24.dp)
    val titleColor = resolveAuroraHeroTitleColor(colors)
    val primaryMetaColor = resolveAuroraHeroPrimaryMetaColor(colors)
    val secondaryMetaColor = resolveAuroraHeroSecondaryMetaColor(colors)

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
                    } else {
                        Modifier
                            .clip(heroPanelShape)
                            .background(resolveAuroraHeroPanelContainerColor(colors))
                            .border(
                                width = 1.dp,
                                color = resolveAuroraHeroPanelBorderColor(colors),
                                shape = heroPanelShape,
                            )
                            .padding(18.dp)
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val displayTitle = buildAnnotatedString {
                append(anime.title)

                if (showOriginalTitle && originalTitle != null) {
                    withStyle(
                        SpanStyle(
                            color = secondaryMetaColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                    ) {
                        append(" ($originalTitle)")
                    }
                }
            }

            Text(
                text = displayTitle,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                color = titleColor,
                style = TextStyle(
                    fontFamily = coverTitleFontFamily,
                    lineBreak = LineBreak.Heading,
                    hyphens = Hyphens.None,
                ),
            )

            if (!anime.genre.isNullOrEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    anime.genre!!.take(3).forEach { genre ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(resolveAuroraHeroChipContainerColor(colors))
                                .then(
                                    if (colors.isDark) {
                                        Modifier
                                    } else {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = resolveAuroraHeroChipBorderColor(colors),
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = genre,
                                fontSize = 11.sp,
                                color = resolveAuroraHeroChipTextColor(colors),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (metadataScore != null) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = metadataScore,
                        fontSize = 13.sp,
                        color = primaryMetaColor,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "|",
                        fontSize = 13.sp,
                        color = secondaryMetaColor,
                    )
                }

                Text(
                    text = AnimeStatusFormatter.formatStatus(anime.status),
                    fontSize = 13.sp,
                    color = secondaryMetaColor,
                )
                Text(
                    text = "|",
                    fontSize = 13.sp,
                    color = secondaryMetaColor,
                )
                Text(
                    text = "$episodeCount эп.",
                    fontSize = 13.sp,
                    color = secondaryMetaColor,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AuroraTitleHeroActionButton(
                    hasProgress = hasWatchingProgress,
                    onClick = onContinueWatching,
                    modifier = Modifier
                        .weight(1f)
                        .height(primaryActionLayoutSpec.heightDp.dp),
                    cornerRadius = 12.dp,
                    iconSize = 20.dp,
                    contentPadding = PaddingValues(horizontal = primaryActionLayoutSpec.horizontalPaddingDp.dp),
                    textSize = 15.sp,
                    textWeight = FontWeight.SemiBold,
                )

                if (onDubbingClicked != null) {
                    val dubbingPalette = resolveAuroraHeroSecondaryButtonPalette(
                        colors = colors,
                        isActive = selectedDubbing?.isNotBlank() == true,
                    )
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(primaryActionLayoutSpec.heightDp.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(dubbingPalette.containerColor)
                            .border(
                                width = 1.dp,
                                color = dubbingPalette.borderColor,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable(onClick = onDubbingClicked)
                            .padding(horizontal = primaryActionLayoutSpec.horizontalPaddingDp.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.RecordVoiceOver,
                                contentDescription = null,
                                tint = dubbingPalette.contentColor,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = selectedDubbing?.takeIf { it.isNotBlank() }
                                    ?: stringResource(MR.strings.label_dubbing),
                                color = dubbingPalette.contentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
