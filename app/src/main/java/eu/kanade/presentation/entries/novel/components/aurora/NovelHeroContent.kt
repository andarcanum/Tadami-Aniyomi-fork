package eu.kanade.presentation.entries.novel.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
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
import eu.kanade.presentation.entries.manga.components.aurora.MangaStatusFormatter
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelHeroContent(
    novel: Novel,
    translation: AuroraEntryTranslationState? = null,
    chapterCount: Int,
    rating: Float?,
    onContinueReading: (() -> Unit)?,
    isReading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val heroPanelShape = RoundedCornerShape(24.dp)
    val titleColor = resolveAuroraHeroTitleColor(colors)
    val normalizedGenres = remember(novel.genre) {
        val seen = LinkedHashSet<String>()
        novel.genre.orEmpty()
            .flatMap { value ->
                value
                    .split(Regex("[,;/|\\n\\r\\t•·]+"))
                    .map {
                        it.trim()
                            .trim('-', '–', '—', ',', ';', '/', '|', '•', '·')
                    }
            }
            .filter { it.isNotBlank() }
            .filter { seen.add(it.lowercase()) }
    }

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
            if (normalizedGenres.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    normalizedGenres.take(3).forEach { genre ->
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

            Text(
                text = translation?.title ?: novel.title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = titleColor,
                lineHeight = 36.sp,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                style = TextStyle(
                    fontFamily = coverTitleFontFamily,
                    lineBreak = LineBreak.Heading,
                    hyphens = Hyphens.None,
                ),
            )

            HeroStatsStrip(
                modifier = Modifier.fillMaxWidth(),
                ratingValue = rating?.let { String.format(Locale.ROOT, "%.1f", it) }
                    ?: stringResource(MR.strings.not_applicable),
                statusValue = MangaStatusFormatter.formatStatus(context, novel.status),
                chaptersValue = pluralStringResource(
                    MR.plurals.manga_num_chapters,
                    count = chapterCount,
                    chapterCount,
                ),
            )

            if (onContinueReading != null) {
                Spacer(modifier = Modifier.height(4.dp))
                AuroraTitleHeroActionButton(
                    hasProgress = isReading,
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
}

@Composable
private fun HeroStatsStrip(
    modifier: Modifier = Modifier,
    ratingValue: String,
    statusValue: String,
    chaptersValue: String,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        HeroRatingStat(
            value = ratingValue,
            modifier = Modifier.weight(1.05f, fill = false),
        )
        HeroStatDivider()
        HeroStat(
            value = statusValue,
            modifier = Modifier.weight(0.95f, fill = false),
        )
        HeroStatDivider()
        HeroStat(
            value = chaptersValue,
            modifier = Modifier.weight(1.15f, fill = false),
        )
    }
}

@Composable
private fun HeroRatingStat(
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = Color(0xFFFACC15),
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
private fun HeroStat(
    value: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = value,
        color = AuroraTheme.colors.textPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

@Composable
private fun HeroStatDivider() {
    val colors = AuroraTheme.colors

    Text(
        text = " | ",
        color = colors.textSecondary.copy(alpha = 0.5f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false,
    )
}
