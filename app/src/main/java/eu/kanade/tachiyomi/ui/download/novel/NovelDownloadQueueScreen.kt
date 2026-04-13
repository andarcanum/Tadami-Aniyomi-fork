package eu.kanade.tachiyomi.ui.download.novel

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.resolveAuroraTabContainerColor
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState as preferenceCollectAsState

@Composable
fun NovelDownloadQueueScreen(
    contentPadding: PaddingValues,
    state: NovelDownloadQueueScreenModel.State,
    onRefresh: () -> Unit,
    onOpenFolder: () -> Unit,
    nestedScrollConnection: NestedScrollConnection,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme by uiPreferences.appTheme().preferenceCollectAsState()
    val isAurora = theme.isAuroraStyle
    val auroraColors = AuroraTheme.colors
    val primaryTextColor = if (isAurora) {
        auroraColors.textPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryTextColor = if (isAurora) {
        auroraColors.textSecondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val queueCardShape = if (isAurora) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(20.dp)
    }
    val queueCardBorder = if (isAurora) {
        BorderStroke(0.75.dp, auroraMenuRimLightBrush(auroraColors))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = queueCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (isAurora) {
                        resolveAuroraTabContainerColor(auroraColors)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    },
                ),
                border = queueCardBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(MaterialTheme.padding.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    Text(
                        text = "${stringResource(MR.strings.label_download_queue)}: ${state.queueCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryTextColor,
                    )
                    Text(
                        text = "${stringResource(MR.strings.ext_pending)}: ${state.pendingCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                    )
                    Text(
                        text = "${stringResource(MR.strings.ext_downloading)}: ${state.activeCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                    )
                    Text(
                        text = "${stringResource(AYMR.strings.novel_downloads_failed)}: ${state.failedCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                    )
                    Text(
                        text = "${stringResource(
                            AYMR.strings.novel_downloads_saved_on_device,
                        )}: ${state.downloadCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = primaryTextColor,
                    )
                    Text(
                        text = "${stringResource(MR.strings.pref_storage_usage)}: $formattedSize",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                    )
                    Text(
                        text = stringResource(AYMR.strings.novel_downloads_info_with_queue),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        if (isAurora) {
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = auroraColors.accent,
                                    contentColor = auroraColors.textOnAccent,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(MR.strings.ext_update))
                            }
                            Button(
                                onClick = onOpenFolder,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = auroraColors.accent.copy(alpha = 0.15f),
                                    contentColor = auroraColors.accent,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(AYMR.strings.action_open_download_folder))
                            }
                        } else {
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(MR.strings.ext_update))
                            }
                            OutlinedButton(
                                onClick = onOpenFolder,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(AYMR.strings.action_open_download_folder))
                            }
                        }
                    }
                }
            }
        }
    }
}
