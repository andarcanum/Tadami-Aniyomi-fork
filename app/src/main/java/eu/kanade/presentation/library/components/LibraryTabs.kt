package eu.kanade.presentation.library.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.category.visualName
import tachiyomi.domain.category.model.Category
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.util.LocalAppHaptics

@Composable
internal fun LibraryTabs(
    categories: List<Category>,
    pagerState: PagerState,
    getNumberOfItemsForCategory: (Category) -> Int?,
    onTabItemLongClick: ((Int) -> Unit)? = null,
    onTabItemClick: (Int) -> Unit,
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(categories.lastIndex)
    val appHaptics = LocalAppHaptics.current
    Column(
        modifier = Modifier.zIndex(1f),
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = currentPageIndex,
            edgePadding = 0.dp,
            // TODO: use default when width is fixed upstream
            // https://issuetracker.google.com/issues/242879624
            divider = {},
        ) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = currentPageIndex == index,
                    // H10 (Q7): combinedClickable below is the single action detector - the
                    // duplicate Tab.onClick double-fired the haptic + animateScrollToPage per
                    // tap (or, depending on the pointer pass, starved the category long-press).
                    onClick = {},
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            appHaptics.tap()
                            onTabItemClick(index)
                        },
                        onLongClick = if (onTabItemLongClick != null) {
                            {
                                appHaptics.tap()
                                onTabItemLongClick(index)
                            }
                        } else {
                            null
                        },
                    ),
                    text = {
                        TabText(
                            text = category.visualName,
                            badgeCount = getNumberOfItemsForCategory(category),
                        )
                    },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider()
    }
}
