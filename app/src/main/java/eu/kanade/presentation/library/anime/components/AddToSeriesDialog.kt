package eu.kanade.presentation.library.anime.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.AuroraBlurBehindDialog
import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AddToSeriesDialog(
    onDismissRequest: () -> Unit,
    series: List<AnimeSeries>,
    onSelect: (AnimeSeries) -> Unit,
    onCreateSeries: () -> Unit,
) {
    val colors = AuroraTheme.colors
    AuroraBlurBehindDialog(onDismissRequest = onDismissRequest) {
        Text(
            text = stringResource(AYMR.strings.action_add_to_series),
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCreateSeries)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
                tint = colors.accent,
            )
            Text(
                text = stringResource(AYMR.strings.action_create_series),
                color = colors.accent,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(series) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(item)
                            onDismissRequest()
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
