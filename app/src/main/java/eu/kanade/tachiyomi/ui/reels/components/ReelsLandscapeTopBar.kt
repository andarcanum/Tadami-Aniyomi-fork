package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Top Navigation Bar for native landscape reels (Variant V1).
 *
 * Uses 48dp minimum touch targets around compact 38dp glass controls arranged in a
 * two-sided header:
 * - Left: Back, Source switcher pill (or unified Source/Feed pill), Feed section pill,
 *   active `#tag` clear chip, or inline search input when expanded.
 * - Right: Search toggle (if supported), Settings (opens Aurora Frost Drawer), Exit landscape
 *   (when wired), and Clear Display.
 */
@Suppress("UNUSED_PARAMETER")
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
    showSearch: Boolean = false,
    isSearchOpen: Boolean = false,
    onToggleSearch: () -> Unit = {},
    searchQuery: String = "",
    onSearchSubmit: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onOpenSourcePicker: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onExitLandscape: (() -> Unit)? = null,
    feedSectionLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    val contentTint = if (auroraColors.isEInk) MaterialTheme.colorScheme.onSurface else Color.White
    val sectionTitle = feedSectionLabel ?: stringResource(MR.strings.reels_menu_section_for_you)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Group: Back + Source / Feed Pills (or Inline Search Field)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            LandscapeTopCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(MR.strings.action_bar_up_description),
                tint = contentTint,
                iconSize = 24.dp,
                onClick = onBackClick,
            )

            if (showSearch && isSearchOpen) {
                LandscapeInlineSearchCluster(
                    searchQuery = searchQuery,
                    contentTint = contentTint,
                    onSearchSubmit = onSearchSubmit,
                    onClearSearch = onClearSearch,
                    onCloseSearch = onToggleSearch,
                    modifier = Modifier.weight(1f),
                )
            } else {
                if (onOpenSourcePicker != null) {
                    LandscapeTopPill(
                        contentDescription = stringResource(MR.strings.reels_switch_source),
                        onClick = onOpenSourcePicker,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        ActiveSourceDot()
                        Text(
                            text = sourceName.ifBlank { sectionTitle },
                            color = contentTint,
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
                            tint = contentTint.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    LandscapeTopPill(
                        onClick = onOpenPlaylist,
                    ) {
                        Text(
                            text = sectionTitle,
                            color = contentTint.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = contentTint.copy(alpha = 0.75f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                } else {
                    LandscapeTopPill(
                        onClick = onOpenPlaylist,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        ActiveSourceDot()
                        Text(
                            text = if (sourceName.isNotBlank()) "$sourceName • $sectionTitle" else sectionTitle,
                            color = contentTint,
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
                            tint = contentTint.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                if (searchQuery.isNotBlank()) {
                    LandscapeTopPill(
                        active = true,
                        contentDescription = stringResource(MR.strings.action_clear),
                        onClick = onClearSearch,
                        modifier = Modifier.widthIn(max = 156.dp),
                    ) {
                        Text(
                            text = "#$searchQuery",
                            color = AuroraTheme.colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = AuroraTheme.colors.accent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // Right Group: Search, Settings, Portrait Exit, Clear Display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showSearch) {
                LandscapeTopCircleButton(
                    icon = Icons.Filled.Search,
                    contentDescription = stringResource(MR.strings.action_search),
                    isActive = isSearchOpen,
                    tint = if (isSearchOpen) auroraColors.accent else contentTint,
                    onClick = onToggleSearch,
                )
            }

            LandscapeTopCircleButton(
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(MR.strings.action_settings),
                tint = contentTint,
                onClick = onOpenSettings ?: onOpenPlaylist,
            )

            if (onExitLandscape != null) {
                LandscapeTopCircleButton(
                    icon = Icons.Filled.StayCurrentPortrait,
                    contentDescription = stringResource(MR.strings.reels_landscape_exit),
                    tint = contentTint,
                    onClick = onExitLandscape,
                )
            }

            LandscapeTopCircleButton(
                icon = Icons.Filled.VisibilityOff,
                contentDescription = stringResource(MR.strings.reels_clear_display),
                tint = contentTint,
                onClick = onClearDisplay,
            )
        }
    }
}

@Composable
private fun ActiveSourceDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color(0xFF10B981)),
    )
}

@Composable
private fun LandscapeTopCircleButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    isActive: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val auroraColors = AuroraTheme.colors
    val isEInk = auroraColors.isEInk
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isEInk) {
            Box(
                modifier = Modifier
                    .size(REELS_LANDSCAPE_CONTROL_VISUAL_HEIGHT_DP.dp)
                    .reelsGlassSurface(
                        active = isActive,
                        eInk = true,
                        eInkColor = auroraColors.cardBackground,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            ReelsChromeIcon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                size = iconSize,
                eInk = false,
            )
        }
    }
}

@Composable
private fun LandscapeTopPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    contentDescription: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val auroraColors = AuroraTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .reelsGlassSurface(
                    shape = CircleShape,
                    active = active,
                    eInk = auroraColors.isEInk,
                    eInkColor = auroraColors.cardBackground,
                    subtle = true,
                )
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun LandscapeInlineSearchCluster(
    searchQuery: String,
    contentTint: Color,
    onSearchSubmit: (String) -> Unit,
    onClearSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var textInput by remember(searchQuery) { mutableStateOf(searchQuery) }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Row(
        modifier = modifier.widthIn(max = 420.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(REELS_LANDSCAPE_CONTROL_VISUAL_HEIGHT_DP.dp)
                    .reelsGlassSurface(
                        shape = CircleShape,
                        active = true,
                        eInk = auroraColors.isEInk,
                        eInkColor = auroraColors.cardBackground,
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = auroraColors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (textInput.isEmpty()) {
                        Text(
                            text = stringResource(MR.strings.reels_search_hint),
                            color = contentTint.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = contentTint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(auroraColors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                onSearchSubmit(textInput.trim())
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
            }
        }

        LandscapeTopCircleButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(
                if (textInput.isNotEmpty()) MR.strings.action_clear else MR.strings.action_close,
            ),
            tint = contentTint,
            onClick = {
                if (textInput.isNotEmpty()) {
                    textInput = ""
                    onClearSearch()
                } else {
                    focusManager.clearFocus()
                    onCloseSearch()
                }
            },
        )
    }
}
