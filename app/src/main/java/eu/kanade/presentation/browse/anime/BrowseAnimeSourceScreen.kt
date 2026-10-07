package eu.kanade.presentation.browse.anime

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.anime.components.BrowseAnimeSourceComfortableGrid
import eu.kanade.presentation.browse.anime.components.BrowseAnimeSourceCompactGrid
import eu.kanade.presentation.browse.anime.components.BrowseAnimeSourceList
import eu.kanade.presentation.browse.components.ExtensionDetailsGlassCard
import eu.kanade.presentation.browse.components.chromeAccentDetails
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.animesource.AnimeSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.NoEpisodesException
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.source.local.entries.anime.LocalAnimeSource

@Composable
fun BrowseAnimeSourceContent(
    source: AnimeSource?,
    animeList: LazyPagingItems<Anime>,
    favoriteAnimeUrls: Set<String>,
    columns: GridCells,
    displayMode: LibraryDisplayMode,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    onLocalAnimeSourceHelpClick: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
) {
    val context = LocalContext.current

    val appendErrorState = animeList.loadState.append
        .takeIf { it is LoadState.Error }
        ?.takeUnless { animeList.itemCount > 0 && (it as LoadState.Error).error is NoEpisodesException }
    val errorState = animeList.loadState.refresh.takeIf { it is LoadState.Error }
        ?: appendErrorState

    val getErrorMessage: (LoadState.Error) -> String = { state ->
        state.error.formattedMessage(context)
    }

    LaunchedEffect(errorState) {
        if (animeList.itemCount > 0 && errorState != null && errorState is LoadState.Error) {
            val result = snackbarHostState.showSnackbar(
                message = getErrorMessage(errorState),
                actionLabel = context.stringResource(MR.strings.action_retry),
                duration = SnackbarDuration.Indefinite,
            )
            when (result) {
                SnackbarResult.Dismissed -> snackbarHostState.currentSnackbarData?.dismiss()
                SnackbarResult.ActionPerformed -> animeList.retry()
            }
        }
    }

    if (animeList.itemCount <= 0 && errorState != null && errorState is LoadState.Error) {
        AnimeSourceCatalogErrorCard(
            modifier = Modifier.padding(contentPadding),
            message = getErrorMessage(errorState),
            isLocalSource = source is LocalAnimeSource,
            onRetry = animeList::refresh,
            onWebViewClick = onWebViewClick,
            onHelpClick = onHelpClick,
            onLocalSourceHelpClick = onLocalAnimeSourceHelpClick,
        )

        return
    }

    // Центрированный индикатор загрузки вместо пустых плиток (скелетона):
    // даёт мгновенный визуальный отклик при сетевом запросе каталога.
    if (animeList.itemCount == 0 && animeList.loadState.refresh is LoadState.Loading) {
        LoadingScreen(
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    when (displayMode) {
        LibraryDisplayMode.ComfortableGrid -> {
            BrowseAnimeSourceComfortableGrid(
                animeList = animeList,
                favoriteAnimeUrls = favoriteAnimeUrls,
                // Комфортный режим каталога — 2 крупные колонки по прототипу.
                columns = GridCells.Fixed(2),
                contentPadding = contentPadding,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
            )
        }
        LibraryDisplayMode.List -> {
            BrowseAnimeSourceList(
                animeList = animeList,
                favoriteAnimeUrls = favoriteAnimeUrls,
                contentPadding = contentPadding,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
            )
        }
        LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
            BrowseAnimeSourceCompactGrid(
                animeList = animeList,
                favoriteAnimeUrls = favoriteAnimeUrls,
                columns = columns,
                contentPadding = contentPadding,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
            )
        }
    }
}

/** Kaomoji frost-карточки ошибки (из набора лиц EmptyScreen / прототипа). */
private const val CATALOG_ERROR_KAOMOJI = "(；￣Д￣)"

/**
 * Frost-карточка ошибки каталога (prototype_source_catalog.html): стеклянная
 * карточка с кромкой-дим сверху, kaomoji, сообщением и пилюлями действий.
 * Набор действий прежний: Повторить / WebView / Помощь (локальный источник — гайд).
 */
@Composable
private fun AnimeSourceCatalogErrorCard(
    message: String,
    isLocalSource: Boolean,
    onRetry: () -> Unit,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    onLocalSourceHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                    if (isLocalSource) {
                        AnimeSourceCatalogActionPill(
                            text = stringResource(MR.strings.local_source_help_guide),
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            accent = true,
                            onClick = onLocalSourceHelpClick,
                        )
                    } else {
                        AnimeSourceCatalogActionPill(
                            text = stringResource(MR.strings.action_retry),
                            icon = Icons.Outlined.Refresh,
                            accent = true,
                            onClick = onRetry,
                        )
                        AnimeSourceCatalogActionPill(
                            text = stringResource(MR.strings.action_open_in_web_view),
                            icon = Icons.Outlined.Public,
                            accent = false,
                            onClick = onWebViewClick,
                        )
                        AnimeSourceCatalogActionPill(
                            text = stringResource(MR.strings.label_help),
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            accent = false,
                            onClick = onHelpClick,
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
private fun AnimeSourceCatalogActionPill(
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

@Composable
internal fun MissingSourceScreen(
    source: StubAnimeSource,
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
