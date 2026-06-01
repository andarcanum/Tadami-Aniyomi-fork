package eu.kanade.tachiyomi.ui.download.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.download.DownloadQueueItem
import eu.kanade.tachiyomi.ui.download.DownloadQueueUiMapper
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as preferenceCollectAsState

@Composable
fun NovelDownloadQueueScreen(
    contentPadding: PaddingValues,
    screenModel: NovelDownloadQueueScreenModel,
    state: NovelDownloadQueueScreenModel.State,
    nestedScrollConnection: NestedScrollConnection,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme = uiPreferences.appTheme().preferenceCollectAsState()
    val isAurora = theme.value.isAuroraStyle
    val secondaryTextColor = if (isAurora) {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Scaffold(
        containerColor = if (isAurora) Color.Transparent else MaterialTheme.colorScheme.background,
    ) {
        if (state.queueCount == 0) {
            EmptyScreen(
                stringRes = MR.strings.information_no_downloads,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .padding(contentPadding)
                .padding(horizontal = MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            // Queue list header
            if (state.queueTasks.isNotEmpty()) {
                item(key = "novel_queue_header") {
                    Text(
                        text = stringResource(MR.strings.label_download_queue) + " (${state.queueTasks.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                items(state.queueTasks, key = { it.taskId }) { task ->
                    val progress = state.simulatedProgress[task.taskId] ?: 0f
                    val uiItem = DownloadQueueUiMapper.toUiItem(task, progress)
                    DownloadQueueItem(
                        item = uiItem,
                        onCancel = { screenModel.cancel(task.novel.id, task.chapter.id) },
                    )
                }
            }
        }
    }
}
