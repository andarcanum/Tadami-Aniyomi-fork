package eu.kanade.presentation.browse.novel

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.components.ExtensionDetailsGlassCard
import eu.kanade.presentation.browse.components.chromeAccentDetails
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.entries.translation.rememberBrowseNovelTitleTranslation
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.network.interceptor.CloudflareManualSolveRegistry
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.source.novel.NovelPluginImageWarmupEffect
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.items.chapter.model.NoChaptersException
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as collectPreferenceAsState

internal fun novelBrowseItemKey(url: String?, index: Int): String {
    return "novel/${url.orEmpty()}#$index"
}

@Composable
fun BrowseNovelSourceContent(
    source: NovelSource?,
    novels: LazyPagingItems<Novel>,
    favoriteNovelUrls: Set<String>,
    displayMode: LibraryDisplayMode,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onNovelClick: (Novel) -> Unit,
    onNovelLongClick: ((Novel) -> Unit)? = null,
) {
    val context = LocalContext.current
    val translationPreferences = remember { Injekt.get<UiPreferences>() }
    val browseTitleTranslationEnabled by translationPreferences
        .auroraEntryTranslationEnabled()
        .collectPreferenceAsState()
    val browseTitleTranslationSourceFamilies by translationPreferences
        .auroraEntryTranslationSourceLanguages()
        .collectPreferenceAsState()
    val effectiveContentPadding = contentPadding

    val appendErrorState = novels.loadState.append
        .takeIf { it is LoadState.Error }
        ?.takeUnless { novels.itemCount > 0 && (it as LoadState.Error).error is NoChaptersException }
    val errorState = novels.loadState.refresh.takeIf { it is LoadState.Error }
        ?: appendErrorState

    val getErrorMessage: (LoadState.Error) -> String = { state ->
        state.error.formattedMessage(context)
    }

    LaunchedEffect(errorState) {
        if (novels.itemCount > 0 && errorState != null && errorState is LoadState.Error) {
            val result = snackbarHostState.showSnackbar(
                message = getErrorMessage(errorState),
                actionLabel = context.stringResource(MR.strings.action_retry),
                duration = SnackbarDuration.Indefinite,
            )
            when (result) {
                SnackbarResult.Dismissed -> snackbarHostState.currentSnackbarData?.dismiss()
                SnackbarResult.ActionPerformed -> novels.retry()
            }
        }
    }

    if (novels.itemCount <= 0 && errorState != null && errorState is LoadState.Error) {
        // A Cloudflare challenge the hidden solve could not finish leaves a clearance request in
        // the registry; passing it once in a visible WebView unblocks the following fetches.
        val manualSolveHost = remember(source) {
            (source as? NovelSiteSource)?.siteUrl?.toHttpUrlOrNull()?.host
        }
        val manualSolvePending = manualSolveHost?.let { CloudflareManualSolveRegistry.isPending(it) } == true
        NovelSourceCatalogErrorCard(
            modifier = Modifier.padding(effectiveContentPadding),
            message = getErrorMessage(errorState),
            onRetry = novels::refresh,
            manualSolveLabel = if (manualSolvePending) {
                stringResource(MR.strings.action_cloudflare_check)
            } else {
                null
            },
            onManualSolve = manualSolveHost?.let { host ->
                {
                    // The pending mark is intentionally kept: it is also the gate that routes the
                    // plugin fetches through the WebView bridge, so consuming it here would push
                    // the next retry back into the doomed OkHttp + solve cycle.
                    context.startActivity(
                        WebViewActivity.newIntent(
                            context = context,
                            url = "https://$host/",
                            sourceId = source?.id,
                            title = source?.name,
                        ),
                    )
                }
            },
        )
        return
    }

    // Центрированный индикатор загрузки вместо пустых плиток (скелетона):
    // даёт мгновенный визуальный отклик при сетевом запросе каталога.
    if (novels.itemCount == 0 && novels.loadState.refresh is LoadState.Loading) {
        LoadingScreen(
            modifier = Modifier.padding(effectiveContentPadding),
        )
        return
    }

    when (displayMode) {
        LibraryDisplayMode.List -> {
            NovelListContent(
                novels = novels,
                favoriteNovelUrls = favoriteNovelUrls,
                sourceLanguage = source?.lang,
                contentPadding = effectiveContentPadding,
                translationEnabled = browseTitleTranslationEnabled,
                allowedSourceFamilies = browseTitleTranslationSourceFamilies,
                onNovelClick = onNovelClick,
                onNovelLongClick = onNovelLongClick,
            )
        }
        LibraryDisplayMode.ComfortableGrid -> {
            NovelComfortableGridContent(
                novels = novels,
                favoriteNovelUrls = favoriteNovelUrls,
                // Комфортный режим каталога — 2 крупные колонки по прототипу.
                columns = GridCells.Fixed(2),
                sourceLanguage = source?.lang,
                contentPadding = effectiveContentPadding,
                translationEnabled = browseTitleTranslationEnabled,
                allowedSourceFamilies = browseTitleTranslationSourceFamilies,
                onNovelClick = onNovelClick,
                onNovelLongClick = onNovelLongClick,
            )
        }
        LibraryDisplayMode.CompactGrid -> {
            NovelCompactGridContent(
                novels = novels,
                favoriteNovelUrls = favoriteNovelUrls,
                columns = GridCells.Adaptive(96.dp),
                sourceLanguage = source?.lang,
                contentPadding = effectiveContentPadding,
                showTitle = true,
                translationEnabled = browseTitleTranslationEnabled,
                allowedSourceFamilies = browseTitleTranslationSourceFamilies,
                onNovelClick = onNovelClick,
                onNovelLongClick = onNovelLongClick,
            )
        }
        LibraryDisplayMode.CoverOnlyGrid -> {
            NovelCompactGridContent(
                novels = novels,
                favoriteNovelUrls = favoriteNovelUrls,
                columns = GridCells.Adaptive(96.dp),
                sourceLanguage = source?.lang,
                contentPadding = effectiveContentPadding,
                showTitle = false,
                translationEnabled = browseTitleTranslationEnabled,
                allowedSourceFamilies = browseTitleTranslationSourceFamilies,
                onNovelClick = onNovelClick,
                onNovelLongClick = onNovelLongClick,
            )
        }
    }
}

