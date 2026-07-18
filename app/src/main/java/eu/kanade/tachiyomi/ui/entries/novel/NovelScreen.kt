package eu.kanade.tachiyomi.ui.entries.novel

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.hasCustomCover
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AuroraSwitchItem
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.components.applyAuroraSheetWindowFx
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.components.EditMetadataSheet
import eu.kanade.presentation.entries.components.aurora.AuroraNoteEditorDialog
import eu.kanade.presentation.entries.novel.NovelChapterSettingsDialog
import eu.kanade.presentation.entries.novel.NovelScreen
import eu.kanade.presentation.entries.novel.NovelTranslationBatchSheet
import eu.kanade.presentation.entries.novel.TranslatedDownloadOptionsDialog
import eu.kanade.presentation.entries.novel.components.NovelCoverDialog
import eu.kanade.presentation.entries.novel.components.NovelTranslatedDownloadFormatSelector
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import eu.kanade.tachiyomi.data.export.novel.NovelEpubExportProgress
import eu.kanade.tachiyomi.data.export.novel.NovelEpubExportResult
import eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.manga.track.MangaTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.toDirectEntryScreenOrNull
import eu.kanade.tachiyomi.ui.entries.suggestions.toGlobalSearchScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.novel.NovelLibraryTab
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt
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
        val updateNovel = remember { Injekt.get<UpdateNovel>() }
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
        var showTranslatedFormatDialog by remember { mutableStateOf(false) }
        var translatedFormatDialogChapterId by remember { mutableStateOf<Long?>(null) }
        var translatedFormatDialogFormat by remember { mutableStateOf(NovelTranslatedDownloadFormat.TXT) }
        var showTranslatedOptionsDialog by remember { mutableStateOf(false) }
        var translatedOptionsChapterId by remember { mutableStateOf<Long?>(null) }
        var showEpubExportDialog by remember { mutableStateOf(false) }
        var epubExportProgress by remember { mutableStateOf<NovelEpubExportProgress?>(null) }
        var showNotesDialog by remember { mutableStateOf(false) }
        var showEditMetadataSheet by remember { mutableStateOf(false) }
        val epubExportPreferences = screenModel.getEpubExportPreferences()
        BackHandler(enabled = screenModel.isAnyChapterSelected) {
            screenModel.toggleAllSelection(false)
        }

        val rawNovelUrl = successState.novel.url
        val canOpenNovelWebView = rawNovelUrl.isNotBlank()
        val isReading = screenModel.isReadingStarted()
        val isSourceConfigurable = successState.isSourceConfigurable
        val actionAvailability = resolveNovelEntryActionAvailability(
            isFavorite = successState.novel.favorite,
            isSourceConfigurable = isSourceConfigurable,
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
            hasCompletedChapterRefresh = successState.hasCompletedChapterRefresh,
        )
        val webViewLoginHintKey = resolveNovelWebViewLoginHintKey(
            novelId = successState.novel.id,
            chaptersCount = successState.chapters.size,
            description = successState.novel.description,
            needsLoginHint = needsWebViewLoginHint,
        )
        val webViewLoginHintUnsetKey = "__novel_web_view_login_hint_unset__"
        var lastEvaluatedWebViewLoginHintKey by rememberSaveable(successState.novel.id) {
            mutableStateOf<String?>(webViewLoginHintUnsetKey)
        }
        var lastShownWebViewLoginHintKey by rememberSaveable(successState.novel.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(webViewLoginHintKey, openWebViewLoginAction) {
            if (lastEvaluatedWebViewLoginHintKey == webViewLoginHintKey) {
                return@LaunchedEffect
            }
            lastEvaluatedWebViewLoginHintKey = webViewLoginHintKey
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
            delay(300)

            val refreshedState = screenModel.state.value as? NovelScreenModel.State.Success
            if (refreshedState == null) {
                logcat {
                    "Novel login hint skipped after stabilization id=${successState.novel.id} source=${successState.source.name} reason=state-not-success"
                }
                return@LaunchedEffect
            }

            val refreshedNeedsWebViewLoginHint = resolveNovelNeedsWebViewLoginHint(
                novel = refreshedState.novel,
                source = refreshedState.source,
                chaptersCount = refreshedState.chapters.size,
                canOpenWebView = openInWebViewAction != null,
                isRefreshing = refreshedState.isRefreshingData,
                hasCompletedChapterRefresh = refreshedState.hasCompletedChapterRefresh,
            )
            val refreshedWebViewLoginHintKey = resolveNovelWebViewLoginHintKey(
                novelId = refreshedState.novel.id,
                chaptersCount = refreshedState.chapters.size,
                description = refreshedState.novel.description,
                needsLoginHint = refreshedNeedsWebViewLoginHint,
            )
            if (refreshedWebViewLoginHintKey == null) {
                logcat {
                    "Novel login hint skipped after stabilization id=${successState.novel.id} " +
                        "source=${successState.source.name} " +
                        "reason=state-updated " +
                        "chapters=${refreshedState.chapters.size} " +
                        "initialized=${refreshedState.novel.initialized} " +
                        "descBlank=${refreshedState.novel.description.isNullOrBlank()}"
                }
                lastShownWebViewLoginHintKey = null
                return@LaunchedEffect
            }
            if (refreshedWebViewLoginHintKey != webViewLoginHintKey) {
                logcat {
                    "Novel login hint skipped after stabilization id=${successState.novel.id} " +
                        "source=${successState.source.name} " +
                        "reason=key-changed " +
                        "oldKey=$webViewLoginHintKey " +
                        "newKey=$refreshedWebViewLoginHintKey"
                }
                return@LaunchedEffect
            }
            if (lastShownWebViewLoginHintKey == refreshedWebViewLoginHintKey) {
                logcat {
                    "Novel login hint suppressed duplicate after stabilization id=${successState.novel.id} " +
                        "key=$refreshedWebViewLoginHintKey"
                }
                return@LaunchedEffect
            }
            lastShownWebViewLoginHintKey = refreshedWebViewLoginHintKey
            logcat {
                "Showing novel login hint id=${refreshedState.novel.id} " +
                    "source=${refreshedState.source.name} " +
                    "url=${refreshedState.novel.url}"
            }
            val result = screenModel.snackbarHostState.showSnackbar(
                message = context.contextStringResource(MR.strings.login_title, refreshedState.source.name),
                actionLabel = context.contextStringResource(MR.strings.action_open_in_web_view),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            logcat {
                "Novel login hint result id=${refreshedState.novel.id} source=${refreshedState.source.name} result=$result"
            }
            if (result == SnackbarResult.ActionPerformed) {
                openWebViewLoginAction?.invoke()
            }
        }

        // Handle folder open intents from screen model
        LaunchedEffect(Unit) {
            screenModel.openTranslatedFolderEvents().collect { uri: android.net.Uri ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "resource/folder")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = uri
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(fallbackIntent)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        NovelScreen(
            state = successState,
            isFromSource = fromSource,
            snackbarHostState = screenModel.snackbarHostState,
            onBack = navigator::pop,
            onStartReading = if (successState.chapters.isEmpty()) {
                null
            } else {
                {
                    coroutineScope.launch {
                        screenModel.getContinueChapter()?.let { chapter ->
                            navigator.push(NovelReaderScreen(chapter.id, successState.source.id))
                        }
                    }
                }
            },
            isReading = isReading,
            onToggleFavorite = screenModel::toggleFavorite,
            onEditCategoryClicked = screenModel::showChangeCategoryDialog.takeIf { successState.novel.favorite },
            onEditNotesClicked = {
                showNotesDialog = true
            },
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
            onGenreClick = { genre ->
                coroutineScope.launch {
                    performGenreSearch(navigator, genre, successState.source)
                }
            },
            onGenreLongClick = null, // handled internally in AuroraImpl as state toggle
            onGenresSearch = { genres ->
                coroutineScope.launch {
                    performGenresSearch(navigator, genres, successState.source)
                }
            },
            onSuggestionClick = { item ->
                coroutineScope.launch {
                    navigator.push(item.toDirectEntryScreenOrNull() ?: item.toGlobalSearchScreen())
                }
            },
            onPosterLongClicked = screenModel::showCoverDialog,
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
                    val srcId = successState.source.id
                    coroutineScope.launch {
                        val current = screenModel.state.value as? NovelScreenModel.State.Success
                        val ch = current?.chapters?.firstOrNull { it.id == chapterId }
                            ?: current?.chapterSourcePreview?.firstOrNull { it.id == chapterId }
                        if (ch != null) {
                            val real = screenModel.resolveChapterForOpen(ch)
                            navigator.push(NovelReaderScreen(real.id, srcId))
                        } else {
                            navigator.push(NovelReaderScreen(chapterId, srcId))
                        }
                    }
                }
            },
            onChapterTranslateClick = { chapterId ->
                if (isTranslatorEnabled) {
                    screenModel.addToTranslationQueue(chapterId)
                }
            },
            onChapterTranslateLongClick = { chapterId ->
                if (isTranslatorEnabled) {
                    screenModel.showTranslationBatchDialog(chapterId)
                }
            },
            onChapterTranslatedDownloadClick = { chapterId ->
                val format = successState.translatedDownloadFormat
                val added = screenModel.runTranslatedDownloadForChapterIds(
                    chapterIds = setOf(chapterId),
                    format = format,
                )
                if (added == 0) {
                    context.toast(
                        context.contextStringResource(AYMR.strings.novel_translated_download_no_available),
                    )
                }
            },
            onChapterTranslatedDownloadLongClick = { chapterId ->
                translatedOptionsChapterId = chapterId
                showTranslatedOptionsDialog = true
            },
            onChapterTranslatedDownloadOpenFolder = { chapterId ->
                screenModel.openTranslatedFolder(chapterId)
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
            onMarkPreviousAsReadClicked = screenModel::markPreviousChapterRead,
            onMultiDownloadClicked = screenModel::downloadSelectedChapters,
            onMultiDeleteClicked = screenModel::deleteDownloadedSelectedChapters,
            onSaveScrollPosition = screenModel::saveScrollPosition,
            onClickEditInfo = { showEditMetadataSheet = true },
            onRetrySuggestions = screenModel::retrySuggestions,
            onOpenSuggestions = {
                val seed = screenModel.getSuggestionSeed()
                    ?: eu.kanade.tachiyomi.data.suggestions.SuggestionSeed(
                        mediaType = eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL,
                        primaryTitle = successState.novel.displayTitle,
                        candidateTitles = emptyList(),
                        description = successState.novel.displayDescription,
                        author = successState.novel.displayAuthor,
                        genres = successState.novel.displayGenre,
                    )
                navigator.push(
                    eu.kanade.tachiyomi.ui.entries.suggestions.EntrySuggestionsScreen(
                        seed = seed,
                        sourceId = successState.novel.source,
                        entryUrl = successState.novel.url,
                    ),
                )
            },
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

        if (showNotesDialog) {
            AuroraNoteEditorDialog(
                initialText = successState.novel.notes,
                onDismissRequest = { showNotesDialog = false },
                onSave = { notes ->
                    coroutineScope.launchIO {
                        updateNovel.await(
                            NovelUpdate(
                                id = successState.novel.id,
                                notes = notes,
                            ),
                        )
                    }
                },
            )
        }

        if (showEditMetadataSheet) {
            EditMetadataSheet(
                onDismissRequest = { showEditMetadataSheet = false },
                currentTitle = successState.novel.displayTitle,
                currentAuthor = successState.novel.displayAuthor,
                currentArtist = null,
                currentDescription = successState.novel.displayDescription,
                currentGenre = successState.novel.displayGenre,
                currentStatus = successState.novel.displayStatus,
                hasArtist = false,
                onSave = { title, author, _, description, tags, status ->
                    screenModel.updateNovelMetadata(title, author, description, tags, status)
                },
                onReset = {
                    screenModel.resetNovelMetadata()
                },
            )
        }

        if (showTranslatedFormatDialog) {
            AlertDialog(
                onDismissRequest = { showTranslatedFormatDialog = false },
                title = { Text(text = stringResource(AYMR.strings.novel_translated_download_title)) },
                text = {
                    NovelTranslatedDownloadFormatSelector(
                        format = translatedFormatDialogFormat,
                        onFormatSelected = { translatedFormatDialogFormat = it },
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = translatedFormatDialogChapterId != null,
                        onClick = {
                            val chapterId = translatedFormatDialogChapterId ?: return@TextButton
                            val format = translatedFormatDialogFormat
                            screenModel.setTranslatedDownloadFormat(format)
                            val added = screenModel.runTranslatedDownloadForChapterIds(
                                chapterIds = setOf(chapterId),
                                format = format,
                            )
                            if (added == 0) {
                                context.toast(
                                    context.contextStringResource(
                                        AYMR.strings.novel_translated_download_no_available,
                                    ),
                                )
                            }
                            showTranslatedFormatDialog = false
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_download))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTranslatedFormatDialog = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        if (showTranslatedOptionsDialog) {
            TranslatedDownloadOptionsDialog(
                onDismissRequest = {
                    showTranslatedOptionsDialog = false
                    translatedOptionsChapterId = null
                },
                onReDownload = {
                    showTranslatedOptionsDialog = false
                    translatedOptionsChapterId?.let { chapterId ->
                        translatedFormatDialogChapterId = chapterId
                        translatedFormatDialogFormat = successState.translatedDownloadFormat
                        showTranslatedFormatDialog = true
                    }
                    translatedOptionsChapterId = null
                },
                onDelete = {
                    showTranslatedOptionsDialog = false
                    translatedOptionsChapterId?.let { chapterId ->
                        screenModel.deleteTranslatedChapter(chapterId)
                    }
                    translatedOptionsChapterId = null
                },
            )
        }

        if (showEpubExportDialog) {
            NovelEpubExportSheet(
                chapters = successState.chapters,
                downloadedChapterIds = successState.downloadedChapterIds,
                initialDestinationTreeUri = epubExportPreferences.destinationTreeUri,
                initialApplyReaderTheme = epubExportPreferences.applyReaderTheme,
                initialIncludeCustomCss = epubExportPreferences.includeCustomCss,
                initialIncludeCustomJs = epubExportPreferences.includeCustomJs,
                progress = epubExportProgress,
                onDismissRequest = {
                    if (epubExportProgress == null) {
                        showEpubExportDialog = false
                    }
                },
                onExportClicked = {
                        downloadedOnly,
                        startChapter,
                        endChapter,
                        destinationTreeUri,
                        applyReaderTheme,
                        includeCustomCss,
                        includeCustomJs,
                    ->
                    coroutineScope.launch {
                        screenModel.saveEpubExportPreferences(
                            destinationTreeUri = destinationTreeUri,
                            applyReaderTheme = applyReaderTheme,
                            includeCustomCss = includeCustomCss,
                            includeCustomJs = includeCustomJs,
                        )
                        epubExportProgress = NovelEpubExportProgress.Preparing(successState.chapters.size)
                        val exportResult = try {
                            screenModel.exportAsEpub(
                                downloadedOnly = downloadedOnly,
                                startChapter = startChapter,
                                endChapter = endChapter,
                                destinationTreeUri = destinationTreeUri,
                                applyReaderTheme = applyReaderTheme,
                                includeCustomCss = includeCustomCss,
                                includeCustomJs = includeCustomJs,
                                onProgress = { progress ->
                                    epubExportProgress = progress
                                },
                            )
                        } finally {
                            epubExportProgress = null
                        }
                        when (exportResult) {
                            is NovelEpubExportResult.Failure -> {
                                context.toast(resolveEpubFailureMessage(context, exportResult))
                                return@launch
                            }
                            is NovelEpubExportResult.Success -> {
                                showEpubExportDialog = false
                                if (destinationTreeUri.isNotBlank()) {
                                    context.toast(resolveEpubSuccessMessage(context, exportResult))
                                    return@launch
                                }
                                shareNovelFile(context, exportResult.cacheFile)
                            }
                        }
                    }
                },
            )
        }

        when (val dialog = successState.dialog) {
            null -> Unit
            NovelScreenModel.Dialog.SettingsSheet -> {
                NovelChapterSettingsDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    novel = successState.novel,
                    downloadedOnly = successState.downloadedOnly,
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
                        isNovelEntry = true,
                        header = stringResource(AYMR.strings.novel_trackers_title),
                    ),
                    enableSwipeDismiss = { it.lastItem is MangaTrackInfoDialogHomeScreen },
                    onDismissRequest = screenModel::dismissDialog,
                )
            }
            is NovelScreenModel.Dialog.TranslationBatchSheet -> {
                val anchorChapter = successState.chapters.find { it.id == dialog.anchorChapterId }
                NovelTranslationBatchSheet(
                    onDismissRequest = screenModel::dismissDialog,
                    chapterTitle = anchorChapter?.name.orEmpty(),
                    anchorChapterId = dialog.anchorChapterId,
                    chapters = successState.processedChapters,
                    selectedChapterIds = successState.selectedChapterIds,
                    downloadedChapterIds = successState.downloadedChapterIds,
                    onStartBatch = { scope, limit, rangeStart, rangeEnd, forceRetranslate ->
                        screenModel.enqueueTranslationBatch(
                            anchorChapterId = dialog.anchorChapterId,
                            scope = scope,
                            limit = limit,
                            rangeStart = rangeStart,
                            rangeEnd = rangeEnd,
                            forceRetranslate = forceRetranslate,
                        )
                    },
                )
            }
            NovelScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { NovelCoverScreenModel(successState.novel.id) }
                val novel by sm.state.collectAsStateWithLifecycle()
                val coverNovel = novel
                if (coverNovel != null) {
                    val getContent = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    NovelCoverDialog(
                        novel = coverNovel,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(coverNovel) {
                            coverNovel.hasCustomCover(sm.coverCache)
                        },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                            }
                        },
                        onDismissRequest = screenModel::dismissDialog,
                    )
                } else {
                    LoadingScreen()
                }
            }
            is NovelScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = {
                        if (screenModel.isFromChangeCategory) {
                            screenModel.isFromChangeCategory = false
                        }
                        screenModel.dismissDialog()
                    },
                    onEditCategories = {
                        navigator.push(CategoriesTab)
                        CategoriesTab.showNovelCategory()
                    },
                    onConfirm = { include, _ ->
                        screenModel.moveNovelToCategoriesAndAddToLibrary(dialog.novel, include)
                    },
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

    /**
     * Performs a genre search using the provided genre name.
     * Always targets the specific source of this novel.
     */
    private suspend fun performGenreSearch(
        navigator: cafe.adriel.voyager.navigator.Navigator,
        genreName: String,
        source: eu.kanade.tachiyomi.novelsource.NovelSource,
    ) {
        val sourceId = source.id
        val existing = navigator.items.firstOrNull { screen ->
            screen is BrowseNovelSourceScreen && screen.sourceId == sourceId
        } as? BrowseNovelSourceScreen

        if (existing != null) {
            navigator.popUntil { it == existing }
            existing.searchGenre(genreName)
            return
        }

        navigator.push(BrowseNovelSourceScreen(sourceId, genreName))
    }

    private suspend fun performGenresSearch(
        navigator: cafe.adriel.voyager.navigator.Navigator,
        genres: List<String>,
        source: eu.kanade.tachiyomi.novelsource.NovelSource,
    ) {
        if (genres.isEmpty()) return
        val sourceId = source.id
        val existing = navigator.items.firstOrNull { screen ->
            screen is BrowseNovelSourceScreen && screen.sourceId == sourceId
        } as? BrowseNovelSourceScreen

        if (existing != null) {
            navigator.popUntil { it == existing }
            existing.searchGenres(genres)
            return
        }

        val newScreen = BrowseNovelSourceScreen(sourceId, null)
        navigator.push(newScreen)
        newScreen.searchGenres(genres)
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

    private fun resolveEpubFailureMessage(
        context: Context,
        result: NovelEpubExportResult.Failure,
    ): String {
        val base = context.contextStringResource(AYMR.strings.novel_export_failed)
        val skipped = result.report?.skippedChapters?.size ?: 0
        return if (skipped > 0) {
            "$base: skipped $skipped selected chapters"
        } else {
            "$base: ${result.reason.name.lowercase().replace('_', ' ')}"
        }
    }

    private fun resolveEpubSuccessMessage(
        context: Context,
        result: NovelEpubExportResult.Success,
    ): String {
        val base = context.contextStringResource(AYMR.strings.novel_export_saved_to_folder)
        val skipped = result.report.skippedChapters.size
        return if (skipped > 0) {
            "$base, skipped $skipped chapters"
        } else {
            base
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
    hasCompletedChapterRefresh: Boolean,
): Boolean {
    if (!canOpenWebView || isRefreshing || !hasCompletedChapterRefresh || chaptersCount > 0) return false
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

                    NovelTranslatedDownloadFormatSelector(
                        format = format,
                        onFormatSelected = { format = it },
                        modifier = Modifier.padding(top = 10.dp),
                    )

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
private fun NovelEpubExportSheet(
    chapters: List<DomainNovelChapter>,
    downloadedChapterIds: Set<Long>,
    initialDestinationTreeUri: String,
    initialApplyReaderTheme: Boolean,
    initialIncludeCustomCss: Boolean,
    initialIncludeCustomJs: Boolean,
    progress: NovelEpubExportProgress?,
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
    val chapterCount = remember(chapters) { chapters.size }
    var exportAll by rememberSaveable { mutableStateOf(true) }
    var downloadedOnly by rememberSaveable { mutableStateOf(true) }
    var startChapterText by rememberSaveable { mutableStateOf("") }
    var endChapterText by rememberSaveable { mutableStateOf("") }
    var destinationTreeUri by rememberSaveable(initialDestinationTreeUri) { mutableStateOf(initialDestinationTreeUri) }
    var applyReaderTheme by rememberSaveable(initialApplyReaderTheme) { mutableStateOf(initialApplyReaderTheme) }
    var includeCustomCss by rememberSaveable(initialIncludeCustomCss) { mutableStateOf(initialIncludeCustomCss) }
    var includeCustomJs by rememberSaveable(initialIncludeCustomJs) { mutableStateOf(initialIncludeCustomJs) }
    val isExporting = progress != null
    val destinationLabel = remember(destinationTreeUri) { resolveTreeUriDisplayName(context, destinationTreeUri) }

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
    val totalSelectedChapters by remember(
        chapters,
        downloadedChapterIds,
        exportAll,
        downloadedOnly,
        rangeSelection,
    ) {
        derivedStateOf {
            calculateEpubSelectedChapterCount(
                chapters = chapters,
                downloadedChapterIds = downloadedChapterIds,
                exportAll = exportAll,
                rangeSelection = rangeSelection,
                downloadedOnly = downloadedOnly,
            )
        }
    }

    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val supportsBlurBehind = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !colors.isEInk
    var sheetReveal by remember { mutableFloatStateOf(0f) }
    val pressInteraction = remember { MutableInteractionSource() }
    val isPressed by pressInteraction.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "epub_export_cta_scale",
    )

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
        enableSwipeDismiss = !isExporting,
        containerColor = when {
            colors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
            !supportsBlurBehind -> colors.surface
            colors.isDark -> Color.Black.copy(alpha = 0.70f)
            else -> Color.White.copy(alpha = 0.88f)
        },
        scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
        onRevealChange = { sheetReveal = it },
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val revealState = rememberUpdatedState(sheetReveal)

        DisposableEffect(window, supportsBlurBehind) {
            val w = window
            if (w != null && supportsBlurBehind) {
                w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                w.setDimAmount(0f)
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = 0 }
            }
            onDispose {
                if (w != null && supportsBlurBehind) {
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                    w.setDimAmount(0f)
                    w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }

        LaunchedEffect(window, supportsBlurBehind) {
            val w = window ?: return@LaunchedEffect
            if (!supportsBlurBehind) return@LaunchedEffect
            snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                .distinctUntilChanged()
                .collect { step -> applyAuroraSheetWindowFx(w, step / 20f) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Drag handle — same language as filter/settings sheets.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.textPrimary.copy(alpha = if (colors.isDark) 0.18f else 0.14f)),
            )

            Text(
                text = stringResource(AYMR.strings.novel_export_as_epub),
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )

            NovelExportGlassSection(
                title = stringResource(AYMR.strings.novel_export_destination_folder),
                icon = Icons.Outlined.Folder,
            ) {
                // No extra fill — sits in the glass section like switch rows, inset from card edges.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isExporting) {
                            appHaptics.tap()
                            folderPicker.launch(null)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (destinationLabel.isNotBlank()) {
                        Text(
                            text = destinationLabel,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(
                        text = stringResource(AYMR.strings.novel_export_select_folder),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            NovelExportGlassSection(
                title = stringResource(AYMR.strings.novel_export_all_chapters),
                icon = Icons.Outlined.FilterList,
            ) {
                AuroraSwitchItem(
                    label = stringResource(AYMR.strings.novel_export_all_chapters),
                    checked = exportAll,
                    enabled = !isExporting,
                    onClick = { exportAll = !exportAll },
                )
                AuroraSwitchItem(
                    label = stringResource(AYMR.strings.novel_export_downloaded_only),
                    checked = downloadedOnly,
                    enabled = !isExporting,
                    onClick = { downloadedOnly = !downloadedOnly },
                )
                AnimatedVisibility(
                    visible = !exportAll,
                    enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                    exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val fieldColors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accent.copy(alpha = 0.55f),
                                unfocusedBorderColor = colors.textPrimary.copy(alpha = 0.18f),
                                focusedLabelColor = colors.accent,
                                unfocusedLabelColor = colors.textSecondary,
                                cursorColor = colors.accent,
                                disabledTextColor = colors.textPrimary.copy(alpha = 0.4f),
                                disabledBorderColor = colors.textPrimary.copy(alpha = 0.10f),
                                disabledLabelColor = colors.textSecondary.copy(alpha = 0.5f),
                            )
                            OutlinedTextField(
                                value = startChapterText,
                                onValueChange = { startChapterText = it.filter(Char::isDigit) },
                                label = { Text(stringResource(AYMR.strings.novel_export_start_chapter_short)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                enabled = !isExporting,
                                colors = fieldColors,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = endChapterText,
                                onValueChange = { endChapterText = it.filter(Char::isDigit) },
                                label = { Text(stringResource(AYMR.strings.novel_export_end_chapter_short)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                enabled = !isExporting,
                                colors = fieldColors,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (!rangeSelection.isValid) {
                            Text(
                                text = stringResource(AYMR.strings.novel_export_invalid_range),
                                color = colors.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Text(
                    text = "Будет обработано: $totalSelectedChapters из $chapterCount",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }

            NovelExportGlassSection(
                title = stringResource(AYMR.strings.novel_export_apply_reader_theme),
                icon = Icons.Outlined.DoneAll,
            ) {
                AuroraSwitchItem(
                    label = stringResource(AYMR.strings.novel_export_apply_reader_theme),
                    checked = applyReaderTheme,
                    enabled = !isExporting,
                    onClick = { applyReaderTheme = !applyReaderTheme },
                )
                AuroraSwitchItem(
                    label = stringResource(AYMR.strings.novel_export_include_custom_css),
                    checked = includeCustomCss,
                    enabled = !isExporting,
                    onClick = { includeCustomCss = !includeCustomCss },
                )
                AuroraSwitchItem(
                    label = stringResource(AYMR.strings.novel_export_include_custom_js),
                    checked = includeCustomJs,
                    enabled = !isExporting,
                    onClick = { includeCustomJs = !includeCustomJs },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (colors.isEInk) {
                                colors.textPrimary.copy(alpha = 0.08f)
                            } else {
                                colors.accent.copy(alpha = 0.10f)
                            },
                        )
                        .border(
                            1.dp,
                            if (colors.isEInk) {
                                colors.textPrimary.copy(alpha = 0.25f)
                            } else {
                                colors.accent.copy(alpha = 0.22f)
                            },
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_export_custom_js_warning),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val ctaEnabled = rangeSelection.isValid && !isExporting
            val ctaShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
                    .clip(ctaShape)
                    .background(
                        when {
                            !ctaEnabled && colors.isEInk -> colors.textPrimary.copy(alpha = 0.25f)
                            !ctaEnabled -> colors.accent.copy(alpha = 0.28f)
                            colors.isEInk -> colors.textPrimary
                            else -> colors.accent
                        },
                        ctaShape,
                    )
                    .clickable(
                        enabled = ctaEnabled,
                        interactionSource = pressInteraction,
                        indication = null,
                    ) {
                        appHaptics.tap()
                        onExportClicked(
                            downloadedOnly,
                            rangeSelection.startChapter,
                            rangeSelection.endChapter,
                            destinationTreeUri,
                            applyReaderTheme,
                            includeCustomCss,
                            includeCustomJs,
                        )
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = if (colors.isEInk) colors.background else colors.textOnAccent,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = resolveEpubProgressLabel(progress),
                            color = if (colors.isEInk) colors.background else colors.textOnAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text(
                            text = stringResource(AYMR.strings.novel_export_confirm),
                            color = if (colors.isEInk) colors.background else colors.textOnAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Text(
                text = stringResource(MR.strings.action_cancel),
                color = if (isExporting) {
                    colors.textSecondary.copy(alpha = 0.4f)
                } else {
                    colors.textSecondary
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !isExporting) {
                        appHaptics.tap()
                        onDismissRequest()
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NovelExportGlassSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(22.dp)
    val frostBase = when {
        colors.isEInk -> colors.surface
        colors.isDark -> Color.White.copy(alpha = 0.06f)
        else -> Color.Black.copy(alpha = 0.04f)
    }
    val rim = if (colors.isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(frostBase)
            .border(1.dp, rim, shape)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = rim,
        )
        content()
    }
}

private fun resolveEpubProgressLabel(progress: NovelEpubExportProgress?): String {
    return when (progress) {
        null -> ""
        is NovelEpubExportProgress.Preparing -> "0/${progress.totalChapters}"
        is NovelEpubExportProgress.ChapterProcessed -> "${progress.current}/${progress.total}"
        NovelEpubExportProgress.Finalizing -> "..."
        is NovelEpubExportProgress.Done -> "100%"
    }
}

private fun resolveTreeUriDisplayName(
    context: Context,
    treeUri: String,
): String {
    if (treeUri.isBlank()) return ""
    val parsed = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return treeUri
    val displayName = runCatching { DocumentFile.fromTreeUri(context, parsed)?.name }.getOrNull()
    if (!displayName.isNullOrBlank()) return displayName
    val segment = parsed.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    return segment ?: treeUri
}

private fun calculateEpubSelectedChapterCount(
    chapters: List<DomainNovelChapter>,
    downloadedChapterIds: Set<Long>,
    exportAll: Boolean,
    rangeSelection: NovelEpubRangeSelection,
    downloadedOnly: Boolean,
): Int {
    if (chapters.isEmpty()) return 0
    val ordered = chapters.sortedBy { it.sourceOrder }
    val scoped = if (exportAll) {
        ordered
    } else if (rangeSelection.isValid && rangeSelection.startChapter != null && rangeSelection.endChapter != null) {
        val start = (rangeSelection.startChapter - 1).coerceAtLeast(0)
        val endExclusive = rangeSelection.endChapter.coerceAtMost(ordered.size)
        if (start >= endExclusive) emptyList() else ordered.subList(start, endExclusive)
    } else {
        emptyList()
    }
    return if (downloadedOnly) {
        scoped.count { it.id in downloadedChapterIds }
    } else {
        scoped.size
    }
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
