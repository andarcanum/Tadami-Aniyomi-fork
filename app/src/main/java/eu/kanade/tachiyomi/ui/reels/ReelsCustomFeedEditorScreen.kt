package eu.kanade.tachiyomi.ui.reels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Custom-feed editor (contract v19). Create mode when [feedId] is null; edit mode otherwise.
 * The tag list is the source's catalog (searchable, multi-select); save creates or updates.
 */
data class ReelsCustomFeedEditorScreen(
    val sourceId: Long,
    val feedId: String? = null,
    val initialName: String? = null,
) : Screen {

    override val key: String
        get() = "ReelsCustomFeedEditorScreen:$sourceId:$feedId"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            ReelsCustomFeedEditorScreenModel(sourceId = sourceId, feedId = feedId, initialName = initialName)
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(state.isSaved) {
            if (state.isSaved) navigator.pop()
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(
                        if (feedId != null) MR.strings.reels_custom_feed_edit else MR.strings.reels_custom_feed_new,
                    ),
                    navigateUp = navigator::pop,
                    actions = {
                        TextButton(
                            enabled = state.name.isNotBlank() && !state.isSaving && !state.isLoading,
                            onClick = screenModel::save,
                        ) {
                            Text(stringResource(MR.strings.reels_custom_feed_save))
                        }
                    },
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(contentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = screenModel::setName,
                        label = { Text(stringResource(MR.strings.reels_custom_feed_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TagPicker(
                        allTags = state.allTags,
                        selectedTags = state.selectedTags,
                        onToggle = screenModel::toggleTag,
                        modifier = Modifier.weight(1f),
                    )
                    state.error?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagPicker(
    allTags: List<String>,
    selectedTags: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) allTags else allTags.filter { it.contains(query, ignoreCase = true) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(MR.strings.reels_custom_feed_search_tags)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (filtered.isEmpty()) {
            Text(
                text = stringResource(MR.strings.reels_custom_feeds_empty),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filtered, key = { it }) { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(tag) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Checkbox(checked = tag in selectedTags, onCheckedChange = { onToggle(tag) })
                        Text(text = tag, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