@Composable
private fun NovelListContent(
    novels: LazyPagingItems<Novel>,
    favoriteNovelUrls: Set<String>,
    sourceLanguage: String?,
    contentPadding: PaddingValues,
    translationEnabled: Boolean,
    allowedSourceFamilies: Set<String>,
    onNovelClick: (Novel) -> Unit,
    onNovelLongClick: ((Novel) -> Unit)?,
) {
    val warmupTargets by remember(novels.itemCount) {
        derivedStateOf {
            buildList {
                val upperBound = minOf(novels.itemCount, BROWSE_NOVEL_WARMUP_WINDOW)
                for (index in 0 until upperBound) {
                    add(novels[index]?.thumbnailUrl)
                }
            }
        }
    }
    NovelPluginImageWarmupEffect(urls = warmupTargets, key = warmupTargets)

    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    LazyColumn(
        modifier = Modifier.auroraCenteredMaxWidth(auroraAdaptiveSpec.listMaxWidthDp),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        // BRN3-control: the List mode had NO loading indicators while prepending/appending
        // (both grid modes have them; the manga List is the etalon).
        if (novels.loadState.prepend is LoadState.Loading) {
            item {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = novels.itemCount,
            key = { index -> novelBrowseItemKey(novels[index]?.url, index) },
        ) { index ->
            val novel = novels[index] ?: return@items
            val isFavorite = remember(novel.url, favoriteNovelUrls) { novel.url in favoriteNovelUrls }
            val translatedTitle = rememberBrowseNovelTitleTranslation(
                title = novel.title,
                sourceLanguage = sourceLanguage,
                enabled = translationEnabled,
                allowedSourceFamilies = allowedSourceFamilies,
            )
            Column {
                // Светящийся разделитель между строками (язык Browse-хаба).
                if (index > 0) {
                    NovelSourceListRowDivider()
                }
                BrowseNovelSourceListRow(
                    novel = novel,
                    title = translatedTitle,
                    isFavorite = isFavorite,
                    onClick = { onNovelClick(novel) },
                    onLongClick = onNovelLongClick?.let { callback -> { callback(novel) } } ?: {},
                )
            }
        }

        if (novels.loadState.refresh is LoadState.Loading || novels.loadState.append is LoadState.Loading) {
            item {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun NovelComfortableGridContent(
    novels: LazyPagingItems<Novel>,
    favoriteNovelUrls: Set<String>,
    columns: GridCells,
    sourceLanguage: String?,
    contentPadding: PaddingValues,
    translationEnabled: Boolean,
    allowedSourceFamilies: Set<String>,
    onNovelClick: (Novel) -> Unit,
    onNovelLongClick: ((Novel) -> Unit)?,
) {
    val warmupTargets by remember(novels.itemCount) {
        derivedStateOf {
            buildList {
                val upperBound = minOf(novels.itemCount, BROWSE_NOVEL_WARMUP_WINDOW)
                for (index in 0 until upperBound) {
                    add(novels[index]?.thumbnailUrl)
                }
            }
        }
    }
    NovelPluginImageWarmupEffect(urls = warmupTargets, key = warmupTargets)

    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.auroraCenteredMaxWidth(
            auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp,
        ),
        // Отступы/шаг прототипа каталога: постеры без внутренней рамки, зазор 10dp.
        contentPadding = contentPadding + PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (novels.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = novels.itemCount,
            key = { index -> novelBrowseItemKey(novels[index]?.url, index) },
        ) { index ->
            val novel = novels[index] ?: return@items
            val isFavorite = remember(novel.url, favoriteNovelUrls) { novel.url in favoriteNovelUrls }
            val translatedTitle = rememberBrowseNovelTitleTranslation(
                title = novel.title,
                sourceLanguage = sourceLanguage,
                enabled = translationEnabled,
                allowedSourceFamilies = allowedSourceFamilies,
            )
            // Постер прототипа каталога: тот же стеклянный постер, что в компактной
            // сетке (комфортный режим отличается шириной колонок — 2 по прототипу).
            NovelSourcePosterItem(
                novel = novel,
                title = translatedTitle,
                isFavorite = isFavorite,
                onClick = { onNovelClick(novel) },
                onLongClick = onNovelLongClick?.let { callback -> { callback(novel) } } ?: {},
            )
        }

        if (novels.loadState.refresh is LoadState.Loading || novels.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun NovelCompactGridContent(
    novels: LazyPagingItems<Novel>,
    favoriteNovelUrls: Set<String>,
    columns: GridCells,
    sourceLanguage: String?,
    contentPadding: PaddingValues,
    showTitle: Boolean,
    translationEnabled: Boolean,
    allowedSourceFamilies: Set<String>,
    onNovelClick: (Novel) -> Unit,
    onNovelLongClick: ((Novel) -> Unit)?,
) {
    val warmupTargets by remember(novels.itemCount) {
        derivedStateOf {
            buildList {
                val upperBound = minOf(novels.itemCount, BROWSE_NOVEL_WARMUP_WINDOW)
                for (index in 0 until upperBound) {
                    add(novels[index]?.thumbnailUrl)
                }
            }
        }
    }
    NovelPluginImageWarmupEffect(urls = warmupTargets, key = warmupTargets)

    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.auroraCenteredMaxWidth(
            auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp,
        ),
        // Отступы/шаг прототипа каталога: постеры без внутренней рамки, зазор 10dp.
        contentPadding = contentPadding + PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (novels.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = novels.itemCount,
            key = { index -> novelBrowseItemKey(novels[index]?.url, index) },
        ) { index ->
            val novel = novels[index] ?: return@items
            val isFavorite = remember(novel.url, favoriteNovelUrls) { novel.url in favoriteNovelUrls }
            val translatedTitle = rememberBrowseNovelTitleTranslation(
                title = novel.title,
                sourceLanguage = sourceLanguage,
                enabled = translationEnabled,
                allowedSourceFamilies = allowedSourceFamilies,
            )
            NovelSourcePosterItem(
                novel = novel,
                title = translatedTitle.takeIf { showTitle },
                isFavorite = isFavorite,
                onClick = { onNovelClick(novel) },
                onLongClick = onNovelLongClick?.let { callback -> { callback(novel) } } ?: {},
            )
        }

        if (novels.loadState.refresh is LoadState.Loading || novels.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

/**
 * Постер каталога источника по одобренному прототипу (prototype_source_catalog.html):
 * скругление 14dp, hairline-рамка 1dp, нижний scrim-градиент с названием в 2 строки
 * поверх обложки, accent-бейдж «в библиотеке» (top-start). Общий для компактной и
 * комфортной сеток каталога; [title] == null — режим «только обложка» (без подписи).
 *
 * E-ink: без scrim-градиентов — hairline-рамка и сплошная подпись под постером.
 */
@Composable
private fun NovelSourcePosterItem(
    novel: Novel,
    title: String?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val hairline = if (colors.isDark) {
        Color.White.copy(alpha = 0.07f)
    } else {
        Color.Black.copy(alpha = 0.07f)
    }
    val coverData = novel.asBrowseNovelCover(isFavorite)
    val coverModifier = Modifier
        .fillMaxWidth()
        .alpha(if (isFavorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f)

    if (colors.isEInk) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .border(1.dp, hairline, shape),
        ) {
            ItemCover.Book(
                modifier = coverModifier,
                data = coverData,
                shape = shape,
                errorPainter = placeholderPainter,
            )
            if (title != null) {
                Text(
                    text = title,
                    modifier = Modifier.padding(6.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = coverTitleFontFamily,
                    color = colors.textPrimary,
                )
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(1.dp, hairline, shape),
    ) {
        ItemCover.Book(
            modifier = coverModifier,
            data = coverData,
            shape = shape,
            errorPainter = placeholderPainter,
        )
        if (title != null) {
            // Scrim под названием: снизу rgba(5,2,4,.92), к 55% — .55, кверху растворяется.
            // Light-тема: scrim инвертируется в белый (подпись textPrimary остаётся читаемой).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colorStops = if (colors.isDark) {
                                arrayOf(
                                    0f to Color.Transparent,
                                    0.45f to Color(0x8C050204),
                                    1f to Color(0xEB050204),
                                )
                            } else {
                                arrayOf(
                                    0f to Color.Transparent,
                                    0.45f to Color(0x8CFFFFFF),
                                    1f to Color(0xEBFFFFFF),
                                )
                            },
                        ),
                    ),
            ) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 7.dp)
                        .padding(bottom = 7.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = coverTitleFontFamily,
                    color = colors.textPrimary,
                )
            }
        }
        if (isFavorite) {
            Badge(
                imageVector = Icons.Outlined.CollectionsBookmark,
                color = colors.accent.copy(alpha = 0.85f),
                iconColor = Color.White,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }
    }
}

/**
 * Строка списка каталога по прототипу: обложка 44x62dp (rounded 9), название,
 * мета-строка textSecondary (автор, иначе «в библиотеке» для избранных).
 */
@Composable
private fun BrowseNovelSourceListRow(
    novel: Novel,
    title: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val meta = novel.author?.takeIf { it.isNotBlank() }
        ?: if (isFavorite) stringResource(MR.strings.in_library) else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ItemCover.Book(
            modifier = Modifier
                .size(width = 44.dp, height = 62.dp)
                .alpha(if (isFavorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f),
            data = novel.asBrowseNovelCover(isFavorite),
            shape = RoundedCornerShape(9.dp),
            errorPainter = placeholderPainter,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            if (meta != null) {
                Text(
                    text = meta,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        if (isFavorite) {
            Icon(
                imageVector = Icons.Outlined.CollectionsBookmark,
                contentDescription = stringResource(MR.strings.in_library),
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Светящаяся градиентная линия между строками (двухслойный glow, паттерн ElegantSourceDivider). */
@Composable
private fun NovelSourceListRowDivider() {
    val chrome = chromeAccentDetails()
    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.Transparent,
                            chrome.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.Transparent,
                            chrome.copy(alpha = 0.45f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

/** Kaomoji frost-карточки ошибки (из набора лиц EmptyScreen / прототипа). */
private const val CATALOG_ERROR_KAOMOJI = "(；￣Д￣)"

/**
 * Frost-карточка ошибки каталога (prototype_source_catalog.html): стеклянная
 * карточка с кромкой-дим сверху, kaomoji, сообщением и пилюлей «Повторить».
 * WebView/Помощь у ранобэ-контента отсутствуют (колбэков нет — минимальная адаптация).
 */
@Composable
private fun NovelSourceCatalogErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    manualSolveLabel: String? = null,
    onManualSolve: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExtensionDetailsGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = CATALOG_ERROR_KAOMOJI,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textSecondary,
                        letterSpacing = 1.sp,
                    )
                }
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                FlowRow(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    NovelSourceCatalogActionPill(
                        text = stringResource(MR.strings.action_retry),
                        icon = Icons.Outlined.Refresh,
                        accent = true,
                        onClick = onRetry,
                    )
                    if (manualSolveLabel != null && onManualSolve != null) {
                        NovelSourceCatalogActionPill(
                            text = manualSolveLabel,
                            icon = Icons.Outlined.VerifiedUser,
                            accent = false,
                            onClick = onManualSolve,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Пилюля действия frost-карточки: accent-градиент для главного действия,
 * стекло с кромкой и верхним димом для остальных (стиль ExtensionAuroraButton).
 */
@Composable
private fun NovelSourceCatalogActionPill(
    text: String,
    icon: ImageVector,
    accent: Boolean,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(999.dp)
    val appHaptics = LocalAppHaptics.current
    val contentColor = if (accent) Color.White else colors.textPrimary
    Box {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(
                    brush = if (accent) {
                        Brush.linearGradient(listOf(colors.accent, chromeAccentDetails()))
                    } else {
                        Brush.verticalGradient(
                            if (colors.isDark) {
                                listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.03f))
                            } else {
                                listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.42f))
                            },
                        )
                    },
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    color = if (accent) {
                        chromeAccentDetails().copy(alpha = 0.65f)
                    } else if (colors.isDark) {
                        Color.White.copy(alpha = 0.10f)
                    } else {
                        Color.Black.copy(alpha = 0.08f)
                    },
                    shape = shape,
                )
                .clickable {
                    appHaptics.tap()
                    onClick()
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
            )
        }
        // Верхний дим пилюли (язык плашек прототипа).
        Box(modifier = Modifier.matchParentSize().clip(shape)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                if (accent) {
                                    Color.White.copy(alpha = 0.35f)
                                } else {
                                    chromeAccentDetails().copy(alpha = 0.50f)
                                },
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
    }
}

private const val BROWSE_NOVEL_WARMUP_WINDOW = 12

internal fun Novel.asBrowseNovelCover(isFavorite: Boolean): NovelCover {
    return NovelCover(
        novelId = id,
        sourceId = source,
        isNovelFavorite = isFavorite,
        url = thumbnailUrl,
        lastModified = coverLastModified,
    )
}

@Composable
internal fun MissingNovelSourceScreen(
    source: StubNovelSource,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = source.name,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        EmptyScreen(
            message = stringResource(MR.strings.source_not_installed, source.toString()),
            modifier = Modifier.padding(paddingValues),
        )
    }
}
