package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import dev.chrisbanes.haze.HazeState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.entries.components.aurora.AuroraHeroGenreChips
import eu.kanade.presentation.entries.components.aurora.AuroraHeroScaffold
import eu.kanade.presentation.entries.components.aurora.AuroraHeroStatsRow
import eu.kanade.presentation.entries.components.aurora.AuroraNotePreviewCard
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionButton
import eu.kanade.presentation.entries.components.aurora.AuroraTitleStaggerIndex
import eu.kanade.presentation.entries.components.aurora.AuroraTitleStaggerState
import eu.kanade.presentation.entries.components.aurora.CopyTitleIcon
import eu.kanade.presentation.entries.components.aurora.copyTitleInlineContent
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroSecondaryMetaColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroTitleColor
import eu.kanade.presentation.entries.components.aurora.titleScreenStagger
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
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
        heightDp = 54,
        horizontalPaddingDp = 18,
    )
}

@Composable
fun AnimeHeroContent(
    anime: Anime,
    translation: AuroraEntryTranslationState? = null,
    hasWatchingProgress: Boolean,
    ratingText: String,
    episodeCount: Int,
    statusText: String,
    note: String,
    onEditNotesClicked: (() -> Unit)?,
    onContinueWatching: () -> Unit,
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    selectedGenres: Set<String> = emptySet(),
    onSearchSelected: (() -> Unit)? = null,
    onClearSelected: (() -> Unit)? = null,
    onCopyTitle: (() -> Unit)? = null,
    titleStaggerState: AuroraTitleStaggerState? = null,
    hazeState: HazeState? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val showOriginalTitle by uiPreferences.showOriginalTitle().collectAsState()
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val primaryActionLayoutSpec = remember { resolveAnimeHeroPrimaryActionLayoutSpec() }
    val originalTitle = remember(anime.displayDescription) {
        parseOriginalTitle(anime.displayDescription)
    }
    val heroPanelShape = RoundedCornerShape(24.dp)
    val titleColor = resolveAuroraHeroTitleColor(colors)
    val secondaryMetaColor = resolveAuroraHeroSecondaryMetaColor(colors)

    AuroraHeroScaffold(
        modifier = modifier,
        shape = heroPanelShape,
        hazeState = hazeState,
        bottomPadding = bottomPadding,
    ) {
        AuroraHeroGenreChips(
            genres = anime.displayGenre,
            modifier = Modifier
                .fillMaxWidth()
                .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_GENRES),
            selectedGenres = selectedGenres,
            onGenreClick = onGenreClick,
            onGenreLongClick = onGenreLongClick,
            onSearchSelected = onSearchSelected,
            onClearSelected = onClearSelected,
        )

        val displayTitle = buildAnnotatedString {
            append(translation?.title ?: anime.displayTitle)

            if (showOriginalTitle && translation?.titleTranslated != true && originalTitle != null) {
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

        var titleOverflow by remember { mutableStateOf(false) }
        val showInlineCopyIcon = onCopyTitle != null && !titleOverflow
        val (copyTitleIconId, copyTitleInlineContent) = copyTitleInlineContent(
            onCopyTitle = if (showInlineCopyIcon) onCopyTitle else null,
            tint = secondaryMetaColor,
            contentDescription = stringResource(AYMR.strings.copy_title),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_TITLE),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = buildAnnotatedString {
                    append(displayTitle)
                    if (showInlineCopyIcon) {
                        append(' ')
                        appendInlineContent(copyTitleIconId)
                    }
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = titleColor,
                style = TextStyle(
                    fontFamily = coverTitleFontFamily,
                    lineBreak = LineBreak.Heading,
                    hyphens = Hyphens.None,
                ),
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
                    contentDescription = stringResource(AYMR.strings.copy_title),
                )
            }
        }

        // Author (moved from the stats card onto the poster)
        anime.displayAuthor?.takeIf { it.isNotBlank() }?.let { authorText ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_AUTHOR),
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
            modifier = Modifier
                .fillMaxWidth()
                .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_STATS),
            ratingValue = ratingText,
            secondValue = stringResource(AYMR.strings.aurora_episode_count, episodeCount),
            thirdValue = statusText,
        )

        if (note.isNotBlank()) {
            AuroraNotePreviewCard(
                note = note,
                onClick = onEditNotesClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_NOTE),
            )
        }

        AuroraTitleHeroActionButton(
            hasProgress = hasWatchingProgress,
            onClick = {
                appHaptics.tap()
                onContinueWatching()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_ACTION_BUTTON),
            cornerRadius = 16.dp,
            iconSize = 28.dp,
            contentPadding = PaddingValues(horizontal = 24.dp),
            textSize = 18.sp,
            textWeight = FontWeight.Bold,
        )
    }
}
