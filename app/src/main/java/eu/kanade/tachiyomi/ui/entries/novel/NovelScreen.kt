package eu.kanade.tachiyomi.ui.entries.novel

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.entries.novel.NovelChapterSettingsDialog
import eu.kanade.presentation.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

class NovelScreen(
    private val novelId: Long,
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

        if (state is NovelScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as NovelScreenModel.State.Success
        BackHandler(enabled = screenModel.isAnyChapterSelected) {
            screenModel.toggleAllSelection(false)
        }

        val novelUrl = getNovelUrl(successState.novel)
        val chapters = successState.processedChapters
        val resumeChapter = chapters.firstOrNull { it.lastPageRead > 0L && !it.read }
        val startChapter = resumeChapter
            ?: screenModel.getNextUnreadChapter()
            ?: chapters.firstOrNull()
        val isReading = resumeChapter != null

        NovelScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            onBack = navigator::pop,
            onStartReading = startChapter?.let { chapter ->
                { navigator.push(NovelReaderScreen(chapter.id)) }
            },
            isReading = isReading,
            onToggleFavorite = screenModel::toggleFavorite,
            onRefresh = screenModel::refreshChapters,
            onToggleAllChaptersRead = screenModel::toggleAllChaptersRead,
            onShare = novelUrl?.let { { shareNovel(context, it) } },
            onWebView = novelUrl?.let { { openNovelInWebView(navigator, it, successState.novel.title) } },
            onChapterClick = { chapterId ->
                if (screenModel.isAnyChapterSelected) {
                    screenModel.toggleSelection(chapterId)
                } else {
                    navigator.push(NovelReaderScreen(chapterId))
                }
            },
            onChapterReadToggle = screenModel::toggleChapterRead,
            onChapterBookmarkToggle = screenModel::toggleChapterBookmark,
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onChapterLongClick = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onMultiBookmarkClicked = screenModel::bookmarkChapters,
            onMultiMarkAsReadClicked = screenModel::markChaptersRead,
        )

        when (successState.dialog) {
            null -> Unit
            NovelScreenModel.Dialog.SettingsSheet -> {
                NovelChapterSettingsDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    novel = successState.novel,
                    onUnreadFilterChanged = screenModel::setUnreadFilter,
                    onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                    onSortModeChanged = screenModel::setSorting,
                    onDisplayModeChanged = screenModel::setDisplayMode,
                )
            }
        }
    }

    private fun getNovelUrl(novel: Novel?): String? {
        val url = novel?.url ?: return null
        return if (url.startsWith("http")) url else null
    }

    private fun openNovelInWebView(
        navigator: cafe.adriel.voyager.navigator.Navigator,
        url: String,
        title: String?,
    ) {
        navigator.push(
            WebViewScreen(
                url = url,
                initialTitle = title,
                sourceId = null,
            ),
        )
    }

    private fun shareNovel(context: Context, url: String) {
        try {
            val intent = url.toUri().toShareIntent(context, type = "text/plain")
            context.startActivity(
                Intent.createChooser(
                    intent,
                    context.stringResource(MR.strings.action_share),
                ),
            )
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }
}
