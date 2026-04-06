package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionButton
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroChipBorderColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroChipContainerColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroChipTextColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroOverlayBrush
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroPanelBorderColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroPanelContainerColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroTitleColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.domain.entries.manga.model.Manga

@Composable
fun MangaHeroContent(
    manga: Manga,
    detailsSnapshot: MangaDetailsSnapshot,
    hasProgress: Boolean,
    onContinueReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val haptic = LocalHapticFeedback.current
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val heroPanelShape = RoundedCornerShape(24.dp)
    val titleColor = resolveAuroraHeroTitleColor(colors)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(resolveAuroraHeroOverlayBrush(colors)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 40.dp)
                .then(
                    if (colors.isDark) {
                        Modifier
                    } else {
                        Modifier
                            .clip(heroPanelShape)
                            .background(resolveAuroraHeroPanelContainerColor(colors))
                            .border(1.dp, resolveAuroraHeroPanelBorderColor(colors), heroPanelShape)
                            .padding(18.dp)
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!manga.genre.isNullOrEmpty() && manga.genre!!.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    manga.genre!!.take(3).forEach { genre ->
                        if (genre.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(resolveAuroraHeroChipContainerColor(colors))
                                    .then(
                                        if (colors.isDark) {
                                            Modifier
                                        } else {
                                            Modifier.border(
                                                1.dp,
                                                resolveAuroraHeroChipBorderColor(colors),
                                                RoundedCornerShape(12.dp),
                                            )
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
            }

            Text(
                text = manga.title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = titleColor,
                lineHeight = 36.sp,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                style = TextStyle(fontFamily = coverTitleFontFamily),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroStat(
                    label = stringResource(AYMR.strings.aurora_rating),
                    value = detailsSnapshot.ratingText ?: stringResource(MR.strings.not_applicable),
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    label = "Chapters",
                    value = detailsSnapshot.progress?.chaptersText ?: stringResource(MR.strings.not_applicable),
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    label = "Progress",
                    value = detailsSnapshot.progress?.progressText ?: stringResource(MR.strings.not_applicable),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            AuroraTitleHeroActionButton(
                hasProgress = hasProgress,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onContinueReading()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                cornerRadius = 16.dp,
                iconSize = 28.dp,
                contentPadding = PaddingValues(horizontal = 16.dp),
                textSize = 18.sp,
                textWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = colors.textSecondary.copy(alpha = 0.75f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}
