package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val HeartColor = Color(0xFFFF3366)

@Composable
fun ReelsActionsColumn(
    isLiked: Boolean,
    isMuted: Boolean,
    onToggleLike: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: () -> Unit,
    // Creator follow slot (contract v18): shown by the caller only when the source is
    // AnimeCreatorFeedSource and the item has an author.
    showFollow: Boolean = false,
    isFollowing: Boolean = false,
    onToggleFollow: () -> Unit = {},
    // B3.2 "more / hide" slot: opens the dialog trigger; null hides the slot.
    onOpenHide: (() -> Unit)? = null,
    // B3.3 offline-copy slot (offline playlist only): null hides the slot.
    onToggleOffline: (() -> Unit)? = null,
    isOfflineStored: Boolean = false,
    offlineSizeText: String? = null,
    // Native window orientation toggle (U01): the thumb reaches the action rail, not the top
    // bar, and it sits right after Like so it reads as a view mode, not as a media action.
    landscape: Boolean = false,
    onToggleLandscape: () -> Unit = {},
    // A rotated window has width but not enough height for the portrait column: the same
    // slots are laid out in one horizontal row.
    isLandscapeLayout: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scaleHeart by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heart_scale",
    )
    val scalePerson by animateFloatAsState(
        targetValue = if (isFollowing) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "follow_scale",
    )

    val showLabels = !isLandscapeLayout

    @Composable
    fun LikeItem() {
        ActionIconItem(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = if (isLiked) "unlike" else "like",
            tint = if (isLiked) HeartColor else Color.White,
            modifier = Modifier.scale(scaleHeart),
            stateDescription = stringResource(
                if (isLiked) MR.strings.reels_feed_action_liked else MR.strings.reels_feed_action_like,
            ),
            showLabel = showLabels,
            onClick = onToggleLike,
        )
    }

    @Composable
    fun LandscapeItem() {
        ActionIconItem(
            icon = if (landscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            label = "full",
            stateDescription = stringResource(
                if (landscape) MR.strings.reels_landscape_exit else MR.strings.reels_landscape_fullscreen,
            ),
            tint = if (landscape) AuroraTheme.colors.accent else Color.White,
            showLabel = showLabels,
            onClick = onToggleLandscape,
        )
    }

    @Composable
    fun FollowItem() {
        // Creator Follow (v18)
        if (showFollow) {
            ActionIconItem(
                icon = if (isFollowing) Icons.Filled.Person else Icons.Filled.PersonAdd,
                label = if (isFollowing) {
                    stringResource(MR.strings.reels_following)
                } else {
                    stringResource(MR.strings.reels_follow)
                },
                tint = if (isFollowing) AuroraTheme.colors.accent else Color.White,
                modifier = Modifier.scale(scalePerson),
                showLabel = showLabels,
                onClick = onToggleFollow,
            )
        }
    }

    @Composable
    fun SoundItem() {
        ActionIconItem(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            label = if (isMuted) "unmute" else "mute",
            // TalkBack needs the CURRENT state, not only a static label.
            stateDescription = stringResource(
                if (isMuted) MR.strings.reels_sound_off else MR.strings.reels_sound_on,
            ),
            tint = Color.White,
            showLabel = showLabels,
            onClick = onToggleMute,
        )
    }

    @Composable
    fun ShareItem() {
        ActionIconItem(
            icon = Icons.Filled.Share,
            label = "share",
            tint = Color.White,
            showLabel = showLabels,
            onClick = onShare,
        )
    }

    @Composable
    fun HideItem() {
        // B3.2 "more / hide" entry: opens the caller's choice dialog.
        if (onOpenHide != null) {
            ActionIconItem(
                icon = Icons.Filled.MoreVert,
                label = stringResource(MR.strings.reels_hide_menu),
                tint = Color.White,
                showLabel = showLabels,
                onClick = onOpenHide,
            )
        }
    }

    @Composable
    fun OfflineItem() {
        // B3.3 offline copy: download / remove the real local file.
        if (onToggleOffline != null) {
            val offlineLabel = resolveOfflineActionLabel(isOfflineStored, offlineSizeText)
            ActionIconItem(
                icon = if (isOfflineStored) Icons.Filled.OfflinePin else Icons.Outlined.Download,
                label = offlineLabel,
                tint = if (isOfflineStored) AuroraTheme.colors.accent else Color.White,
                stateDescription = stringResource(
                    if (isOfflineStored) MR.strings.reels_offline_saved else MR.strings.reels_offline_save,
                ),
                showLabel = showLabels,
                onClick = onToggleOffline,
            )
        }
    }

    if (isLandscapeLayout) {
        // A rotated window has no height for the stacked column and no room for captions:
        // the playback actions stay under the thumb, the contextual ones ride above them.
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            val hasContextual = showFollow || onOpenHide != null || onToggleOffline != null
            if (hasContextual) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FollowItem()
                    HideItem()
                    OfflineItem()
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LikeItem()
                LandscapeItem()
                SoundItem()
                ShareItem()
            }
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LikeItem()
            LandscapeItem()
            FollowItem()
            ShareItem()
            OfflineItem()
            SoundItem()
        }
    }
}

@Composable
private fun ActionIconItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
    showLabel: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier,
    ) {
        // Visual circle matches the reels top-bar buttons (36dp/18dp); the outer box keeps
        // a 44dp touch target — the column sits against the screen edge.
        Box(
            modifier = Modifier
                .size(44.dp)
                .semantics {
                    role = Role.Button
                    if (stateDescription != null) this.stateDescription = stateDescription
                }
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showLabel) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

internal fun resolveOfflineActionLabel(isOfflineStored: Boolean, offlineSizeText: String?): String =
    when {
        isOfflineStored -> "✓"
        !offlineSizeText.isNullOrBlank() -> offlineSizeText
        else -> "24 МБ"
    }
