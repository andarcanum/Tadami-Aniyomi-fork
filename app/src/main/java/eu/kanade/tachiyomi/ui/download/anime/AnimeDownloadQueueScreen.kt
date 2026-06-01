package eu.kanade.tachiyomi.ui.download.anime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.download.DownloadQueueItem
import eu.kanade.presentation.download.DownloadQueueSectionHeader
import eu.kanade.tachiyomi.ui.download.DownloadQueueUiMapper
import kotlinx.coroutines.CoroutineScope
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as preferenceCollectAsState

@Composable
fun AnimeDownloadQueueScreen(
    contentPadding: PaddingValues,
    scope: CoroutineScope,
    screenModel: AnimeDownloadQueueScreenModel,
    downloadList: List<AnimeDownloadHeaderItem>,
    nestedScrollConnection: NestedScrollConnection,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme = uiPreferences.appTheme().preferenceCollectAsState()
    val isAurora = theme.value.isAuroraStyle

    Scaffold(
        containerColor = if (isAurora) Color.Transparent else MaterialTheme.colorScheme.background,
    ) {
        if (downloadList.isEmpty()) {
            EmptyScreen(
                stringRes = MR.strings.information_no_downloads,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .padding(contentPadding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                downloadList.forEach { header ->
                    item(key = "hdr_${header.id}") {
                        DownloadQueueSectionHeader(header = DownloadQueueUiMapper.toSectionHeader(header))
                    }

                    items(header.subItems, key = { (it as AnimeDownloadItem).download.episode.id }) { subItem ->
                        val item = subItem as AnimeDownloadItem
                        val download = item.download

                        // Collect progress and status changes as Compose state to drive recomposition
                        val progressState by download.progressFlow.collectAsStateWithLifecycle(
                            initialValue = download.progress,
                        )
                        val statusState by download.statusFlow.collectAsStateWithLifecycle(
                            initialValue = download.status,
                        )
                        val downloadedBytesState by download.downloadedBytesFlow.collectAsStateWithLifecycle(
                            initialValue = download.downloadedBytes,
                        )
                        val currentSpeedState by download.currentSpeedBytesFlow.collectAsStateWithLifecycle(
                            initialValue = download.currentSpeedBytesPerSecond,
                        )

                        val uiItem = DownloadQueueUiMapper.toUiItem(
                            item = item,
                            progress = progressState,
                            status = statusState,
                            downloadedBytes = downloadedBytesState,
                            currentSpeedBytesPerSecond = currentSpeedState,
                        )
                        DownloadQueueItem(
                            item = uiItem,
                            onMoveToTop = {
                                screenModel.reorder(
                                    reorderWithinHeader(
                                        downloadList = downloadList,
                                        targetHeader = header,
                                        targetDownload = download,
                                        toTop = true,
                                    ),
                                )
                            },
                            onMoveToBottom = {
                                screenModel.reorder(
                                    reorderWithinHeader(
                                        downloadList = downloadList,
                                        targetHeader = header,
                                        targetDownload = download,
                                        toTop = false,
                                    ),
                                )
                            },
                            onCancel = { screenModel.cancel(listOf(download)) },
                        )
                    }
                }
            }
        }
    }
}

private fun reorderWithinHeader(
    downloadList: List<AnimeDownloadHeaderItem>,
    targetHeader: AnimeDownloadHeaderItem,
    targetDownload: eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload,
    toTop: Boolean,
): List<eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload> {
    val reordered = mutableListOf<eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload>()
    downloadList.forEach { header ->
        val downloads = header.subItems.map { (it as AnimeDownloadItem).download }.toMutableList()
        if (header.id == targetHeader.id) {
            downloads.remove(targetDownload)
            if (toTop) {
                downloads.add(0, targetDownload)
            } else {
                downloads.add(targetDownload)
            }
        }
        reordered.addAll(downloads)
    }
    return reordered
}
