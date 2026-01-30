package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EntryDownloadDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    isManga: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val downloadAmount = if (isManga) MR.plurals.download_amount else AYMR.plurals.download_amount_anime
    val downloadUnviewed = if (isManga) MR.strings.download_unread else AYMR.strings.download_unseen
    val options = persistentListOf(
        DownloadAction.NEXT_1_ITEM to pluralStringResource(downloadAmount, 1, 1),
        DownloadAction.NEXT_5_ITEMS to pluralStringResource(downloadAmount, 5, 5),
        DownloadAction.NEXT_10_ITEMS to pluralStringResource(downloadAmount, 10, 10),
        DownloadAction.NEXT_25_ITEMS to pluralStringResource(downloadAmount, 25, 25),
        DownloadAction.UNVIEWED_ITEMS to stringResource(downloadUnviewed),
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = 0.dp, y = 8.dp),
        modifier = modifier,
    ) {
        options.map { (downloadAction, string) ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = string,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                },
                onClick = {
                    onDownloadClicked(downloadAction)
                    onDismissRequest()
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                colors = androidx.compose.material3.MenuDefaults.itemColors(
                    textColor = colors.textPrimary,
                ),
            )
        }
    }
}
