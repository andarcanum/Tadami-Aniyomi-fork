package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.anime.components.BaseAnimeSourceItem
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.browse.anime.source.AnimeSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.entries.anime.LocalAnimeSource

@Composable
fun AnimeSourcesScreen(
    state: AnimeSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (AnimeSource, Listing) -> Unit,
    onClickPin: (AnimeSource) -> Unit,
    onLongClickItem: (AnimeSource) -> Unit,
    searchQuery: String? = null,
    onChangeSearchQuery: ((String) -> Unit)? = null,
    onToggleLanguage: ((String) -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val searchBackground = if (colors.isDark) {
        colors.glass.copy(alpha = 0.12f)
    } else {
        colors.glass.copy(alpha = 0.03f)
    }
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            stringRes = MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            ScrollbarLazyColumn(
                contentPadding = contentPadding + topSmallPaddingValues,
            ) {
                // Search Bar
                if (searchQuery != null && onChangeSearchQuery != null) {
                    item(key = "search") {
                        var isSearchActive by rememberSaveable { mutableStateOf(false) }
                        val active = isSearchActive || searchQuery.isNotEmpty()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            if (!active) {
                                // Collapsed - Icon Button
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(searchBackground)
                                        .clickable { isSearchActive = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = colors.accent,
                                    )
                                }
                            } else {
                                // Expanded - Beautiful TextField
                                TextField(
                                    value = searchQuery,
                                    onValueChange = onChangeSearchQuery,
                                    placeholder = {
                                        Text(stringResource(MR.strings.action_search), color = colors.textSecondary)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Search,
                                            null,
                                            tint = colors.accent,
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            onChangeSearchQuery("")
                                            isSearchActive = false
                                        }) {
                                            Icon(Icons.Filled.Close, null, tint = colors.textSecondary)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = CircleShape,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = searchBackground,
                                        unfocusedContainerColor = searchBackground,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary,
                                        cursorColor = colors.accent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                    ),
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }

                // Pinned Carousel
                if (state.pinnedItems.isNotEmpty()) {
                    item(key = "pinned-carousel") {
                        Column {
                            Text(
                                text = stringResource(MR.strings.pinned_sources),
                                style = MaterialTheme.typography.header,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.pinnedItems, key = { "pinned-${it.key()}" }) { source ->
                                    AnimeSourceItem(
                                        modifier = Modifier.width(200.dp).animateItem(),
                                        source = source,
                                        onClickItem = onClickItem,
                                        onLongClickItem = onLongClickItem,
                                        onClickPin = onClickPin,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                items(
                    items = state.items,
                    contentType = {
                        when (it) {
                            is AnimeSourceUiModel.Header -> "header"
                            is AnimeSourceUiModel.Item -> "item"
                        }
                    },
                    key = {
                        when (it) {
                            is AnimeSourceUiModel.Header -> it.hashCode()
                            is AnimeSourceUiModel.Item -> "source-${it.source.key()}"
                        }
                    },
                ) { model ->
                    when (model) {
                        is AnimeSourceUiModel.Header -> {
                            AnimeSourceHeader(
                                modifier = Modifier.animateItem(),
                                language = model.language,
                                isCollapsed = model.isCollapsed,
                                onToggle = { onToggleLanguage?.invoke(model.language) },
                            )
                        }
                        is AnimeSourceUiModel.Item -> AnimeSourceItem(
                            modifier = Modifier.animateItem(),
                            source = model.source,
                            onClickItem = onClickItem,
                            onLongClickItem = onLongClickItem,
                            onClickPin = onClickPin,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeSourceHeader(
    language: String,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = LocaleHelper.getSourceDisplayName(language, context),
            style = MaterialTheme.typography.header,
        )
        Icon(
            imageVector = if (isCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnimeSourceItem(
    source: AnimeSource,
    onClickItem: (AnimeSource, Listing) -> Unit,
    onLongClickItem: (AnimeSource) -> Unit,
    onClickPin: (AnimeSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseAnimeSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        action = {
            if (source.supportsLatest) {
                TextButton(onClick = { onClickItem(source, Listing.Latest) }) {
                    Text(
                        text = stringResource(MR.strings.latest),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            AnimeSourcePinButton(
                isPinned = Pin.Pinned in source.pin,
                onClick = { onClickPin(source) },
            )
        },
    )
}

@Composable
private fun AnimeSourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = SECONDARY_ALPHA,
        )
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
fun AnimeSourceOptionsDialog(
    source: AnimeSource,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (source.id != LocalAnimeSource.ID) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed interface AnimeSourceUiModel {
    data class Item(val source: AnimeSource) : AnimeSourceUiModel
    data class Header(val language: String, val isCollapsed: Boolean) : AnimeSourceUiModel
}
