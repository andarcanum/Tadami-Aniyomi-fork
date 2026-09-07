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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.SearchSuggestion
import eu.kanade.tachiyomi.animesource.model.SearchSuggestions
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReelsTopBar(
    sourceName: String,
    sourceIcon: ImageBitmap?,
    searchQuery: String,
    isSearchBarOpen: Boolean,
    isAutoAdvance: Boolean,
    isCropMode: Boolean,
    isHdQuality: Boolean,
    dataSaverEnabled: Boolean,
    preloadEnabled: Boolean,
    preloadWifiOnly: Boolean,
    isOffline: Boolean,
    showSearch: Boolean = true,
    // Real tag list from the source (contract v19 addendum); the static popular list below is
    // the fallback while the source offers no hints.
    searchHints: List<String> = emptyList(),
    // Categorized search hits (contract v20): rendered as tabs under the chips while a
    // query is active; null hides the whole block.
    searchSuggestions: SearchSuggestions? = null,
    showFilter: Boolean = true,
    // Favorites keeps a first-class circle (frequent, one-tap local action) — a hub with a
    // single entry would be pure indirection. Account & personal content (custom feeds,
    // Following) live in the account hub, which always has at least two entries.
    onOpenFavorites: () -> Unit = {},
    onOpenFollows: () -> Unit = {},
    // Account hub (contract v19): identity + personal content. The hub shows the login state,
    // the custom-feeds entry (enabled only while logged in), the followed-creators entry
    // (capability-gated) and login/logout.
    showAccount: Boolean = false,
    isLoggedIn: Boolean = false,
    loggedInAccount: String? = null,
    showCustomFeedsAccountRow: Boolean = false,
    showFollowsAccountRow: Boolean = false,
    // Category browser entry (contract v20): public, not login-gated.
    showNichesAccountRow: Boolean = false,
    // Content-preferences entry (contract v20): enabled only while logged in.
    showContentPrefsAccountRow: Boolean = false,
    onLoginRequest: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenCustomFeeds: () -> Unit = {},
    onOpenNiches: () -> Unit = {},
    onOpenContentPrefs: () -> Unit = {},
    // Creator chrome (contract v18): Follow/Following action on the creator page. The
    // follows-screen entry moved into the library hub.
    showFollowToggle: Boolean = false,
    isFollowingCreator: Boolean = false,
    onToggleFollow: () -> Unit = {},
    showSourcePicker: Boolean,
    onBackClick: () -> Unit,
    onOpenSourcePicker: () -> Unit,
    onToggleAutoAdvance: () -> Unit,
    onToggleCropMode: () -> Unit,
    onToggleQuality: () -> Unit,
    onToggleDataSaver: () -> Unit,
    onTogglePreload: () -> Unit,
    onTogglePreloadWifiOnly: () -> Unit,
    onToggleSearchBar: () -> Unit,
    onOpenFilterDialog: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSuggestionClick: (SearchSuggestion) -> Unit = {},
    onSuggestionsRequest: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var textInput by remember(searchQuery) { mutableStateOf(searchQuery) }

    // Live categorized suggestions while typing (site parity), debounced.
    LaunchedEffect(textInput) {
        if (textInput.isBlank()) {
            onSuggestionsRequest("")
        } else {
            kotlinx.coroutines.delay(400)
            onSuggestionsRequest(textInput)
        }
    }
    var openHub by remember { mutableStateOf(HubMenu.NONE) }

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
            // Left: Back button + Source Title. Weighted (fill=false) so a long title
            // ellipsizes instead of stealing width from the action buttons.
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Back Button — visual circle stays 40dp; the outer 48dp box provides the
                // minimum touch target.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MR.strings.action_bar_up_description),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Source Title Badge (with dropdown if multiple sources available)
                if (sourceName.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .clickable(enabled = showSourcePicker) {
                                onOpenSourcePicker()
                                openHub = HubMenu.NONE
                            }
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
                                // Weighted so the fixed icon/dropdown are measured first and
                                // never squeezed out by a long name.
                                modifier = Modifier.weight(1f, fill = false),
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

            // Right Actions: Search, Filter (online only), Favorites, Follows, Follow, More.
            // Horizontally scrollable: the fixed-size circles must never be squeezed to
            // slivers when the row runs out of width (long creator names, large fonts).
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
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
                            .clickable {
                                onToggleSearchBar()
                                openHub = HubMenu.NONE
                            },
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

                if (!isOffline && showFilter) {
                    // Filter / Sort button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable {
                                onOpenFilterDialog()
                                openHub = HubMenu.NONE
                            },
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

                // Favorites — first-class circle in every mode (frequent local action).
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable {
                            onOpenFavorites()
                            openHub = HubMenu.NONE
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(MR.strings.reels_open_favorites),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Follow / Following toggle on the creator page.
                if (showFollowToggle) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isFollowingCreator) {
                                    AuroraTheme.colors.accent.copy(alpha = 0.35f)
                                } else {
                                    Color.Black.copy(alpha = 0.5f)
                                },
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                if (isFollowingCreator) AuroraTheme.colors.accent else Color.White.copy(alpha = 0.2f),
                                CircleShape,
                            )
                            .clickable {
                                onToggleFollow()
                                openHub = HubMenu.NONE
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isFollowingCreator) Icons.Filled.Person else Icons.Filled.PersonAdd,
                            contentDescription = stringResource(
                                if (isFollowingCreator) MR.strings.reels_following else MR.strings.reels_follow,
                            ),
                            tint = if (isFollowingCreator) AuroraTheme.colors.accent else Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Account hub (contract v19): login state, custom feeds, login/logout.
                if (!isOffline && showAccount) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isLoggedIn) {
                                        AuroraTheme.colors.accent.copy(alpha = 0.35f)
                                    } else {
                                        Color.Black.copy(alpha = 0.5f)
                                    },
                                    CircleShape,
                                )
                                .border(
                                    1.dp,
                                    if (isLoggedIn) AuroraTheme.colors.accent else Color.White.copy(alpha = 0.2f),
                                    CircleShape,
                                )
                                .clickable {
                                    openHub = if (openHub ==
                                        HubMenu.ACCOUNT
                                    ) {
                                        HubMenu.NONE
                                    } else {
                                        HubMenu.ACCOUNT
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = stringResource(MR.strings.reels_account),
                                tint = if (isLoggedIn) AuroraTheme.colors.accent else Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (openHub == HubMenu.ACCOUNT) {
                            ReelsAccountMenu(
                                isLoggedIn = isLoggedIn,
                                loggedInAccount = loggedInAccount,
                                showCustomFeedsRow = showCustomFeedsAccountRow,
                                showFollowsRow = showFollowsAccountRow,
                                showNichesRow = showNichesAccountRow,
                                showContentPrefsRow = showContentPrefsAccountRow,
                                onLoginRequest = onLoginRequest,
                                onLogout = onLogout,
                                onOpenCustomFeeds = onOpenCustomFeeds,
                                onOpenNiches = onOpenNiches,
                                onOpenContentPrefs = onOpenContentPrefs,
                                onOpenFollows = onOpenFollows,
                                onDismiss = { openHub = HubMenu.NONE },
                            )
                        }
                    }
                }

                // More (settings: auto-advance, aspect, quality)
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (openHub == HubMenu.MORE) {
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
                                if (openHub ==
                                    HubMenu.MORE
                                ) {
                                    AuroraTheme.colors.accent
                                } else {
                                    Color.White.copy(alpha = 0.2f)
                                },
                                CircleShape,
                            )
                            .clickable { openHub = if (openHub == HubMenu.MORE) HubMenu.NONE else HubMenu.MORE },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(MR.strings.reels_more),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (openHub == HubMenu.MORE) {
                        ReelsMoreMenu(
                            isAutoAdvance = isAutoAdvance,
                            isCropMode = isCropMode,
                            isHdQuality = isHdQuality,
                            dataSaverEnabled = dataSaverEnabled,
                            preloadEnabled = preloadEnabled,
                            preloadWifiOnly = preloadWifiOnly,
                            onToggleAutoAdvance = {
                                onToggleAutoAdvance()
                                openHub = HubMenu.NONE
                            },
                            onToggleCropMode = {
                                onToggleCropMode()
                                openHub = HubMenu.NONE
                            },
                            onToggleQuality = {
                                onToggleQuality()
                                openHub = HubMenu.NONE
                            },
                            onToggleDataSaver = {
                                onToggleDataSaver()
                                openHub = HubMenu.NONE
                            },
                            onTogglePreload = {
                                onTogglePreload()
                                openHub = HubMenu.NONE
                            },
                            onTogglePreloadWifiOnly = {
                                onTogglePreloadWifiOnly()
                                openHub = HubMenu.NONE
                            },
                            onDismiss = { openHub = HubMenu.NONE },
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
                    // Source-provided chips only: the static fallback list was removed (the source's own
                    // hints are authoritative; no chips are shown when it supplies none).
                    searchHints.forEach { tag ->
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

                // Categorized search tabs (contract v20): niches/creators/tags with previews — live
                // while typing (site parity), not only after submit.
                if (searchSuggestions != null && textInput.isNotBlank()) {
                    SearchSuggestionTabs(
                        suggestions = searchSuggestions,
                        onSuggestionClick = onSuggestionClick,
                    )
                }
            }
        }
    }
}

