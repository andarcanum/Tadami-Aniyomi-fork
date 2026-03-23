package eu.kanade.presentation.library.novel

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.kanade.presentation.components.AuroraCard
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.LocalTabState
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.resolveAuroraCardOverlaySpec
import eu.kanade.presentation.components.resolveAuroraCoverModel
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.components.GlowContourLibraryGridItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.resolveGlowContourCornerIndicatorState
import eu.kanade.presentation.library.components.resolveGlowContourLibraryTextSpec
import eu.kanade.presentation.library.resolveNovelLibraryCardProgressPercent
import eu.kanade.presentation.novel.sourceAwareNovelCoverModel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.library.model.AuroraLibraryCardStyle
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems

@Composable
fun NovelLibraryAuroraContent(
    items: List<LibraryNovel>,
    selection: List<LibraryNovel> = emptyList(),
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    onNovelClicked: (Long) -> Unit,
    onToggleSelection: ((LibraryNovel) -> Unit)? = null,
    onToggleRangeSelection: ((LibraryNovel) -> Unit)? = null,
    contentPadding: PaddingValues,
    hasActiveFilters: Boolean,
    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onGlobalUpdate: () -> Unit,
    onOpenRandomEntry: () -> Unit,
    onLongClickNovel: ((LibraryNovel) -> Unit)? = null,
    onContinueReadingClicked: ((LibraryNovel) -> Unit)? = null,
    showInlineHeader: Boolean = true,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val sourceManager = remember { Injekt.get<NovelSourceManager>() }
    val downloadCache = remember { Injekt.get<NovelDownloadCache>() }
    val useSeparateDisplayModePerMedia by libraryPreferences
        .separateDisplayModePerMedia()
        .collectAsState()
    val displayModePreference = remember(useSeparateDisplayModePerMedia) {
        if (useSeparateDisplayModePerMedia) {
            libraryPreferences.novelDisplayMode()
        } else {
            libraryPreferences.displayMode()
        }
    }
    val displayMode by displayModePreference.collectAsState()
    val columnPreference = remember(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            libraryPreferences.novelLandscapeColumns()
        } else {
            libraryPreferences.novelPortraitColumns()
        }
    }
    val columns by columnPreference.collectAsState()
    val auroraCardStyle by libraryPreferences.auroraLibraryCardStyle().collectAsState()
    val useGlowContourCards = auroraCardStyle == AuroraLibraryCardStyle.GlowContour
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val displaySpec = remember(displayMode, columns, auroraAdaptiveSpec) {
        resolveNovelLibraryAuroraDisplaySpec(
            displayMode = displayMode,
            columns = columns,
            auroraAdaptiveSpec = auroraAdaptiveSpec,
        )
    }
    val showDownloadBadge by libraryPreferences.downloadBadge().collectAsState()
    val showUnreadBadge by libraryPreferences.unreadBadge().collectAsState()
    val showLanguageBadge by libraryPreferences.languageBadge().collectAsState()
    val downloadCacheSignal by downloadCache.changes.collectAsStateWithLifecycle(initialValue = Unit)
    val downloadedNovelIds = remember(items, showDownloadBadge, downloadCacheSignal) {
        if (!showDownloadBadge) return@remember emptySet()

        items.asSequence()
            .mapNotNull { item ->
                item.novel.id.takeIf { downloadCache.hasAnyDownloadedChapter(item.novel) }
            }
            .toSet()
    }
    val sourceLanguageByNovelId = remember(items, showLanguageBadge) {
        if (!showLanguageBadge) return@remember emptyMap()

        items.associate { item ->
            item.novel.id to sourceManager.getOrStub(item.novel.source).lang
        }
    }
    var isSearchActive by remember(searchQuery) { mutableStateOf(!searchQuery.isNullOrBlank()) }

    val query = searchQuery.orEmpty()
    val filteredItems = if (query.isBlank()) {
        items
    } else {
        items.filter { it.novel.title.contains(query, ignoreCase = true) }
    }
    val isSelectionMode = selection.isNotEmpty() && onToggleSelection != null
    val onClickNovelItem: (LibraryNovel) -> Unit = { libraryNovel ->
        if (isSelectionMode) {
            onToggleSelection?.invoke(libraryNovel)
        } else {
            onNovelClicked(libraryNovel.novel.id)
        }
    }
    val onLongClickNovelItem: ((LibraryNovel) -> Unit)? = when {
        onToggleRangeSelection != null -> onToggleRangeSelection
        onLongClickNovel != null -> onLongClickNovel
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        if (displaySpec.isList) {
            FastScrollLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding + PaddingValues(
                    horizontal = auroraAdaptiveSpec.contentHorizontalPaddingDp.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showInlineHeader) {
                    item {
                        InlineNovelLibraryHeader(
                            isSearchActive = isSearchActive,
                            searchQuery = query,
                            onSearchQueryChange = onSearchQueryChange,
                            onSearchClick = { isSearchActive = true },
                            onSearchClose = {
                                onSearchQueryChange(null)
                                isSearchActive = false
                            },
                            hasActiveFilters = hasActiveFilters,
                            onFilterClicked = onFilterClicked,
                            onRefresh = onRefresh,
                            onGlobalUpdate = onGlobalUpdate,
                            onOpenRandomEntry = onOpenRandomEntry,
                            modifier = Modifier.auroraCenteredMaxWidth(auroraAdaptiveSpec.listMaxWidthDp),
                        )
                    }
                }

                listItems(filteredItems, key = { it.id }) { item ->
                    val badgeState = resolveNovelLibraryBadgeState(
                        item = item,
                        showDownloadBadge = showDownloadBadge,
                        downloadedNovelIds = downloadedNovelIds,
                        showUnreadBadge = showUnreadBadge,
                        showLanguageBadge = showLanguageBadge,
                        sourceLanguage = sourceLanguageByNovelId[item.novel.id].orEmpty(),
                    )
                    NovelLibraryAuroraCard(
                        item = item,
                        context = context,
                        badgeState = badgeState,
                        showMetadata = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .auroraCenteredMaxWidth(auroraAdaptiveSpec.listMaxWidthDp)
                            .aspectRatio(2.2f),
                        coverHeightFraction = 0.62f,
                        onNovelClicked = { onClickNovelItem(item) },
                        onLongClick = onLongClickNovelItem?.let { { it(item) } },
                        onClickContinueReading = onContinueReadingClicked,
                        isSelected = selection.fastAny { it.id == item.id },
                        cardStyle = AuroraLibraryCardStyle.Standard,
                        glowDisplayMode = LibraryDisplayMode.List,
                        gridColumns = null,
                    )
                }
            }
        } else {
            LazyLibraryGrid(
                modifier = Modifier
                    .fillMaxSize()
                    .auroraCenteredMaxWidth(auroraAdaptiveSpec.listMaxWidthDp),
                columns = columns.coerceAtLeast(0),
                adaptiveMinCellDp = displaySpec.adaptiveMinCellDp,
                contentPadding = contentPadding,
            ) {
                if (showInlineHeader) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        InlineNovelLibraryHeader(
                            isSearchActive = isSearchActive,
                            searchQuery = query,
                            onSearchQueryChange = onSearchQueryChange,
                            onSearchClick = { isSearchActive = true },
                            onSearchClose = {
                                onSearchQueryChange(null)
                                isSearchActive = false
                            },
                            hasActiveFilters = hasActiveFilters,
                            onFilterClicked = onFilterClicked,
                            onRefresh = onRefresh,
                            onGlobalUpdate = onGlobalUpdate,
                            onOpenRandomEntry = onOpenRandomEntry,
                            modifier = Modifier.auroraCenteredMaxWidth(auroraAdaptiveSpec.listMaxWidthDp),
                        )
                    }
                }

                gridItems(filteredItems, key = { it.id }) { item ->
                    val badgeState = resolveNovelLibraryBadgeState(
                        item = item,
                        showDownloadBadge = showDownloadBadge,
                        downloadedNovelIds = downloadedNovelIds,
                        showUnreadBadge = showUnreadBadge,
                        showLanguageBadge = showLanguageBadge,
                        sourceLanguage = sourceLanguageByNovelId[item.novel.id].orEmpty(),
                    )
                    if (displaySpec.useCompactGridEntryStyle && !useGlowContourCards) {
                        NovelLibraryCompactGridItem(
                            item = item,
                            badgeState = badgeState,
                            selection = selection,
                            onNovelClicked = onClickNovelItem,
                            onLongClickNovel = onLongClickNovelItem,
                            onClickContinueReading = onContinueReadingClicked,
                        )
                    } else {
                        NovelLibraryAuroraCard(
                            item = item,
                            context = context,
                            badgeState = badgeState,
                            showMetadata = displaySpec.showMetadata,
                            modifier = if (useGlowContourCards) {
                                Modifier
                            } else {
                                Modifier.aspectRatio(displaySpec.gridCardAspectRatio)
                            },
                            coverHeightFraction = displaySpec.gridCoverHeightFraction,
                            onNovelClicked = { onClickNovelItem(item) },
                            onLongClick = onLongClickNovelItem?.let { { it(item) } },
                            onClickContinueReading = onContinueReadingClicked,
                            isSelected = selection.fastAny { it.id == item.id },
                            cardStyle = auroraCardStyle,
                            glowDisplayMode = displayMode,
                            gridColumns = columns.coerceAtLeast(0),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelLibraryCompactGridItem(
    item: LibraryNovel,
    badgeState: NovelLibraryBadgeState,
    selection: List<LibraryNovel>,
    onNovelClicked: (LibraryNovel) -> Unit,
    onLongClickNovel: ((LibraryNovel) -> Unit)?,
    onClickContinueReading: ((LibraryNovel) -> Unit)?,
) {
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()

    EntryCompactGridItem(
        coverData = item.novel.asNovelCover(),
        title = item.novel.title,
        onClick = { onNovelClicked(item) },
        onLongClick = { onLongClickNovel?.invoke(item) },
        isSelected = selection.fastAny { it.id == item.id },
        onClickContinueViewing = if (onClickContinueReading != null && item.unreadCount > 0) {
            { onClickContinueReading(item) }
        } else {
            null
        },
        errorPainter = placeholderPainter,
        coverBadgeStart = {
            if (badgeState.showDownloaded) {
                Badge(
                    text = stringResource(AYMR.strings.aurora_downloaded),
                    color = MaterialTheme.colorScheme.tertiary,
                    textColor = MaterialTheme.colorScheme.onTertiary,
                )
            }
            UnviewedBadge(count = badgeState.unreadCount ?: 0L)
        },
        coverBadgeEnd = {
            badgeState.language?.let {
                LanguageBadge(
                    isLocal = false,
                    sourceLanguage = it,
                )
            }
        },
    )
}

internal fun resolveNovelLibraryCornerIndicatorIsFinished(status: Long): Boolean {
    return status == SManga.COMPLETED.toLong() ||
        status == SManga.PUBLISHING_FINISHED.toLong() ||
        status == SManga.CANCELLED.toLong()
}

@Composable
private fun NovelLibraryAuroraCard(
    item: LibraryNovel,
    context: Context,
    badgeState: NovelLibraryBadgeState,
    showMetadata: Boolean,
    modifier: Modifier,
    coverHeightFraction: Float,
    onNovelClicked: () -> Unit,
    onLongClick: (() -> Unit)?,
    onClickContinueReading: ((LibraryNovel) -> Unit)?,
    isSelected: Boolean,
    cardStyle: AuroraLibraryCardStyle,
    glowDisplayMode: LibraryDisplayMode,
    gridColumns: Int?,
) {
    val useGlowContourCards = cardStyle == AuroraLibraryCardStyle.GlowContour
    val progressText = if (showMetadata && item.totalChapters > 0) {
        "${item.totalChapters - item.unreadCount}/${item.totalChapters} ${stringResource(
            MR.strings.chapters,
        )}"
    } else {
        null
    }
    val progressPercent = resolveNovelLibraryCardProgressPercent(
        readCount = item.readCount,
        totalCount = item.totalChapters,
    )
    val textSpec = resolveGlowContourLibraryTextSpec(glowDisplayMode)
    val cornerIndicatorState = resolveGlowContourCornerIndicatorState(
        hasContinueAction = onClickContinueReading != null,
        remainingCount = item.unreadCount,
        isFinished = resolveNovelLibraryCornerIndicatorIsFinished(item.novel.status),
    )
    val coverRequest = remember(item.novel.id, item.novel.thumbnailUrl, item.novel.coverLastModified) {
        ImageRequest.Builder(context)
            .data(sourceAwareNovelCoverModel(item.novel))
            .placeholderMemoryCacheKey(item.novel.thumbnailUrl)
            .build()
    }

    if (useGlowContourCards) {
        GlowContourLibraryGridItem(
            modifier = modifier,
            title = item.novel.title,
            subtitle = progressText,
            coverData = coverRequest,
            progressPercent = progressPercent,
            cardAspectRatio = 0.76f,
            cornerIndicatorState = cornerIndicatorState,
            textSpec = textSpec,
            badge = if (badgeState.hasBadge()) {
                {
                    NovelAuroraBadgeGroup(
                        badgeState = badgeState,
                        glowStyle = true,
                    )
                }
            } else {
                null
            },
            onClick = onNovelClicked,
            onLongClick = onLongClick,
            onClickContinueViewing = if (onClickContinueReading != null && item.unreadCount > 0) {
                { onClickContinueReading(item) }
            } else {
                null
            },
            isSelected = isSelected,
            gridColumns = gridColumns,
        )
    } else if (showMetadata) {
        val colors = AuroraTheme.colors
        AuroraCard(
            modifier = modifier,
            title = item.novel.title,
            coverData = coverRequest,
            subtitle = progressText,
            coverHeightFraction = coverHeightFraction,
            badge = if (badgeState.hasBadge()) {
                {
                    NovelAuroraBadgeGroup(
                        badgeState = badgeState,
                        glowStyle = false,
                    )
                }
            } else {
                null
            },
            onClick = onNovelClicked,
            onLongClick = onLongClick,
            onClickContinueViewing = if (onClickContinueReading != null && item.unreadCount > 0) {
                { onClickContinueReading(item) }
            } else {
                null
            },
            isSelected = isSelected,
            titleMaxLines = if (showMetadata) 1 else 2,
            gridColumns = gridColumns,
        )
    } else {
        NovelLibraryAuroraCoverOnlyCard(
            coverData = coverRequest,
            badgeState = badgeState,
            modifier = modifier,
            isSelected = isSelected,
            gridColumns = gridColumns,
            onClickContinueViewing = if (onClickContinueReading != null && item.unreadCount > 0) {
                { onClickContinueReading(item) }
            } else {
                null
            },
            onClick = onNovelClicked,
            onLongClick = onLongClick,
        )
    }
}

@Composable
private fun NovelAuroraBadgeGroup(
    badgeState: NovelLibraryBadgeState,
    glowStyle: Boolean,
) {
    val colors = AuroraTheme.colors
    val badgeContainerColor = if (glowStyle) {
        colors.surface.copy(alpha = 0.82f)
    } else {
        colors.accent
    }
    val badgeTextColor = if (glowStyle) {
        colors.textPrimary
    } else {
        colors.textOnAccent
    }

    BadgeGroup {
        if (badgeState.showDownloaded) {
            Badge(
                text = stringResource(AYMR.strings.aurora_downloaded),
                color = badgeContainerColor,
                textColor = badgeTextColor,
                shape = RoundedCornerShape(4.dp),
            )
        }
        badgeState.unreadCount?.let {
            Badge(
                text = it.toString(),
                color = badgeContainerColor,
                textColor = badgeTextColor,
                shape = RoundedCornerShape(4.dp),
            )
        }
        badgeState.language?.let {
            Badge(
                text = it.uppercase(),
                color = badgeContainerColor,
                textColor = badgeTextColor,
                shape = RoundedCornerShape(4.dp),
            )
        }
    }
}

@Composable
private fun NovelLibraryAuroraCoverOnlyCard(
    coverData: Any?,
    badgeState: NovelLibraryBadgeState,
    modifier: Modifier,
    isSelected: Boolean,
    gridColumns: Int?,
    onClickContinueViewing: (() -> Unit)?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val colors = AuroraTheme.colors
    val tabContainerColor = if (colors.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.05f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    Card(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = tabContainerColor),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                colors.accent
            } else if (colors.isDark) {
                Color.Transparent
            } else {
                Color.LightGray.copy(alpha = 0.4f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.1f)),
        ) {
            val overlaySpec = resolveAuroraCardOverlaySpec(
                gridColumns = gridColumns,
                cardWidthDp = maxWidth.value,
            )

            AsyncImage(
                model = resolveAuroraCoverModel(coverData),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                error = placeholderPainter,
                fallback = placeholderPainter,
            )

            if (badgeState.hasBadge()) {
                BadgeGroup(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                ) {
                    if (badgeState.showDownloaded) {
                        Badge(
                            text = stringResource(AYMR.strings.aurora_downloaded),
                            color = colors.accent,
                            textColor = colors.textOnAccent,
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                    badgeState.unreadCount?.let {
                        Badge(
                            text = it.toString(),
                            color = colors.accent,
                            textColor = colors.textOnAccent,
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                    badgeState.language?.let {
                        Badge(
                            text = it.uppercase(),
                            color = colors.accent,
                            textColor = colors.textOnAccent,
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                }
            }

            if (onClickContinueViewing != null) {
                FilledIconButton(
                    onClick = onClickContinueViewing,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(overlaySpec.buttonSizeDp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.accent.copy(alpha = 0.9f),
                        contentColor = colors.textOnAccent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(overlaySpec.buttonIconSizeDp),
                    )
                }
            }
        }
    }
}

private fun NovelLibraryBadgeState.hasBadge(): Boolean {
    return showDownloaded || unreadCount != null || language != null
}

@Composable
private fun InlineNovelLibraryHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String?) -> Unit,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    hasActiveFilters: Boolean,
    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onGlobalUpdate: () -> Unit,
    onOpenRandomEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val tabState = LocalTabState.current
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = { value ->
                        onSearchQueryChange(value.ifBlank { null })
                    },
                    placeholder = {
                        Text(
                            text = stringResource(MR.strings.action_search),
                            color = colors.textSecondary,
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = colors.textPrimary) },
                    trailingIcon = {
                        IconButton(onClick = onSearchClose) {
                            Icon(Icons.Filled.Close, null, tint = colors.textSecondary)
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.cardBackground,
                        unfocusedContainerColor = colors.cardBackground,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Text(
                    text = stringResource(AYMR.strings.aurora_library),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )

                Row {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, null, tint = colors.textPrimary)
                    }
                    IconButton(onClick = onFilterClicked) {
                        Icon(
                            Icons.Filled.FilterList,
                            null,
                            tint = if (hasActiveFilters) colors.accent else colors.textSecondary,
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, null, tint = colors.textSecondary)
                        }
                        AuroraEntryDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(MR.strings.action_update_library),
                                leadingIcon = Icons.Filled.Refresh,
                                onClick = {
                                    onRefresh()
                                    showMenu = false
                                },
                            )
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(MR.strings.pref_category_library_update),
                                leadingIcon = Icons.Filled.Refresh,
                                onClick = {
                                    onGlobalUpdate()
                                    showMenu = false
                                },
                            )
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(MR.strings.action_open_random_manga),
                                leadingIcon = Icons.Filled.Shuffle,
                                onClick = {
                                    onOpenRandomEntry()
                                    showMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        if (tabState != null && tabState.tabs.size > 1) {
            Spacer(Modifier.height(12.dp))
            AuroraTabRow(
                tabs = tabState.tabs,
                selectedIndex = tabState.selectedIndex,
                onTabSelected = tabState.onTabSelected,
                scrollable = false,
            )
        }
    }
}
