package eu.kanade.tachiyomi.ui.download.novel

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.collectAsState as preferenceCollectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun NovelDownloadQueueScreen(
    contentPadding: PaddingValues,
    state: NovelDownloadQueueScreenModel.State,
    onRefresh: () -> Unit,
    nestedScrollConnection: NestedScrollConnection,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme by uiPreferences.appTheme().preferenceCollectAsState()
    val isAurora = theme.isAuroraStyle
    val auroraColors = AuroraTheme.colors

    Scaffold(
        containerColor = if (isAurora) Color.Transparent else MaterialTheme.colorScheme.background,
    ) {
        if (state.downloadCount == 0 && state.queueCount == 0) {
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
            if (isAurora) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = auroraColors.glass),
                    border = BorderStroke(1.dp, auroraColors.accent.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        Text(
                            text = "${stringResource(MR.strings.label_download_queue)}: ${state.queueCount}",
                            style = MaterialTheme.typography.titleMedium,
                            color = auroraColors.textPrimary,
                        )
                        Text(
                            text = "${stringResource(MR.strings.ext_pending)}: ${state.pendingCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = auroraColors.textSecondary,
                        )
                        Text(
                            text = "${stringResource(MR.strings.ext_downloading)}: ${state.activeCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = auroraColors.textSecondary,
                        )
                        Text(
                            text = "${stringResource(AYMR.strings.novel_downloads_failed)}: ${state.failedCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = auroraColors.textSecondary,
                        )
                        Text(
                            text = "${stringResource(AYMR.strings.novel_downloads_saved_on_device)}: ${state.downloadCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = auroraColors.textPrimary,
                        )
                        Text(
                            text = "${stringResource(MR.strings.pref_storage_usage)}: $formattedSize",
                            style = MaterialTheme.typography.bodyMedium,
                            color = auroraColors.textSecondary,
                        )
                        Text(
                            text = stringResource(AYMR.strings.novel_downloads_info_with_queue),
                            style = MaterialTheme.typography.bodySmall,
                            color = auroraColors.textSecondary,
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = auroraColors.accent,
                                    contentColor = auroraColors.textOnAccent,
                                ),
                            ) {
                                Text(text = stringResource(MR.strings.ext_update))
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "${stringResource(MR.strings.label_download_queue)}: ${state.queueCount}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${stringResource(MR.strings.ext_pending)}: ${state.pendingCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${stringResource(MR.strings.ext_downloading)}: ${state.activeCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${stringResource(AYMR.strings.novel_downloads_failed)}: ${state.failedCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${stringResource(AYMR.strings.novel_downloads_saved_on_device)}: ${state.downloadCount}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${stringResource(MR.strings.pref_storage_usage)}: $formattedSize",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(AYMR.strings.novel_downloads_info_with_queue),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onRefresh) {
                    Text(text = stringResource(MR.strings.ext_update))
                }
            }
        }
    }
}
