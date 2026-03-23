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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroSecondaryMetaColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroTitleColor
import eu.kanade.presentation.entries.manga.components.aurora.MangaStatusFormatter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelHeroContent(
    novel: Novel,
    chapterCount: Int,
    onContinueReading: (() -> Unit)?,
    isReading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val haptic = LocalHapticFeedback.current
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val heroPanelShape = RoundedCornerShape(24.dp)
    val titleColor = resolveAuroraHeroTitleColor(colors)
    val secondaryMetaColor = resolveAuroraHeroSecondaryMetaColor(colors)
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
                    }
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
                                    }
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
                text = novel.title,
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = MangaStatusFormatter.formatStatus(novel.status),
                    color = secondaryMetaColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "|",
                    color = secondaryMetaColor,
                    fontSize = 13.sp,
                )
                Text(
                    text = pluralStringResource(
                        MR.plurals.manga_num_chapters,
                        count = chapterCount,
                        chapterCount,
                    ),
                    color = secondaryMetaColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

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
