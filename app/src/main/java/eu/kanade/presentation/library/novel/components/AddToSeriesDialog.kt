package eu.kanade.presentation.library.novel.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.series.novel.model.NovelSeries
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AddToSeriesDialog(
    onDismissRequest: () -> Unit,
    series: List<NovelSeries>,
    onSelect: (NovelSeries) -> Unit,
    onCreateSeries: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        title = {
            Text(stringResource(AYMR.strings.action_add_to_series))
        },
        text = {
            LazyColumn {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCreateSeries)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(AYMR.strings.action_create_series),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(series) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(item)
                                onDismissRequest()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.title,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    )
}
