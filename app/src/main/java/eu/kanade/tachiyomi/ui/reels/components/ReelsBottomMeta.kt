package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.reels.reelsCreatorLabel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

private val MENTION_REGEX = Regex("""@([A-Za-z0-9_]{1,50})""")
private val META_TEXT_SHADOW = Shadow(color = Color.Black.copy(alpha = 0.75f), offset = Offset(0f, 1f), blurRadius = 4f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReelsBottomMeta(
    item: ShortVideoItem,
    onTagClick: (String) -> Unit,
    // Non-null (capability- and author-gated by the caller) makes the author chip open the
    // creator's page (contract v18).
    onAuthorClick: (() -> Unit)? = null,
    authorDisplayLabel: String? = null,
    onCreatorClick: ((String) -> Unit)? = null,
    showFollowAction: Boolean = false,
    isFollowingCreator: Boolean = false,
    onToggleFollowCreator: () -> Unit = {},
    onMetaStatsClick: (() -> Unit)? = null,
    sourceBadgeName: String? = null,
    sourceBadgeIcon: ImageBitmap? = null,
    onSourceBadgeClick: (() -> Unit)? = null,
    isLandscapeCompact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var isCaptionExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (isLandscapeCompact) {
        ReelsLandscapeCompactMeta(
            item = item,
            isCaptionExpanded = isCaptionExpanded,
            onToggleCaptionExpanded = { isCaptionExpanded = !isCaptionExpanded },
            onTagClick = onTagClick,
            onAuthorClick = onAuthorClick,
            authorDisplayLabel = authorDisplayLabel,
            onCreatorClick = onCreatorClick,
            showFollowAction = showFollowAction,
            isFollowingCreator = isFollowingCreator,
            onToggleFollowCreator = onToggleFollowCreator,
            onMetaStatsClick = onMetaStatsClick,
            sourceBadgeName = sourceBadgeName,
            sourceBadgeIcon = sourceBadgeIcon,
            onSourceBadgeClick = onSourceBadgeClick,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!sourceBadgeName.isNullOrBlank()) {
            val isEInk = AuroraTheme.colors.isEInk
            val pillShape = RoundedCornerShape(999.dp)
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .background(
                        if (isEInk) MaterialTheme.colorScheme.surface else Color.Black.copy(alpha = 0.48f),
                    )
                    .border(
                        width = 1.dp,
                        color = if (isEInk) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            Color.White.copy(alpha = 0.22f)
                        },
                        shape = pillShape,
                    )
                    .then(
                        if (onSourceBadgeClick != null) {
                            Modifier.clickable(onClick = onSourceBadgeClick)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (sourceBadgeIcon != null) {
                    Image(
                        bitmap = sourceBadgeIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape),
                    )
                }
                Text(
                    text = sourceBadgeName,
                    color = if (isEInk) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.92f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onSourceBadgeClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (isEInk) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            Color.White.copy(alpha = 0.72f)
                        },
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        // Author Row (Avatar + Handle share clickable; Follow pill is isolated)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val cleanAuthor = authorDisplayLabel ?: reelsCreatorLabel(null, item.author)
            val initialSource = cleanAuthor?.removePrefix("@") ?: item.title.orEmpty()
            val initial = initialSource.firstOrNull()?.uppercase() ?: "?"
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(
                        onAuthorClick?.let { click ->
                            Modifier.clickable(onClick = click)
                        } ?: Modifier,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(AuroraTheme.colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = cleanAuthor ?: (item.title ?: stringResource(MR.strings.reels_default_video_title)),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = META_TEXT_SHADOW),
                    // Weighted children measure last: a long name yields width to the badge
                    // instead of squeezing it into a one-letter-per-line vertical strip.
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.isAuthorVerified) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = AuroraTheme.colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (showFollowAction) {
                val followShape = RoundedCornerShape(999.dp)
                Box(
                    modifier = Modifier
                        .clip(followShape)
                        .background(
                            if (isFollowingCreator) {
                                AuroraTheme.colors.accent.copy(alpha = 0.22f)
                            } else {
                                Color.Black.copy(alpha = 0.36f)
                            },
                        )
                        .border(
                            1.dp,
                            if (isFollowingCreator) {
                                AuroraTheme.colors.accent.copy(alpha = 0.75f)
                            } else {
                                Color.White.copy(alpha = 0.42f)
                            },
                            followShape,
                        )
                        .clickable(onClick = onToggleFollowCreator)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isFollowingCreator) {
                            stringResource(MR.strings.reels_following)
                        } else {
                            "+ ${stringResource(MR.strings.reels_follow)}"
                        },
                        color = if (isFollowingCreator) AuroraTheme.colors.accent else Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            // "No sound" badge for silent clips
            if (!item.hasAudio) {
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.reels_no_audio),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }

        // Title / Description (with expand/collapse on tap)
        if (!item.title.isNullOrBlank() && item.title != item.author) {
            val titleText = item.title.orEmpty()
            if (onCreatorClick != null && MENTION_REGEX.containsMatchIn(titleText)) {
                val annotatedTitle = buildAnnotatedString {
                    var lastIndex = 0
                    MENTION_REGEX.findAll(titleText).forEach { match ->
                        if (match.range.first > lastIndex) {
                            append(titleText.substring(lastIndex, match.range.first))
                        }
                        val creator = match.groupValues[1]
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = creator,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = AuroraTheme.colors.accent,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                ),
                                linkInteractionListener = { onCreatorClick(creator) },
                            ),
                        ) {
                            append(match.value)
                        }
                        lastIndex = match.range.last + 1
                    }
                    if (lastIndex < titleText.length) {
                        append(titleText.substring(lastIndex))
                    }
                }
                Text(
                    text = annotatedTitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    maxLines = if (isCaptionExpanded) 12 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = META_TEXT_SHADOW),
                    modifier = Modifier.clickable { isCaptionExpanded = !isCaptionExpanded },
                )
            } else {
                Text(
                    text = titleText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    maxLines = if (isCaptionExpanded) 12 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = META_TEXT_SHADOW),
                    modifier = Modifier.clickable { isCaptionExpanded = !isCaptionExpanded },
                )
            }
        }

        // Views count only. The quality/offline pills were dropped (device report): the HD
        // pill always claimed 1080p regardless of the real stream, and the offline state is
        // already reflected by the download action slot.
        item.viewsCount?.let { views ->
            Row(
                modifier = Modifier.then(
                    if (onMetaStatsClick != null) {
                        Modifier.clickable(onClick = onMetaStatsClick)
                    } else {
                        Modifier
                    },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricItem(
                    icon = Icons.Filled.PlayArrow,
                    value = stringResource(MR.strings.reels_views_count, formatCompactCount(views)),
                )
            }
        }

        // Tag Chips
        if (item.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item.tags.take(4).forEach { tag ->
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .clickable { onTagClick(tag) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "#$tag",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReelsLandscapeCompactMeta(
    item: ShortVideoItem,
    isCaptionExpanded: Boolean,
    onToggleCaptionExpanded: () -> Unit,
    onTagClick: (String) -> Unit,
    onAuthorClick: (() -> Unit)?,
    authorDisplayLabel: String?,
    onCreatorClick: ((String) -> Unit)?,
    showFollowAction: Boolean,
    isFollowingCreator: Boolean,
    onToggleFollowCreator: () -> Unit,
    onMetaStatsClick: (() -> Unit)?,
    sourceBadgeName: String?,
    sourceBadgeIcon: ImageBitmap?,
    onSourceBadgeClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    val isEInk = auroraColors.isEInk
    val cleanAuthor = authorDisplayLabel ?: reelsCreatorLabel(null, item.author)
    val initialSource = cleanAuthor?.removePrefix("@") ?: item.title.orEmpty()
    val initial = initialSource.firstOrNull()?.uppercase() ?: "?"
    val authorLabel = cleanAuthor
        ?: (item.title ?: stringResource(MR.strings.reels_default_video_title))

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Line 1: Mini avatar (20dp) + @Author + inline source pill + inline views
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(onAuthorClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(auroraColors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        color = Color.Black,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = authorLabel,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
                if (item.isAuthorVerified) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = auroraColors.accent,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            if (showFollowAction) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isFollowingCreator) {
                                auroraColors.accent.copy(alpha = 0.2f)
                            } else {
                                Color.White.copy(alpha = 0.16f)
                            },
                            CircleShape,
                        )
                        .border(
                            1.dp,
                            if (isFollowingCreator) {
                                auroraColors.accent.copy(alpha = 0.6f)
                            } else {
                                Color.White.copy(alpha = 0.24f)
                            },
                            CircleShape,
                        )
                        .clickable(onClick = onToggleFollowCreator)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isFollowingCreator) "✓" else "+",
                        color = if (isFollowingCreator) auroraColors.accent else Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (!sourceBadgeName.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isEInk) MaterialTheme.colorScheme.surface else Color.White.copy(alpha = 0.14f),
                        )
                        .border(
                            width = 1.dp,
                            color = if (isEInk) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                Color.White.copy(alpha = 0.20f)
                            },
                            shape = CircleShape,
                        )
                        .then(
                            if (onSourceBadgeClick != null) {
                                Modifier.clickable(onClick = onSourceBadgeClick)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (sourceBadgeIcon != null) {
                        Image(
                            bitmap = sourceBadgeIcon,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape),
                        )
                    }
                    Text(
                        text = sourceBadgeName,
                        color = if (isEInk) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 132.dp),
                    )
                    if (onSourceBadgeClick != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = if (isEInk) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                Color.White.copy(alpha = 0.72f)
                            },
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            item.viewsCount?.let { views ->
                Text(
                    text = "· ${formatCompactCount(views)}",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.then(
                        if (onMetaStatsClick != null) {
                            Modifier.clickable(onClick = onMetaStatsClick)
                        } else {
                            Modifier
                        },
                    ),
                )
            }

            if (!item.hasAudio) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = stringResource(MR.strings.reels_no_audio),
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        // Line 2: Compact 1-line title/description (expands up to 3 lines on tap)
        if (!item.title.isNullOrBlank() && item.title != item.author) {
            val titleText = item.title.orEmpty()
            val captionModifier = if (isCaptionExpanded) {
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isEInk) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color(0xFF0C0C12).copy(alpha = 0.82f)
                        },
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.14f),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(onClick = onToggleCaptionExpanded)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            } else {
                Modifier.clickable(onClick = onToggleCaptionExpanded)
            }

            if (onCreatorClick != null && MENTION_REGEX.containsMatchIn(titleText)) {
                val annotatedTitle = buildAnnotatedString {
                    var lastIndex = 0
                    MENTION_REGEX.findAll(titleText).forEach { match ->
                        if (match.range.first > lastIndex) {
                            append(titleText.substring(lastIndex, match.range.first))
                        }
                        val creator = match.groupValues[1]
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = creator,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = auroraColors.accent,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                ),
                                linkInteractionListener = { onCreatorClick(creator) },
                            ),
                        ) {
                            append(match.value)
                        }
                        lastIndex = match.range.last + 1
                    }
                    if (lastIndex < titleText.length) {
                        append(titleText.substring(lastIndex))
                    }
                }
                Text(
                    text = annotatedTitle,
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = if (isCaptionExpanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = captionModifier,
                )
            } else {
                Text(
                    text = titleText,
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = if (isCaptionExpanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = captionModifier,
                )
            }
        }

        if (isCaptionExpanded && item.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                item.tags.take(4).forEach { tag ->
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                            .clickable { onTagClick(tag) }
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "#$tag",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    icon: ImageVector,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(shadow = META_TEXT_SHADOW),
        )
    }
}

/** 1234 -> 1.2K, 1234567 -> 1.2M, etc. */
fun formatCompactCount(value: Long): String {
    // Explicit (default) locale: user-facing compact counts localize the decimal separator
    // intentionally instead of relying on the implicit default.
    val locale = Locale.getDefault()
    return when {
        value >= 1_000_000_000 -> "%.1fB".format(locale, value / 1_000_000_000.0)
        value >= 1_000_000 -> "%.1fM".format(locale, value / 1_000_000.0)
        value >= 1_000 -> "%.1fK".format(locale, value / 1_000.0)
        else -> value.toString()
    }
}