/**
 * Categorized search hits (contract v20): site-like tabs (Niches/Creators/Tags) with preview
 * rows; empty sections are hidden. Taps route through [onSuggestionClick].
 */
@Composable
private fun SearchSuggestionTabs(
    suggestions: SearchSuggestions,
    onSuggestionClick: (SearchSuggestion) -> Unit,
) {
    val tabs = listOf(
        Pair(MR.strings.reels_search_tab_niches, suggestions.niches),
        Pair(MR.strings.reels_search_tab_creators, suggestions.creators),
        Pair(MR.strings.reels_search_tab_tags, suggestions.tags),
    ).filter { it.second.isNotEmpty() }
    if (tabs.isEmpty()) return
    var active by remember(tabs.size) { mutableStateOf(0) }
    val activeIndex = active.coerceIn(0, tabs.size - 1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            Box(
                modifier = Modifier
                    .background(
                        if (index == activeIndex) {
                            AuroraTheme.colors.accent.copy(alpha = 0.3f)
                        } else {
                            Color.White.copy(alpha = 0.1f)
                        },
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { active = index }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "${stringResource(tab.first)} (${tab.second.size})",
                    color = if (index == activeIndex) AuroraTheme.colors.accent else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
    ) {
        items(tabs[activeIndex].second, key = { it.kind.name + ":" + it.id }) { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSuggestionClick(suggestion) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (suggestion.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(suggestion.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Tag,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp),
                    )
                }
                Column {
                    Text(
                        text = suggestion.label,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = suggestion.subtitle
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
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
    dataSaverEnabled: Boolean,
    preloadEnabled: Boolean,
    preloadWifiOnly: Boolean,
    onToggleAutoAdvance: () -> Unit,
    onToggleCropMode: () -> Unit,
    onToggleQuality: () -> Unit,
    onToggleDataSaver: () -> Unit,
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
            MoreMenuRow(
                icon = {
                    Icon(
                        Icons.Filled.DataSaverOn,
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = stringResource(MR.strings.reels_data_saver_metered),
                value = null,
                checked = dataSaverEnabled,
                onClick = onToggleDataSaver,
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

/** Which popup is open in the top bar; only one at a time. */
private enum class HubMenu { NONE, MORE, ACCOUNT }

/**
 * Identity + personal-content popup (contract v19) anchored under the account button:
 * login state, the custom-feeds entry (enabled only while logged in), the followed-creators
 * entry (capability-gated) and the login/logout action. Always at least two rows, so the
 * hub never becomes a one-entry indirection.
 */
@Composable
private fun ReelsAccountMenu(
    isLoggedIn: Boolean,
    loggedInAccount: String?,
    showCustomFeedsRow: Boolean,
    showFollowsRow: Boolean,
    showNichesRow: Boolean,
    showContentPrefsRow: Boolean,
    onLoginRequest: () -> Unit,
    onLogout: () -> Unit,
    onOpenCustomFeeds: () -> Unit,
    onOpenNiches: () -> Unit,
    onOpenContentPrefs: () -> Unit,
    onOpenFollows: () -> Unit,
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
            if (isLoggedIn && loggedInAccount != null) {
                Text(
                    text = stringResource(MR.strings.reels_logged_in_as, loggedInAccount),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            if (showCustomFeedsRow) {
                HubMenuRow(
                    icon = { HubMenuIcon(Icons.AutoMirrored.Filled.List) },
                    label = stringResource(MR.strings.reels_custom_feeds),
                    enabled = isLoggedIn,
                ) {
                    if (isLoggedIn) {
                        onOpenCustomFeeds()
                        onDismiss()
                    }
                }
            }
            if (showFollowsRow) {
                HubMenuRow(
                    icon = { HubMenuIcon(Icons.Outlined.Subscriptions) },
                    label = stringResource(MR.strings.reels_following_feed),
                ) {
                    onOpenFollows()
                    onDismiss()
                }
            }
            if (showNichesRow) {
                HubMenuRow(
                    icon = { HubMenuIcon(Icons.Outlined.GridView) },
                    label = stringResource(MR.strings.reels_niches),
                ) {
                    onOpenNiches()
                    onDismiss()
                }
            }
            if (showContentPrefsRow) {
                HubMenuRow(
                    icon = { HubMenuIcon(Icons.Outlined.Tune) },
                    label = stringResource(MR.strings.reels_content_prefs),
                    enabled = isLoggedIn,
                ) {
                    if (isLoggedIn) {
                        onOpenContentPrefs()
                        onDismiss()
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f)),
            )
            if (isLoggedIn) {
                HubMenuRow(
                    icon = { HubMenuIcon(Icons.Filled.Person) },
                    label = stringResource(MR.strings.reels_logout),
                ) {
                    onLogout()
                    onDismiss()
                }
            } else {
                HubMenuRow(
                    icon = { HubMenuIcon(Icons.Filled.Person) },
                    label = stringResource(MR.strings.reels_login),
                ) {
                    onLoginRequest()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun HubMenuIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.8f),
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun HubMenuRow(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
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
    }
}
