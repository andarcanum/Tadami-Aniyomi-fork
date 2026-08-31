package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.animesource.model.CustomFeedRef
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Bottom sheet listing the user's custom feeds (contract v19). Selecting a feed opens it,
 * while each row also exposes edit/delete actions; "New feed" opens the editor in create mode.
 */
@Composable
fun ReelsCustomFeedsSheet(
    feeds: ImmutableList<CustomFeedRef>,
    isLoading: Boolean,
    error: String?,
    onDismissRequest: () -> Unit,
    onSelectFeed: (CustomFeedRef) -> Unit,
    onNewFeed: () -> Unit,
    onEditFeed: (CustomFeedRef) -> Unit,
    onDeleteFeed: (CustomFeedRef) -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(MR.strings.reels_custom_feeds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onNewFeed) {
                    Text(stringResource(MR.strings.reels_custom_feed_new))
                }
            }

            when {
                isLoading -> Text(
                    text = stringResource(MR.strings.reels_loading_more),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error != null -> Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                )
                feeds.isEmpty() -> Text(
                    text = stringResource(MR.strings.reels_custom_feeds_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(feeds, key = { it.id }) { feed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent, RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectFeed(feed)
                                    onDismissRequest()
                                }
                                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = feed.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                            Text(
                                text = feed.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onEditFeed(feed) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(MR.strings.reels_custom_feed_edit),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(onClick = { onDeleteFeed(feed) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(MR.strings.reels_custom_feed_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
