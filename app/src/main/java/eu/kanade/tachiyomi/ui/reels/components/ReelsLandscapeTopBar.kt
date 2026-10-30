package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Top Navigation Bar for native landscape reels (Prototype v2).
 *
 * Left: Back button, Source pill (opens the settings/playlist drawer) and Clear display
 * toggle — the quick controls sit right after the plugin name. No separate settings button:
 * the source pill already opens the drawer (device report: duplicate entry points).
 */
@Composable
fun ReelsLandscapeTopBar(
    sourceName: String,
    onBackClick: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onClearDisplay: () -> Unit,
    playbackSpeed: Float = 1f,
    isCropMode: Boolean = false,
    onCycleSpeed: () -> Unit = {},
    onToggleCropMode: () -> Unit = {},
    // Search parity with portrait: landscape used to have no way to open the search bar at all.
    showSearch: Boolean = false,
    isSearchOpen: Boolean = false,
    onToggleSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Group: Back Button + Source / Playlist Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            // Disc Back Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .semantics { role = Role.Button }
                    .clip(CircleShape)
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .reelsGlassSurface(
                            eInk = AuroraTheme.colors.isEInk,
                            eInkColor = AuroraTheme.colors.cardBackground,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(MR.strings.action_bar_up_description),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Source Pill (Clickable -> Opens Playlist Tab in Aurora Frost Drawer)
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenPlaylist),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .reelsGlassSurface(
                            CircleShape,
                            eInk = AuroraTheme.colors.isEInk,
                            eInkColor = AuroraTheme.colors.cardBackground,
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    // Active Green Dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)),
                    )
                    val sectionTitle = stringResource(MR.strings.reels_menu_section_for_you)
                    Text(
                        text = if (sourceName.isNotBlank()) "$sourceName • $sectionTitle" else sectionTitle,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showSearch) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .semantics { role = Role.Button }
                            .clip(CircleShape)
                            .clickable(onClick = onToggleSearch),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .reelsGlassSurface(
                                    active = isSearchOpen,
                                    eInk = AuroraTheme.colors.isEInk,
                                    eInkColor = AuroraTheme.colors.cardBackground,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(MR.strings.action_search),
                                tint = if (isSearchOpen) AuroraTheme.colors.accent else Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Clear Display (Whisper / Clean Viewing Mode): hides the chrome WITHOUT
                // pausing — the only path to a clean screen now that tap pauses playback.
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { role = Role.Button }
                        .clip(CircleShape)
                        .clickable(onClick = onClearDisplay),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .reelsGlassSurface(
                                eInk = AuroraTheme.colors.isEInk,
                                eInkColor = AuroraTheme.colors.cardBackground,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = stringResource(MR.strings.reels_clear_display),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
