package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.util.plus

// H13: constant modifier inputs were re-allocated on every grid recomposition.
private val LIBRARY_GRID_CONTENT_EXTRA_PADDING = PaddingValues(8.dp)
private val LIBRARY_GRID_VERTICAL_ARRANGEMENT = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer)
private val LIBRARY_GRID_HORIZONTAL_ARRANGEMENT = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer)

@Composable
internal fun LazyLibraryGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    adaptiveMinCellDp: Int? = null,
    contentPadding: PaddingValues,
    state: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit,
) {
    val gridCells = remember(columns, adaptiveMinCellDp) {
        if (columns <= 0) {
            GridCells.Adaptive((adaptiveMinCellDp ?: 128).dp)
        } else {
            GridCells.Fixed(columns)
        }
    }
    FastScrollLazyVerticalGrid(
        columns = gridCells,
        state = state,
        modifier = modifier,
        contentPadding = contentPadding + LIBRARY_GRID_CONTENT_EXTRA_PADDING,
        verticalArrangement = LIBRARY_GRID_VERTICAL_ARRANGEMENT,
        horizontalArrangement = LIBRARY_GRID_HORIZONTAL_ARRANGEMENT,
        content = content,
    )
}

fun LazyGridScope.globalSearchItem(
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    if (!searchQuery.isNullOrEmpty()) {
        item(
            span = { GridItemSpan(maxLineSpan) },
            // H13: contentType is a value (Any?), not a lambda - the function object only
            // worked because a non-capturing lambda compiles to a singleton.
            contentType = "library_global_search_item",
        ) {
            GlobalSearchItem(
                searchQuery = searchQuery,
                onClick = onGlobalSearchClicked,
            )
        }
    }
}
