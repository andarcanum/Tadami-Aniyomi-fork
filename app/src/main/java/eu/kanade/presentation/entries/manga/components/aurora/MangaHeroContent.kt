package eu.kanade.presentation.entries.manga.components.aurora

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
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

@Composable
fun MangaHeroContent(
    manga: Manga,
    translation: AuroraEntryTranslationState? = null,
    detailsSnapshot: MangaDetailsSnapshot,
    note: String,
    onEditNotesClicked: (() -> Unit)?,
    hasProgress: Boolean,
    onContinueReading: () -> Unit,
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
            genres = manga.displayGenre,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = translation?.title ?: manga.displayTitle,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = titleColor,
            lineHeight = 40.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = coverTitleFontFamily),
        )

        AuroraHeroStatsRow(
            modifier = Modifier.fillMaxWidth(),
            ratingValue = detailsSnapshot.ratingText ?: stringResource(MR.strings.not_applicable),
            secondValue = detailsSnapshot.progress?.totalChapters?.let {
                pluralStringResource(
                    MR.plurals.manga_num_chapters,
                    count = it,
                    it,
                )
            } ?: stringResource(MR.strings.not_applicable),
            thirdValue = detailsSnapshot.progress?.progressText ?: stringResource(MR.strings.not_applicable),
        )

        AuroraNotePreviewCard(
            note = note,
            onClick = onEditNotesClicked,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(2.dp))

        AuroraTitleHeroActionButton(
            hasProgress = hasProgress,
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
