package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val POPULAR_REELS_TAGS = listOf(
    "cosplay",
    "anime",
    "dance",
    "3d",
    "gaming",
    "music",
    "art",
    "kpop",
    "fitness",
    "funny",
)

@Composable
fun ReelsTopBar(
    sourceName: String,
    sourceIcon: ImageBitmap?,
    searchQuery: String,
    isSearchBarOpen: Boolean,
    isAutoAdvance: Boolean,
    isCropMode: Boolean,
    isHdQuality: Boolean,
    preloadEnabled: Boolean,
    preloadWifiOnly: Boolean,
    isOffline: Boolean,
    showSearch: Boolean = true,
    showSourcePicker: Boolean,
    onBackClick: () -> Unit,
    onOpenSourcePicker: () -> Unit,
    onToggleAutoAdvance: () -> Unit,
    onToggleCropMode: () -> Unit,
    onToggleQuality: () -> Unit,
    onTogglePreload: () -> Unit,
    onTogglePreloadWifiOnly: () -> Unit,
    onToggleSearchBar: () -> Unit,
    onOpenFilterDialog: () -> Unit,
    onOpenFavorites: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var textInput by remember(searchQuery) { mutableStateOf(searchQuery) }
    var moreMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Main Navigation & Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Back button + Source Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(MR.strings.action_bar_up_description),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Source Title Badge (with dropdown if multiple sources available)
                if (sourceName.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .clickable(enabled = showSourcePicker) { onOpenSourcePicker() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (sourceIcon != null) {
                                Image(
                                    bitmap = sourceIcon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                            }
                            Text(
                                text = sourceName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (showSourcePicker) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = stringResource(MR.strings.reels_switch_source),
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Right Actions: Search, Filter (online only), Favorites, More
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!isOffline && showSearch) {
                    // Search toggle button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isSearchBarOpen) {
                                    AuroraTheme.colors.accent.copy(alpha = 0.35f)
                                } else {
                                    Color.Black.copy(alpha = 0.5f)
                                },
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                if (isSearchBarOpen) AuroraTheme.colors.accent else Color.White.copy(alpha = 0.2f),
                                CircleShape,
                            )
                            .clickable { onToggleSearchBar() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(MR.strings.action_search),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (!isOffline) {
                    // Filter / Sort button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable { onOpenFilterDialog() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = stringResource(MR.strings.action_filter),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Favorites
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable { onOpenFavorites() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(MR.strings.reels_open_favorites),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // More (settings: auto-advance, aspect, quality)
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (moreMenuOpen) {
                                    AuroraTheme.colors.accent.copy(
                                        alpha = 0.35f,
                                    )
                                } else {
                                    Color.Black.copy(alpha = 0.5f)
                                },
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                if (moreMenuOpen) AuroraTheme.colors.accent else Color.White.copy(alpha = 0.2f),
                                CircleShape,
                            )
                            .clickable { moreMenuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(MR.strings.reels_more),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (moreMenuOpen) {
                        ReelsMoreMenu(
                            isAutoAdvance = isAutoAdvance,
                            isCropMode = isCropMode,
                            isHdQuality = isHdQuality,
                            preloadEnabled = preloadEnabled,
                            preloadWifiOnly = preloadWifiOnly,
                            onToggleAutoAdvance = {
                                onToggleAutoAdvance()
                                moreMenuOpen = false
                            },
                            onToggleCropMode = {
                                onToggleCropMode()
                                moreMenuOpen = false
                            },
                            onToggleQuality = {
                                onToggleQuality()
                                moreMenuOpen = false
                            },
                            onTogglePreload = {
                                onTogglePreload()
                                moreMenuOpen = false
                            },
                            onTogglePreloadWifiOnly = {
                                onTogglePreloadWifiOnly()
                                moreMenuOpen = false
                            },
                            onDismiss = { moreMenuOpen = false },
                        )
                    }
                }
            }
        }

        // Active Filter / Tag badge (Dedicated sub-row so it never clips)
        AnimatedVisibility(
            visible = searchQuery.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Start,
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .border(1.dp, AuroraTheme.colors.accent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "#$searchQuery",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(MR.strings.action_clear),
                            tint = AuroraTheme.colors.accent,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onClearSearch() },
                        )
                    }
                }
            }
        }

        // Expandable Search & Popular Tags Bar
        AnimatedVisibility(
            visible = isSearchBarOpen && showSearch,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Search Input Field
                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = stringResource(MR.strings.reels_search_hint),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onSearch(textInput)
                        },
                    ),
                    trailingIcon = {
                        if (textInput.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(MR.strings.action_clear),
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        textInput = ""
                                        onClearSearch()
                                    },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Quick Popular Tag Chips Scroll
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    POPULAR_REELS_TAGS.forEach { tag ->
                        val isSelected = searchQuery.equals(tag, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) {
                                        AuroraTheme.colors.accent.copy(alpha = 0.3f)
                                    } else {
                                        Color.White.copy(alpha = 0.1f)
                                    },
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) AuroraTheme.colors.accent else Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    textInput = tag
                                    focusManager.clearFocus()
                                    onSearch(tag)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "#$tag",
                                color = if (isSelected) AuroraTheme.colors.accent else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact settings popup (auto-advance / aspect / quality / preload) anchored under the
 * "more" button.
 */
@Composable
private fun ReelsMoreMenu(
    isAutoAdvance: Boolean,
    isCropMode: Boolean,
    isHdQuality: Boolean,
    preloadEnabled: Boolean,
    preloadWifiOnly: Boolean,
    onToggleAutoAdvance: () -> Unit,
    onToggleCropMode: () -> Unit,
    onToggleQuality: () -> Unit,
    onTogglePreload: () -> Unit,
    onTogglePreloadWifiOnly: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(x = 0, y = 44.dp.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MoreMenuRow(
                icon = {
                    Icon(
                        Icons.Filled.Autorenew,
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = stringResource(MR.strings.reels_auto_advance),
                value = null,
                checked = isAutoAdvance,
                onClick = onToggleAutoAdvance,
            )
            MoreMenuRow(
                icon = {
                    Icon(
                        Icons.Outlined.AspectRatio,
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = stringResource(MR.strings.reels_aspect),
                value = stringResource(if (isCropMode) MR.strings.reels_aspect_fill else MR.strings.reels_aspect_fit),
                checked = null,
                onClick = onToggleCropMode,
            )
            MoreMenuRow(
                icon = {
                    Icon(
                        Icons.Outlined.HighQuality,
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = stringResource(MR.strings.reels_quality),
                value = stringResource(if (isHdQuality) MR.strings.reels_quality_hd else MR.strings.reels_quality_sd),
                checked = null,
                onClick = onToggleQuality,
            )
            MoreMenuRow(
                icon = {
                    Icon(
                        Icons.Filled.Download,
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = stringResource(MR.strings.reels_preload),
                value = null,
                checked = preloadEnabled,
                onClick = onTogglePreload,
            )
            MoreMenuRow(
                icon = {
                    Icon(
                        Icons.Filled.Wifi,
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = stringResource(MR.strings.reels_preload_wifi_only),
                value = null,
                checked = preloadWifiOnly,
                onClick = onTogglePreloadWifiOnly,
            )
        }
    }
}

@Composable
private fun MoreMenuRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String?,
    checked: Boolean?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (checked != null) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = AuroraTheme.colors.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            Text(
                text = value.orEmpty(),
                color = AuroraTheme.colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
