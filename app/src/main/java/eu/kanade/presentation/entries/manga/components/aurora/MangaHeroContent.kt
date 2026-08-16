package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.aurora.AuroraHeroGenreChips
import eu.kanade.presentation.entries.components.aurora.AuroraHeroScaffold
import eu.kanade.presentation.entries.components.aurora.AuroraHeroStatsRow
import eu.kanade.presentation.entries.components.aurora.AuroraNotePreviewCard
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionButton
import eu.kanade.presentation.entries.components.aurora.CopyTitleIcon
import eu.kanade.presentation.entries.components.aurora.copyTitleInlineContent
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroSecondaryMetaColor
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
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    selectedGenres: Set<String> = emptySet(),
    onSearchSelected: (() -> Unit)? = null,
    onClearSelected: (() -> Unit)? = null,
    onCopyTitle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val heroPanelShape = RoundedCornerShape(24.dp)
    val titleColor = resolveAuroraHeroTitleColor(colors)
    val secondaryMetaColor = resolveAuroraHeroSecondaryMetaColor(colors)
    val titleText = translation?.title ?: manga.displayTitle
    var titleOverflow by remember { mutableStateOf(false) }
    val showInlineCopyIcon = onCopyTitle != null && !titleOverflow
    val (copyTitleIconId, copyTitleInlineContent) = copyTitleInlineContent(
        onCopyTitle = if (showInlineCopyIcon) onCopyTitle else null,
        tint = secondaryMetaColor,
        contentDescription = stringResource(MR.strings.copy_title),
    )

    AuroraHeroScaffold(
        modifier = modifier,
        shape = heroPanelShape,
    ) {
        AuroraHeroGenreChips(
            genres = manga.displayGenre,
            modifier = Modifier.fillMaxWidth(),
            selectedGenres = selectedGenres,
            onGenreClick = onGenreClick,
            onGenreLongClick = onGenreLongClick,
            onSearchSelected = onSearchSelected,
            onClearSelected = onClearSelected,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = buildAnnotatedString {
                    append(titleText)
                    if (showInlineCopyIcon) {
                        append(' ')
                        appendInlineContent(copyTitleIconId)
                    }
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = titleColor,
                lineHeight = 40.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = coverTitleFontFamily),
                inlineContent = copyTitleInlineContent,
                onTextLayout = { result ->
                    if (result.hasVisualOverflow && !titleOverflow) {
                        titleOverflow = true
                    }
                },
            )

            if (titleOverflow && onCopyTitle != null) {
                CopyTitleIcon(
                    onCopyTitle = onCopyTitle,
                    tint = secondaryMetaColor,
                    contentDescription = stringResource(MR.strings.copy_title),
                )
            }
        }

        // Author (moved from the stats card onto the poster)
        manga.displayAuthor?.takeIf { it.isNotBlank() }?.let { authorText ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonOutline,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = authorText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

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
                .fillMaxWidth()
                .height(54.dp),
            cornerRadius = 16.dp,
            iconSize = 28.dp,
            contentPadding = PaddingValues(horizontal = 24.dp),
            textSize = 18.sp,
            textWeight = FontWeight.Bold,
        )
    }
}
