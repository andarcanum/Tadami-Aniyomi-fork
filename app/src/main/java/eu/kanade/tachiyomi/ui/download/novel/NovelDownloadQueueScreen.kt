package eu.kanade.tachiyomi.ui.download.novel

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun NovelDownloadQueueScreen(
    contentPadding: PaddingValues,
    state: NovelDownloadQueueScreenModel.State,
    onRefresh: () -> Unit,
    nestedScrollConnection: NestedScrollConnection,
) {
    Scaffold {
        if (state.downloadCount == 0) {
            EmptyScreen(
                stringRes = MR.strings.information_no_downloads,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        val context = LocalContext.current
        val formattedSize = Formatter.formatFileSize(context, state.downloadSize)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .padding(contentPadding)
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(
                text = "${stringResource(MR.strings.label_downloaded)}: ${state.downloadCount}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${stringResource(MR.strings.pref_storage_usage)}: $formattedSize",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRefresh) {
                Text(text = stringResource(MR.strings.action_retry))
            }
        }
    }
}
