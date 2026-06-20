package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.entries.components.aurora.AuroraHeroGenreChips
import eu.kanade.presentation.entries.components.aurora.AuroraHeroScaffold
import eu.kanade.presentation.entries.components.aurora.AuroraHeroStatsRow
import eu.kanade.presentation.entries.components.aurora.AuroraNotePreviewCard
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionButton
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroSecondaryButtonPalette
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroSecondaryMetaColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroTitleColor
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
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
    ) {
        AuroraHeroGenreChips(
            genres = anime.displayGenre,
            modifier = Modifier.fillMaxWidth(),
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

        Text(
            text = displayTitle,
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
        )

        AuroraHeroStatsRow(
            modifier = Modifier.fillMaxWidth(),
            ratingValue = ratingText,
            secondValue = stringResource(AYMR.strings.aurora_episode_count, episodeCount),
            thirdValue = statusText,
        )

        AuroraNotePreviewCard(
            note = note,
            onClick = onEditNotesClicked,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AuroraTitleHeroActionButton(
                hasProgress = hasWatchingProgress,
                onClick = {
                    appHaptics.tap()
                    onContinueWatching()
                },
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
                        .clickable {
                            appHaptics.tap()
                            onDubbingClicked()
                        }
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
