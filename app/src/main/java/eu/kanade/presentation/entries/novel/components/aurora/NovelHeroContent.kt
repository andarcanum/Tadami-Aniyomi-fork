package eu.kanade.presentation.entries.novel.components.aurora

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.aurora.AuroraHeroGenreChips
import eu.kanade.presentation.entries.components.aurora.AuroraHeroScaffold
import eu.kanade.presentation.entries.components.aurora.AuroraHeroStatsRow
import eu.kanade.presentation.entries.components.aurora.AuroraNotePreviewCard
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionButton
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroTitleColor
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import java.util.Locale

@Composable
fun NovelHeroContent(
    novel: Novel,
    translation: AuroraEntryTranslationState? = null,
    chapterCount: Int,
    rating: Float?,
    note: String,
    onEditNotesClicked: (() -> Unit)?,
    onContinueReading: (() -> Unit)?,
    isReading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val heroPanelShape = RoundedCornerShape(24.dp)
    val titleColor = resolveAuroraHeroTitleColor(colors)

    AuroraHeroScaffold(
        modifier = modifier,
        shape = heroPanelShape,
    ) {
        AuroraHeroGenreChips(
            genres = novel.displayGenre,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = translation?.title ?: novel.displayTitle,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = titleColor,
            lineHeight = 40.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = coverTitleFontFamily,
                lineBreak = LineBreak.Heading,
                hyphens = Hyphens.None,
            ),
        )

        AuroraHeroStatsRow(
            modifier = Modifier.fillMaxWidth(),
            ratingValue = rating?.let { String.format(Locale.ROOT, "%.1f", it) }
                ?: stringResource(MR.strings.not_applicable),
            secondValue = novelStatusText(novel.displayStatus),
            thirdValue = pluralStringResource(
                MR.plurals.manga_num_chapters,
                count = chapterCount,
                chapterCount,
            ),
        )

        AuroraNotePreviewCard(
            note = note,
            onClick = onEditNotesClicked,
            modifier = Modifier.fillMaxWidth(),
        )

        if (onContinueReading != null) {
            Spacer(modifier = Modifier.height(4.dp))
            AuroraTitleHeroActionButton(
                hasProgress = isReading,
                onClick = {
                    appHaptics.tap()
                    onContinueReading()
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.9f)
                    .height(54.dp),
                cornerRadius = 16.dp,
                iconSize = 28.dp,
                contentPadding = PaddingValues(horizontal = 24.dp),
                textSize = 18.sp,
                textWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun novelStatusText(status: Long): String {
    return when (status.toInt()) {
        SManga.ONGOING -> stringResource(MR.strings.ongoing)
        SManga.COMPLETED -> stringResource(MR.strings.completed)
        SManga.LICENSED -> stringResource(MR.strings.licensed)
        SManga.PUBLISHING_FINISHED -> stringResource(MR.strings.publishing_finished)
        SManga.CANCELLED -> stringResource(MR.strings.cancelled)
        SManga.ON_HIATUS -> stringResource(MR.strings.on_hiatus)
        else -> stringResource(MR.strings.unknown)
    }
}
