package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.browse.novel.migration.novel.MigrateNovelScreenModel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MigrateNovelScreen(
    navigateUp: () -> Unit,
    title: String?,
    state: MigrateNovelScreenModel.State,
    onClickItem: (Novel) -> Unit,
    onClickCover: (Novel) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = title,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        MigrateNovelContent(
            contentPadding = contentPadding,
            state = state,
            onClickItem = onClickItem,
            onClickCover = onClickCover,
        )
    }
}

@Composable
private fun MigrateNovelContent(
    contentPadding: PaddingValues,
    state: MigrateNovelScreenModel.State,
    onClickItem: (Novel) -> Unit,
    onClickCover: (Novel) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(state.titles) { novel ->
            MigrateNovelItem(
                novel = novel,
                onClickItem = onClickItem,
                onClickCover = onClickCover,
            )
        }
    }
}

@Composable
private fun MigrateNovelItem(
    novel: Novel,
    onClickItem: (Novel) -> Unit,
    onClickCover: (Novel) -> Unit,
    modifier: Modifier = Modifier,
) {
    eu.kanade.presentation.browse.BaseBrowseItem(
        modifier = modifier,
        onClickItem = { onClickItem(novel) },
        onLongClickItem = { onClickItem(novel) },
        icon = {
            ItemCover.Book(
                data = novel.thumbnailUrl,
                modifier = Modifier
                    .size(width = 52.dp, height = 74.dp),
                onClick = { onClickCover(novel) },
            )
        },
        content = {
            Row(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.medium)
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Text(
                    text = novel.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}
