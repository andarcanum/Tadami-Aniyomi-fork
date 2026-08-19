package eu.kanade.presentation.entries.manga.components.aurora

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.rememberCoverReloadTick
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
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
import okhttp3.Call
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
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
fun MangaGlassHeroCard(
    manga: Manga,
    translation: AuroraEntryTranslationState? = null,
    note: String,
    onEditNotesClicked: (() -> Unit)?,
    hasProgress: Boolean,
    onContinueReading: () -> Unit,
    onCoverClicked: () -> Unit,
    onCopyTitle: (() -> Unit)? = null,
    actionLabel: String? = null,
    showOriginalTitle: Boolean = false,
    chapterCount: Int? = null,
    resolvedCoverUrl: String? = null,
    resolvedCoverUrlFallback: String? = null,
    refererUrl: String? = null,
    sourceHeaders: Map<String, String>? = null,
    sourceClient: Call.Factory? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val titleColor = resolveAuroraHeroTitleColor(colors)
    val secondaryMetaColor = resolveAuroraHeroSecondaryMetaColor(colors)
    val titleText = translation?.title ?: manga.displayTitle
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
        contentDescription = stringResource(MR.strings.copy_title),
    )

    val placeholderPainter = rememberAuroraCoverPlaceholderPainter(AuroraCoverPlaceholderVariant.Portrait)
    val coverReloadTick = rememberCoverReloadTick()
    // Same poster resolution logic as the fullscreen background: a user-set
    // custom cover always wins, then the metadata-resolved full cover, then
    // the list thumbnail as fallback.
    val coverCache = remember { Injekt.get<MangaCoverCache>() }
    val customCoverFile = remember(manga.id, manga.coverLastModified) {
        coverCache.getCustomCoverFile(manga.id).takeIf { it.exists() }
    }
    val posterRequest = remember(
        resolvedCoverUrl,
        resolvedCoverUrlFallback,
        refererUrl,
        sourceHeaders,
        sourceClient,
        manga.thumbnailUrl,
        customCoverFile,
        manga.coverLastModified,
    ) {
        AuroraPosterRequest(
            primaryUrl = resolvedCoverUrl?.takeIf { it.isNotBlank() },
            fallbackUrl = resolvedCoverUrlFallback?.takeIf { it.isNotBlank() } ?: manga.thumbnailUrl,
            refererUrl = refererUrl?.takeIf { it.isNotBlank() },
            headers = sourceHeaders,
            client = sourceClient,
            customCoverFile = customCoverFile,
            coverLastModified = manga.coverLastModified,
        )
    }
    val previewCoverModel = remember(manga.id) {
        MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
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
                entryId = manga.id,
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
                            contentDescription = stringResource(MR.strings.copy_title),
                        )
                    }
                }

                // Author / Artist
                val authorText = manga.displayAuthor?.takeIf { it.isNotBlank() }
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

                // Chapter count
                if (chapterCount != null) {
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
                            text = stringResource(AYMR.strings.aurora_chapter_count, chapterCount),
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
                val originalTitle = remember(manga.displayDescription) {
                    parseOriginalTitle(manga.displayDescription)
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
                    hasProgress = hasProgress,
                    actionLabel = actionLabel,
                    onClick = {
                        appHaptics.tap()
                        onContinueReading()
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
