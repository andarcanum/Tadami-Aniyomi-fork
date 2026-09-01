package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.entries.components.FinaleStamp
import eu.kanade.presentation.entries.components.aurora.AuroraHeroCoverImage
import eu.kanade.presentation.entries.components.aurora.AuroraNotePreviewCard
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionButton
import eu.kanade.presentation.entries.components.aurora.CopyTitleIcon
import eu.kanade.presentation.entries.components.aurora.GlassmorphismCard
import eu.kanade.presentation.entries.components.aurora.copyTitleInlineContent
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroSecondaryMetaColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroTitleColor
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
import okhttp3.Call
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
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

@Composable
fun AnimeGlassHeroCard(
    anime: Anime,
    translation: AuroraEntryTranslationState? = null,
    hasWatchingProgress: Boolean,
    note: String,
    onEditNotesClicked: (() -> Unit)?,
    onContinueWatching: () -> Unit,
    onCoverClicked: () -> Unit,
    onCopyTitle: (() -> Unit)? = null,
    actionLabel: String? = null,
    showOriginalTitle: Boolean = false,
    episodeCount: Int? = null,
    resolvedCoverUrl: String? = null,
    resolvedCoverUrlFallback: String? = null,
    refererUrl: String? = null,
    sourceHeaders: Map<String, String>? = null,
    sourceClient: Call.Factory? = null,
    showFinishedStamp: Boolean = false,
    finishedStampDate: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val titleColor = resolveAuroraHeroTitleColor(colors)
    val secondaryMetaColor = resolveAuroraHeroSecondaryMetaColor(colors)
    val titleText = translation?.title ?: anime.displayTitle

    var titleOverflow by remember { mutableStateOf(false) }
    val showInlineCopyIcon = onCopyTitle != null && !titleOverflow
    val titleShadow = Shadow(
        color = Color.Black.copy(alpha = 0.92f),
        offset = androidx.compose.ui.geometry.Offset(0f, 2.5f),
        blurRadius = 10f,
    )
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.85f),
        offset = androidx.compose.ui.geometry.Offset(0f, 1.5f),
        blurRadius = 6f,
    )
    val metaTextColor = Color.White.copy(alpha = 0.85f)

    val (copyTitleIconId, copyTitleInlineContent) = copyTitleInlineContent(
        onCopyTitle = if (showInlineCopyIcon) onCopyTitle else null,
        tint = metaTextColor,
        contentDescription = stringResource(AYMR.strings.copy_title),
    )

    val placeholderPainter = rememberAuroraCoverPlaceholderPainter(AuroraCoverPlaceholderVariant.Portrait)
    val coverReloadTick = rememberCoverReloadTick()
    // Same poster resolution logic as the fullscreen background: a user-set
    // custom cover always wins, then the metadata-resolved full cover, then
    // the list thumbnail as fallback.
    val coverCache = remember { Injekt.get<AnimeCoverCache>() }
    val customCoverFile = remember(anime.id, anime.coverLastModified) {
        coverCache.getCustomCoverFile(anime.id).takeIf { it.exists() }
    }
    val posterRequest = remember(
        resolvedCoverUrl,
        resolvedCoverUrlFallback,
        refererUrl,
        sourceHeaders,
        sourceClient,
        anime.thumbnailUrl,
        customCoverFile,
        anime.coverLastModified,
    ) {
        AuroraPosterRequest(
            primaryUrl = resolvedCoverUrl?.takeIf { it.isNotBlank() },
            fallbackUrl = resolvedCoverUrlFallback?.takeIf { it.isNotBlank() } ?: anime.thumbnailUrl,
            refererUrl = refererUrl?.takeIf { it.isNotBlank() },
            headers = sourceHeaders,
            client = sourceClient,
            customCoverFile = customCoverFile,
            coverLastModified = anime.coverLastModified,
        )
    }
    val previewCoverModel = remember(anime.id) {
        AnimeCover(
            animeId = anime.id,
            sourceId = anime.source,
            isAnimeFavorite = anime.favorite,
            url = anime.thumbnailUrl,
            lastModified = anime.coverLastModified,
        )
    }

    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp)
                .clickable {
                    appHaptics.tap()
                    onCoverClicked()
                },
        ) {
            // Full Edge-to-Edge Cover Background inside Card.
            // Two-layer pipeline: instant thumbnail preview + full poster
            // (resolved/custom) fading in, never blank during reloads.
            AuroraHeroCoverImage(
                entryId = anime.id,
                posterRequest = posterRequest,
                previewCoverModel = previewCoverModel,
                placeholderPainter = placeholderPainter,
                reloadTick = coverReloadTick,
                modifier = Modifier.matchParentSize(),
            )

            // Cinematic Gradient Scrim (Clear top, deep dark bottom for crystal clear text readability)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Transparent,
                                0.35f to Color.Transparent,
                                0.65f to Color.Black.copy(alpha = 0.60f),
                                1.00f to Color.Black.copy(alpha = 0.96f),
                            ),
                        ),
                    ),
            )

            // Keepsake «finished» stamp: same double-ring seal as the reader finale plate,
            // pressed onto the top-right corner of the cover. Hidden in RTL (rotated seal).
            if (showFinishedStamp && LocalLayoutDirection.current != LayoutDirection.Rtl) {
                FinaleStamp(
                    label = stringResource(MR.strings.reader_finale_stamp_label),
                    date = finishedStampDate,
                    accent = colors.accent,
                    size = 76.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp),
                )
            }

            // Card Foreground Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Title with inline copy icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append(titleText)
                            if (showInlineCopyIcon) {
                                append(' ')
                                appendInlineContent(copyTitleIconId)
                            }
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 28.sp,
                        maxLines = 3,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = coverTitleFontFamily,
                            shadow = titleShadow,
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
                            tint = metaTextColor,
                            contentDescription = stringResource(AYMR.strings.copy_title),
                        )
                    }
                }

                // Author / Studio / Artist
                val authorText = anime.displayAuthor?.takeIf { it.isNotBlank() }
                if (authorText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonOutline,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = authorText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                            style = TextStyle(shadow = textShadow),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Episode count
                if (episodeCount != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = metaTextColor,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = stringResource(AYMR.strings.aurora_episode_count, episodeCount),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = metaTextColor,
                            style = TextStyle(shadow = textShadow),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Original Title (under author)
                val originalTitle = remember(anime.displayDescription) {
                    parseOriginalTitle(anime.displayDescription)
                }
                if (showOriginalTitle && !originalTitle.isNullOrBlank() && originalTitle != titleText) {
                    Text(
                        text = originalTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = metaTextColor,
                        style = TextStyle(shadow = textShadow),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Optional note preview
                AuroraNotePreviewCard(
                    note = note,
                    onClick = onEditNotesClicked,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Primary CTA Button
                AuroraTitleHeroActionButton(
                    hasProgress = hasWatchingProgress,
                    actionLabel = actionLabel,
                    onClick = {
                        appHaptics.tap()
                        onContinueWatching()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    cornerRadius = 16.dp,
                    iconSize = 26.dp,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    textSize = 16.sp,
                    textWeight = FontWeight.Bold,
                )
            }
        }
    }
}
