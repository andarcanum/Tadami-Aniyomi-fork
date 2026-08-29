package eu.kanade.tachiyomi.ui.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.launch
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Creator follows management (contract v18). Mirrors [ReelsFavoritesScreen]: a grid with
 * swipe-to-unfollow plus an undo snackbar, with an AppBar action that opens the aggregated
 * Following feed of the given source.
 */
data class ReelsFollowsScreen(val sourceId: Long) : Screen {

    // Same Voyager key rule as ReelsFeedScreen: the key must include the payload that
    // distinguishes two instances, so a popped screen disposes its own ScreenModel.
    override val key: String
        get() = "ReelsFollowsScreen:$sourceId"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ReelsFollowsScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val removedMessage = stringResource(MR.strings.reels_follows_removed_snackbar)
        val undoLabel = stringResource(MR.strings.action_undo)

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.reels_following_feed),
                    navigateUp = navigator::pop,
                    actions = {
                        // Play the aggregated feed of the source this screen was opened from.
                        IconButton(onClick = {
                            navigator.push(ReelsFeedScreen(sourceId = sourceId, followingFeed = true))
                        }) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = stringResource(MR.strings.reels_following_feed),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            if (state.follows.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.reels_follows_empty),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.reels_follows_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(state.follows, key = { it.sourceId.toString() + ":" + it.creator }) { follow ->
                        SwipeToRemoveFollow(
                            follow = follow,
                            onRemove = {
                                screenModel.removeFollow(follow)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = removedMessage,
                                        actionLabel = undoLabel,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        screenModel.restoreFollow(follow)
                                    }
                                }
                            },
                            onClick = {
                                navigator.push(
                                    ReelsFeedScreen(sourceId = follow.sourceId, creator = follow.creator),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToRemoveFollow(
    follow: ReelsFollow,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    // material3 1.4: confirmValueChange is deprecated without replacement — handle the swipe
    // outcome by observing currentValue (same pattern as NovelDictionaryHistoryScreen).
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onRemove()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        // One-directional removal (end-to-start only): the accidental edge-swipe from the
        // left edge is the navigation gesture and must not delete follows (favorites pattern).
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        FollowCell(follow = follow, onClick = onClick)
    }
}

@Composable
private fun FollowCell(
    follow: ReelsFollow,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val initial = follow.creator.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(AuroraTheme.colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                color = Color.Black,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "@${follow.creator}",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            text = stringResource(MR.strings.reels_following),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp),
        )
    }
}
