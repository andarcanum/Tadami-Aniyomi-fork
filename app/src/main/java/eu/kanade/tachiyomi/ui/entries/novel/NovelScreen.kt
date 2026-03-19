package eu.kanade.tachiyomi.ui.entries.novel

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.entries.novel.NovelChapterSettingsDialog
import eu.kanade.presentation.entries.novel.NovelScreen
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl
import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.entries.manga.track.MangaTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.novel.NovelLibraryTab
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.domain.entries.novel.model.Novel as DomainNovel
import tachiyomi.domain.items.novelchapter.model.NovelChapter as DomainNovelChapter

class NovelScreen(
    private val novelId: Long,
    val fromSource: Boolean = false,
) : eu.kanade.presentation.util.Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val screenModel = rememberScreenModel {
            NovelScreenModel(lifecycleOwner.lifecycle, novelId)
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val novelReaderPreferences = remember { Injekt.get<NovelReaderPreferences>() }
        val isTranslatorEnabled by novelReaderPreferences.geminiEnabled().collectAsState()

        if (state is NovelScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as NovelScreenModel.State.Success
        val coroutineScope = rememberCoroutineScope()
        var showBatchDownloadDialog by remember { mutableStateOf(false) }
        var showBatchChapterPickerDialog by remember { mutableStateOf(false) }
        var batchPickerChapters by remember { mutableStateOf<List<DomainNovelChapter>>(emptyList()) }
        var showTranslatedDownloadDialog by remember { mutableStateOf(false) }
        var showTranslatedChapterPickerDialog by remember { mutableStateOf(false) }
        var translatedPickerFormat by remember { mutableStateOf(NovelTranslatedDownloadFormat.TXT) }
        var translatedPickerChapters by remember { mutableStateOf<List<DomainNovelChapter>>(emptyList()) }
        var showEpubExportDialog by remember { mutableStateOf(false) }
        val epubExportPreferences = screenModel.getEpubExportPreferences()
        BackHandler(enabled = screenModel.isAnyChapterSelected) {
            screenModel.toggleAllSelection(false)
        }

        val rawNovelUrl = successState.novel.url
        val canOpenNovelWebView = rawNovelUrl.isNotBlank()
        val startChapter = screenModel.getResumeOrNextChapter()
        val isReading = screenModel.isReadingStarted()
        val actionAvailability = resolveNovelEntryActionAvailability(
            isFavorite = successState.novel.favorite,
            isSourceConfigurable = successState.source is ConfigurableNovelSource,
        )
        val openInWebViewAction: (() -> Unit)? = if (canOpenNovelWebView) {
            {
                coroutineScope.launch {
                    val resolvedUrl = resolveNovelEntryWebUrl(
                        novelUrl = rawNovelUrl,
                        source = successState.source,
                    )
                    if (resolvedUrl == null) {
                        context.toast("Unable to open title in WebView")
                        return@launch
                    }
                    openNovelInWebView(
                        navigator = navigator,
                        url = resolvedUrl,
                        title = successState.novel.title,
                        sourceId = successState.novel.source,
                    )
                }
            }
        } else {
            null
        }
        val openWebViewLoginAction: (() -> Unit)? = if (canOpenNovelWebView) {
            {
                coroutineScope.launch {
                    val resolvedUrl = resolveNovelLoginWebUrl(
                        novelUrl = rawNovelUrl,
                        source = successState.source,
                    )
                    if (resolvedUrl == null) {
                        context.toast("Unable to open source login page in WebView")
                        return@launch
                    }
                    openNovelInWebView(
                        navigator = navigator,
                        url = resolvedUrl,
                        title = successState.source.name,
                        sourceId = successState.novel.source,
                    )
                }
            }
        } else {
            null
        }

        val needsWebViewLoginHint = resolveNovelNeedsWebViewLoginHint(
            novel = successState.novel,
            source = successState.source,
            chaptersCount = successState.chapters.size,
            canOpenWebView = openInWebViewAction != null,
            isRefreshing = successState.isRefreshingData,
        )
        val webViewLoginHintKey = resolveNovelWebViewLoginHintKey(
            novelId = successState.novel.id,
            chaptersCount = successState.chapters.size,
            description = successState.novel.description,
            needsLoginHint = needsWebViewLoginHint,
        )
        var lastShownWebViewLoginHintKey by rememberSaveable(successState.novel.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(webViewLoginHintKey, openWebViewLoginAction) {
            if (webViewLoginHintKey == null) {
                logcat {
                    "Novel login hint skipped id=${successState.novel.id} source=${successState.source.name} " +
                        "needsHint=false chapters=${successState.chapters.size} " +
                        "initialized=${successState.novel.initialized} descBlank=${successState.novel.description.isNullOrBlank()}"
                }
                lastShownWebViewLoginHintKey = null
                return@LaunchedEffect
            }
            if (lastShownWebViewLoginHintKey == webViewLoginHintKey) {
                logcat {
                    "Novel login hint suppressed duplicate id=${successState.novel.id} key=$webViewLoginHintKey"
                }
                return@LaunchedEffect
            }
            lastShownWebViewLoginHintKey = webViewLoginHintKey
            logcat {
                "Showing novel login hint id=${successState.novel.id} source=${successState.source.name} " +
                    "url=${successState.novel.url}"
            }
            val result = screenModel.snackbarHostState.showSnackbar(
                message = context.contextStringResource(MR.strings.login_title, successState.source.name),
                actionLabel = context.contextStringResource(MR.strings.action_open_in_web_view),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            logcat {
                "Novel login hint result id=${successState.novel.id} source=${successState.source.name} result=$result"
            }
            if (result == SnackbarResult.ActionPerformed) {
                openWebViewLoginAction?.invoke()
            }
        }

        NovelScreen(
            state = successState,
            isFromSource = fromSource,
            snackbarHostState = screenModel.snackbarHostState,
            onBack = navigator::pop,
            onStartReading = startChapter?.let { chapter ->
                { navigator.push(NovelReaderScreen(chapter.id)) }
            },
            isReading = isReading,
            onToggleFavorite = screenModel::toggleFavorite,
            onRefresh = screenModel::refreshChapters,
            onSearch = { query, global ->
                coroutineScope.launch {
                    performSearch(
                        navigator = navigator,
                        query = query,
                        global = global,
                    )
                }
            },
            onToggleAllChaptersRead = screenModel::toggleAllChaptersRead,
            onShare = if (canOpenNovelWebView) {
                {
                    coroutineScope.launch {
                        val resolvedUrl = resolveNovelEntryWebUrl(
                            novelUrl = rawNovelUrl,
                            source = successState.source,
                        )
                        if (resolvedUrl == null) {
                            context.toast("Unable to share title link")
                            return@launch
                        }
                        shareNovel(context, resolvedUrl)
                    }
                }
            } else {
                null
            },
            onWebView = openInWebViewAction,
            onSourceSettings = if (actionAvailability.showSourceSettings) {
                { navigator.push(NovelSourcePreferencesScreen(successState.source.id)) }
            } else {
                null
            },
            onMigrateClicked = {
                navigator.push(MigrateNovelSearchScreen(successState.novel.id))
            }.takeIf { actionAvailability.showMigrate },
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    screenModel.showTrackDialog()
                }
            },
            trackingCount = successState.trackingCount,
            onOpenBatchDownloadDialog = { showBatchDownloadDialog = true },
            onOpenTranslatedDownloadDialog = {
                showTranslatedDownloadDialog = true
            }.takeIf { isTranslatorEnabled },
            onOpenEpubExportDialog = { showEpubExportDialog = true },
            onChapterClick = { chapterId ->
                if (screenModel.isAnyChapterSelected) {
                    screenModel.toggleSelection(chapterId)
                } else {
                    navigator.push(NovelReaderScreen(chapterId))
                }
            },
            onChapterReadToggle = screenModel::toggleChapterRead,
            onChapterBookmarkToggle = screenModel::toggleChapterBookmark,
            onChapterDownloadToggle = screenModel::toggleChapterDownload,
            chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
            chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
            onChapterSwipe = screenModel::chapterSwipe,
            onFilterButtonClicked = screenModel::showSettingsDialog,
            scanlatorChapterCounts = successState.scanlatorChapterCounts,
            selectedScanlator = successState.selectedScanlator,
            onScanlatorSelected = screenModel::selectScanlator,
            chapterPageEnabled = successState.chapterPageEnabled,
            chapterPageCurrent = successState.chapterPageCurrent,
            chapterPageTotal = successState.chapterPageTotal,
            chapterPageLoading = successState.chapterPageLoading,
            onChapterPageChange = screenModel::selectChapterPage,
            onChapterLongClick = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onMultiBookmarkClicked = screenModel::bookmarkChapters,
            onMultiMarkAsReadClicked = screenModel::markChaptersRead,
            onMultiDownloadClicked = screenModel::downloadSelectedChapters,
            onMultiDeleteClicked = screenModel::deleteDownloadedSelectedChapters,
            onSaveScrollPosition = screenModel::saveScrollPosition,
        )

        if (showBatchDownloadDialog) {
            NovelBatchDownloadDialog(
                onDismissRequest = { showBatchDownloadDialog = false },
                onSelectChapters = {
                    val candidates = screenModel.getBatchDownloadCandidates(
                        onlyNotDownloaded = true,
                    )
                    if (candidates.isEmpty()) {
                        context.toast(context.contextStringResource(AYMR.strings.novel_download_no_available))
                        return@NovelBatchDownloadDialog
                    }
                    batchPickerChapters = candidates
                    showBatchDownloadDialog = false
                    showBatchChapterPickerDialog = true
                },
                onActionSelected = { action, amount ->
                    screenModel.runDownloadAction(action, amount)
                    showBatchDownloadDialog = false
                },
            )
        }

        if (showBatchChapterPickerDialog) {
            NovelDownloadChapterPickerDialog(
                title = stringResource(AYMR.strings.novel_download_select_chapters_title),
                chapters = batchPickerChapters,
                onDismissRequest = { showBatchChapterPickerDialog = false },
                onConfirm = { selectedChapterIds ->
                    val added = screenModel.runDownloadForChapterIds(selectedChapterIds)
                    if (added == 0) {
                        context.toast(context.contextStringResource(AYMR.strings.novel_download_no_available))
                    }
                    showBatchChapterPickerDialog = false
                },
            )
        }

        if (showTranslatedDownloadDialog) {
            NovelTranslatedDownloadDialog(
                onDismissRequest = { showTranslatedDownloadDialog = false },
                onSelectChapters = { format ->
                    translatedPickerFormat = format
                    val candidates = screenModel.getTranslatedDownloadCandidates(
                        format = format,
                        onlyNotDownloaded = true,
                    )
                    if (candidates.isEmpty()) {
                        context.toast(
                            context.contextStringResource(AYMR.strings.novel_translated_download_no_available),
                        )
                        return@NovelTranslatedDownloadDialog
                    }
                    translatedPickerChapters = candidates
                    showTranslatedDownloadDialog = false
                    showTranslatedChapterPickerDialog = true
                },
                onActionSelected = { action, amount, format ->
                    val added = screenModel.runTranslatedDownloadAction(
                        action = action,
                        amount = amount,
                        format = format,
                    )
                    if (added == 0) {
                        context.toast(
                            context.contextStringResource(AYMR.strings.novel_translated_download_no_available),
                        )
                    }
                    showTranslatedDownloadDialog = false
                },
            )
        }

        if (showTranslatedChapterPickerDialog) {
            NovelDownloadChapterPickerDialog(
                title = stringResource(AYMR.strings.novel_translated_download_select_title),
                chapters = translatedPickerChapters,
                onDismissRequest = { showTranslatedChapterPickerDialog = false },
                onConfirm = { selectedChapterIds ->
                    val added = screenModel.runTranslatedDownloadForChapterIds(
                        chapterIds = selectedChapterIds,
                        format = translatedPickerFormat,
                    )
                    if (added == 0) {
                        context.toast(
                            context.contextStringResource(AYMR.strings.novel_translated_download_no_available),
                        )
                    }
                    showTranslatedChapterPickerDialog = false
                },
            )
        }

        if (showEpubExportDialog) {
            NovelEpubExportDialog(
                chapterCount = successState.chapters.size,
                initialDestinationTreeUri = epubExportPreferences.destinationTreeUri,
                initialApplyReaderTheme = epubExportPreferences.applyReaderTheme,
                initialIncludeCustomCss = epubExportPreferences.includeCustomCss,
                initialIncludeCustomJs = epubExportPreferences.includeCustomJs,
                onDismissRequest = { showEpubExportDialog = false },
                onExportClicked = {
                        downloadedOnly,
                        startChapter,
                        endChapter,
                        destinationTreeUri,
                        applyReaderTheme,
                        includeCustomCss,
                        includeCustomJs,
                    ->
                    showEpubExportDialog = false
                    coroutineScope.launch {
                        screenModel.saveEpubExportPreferences(
                            destinationTreeUri = destinationTreeUri,
                            applyReaderTheme = applyReaderTheme,
                            includeCustomCss = includeCustomCss,
                            includeCustomJs = includeCustomJs,
                        )

                        val exportFile = screenModel.exportAsEpub(
                            downloadedOnly = downloadedOnly,
                            startChapter = startChapter,
                            endChapter = endChapter,
                            destinationTreeUri = destinationTreeUri,
                            applyReaderTheme = applyReaderTheme,
                            includeCustomCss = includeCustomCss,
                            includeCustomJs = includeCustomJs,
                        )
                        if (exportFile == null) {
                            context.toast(context.contextStringResource(AYMR.strings.novel_export_failed))
                            return@launch
                        }
                        if (destinationTreeUri.isNotBlank()) {
                            context.toast(context.contextStringResource(AYMR.strings.novel_export_saved_to_folder))
                            return@launch
                        }
                        shareNovelFile(context, exportFile)
                    }
                },
            )
        }

        when (successState.dialog) {
            null -> Unit
            NovelScreenModel.Dialog.SettingsSheet -> {
                NovelChapterSettingsDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    novel = successState.novel,
                    onDownloadFilterChanged = screenModel::setDownloadedFilter,
                    onUnreadFilterChanged = screenModel::setUnreadFilter,
                    onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                    onSortModeChanged = screenModel::setSorting,
                    onDisplayModeChanged = screenModel::setDisplayMode,
                    onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
                    onResetToDefault = screenModel::resetToDefaultSettings,
                )
            }
            NovelScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = MangaTrackInfoDialogHomeScreen(
                        mangaId = successState.novel.id,
                        mangaTitle = successState.novel.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is MangaTrackInfoDialogHomeScreen },
                    onDismissRequest = screenModel::dismissDialog,
                )
            }
        }
    }

    private suspend fun performSearch(
        navigator: cafe.adriel.voyager.navigator.Navigator,
        query: String,
        global: Boolean,
    ) {
        if (global) {
            navigator.push(GlobalNovelSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                NovelLibraryTab.search(query)
            }
            is BrowseNovelSourceScreen -> {
                navigator.pop()
                navigator.push(BrowseNovelSourceScreen(previousController.sourceId, query))
            }
        }
    }

    private fun openNovelInWebView(
        navigator: cafe.adriel.voyager.navigator.Navigator,
        url: String,
        title: String?,
        sourceId: Long?,
    ) {
        navigator.push(
            WebViewScreen(
                url = url,
                initialTitle = title,
                sourceId = sourceId,
            ),
        )
    }

    private fun shareNovel(context: Context, url: String) {
        try {
            val intent = url.toUri().toShareIntent(context, type = "text/plain")
            context.startActivity(
                Intent.createChooser(
                    intent,
                    context.contextStringResource(MR.strings.action_share),
                ),
            )
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    private fun shareNovelFile(context: Context, file: java.io.File) {
        runCatching {
            val uri = file.getUriCompat(context)
            context.startActivity(uri.toShareIntent(context, type = "application/epub+zip"))
        }.onFailure {
            context.toast(it.message)
        }
    }
}

internal data class NovelEntryActionAvailability(
    val showSourceSettings: Boolean,
    val showMigrate: Boolean,
)

internal fun resolveNovelEntryActionAvailability(
    isFavorite: Boolean,
    isSourceConfigurable: Boolean,
): NovelEntryActionAvailability {
    return NovelEntryActionAvailability(
        showSourceSettings = isSourceConfigurable,
        showMigrate = isFavorite,
    )
}

internal fun resolveNovelNeedsWebViewLoginHint(
    novel: DomainNovel,
    source: NovelSource,
    chaptersCount: Int,
    canOpenWebView: Boolean,
    isRefreshing: Boolean,
): Boolean {
    if (!canOpenWebView || isRefreshing || chaptersCount > 0) return false
    val sourceSupportsWeb = source is NovelWebUrlSource || source is NovelSiteSource
    val hasAbsoluteUrl = novel.url.toHttpUrlOrNull() != null
    val hasRelativePathUrl = novel.url.isNotBlank() && !hasAbsoluteUrl
    if (!sourceSupportsWeb && !hasAbsoluteUrl) return false

    return !novel.initialized ||
        novel.description.isNullOrBlank() ||
        hasRelativePathUrl
}

internal fun resolveNovelWebViewLoginHintKey(
    novelId: Long,
    chaptersCount: Int,
    description: String?,
    needsLoginHint: Boolean,
): String? {
    if (!needsLoginHint) return null
    val normalizedDescriptionLength = description?.trim()?.length ?: 0
    return "$novelId:$chaptersCount:$normalizedDescriptionLength"
}

@Composable
internal fun NovelBatchDownloadDialog(
    onDismissRequest: () -> Unit,
    onSelectChapters: () -> Unit,
    onActionSelected: (NovelDownloadAction, Int) -> Unit,
) {
    var customCount by remember { mutableStateOf("20") }
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = colorScheme.primary
    val onAccentColor = colorScheme.onPrimary

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surfaceContainerHigh,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                            radius = 900f,
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(AYMR.strings.novel_batch_download_title),
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val actionItems = listOf(
                        DownloadActionItem(
                            icon = Icons.AutoMirrored.Outlined.ArrowForward,
                            label = stringResource(AYMR.strings.novel_download_next_1),
                            badgeText = "1",
                            onClick = { onActionSelected(NovelDownloadAction.NEXT, 1) },
                        ),
                        DownloadActionItem(
                            icon = Icons.AutoMirrored.Outlined.ArrowForward,
                            label = stringResource(AYMR.strings.novel_download_next_5),
                            badgeText = "5",
                            onClick = { onActionSelected(NovelDownloadAction.NEXT, 5) },
                        ),
                        DownloadActionItem(
                            icon = Icons.AutoMirrored.Outlined.ArrowForward,
                            label = stringResource(AYMR.strings.novel_download_next_10),
                            badgeText = "10",
                            onClick = { onActionSelected(NovelDownloadAction.NEXT, 10) },
                        ),
                        DownloadActionItem(
                            icon = Icons.Outlined.Visibility,
                            label = stringResource(AYMR.strings.action_download_unread),
                            onClick = { onActionSelected(NovelDownloadAction.UNREAD, 0) },
                        ),
                        DownloadActionItem(
                            icon = Icons.Outlined.DoneAll,
                            label = stringResource(AYMR.strings.novel_download_all),
                            onClick = { onActionSelected(NovelDownloadAction.ALL, 0) },
                        ),
                        DownloadActionItem(
                            icon = Icons.Outlined.Download,
                            label = stringResource(AYMR.strings.novel_download_not_downloaded),
                            onClick = { onActionSelected(NovelDownloadAction.NOT_DOWNLOADED, 0) },
                        ),
                        DownloadActionItem(
                            icon = Icons.Outlined.FilterList,
                            label = stringResource(AYMR.strings.novel_download_choose_chapters),
                            onClick = onSelectChapters,
                        ),
                    )

                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        actionItems.forEachIndexed { index, item ->
                            DownloadActionRow(item)
                            if (index != actionItems.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 32.dp),
                                    color = colorScheme.outlineVariant.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = customCount,
                            onValueChange = { customCount = it.filter(Char::isDigit) },
                            label = {
                                Text(
                                    text = stringResource(AYMR.strings.novel_download_custom_count),
                                    color = colorScheme.onSurfaceVariant,
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                val amount = customCount.toIntOrNull()?.coerceAtLeast(1) ?: return@Button
                                onActionSelected(NovelDownloadAction.NEXT, amount)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                        ) {
                            Text(
                                text = stringResource(MR.strings.manga_download),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 4.dp),
                    ) {
                        Text(
                            text = stringResource(MR.strings.action_cancel),
                            color = accentColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovelTranslatedDownloadDialog(
    onDismissRequest: () -> Unit,
    onSelectChapters: (NovelTranslatedDownloadFormat) -> Unit,
    onActionSelected: (NovelDownloadAction, Int, NovelTranslatedDownloadFormat) -> Unit,
) {
    var customCount by remember { mutableStateOf("20") }
    var format by remember { mutableStateOf(NovelTranslatedDownloadFormat.TXT) }
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = colorScheme.primary
    val onAccentColor = colorScheme.onPrimary

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surfaceContainerHigh,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                            radius = 900f,
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(AYMR.strings.novel_translated_download_title),
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .background(
                                color = colorScheme.surfaceVariant.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(2.dp),
                    ) {
                        val selectedColor = accentColor
                        val unselectedColor = Color.Transparent
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { format = NovelTranslatedDownloadFormat.TXT },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (format ==
                                    NovelTranslatedDownloadFormat.TXT
                                ) {
                                    selectedColor
                                } else {
                                    unselectedColor
                                },
                                contentColor = if (format == NovelTranslatedDownloadFormat.TXT) {
                                    onAccentColor
                                } else {
                                    colorScheme.onSurface
                                },
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                        ) {
                            Text(
                                text = if (format == NovelTranslatedDownloadFormat.TXT) {
                                    "* ${stringResource(AYMR.strings.novel_translated_download_format_txt)}"
                                } else {
                                    stringResource(AYMR.strings.novel_translated_download_format_txt)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp),
                            onClick = { format = NovelTranslatedDownloadFormat.DOCX },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (format ==
                                    NovelTranslatedDownloadFormat.DOCX
                                ) {
                                    selectedColor
                                } else {
                                    unselectedColor
                                },
                                contentColor = if (format == NovelTranslatedDownloadFormat.DOCX) {
                                    onAccentColor
                                } else {
                                    colorScheme.onSurface
                                },
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                        ) {
                            Text(
                                text = if (format == NovelTranslatedDownloadFormat.DOCX) {
                                    "* ${stringResource(AYMR.strings.novel_translated_download_format_docx)}"
                                } else {
                                    stringResource(AYMR.strings.novel_translated_download_format_docx)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    val actionItems = listOf(
                        DownloadActionItem(
                            icon = Icons.AutoMirrored.Outlined.ArrowForward,
                            label = stringResource(AYMR.strings.novel_download_next_1),
                            badgeText = "1",
                            onClick = { onActionSelected(NovelDownloadAction.NEXT, 1, format) },
                        ),
                        DownloadActionItem(
                            icon = Icons.AutoMirrored.Outlined.ArrowForward,
                            label = stringResource(AYMR.strings.novel_download_next_5),
                            badgeText = "5",
                            onClick = { onActionSelected(NovelDownloadAction.NEXT, 5, format) },
                        ),
                        DownloadActionItem(
                            icon = Icons.AutoMirrored.Outlined.ArrowForward,
                            label = stringResource(AYMR.strings.novel_download_next_10),
                            badgeText = "10",
                            onClick = { onActionSelected(NovelDownloadAction.NEXT, 10, format) },
                        ),
                        DownloadActionItem(
                            icon = Icons.Outlined.Visibility,
                            label = stringResource(AYMR.strings.action_download_unread),
                            onClick = { onActionSelected(NovelDownloadAction.UNREAD, 0, format) },
                        ),
                        DownloadActionItem(
                            icon = Icons.Outlined.DoneAll,
                            label = stringResource(AYMR.strings.novel_download_all),
                            onClick = { onActionSelected(NovelDownloadAction.ALL, 0, format) },
                        ),
                        DownloadActionItem(
                            icon = Icons.Outlined.Download,
                            label = stringResource(AYMR.strings.novel_download_not_downloaded),
                            onClick = { onActionSelected(NovelDownloadAction.NOT_DOWNLOADED, 0, format) },
                        ),
                        DownloadActionItem(
                            icon = Icons.Outlined.FilterList,
                            label = stringResource(AYMR.strings.novel_translated_download_choose_chapters),
                            onClick = { onSelectChapters(format) },
                        ),
                    )

                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        actionItems.forEachIndexed { index, item ->
                            DownloadActionRow(item)
                            if (index != actionItems.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 32.dp),
                                    color = colorScheme.outlineVariant.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = customCount,
                            onValueChange = { customCount = it.filter(Char::isDigit) },
                            label = {
                                Text(
                                    text = stringResource(AYMR.strings.novel_download_custom_count),
                                    color = colorScheme.onSurfaceVariant,
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                val amount = customCount.toIntOrNull()?.coerceAtLeast(1) ?: return@Button
                                onActionSelected(NovelDownloadAction.NEXT, amount, format)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                        ) {
                            Text(
                                text = stringResource(MR.strings.manga_download),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 4.dp),
                    ) {
                        Text(
                            text = stringResource(MR.strings.action_cancel),
                            color = accentColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private data class DownloadActionItem(
    val icon: ImageVector,
    val label: String,
    val badgeText: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun DownloadActionRow(item: DownloadActionItem) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = item.label,
            color = colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        item.badgeText?.let { badge ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colorScheme.primary.copy(alpha = 0.14f),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
internal fun NovelDownloadChapterPickerDialog(
    title: String,
    chapters: List<DomainNovelChapter>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var selectedChapterIds: Set<Long> by remember(chapters) {
        mutableStateOf(chapters.mapTo(linkedSetOf()) { it.id })
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = chapters,
                    key = { chapter -> chapter.id },
                ) { chapter ->
                    val isSelected = chapter.id in selectedChapterIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedChapterIds = if (isSelected) {
                                    selectedChapterIds - chapter.id
                                } else {
                                    selectedChapterIds + chapter.id
                                }
                            }
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedChapterIds = if (checked) {
                                    selectedChapterIds + chapter.id
                                } else {
                                    selectedChapterIds - chapter.id
                                }
                            },
                        )
                        Text(
                            text = chapter.name.ifBlank {
                                "ID ${chapter.id}"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 12.dp, start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedChapterIds.isNotEmpty(),
                onClick = { onConfirm(selectedChapterIds) },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun NovelEpubExportDialog(
    chapterCount: Int,
    initialDestinationTreeUri: String,
    initialApplyReaderTheme: Boolean,
    initialIncludeCustomCss: Boolean,
    initialIncludeCustomJs: Boolean,
    onDismissRequest: () -> Unit,
    onExportClicked: (
        downloadedOnly: Boolean,
        startChapter: Int?,
        endChapter: Int?,
        destinationTreeUri: String,
        applyReaderTheme: Boolean,
        includeCustomCss: Boolean,
        includeCustomJs: Boolean,
    ) -> Unit,
) {
    val context = LocalContext.current
    var exportAll by remember { mutableStateOf(true) }
    var downloadedOnly by remember { mutableStateOf(true) }
    var startChapterText by remember { mutableStateOf("") }
    var endChapterText by remember { mutableStateOf("") }
    var destinationTreeUri by remember(initialDestinationTreeUri) { mutableStateOf(initialDestinationTreeUri) }
    var applyReaderTheme by remember(initialApplyReaderTheme) { mutableStateOf(initialApplyReaderTheme) }
    var includeCustomCss by remember(initialIncludeCustomCss) { mutableStateOf(initialIncludeCustomCss) }
    var includeCustomJs by remember(initialIncludeCustomJs) { mutableStateOf(initialIncludeCustomJs) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Some devices do not provide persistable grants; URI can still work for current sessions.
            }
            destinationTreeUri = uri.toString()
        }
    }

    val rangeSelection = resolveNovelEpubRangeSelection(
        exportAll = exportAll,
        startChapterText = startChapterText,
        endChapterText = endChapterText,
        chapterCount = chapterCount,
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(AYMR.strings.novel_export_as_epub),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = destinationTreeUri,
                    onValueChange = { destinationTreeUri = it },
                    label = { Text(stringResource(AYMR.strings.novel_export_destination_folder)) },
                    placeholder = { Text(stringResource(AYMR.strings.novel_export_select_folder)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = stringResource(AYMR.strings.novel_export_select_folder),
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_export_all_chapters),
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 12.dp),
                    )
                    Switch(
                        checked = exportAll,
                        onCheckedChange = { exportAll = it },
                    )
                }
                if (!exportAll) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = startChapterText,
                            onValueChange = { startChapterText = it.filter(Char::isDigit) },
                            label = { Text(stringResource(AYMR.strings.novel_export_start_chapter_short)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = endChapterText,
                            onValueChange = { endChapterText = it.filter(Char::isDigit) },
                            label = { Text(stringResource(AYMR.strings.novel_export_end_chapter_short)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_export_downloaded_only),
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 12.dp),
                    )
                    Switch(
                        checked = downloadedOnly,
                        onCheckedChange = { downloadedOnly = it },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_export_apply_reader_theme),
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 12.dp),
                    )
                    Switch(
                        checked = applyReaderTheme,
                        onCheckedChange = { applyReaderTheme = it },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_export_include_custom_css),
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 12.dp),
                    )
                    Switch(
                        checked = includeCustomCss,
                        onCheckedChange = { includeCustomCss = it },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_export_include_custom_js),
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 12.dp),
                    )
                    Switch(
                        checked = includeCustomJs,
                        onCheckedChange = { includeCustomJs = it },
                    )
                }
                Text(
                    text = stringResource(AYMR.strings.novel_export_custom_js_warning),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!rangeSelection.isValid) {
                    Text(
                        text = stringResource(AYMR.strings.novel_export_invalid_range),
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = rangeSelection.isValid,
                onClick = {
                    onExportClicked(
                        downloadedOnly,
                        rangeSelection.startChapter,
                        rangeSelection.endChapter,
                        destinationTreeUri,
                        applyReaderTheme,
                        includeCustomCss,
                        includeCustomJs,
                    )
                },
            ) {
                Text(text = stringResource(AYMR.strings.novel_export_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

internal data class NovelEpubRangeSelection(
    val isValid: Boolean,
    val startChapter: Int?,
    val endChapter: Int?,
)

internal fun resolveNovelEpubRangeSelection(
    exportAll: Boolean,
    startChapterText: String,
    endChapterText: String,
    chapterCount: Int,
): NovelEpubRangeSelection {
    if (exportAll) {
        return NovelEpubRangeSelection(
            isValid = true,
            startChapter = null,
            endChapter = null,
        )
    }

    val startChapter = startChapterText.toIntOrNull()?.takeIf { it > 0 }
    val endChapter = endChapterText.toIntOrNull()?.takeIf { it > 0 }

    if (startChapter == null || endChapter == null) {
        return NovelEpubRangeSelection(
            isValid = false,
            startChapter = null,
            endChapter = null,
        )
    }

    if (startChapter > chapterCount || endChapter > chapterCount || startChapter > endChapter) {
        return NovelEpubRangeSelection(
            isValid = false,
            startChapter = null,
            endChapter = null,
        )
    }

    return NovelEpubRangeSelection(
        isValid = true,
        startChapter = startChapter,
        endChapter = endChapter,
    )
}

internal suspend fun resolveNovelEntryWebUrl(
    novelUrl: String?,
    source: NovelSource?,
): String? {
    val rawUrl = novelUrl?.trim().orEmpty()
    if (rawUrl.isBlank()) return null

    rawUrl.toHttpUrlOrNull()?.let { return it.toString() }

    val sourceResolved = (source as? NovelWebUrlSource)
        ?.getNovelWebUrl(rawUrl)
        ?.trim()
        .orEmpty()
    sourceResolved.toHttpUrlOrNull()?.let { return it.toString() }

    val fallbackSiteUrl = (source as? NovelSiteSource)?.siteUrl?.trim().orEmpty()
    if (fallbackSiteUrl.isNotBlank()) {
        val fallbackResolved = resolveUrl(rawUrl, fallbackSiteUrl).trim()
        fallbackResolved.toHttpUrlOrNull()?.let { return it.toString() }
    }

    return null
}

internal suspend fun resolveNovelLoginWebUrl(
    novelUrl: String?,
    source: NovelSource?,
): String? {
    val rawSiteUrl = (source as? NovelSiteSource)?.siteUrl?.trim().orEmpty()
    if (rawSiteUrl.isNotBlank()) {
        val normalizedSiteUrl = if (rawSiteUrl.startsWith("http://") || rawSiteUrl.startsWith("https://")) {
            rawSiteUrl
        } else {
            "https://$rawSiteUrl"
        }
        normalizedSiteUrl.toHttpUrlOrNull()?.let { return it.toString() }
    }
    return resolveNovelEntryWebUrl(novelUrl, source)
}
