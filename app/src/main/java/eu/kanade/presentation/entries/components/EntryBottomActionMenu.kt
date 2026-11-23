package eu.kanade.presentation.entries.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Input
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NewLabel
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.SwapCalls
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tadami.aurora.R
import dev.chrisbanes.haze.HazeState
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.components.auroraGlassBar
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

@Composable
fun EntryBottomActionMenu(
    visible: Boolean,
    isManga: Boolean,
    modifier: Modifier = Modifier,
    onBookmarkClicked: (() -> Unit)? = null,
    onRemoveBookmarkClicked: (() -> Unit)? = null,
    onFillermarkClicked: (() -> Unit)? = null,
    onRemoveFillermarkClicked: (() -> Unit)? = null,
    onMarkAsViewedClicked: (() -> Unit)? = null,
    onMarkAsUnviewedClicked: (() -> Unit)? = null,
    onMarkPreviousAsViewedClicked: (() -> Unit)? = null,
    onDownloadClicked: (() -> Unit)? = null,
    onDeleteClicked: (() -> Unit)? = null,
    onExternalClicked: (() -> Unit)? = null,
    onInternalClicked: (() -> Unit)? = null,
    onTranslationBatchClicked: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        val scope = rememberCoroutineScope()
        val playerPreferences: PlayerPreferences = Injekt.get()
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = ZeroCornerSize,
                bottomStart = ZeroCornerSize,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val haptic = LocalHapticFeedback.current
            val confirm =
                remember {
                    mutableStateListOf(
                        false, false, false, false, false, false,
                        false, false, false, false, false, false,
                    )
                }
            val confirmRange = 0..<12
            var resetJob: Job? = remember { null }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                (confirmRange).forEach { i -> confirm[i] = i == toConfirmIndex }
                resetJob?.cancel()
                resetJob = scope.launch {
                    delay(1.seconds)
                    if (isActive) confirm[toConfirmIndex] = false
                }
            }
            Row(
                modifier = Modifier
                    .padding(
                        WindowInsets.navigationBars
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues(),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                if (onBookmarkClicked != null) {
                    val bookmark = if (isManga) MR.strings.action_bookmark else AYMR.strings.action_bookmark_episode
                    Button(
                        title = stringResource(bookmark),
                        icon = Icons.Outlined.BookmarkAdd,
                        toConfirm = confirm[0],
                        onLongClick = { onLongClickItem(0) },
                        onClick = onBookmarkClicked,
                    )
                }
                if (onRemoveBookmarkClicked != null) {
                    val removeBookmark = if (isManga) {
                        MR.strings.action_remove_bookmark
                    } else {
                        AYMR.strings.action_remove_bookmark_episode
                    }
                    Button(
                        title = stringResource(removeBookmark),
                        icon = Icons.Outlined.BookmarkRemove,
                        toConfirm = confirm[1],
                        onLongClick = { onLongClickItem(1) },
                        onClick = onRemoveBookmarkClicked,
                    )
                }
                if (onFillermarkClicked != null) {
                    Button(
                        title = stringResource(AYMR.strings.action_fillermark_episode),
                        icon = Icons.Outlined.NewLabel,
                        toConfirm = confirm[2],
                        onLongClick = { onLongClickItem(2) },
                        onClick = onFillermarkClicked,
                    )
                }
                if (onRemoveFillermarkClicked != null) {
                    Button(
                        title = stringResource(AYMR.strings.action_remove_fillermark_episode),
                        icon = Icons.AutoMirrored.Outlined.LabelOff,
                        toConfirm = confirm[3],
                        onLongClick = { onLongClickItem(3) },
                        onClick = onRemoveFillermarkClicked,
                    )
                }
                if (onMarkAsViewedClicked != null) {
                    val viewed = if (isManga) MR.strings.action_mark_as_read else AYMR.strings.action_mark_as_seen
                    Button(
                        title = stringResource(viewed),
                        icon = Icons.Outlined.DoneAll,
                        toConfirm = confirm[4],
                        onLongClick = { onLongClickItem(4) },
                        onClick = onMarkAsViewedClicked,
                    )
                }
                if (onMarkAsUnviewedClicked != null) {
                    val unviewed = if (isManga) MR.strings.action_mark_as_unread else AYMR.strings.action_mark_as_unseen
                    Button(
                        title = stringResource(unviewed),
                        icon = Icons.Outlined.RemoveDone,
                        toConfirm = confirm[5],
                        onLongClick = { onLongClickItem(5) },
                        onClick = onMarkAsUnviewedClicked,
                    )
                }
                if (onMarkPreviousAsViewedClicked != null) {
                    val previousUnviewed = if (isManga) {
                        MR.strings.action_mark_previous_as_read
                    } else {
                        AYMR.strings.action_mark_previous_as_seen
                    }
                    Button(
                        title = stringResource(previousUnviewed),
                        icon = ImageVector.vectorResource(R.drawable.ic_done_prev_24dp),
                        toConfirm = confirm[6],
                        onLongClick = { onLongClickItem(6) },
                        onClick = onMarkPreviousAsViewedClicked,
                    )
                }
                if (onDownloadClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_download),
                        icon = Icons.Outlined.Download,
                        toConfirm = confirm[7],
                        onLongClick = { onLongClickItem(7) },
                        onClick = onDownloadClicked,
                    )
                }
                if (onTranslationBatchClicked != null) {
                    Button(
                        title = stringResource(AYMR.strings.novel_reader_selected_text_translation_action_translate),
                        icon = Icons.Rounded.Translate,
                        toConfirm = confirm[11],
                        onLongClick = { onLongClickItem(11) },
                        onClick = onTranslationBatchClicked,
                    )
                }
                if (onDeleteClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_delete),
                        icon = Icons.Outlined.Delete,
                        toConfirm = confirm[8],
                        onLongClick = { onLongClickItem(8) },
                        onClick = onDeleteClicked,
                    )
                }
                if (!isManga && onExternalClicked != null && !playerPreferences.alwaysUseExternalPlayer().get()) {
                    Button(
                        title = stringResource(AYMR.strings.action_play_externally),
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        toConfirm = confirm[9],
                        onLongClick = { onLongClickItem(9) },
                        onClick = onExternalClicked,
                    )
                }
                if (!isManga && onInternalClicked != null && playerPreferences.alwaysUseExternalPlayer().get()) {
                    Button(
                        title = stringResource(AYMR.strings.action_play_internally),
                        icon = Icons.AutoMirrored.Outlined.Input,
                        toConfirm = confirm[10],
                        onLongClick = { onLongClickItem(10) },
                        onClick = onInternalClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.Button(
    title: String,
    icon: ImageVector,
    toConfirm: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val animatedWeight by animateFloatAsState(
        targetValue = if (toConfirm) 2f else 1f,
        label = "weight",
    )
    val appHaptics = LocalAppHaptics.current
    Column(
        modifier = Modifier
            .size(48.dp)
            .weight(animatedWeight)
            .combinedClickable(
                interactionSource = null,
                indication = ripple(bounded = false),
                onLongClick = onLongClick,
                onClick = {
                    appHaptics.tap()
                    onClick()
                },
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
        )
        AnimatedVisibility(
            visible = toConfirm,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Text(
                text = title,
                overflow = TextOverflow.Visible,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        content?.invoke()
    }
}

@Composable
fun LibraryBottomActionMenu(
    visible: Boolean,
    onChangeCategoryClicked: () -> Unit,
    onTogglePinnedClicked: ((Boolean) -> Unit)? = null,
    isPinned: Boolean = false,
    onMarkAsViewedClicked: () -> Unit,
    onMarkAsUnviewedClicked: () -> Unit,
    onDownloadClicked: ((DownloadAction) -> Unit)?,
    onOpenDownloadDialog: (() -> Unit)? = null,
    onTranslatedDownloadClicked: (() -> Unit)? = null,
    onUpdateClicked: (() -> Unit)? = null,
    updatableCount: Int = 0,
    skippedLocalCount: Int = 0,
    onMigrateClicked: (() -> Unit)? = null,
    onSeriesClicked: (() -> Unit)? = null,
    onDeleteClicked: () -> Unit,
    isManga: Boolean,
    useAuroraGlass: Boolean = false,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    AnimatedVisibility(
        visible = visible,
        enter = if (useAuroraGlass) {
            slideInVertically(animationSpec = tween(durationMillis = 240)) { it } +
                fadeIn(animationSpec = tween(durationMillis = 180))
        } else {
            expandVertically(animationSpec = tween(delayMillis = 300))
        },
        exit = if (useAuroraGlass) {
            slideOutVertically(animationSpec = tween(durationMillis = 200)) { it } +
                fadeOut(animationSpec = tween(durationMillis = 150))
        } else {
            shrinkVertically(animationSpec = tween())
        },
    ) {
        val scope = rememberCoroutineScope()
        val appHaptics = LocalAppHaptics.current
        var pinned by remember(isPinned) { mutableStateOf(isPinned) }
        val haptic = LocalHapticFeedback.current
        val confirm = remember { mutableStateListOf(false, false, false, false, false, false, false, false) }
        var resetJob: Job? = remember { null }
        val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            (0..<confirm.size).forEach { i -> confirm[i] = i == toConfirmIndex }
            resetJob?.cancel()
            resetJob = scope.launch {
                delay(1.seconds)
                if (isActive) confirm[toConfirmIndex] = false
            }
        }
        val moveSeriesToOverflow = onSeriesClicked != null && onTranslatedDownloadClicked != null
        val showUpdateItem = onUpdateClicked != null || skippedLocalCount > 0

        val rowContent: @Composable RowScope.() -> Unit = {
            Button(
                title = stringResource(MR.strings.action_move_category),
                icon = Icons.AutoMirrored.Outlined.Label,
                toConfirm = confirm[0],
                onLongClick = { onLongClickItem(0) },
                onClick = onChangeCategoryClicked,
            )
            if (onTogglePinnedClicked != null) {
                Button(
                    title = stringResource(
                        if (pinned) MR.strings.action_unpin else MR.strings.action_pin,
                    ),
                    icon = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    toConfirm = false,
                    onLongClick = {},
                    onClick = {
                        val desiredPinned = !pinned
                        pinned = desiredPinned
                        onTogglePinnedClicked(desiredPinned)
                    },
                )
            }
            val viewed = if (isManga) MR.strings.action_mark_as_read else AYMR.strings.action_mark_as_seen
            Button(
                title = stringResource(viewed),
                icon = Icons.Outlined.DoneAll,
                toConfirm = confirm[1],
                onLongClick = { onLongClickItem(1) },
                onClick = onMarkAsViewedClicked,
            )
            val unviewed = if (isManga) MR.strings.action_mark_as_unread else AYMR.strings.action_mark_as_unseen
            Button(
                title = stringResource(unviewed),
                icon = Icons.Outlined.RemoveDone,
                toConfirm = confirm[2],
                onLongClick = { onLongClickItem(2) },
                onClick = onMarkAsUnviewedClicked,
            )
            if (onOpenDownloadDialog != null) {
                Button(
                    title = stringResource(MR.strings.action_download),
                    icon = Icons.Outlined.Download,
                    toConfirm = confirm[3],
                    onLongClick = { onLongClickItem(3) },
                    onClick = onOpenDownloadDialog,
                )
            } else if (onDownloadClicked != null) {
                var downloadExpanded by remember { mutableStateOf(false) }
                Button(
                    title = stringResource(MR.strings.action_download),
                    icon = Icons.Outlined.Download,
                    toConfirm = confirm[3],
                    onLongClick = { onLongClickItem(3) },
                    onClick = { downloadExpanded = !downloadExpanded },
                ) {
                    val onDismissRequest = { downloadExpanded = false }
                    EntryDownloadDropdownMenu(
                        expanded = downloadExpanded,
                        onDismissRequest = onDismissRequest,
                        onDownloadClicked = onDownloadClicked,
                        isManga = isManga,
                        useAuroraStyle = useAuroraGlass,
                    )
                }
            }
            if (onTranslatedDownloadClicked != null) {
                Button(
                    title = stringResource(AYMR.strings.novel_translated_download_short),
                    icon = Icons.Rounded.Translate,
                    toConfirm = confirm[4],
                    onLongClick = { onLongClickItem(4) },
                    onClick = onTranslatedDownloadClicked,
                )
            }

            if (onSeriesClicked != null && !moveSeriesToOverflow) {
                Button(
                    title = stringResource(AYMR.strings.series_detail),
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    toConfirm = confirm[7],
                    onLongClick = { onLongClickItem(7) },
                    onClick = onSeriesClicked,
                )
            }

            var overflowMenuOpen by remember { mutableStateOf(false) }
            Button(
                title = stringResource(MR.strings.label_more),
                icon = Icons.Outlined.MoreVert,
                toConfirm = false,
                onLongClick = {},
                onClick = { overflowMenuOpen = true },
            ) {
                val updateEnabled = onUpdateClicked != null && updatableCount > 0
                val updateTitle = if (updatableCount > 0) {
                    stringResource(AYMR.strings.action_update_selected_count, updatableCount)
                } else {
                    stringResource(AYMR.strings.action_update_selected)
                }
                val updateSubtitle = when {
                    skippedLocalCount <= 0 -> null
                    updatableCount == 0 -> stringResource(AYMR.strings.action_update_selected_local_only)
                    else -> stringResource(AYMR.strings.action_update_selected_local_skipped, skippedLocalCount)
                }

                if (useAuroraGlass) {
                    AuroraEntryDropdownMenu(
                        expanded = overflowMenuOpen,
                        onDismissRequest = { overflowMenuOpen = false },
                    ) {
                        if (showUpdateItem) {
                            val titleColor = if (updateEnabled) {
                                auroraColors.textPrimary
                            } else {
                                auroraColors.textSecondary.copy(alpha = 0.55f)
                            }
                            val iconTint = if (updateEnabled) {
                                auroraColors.accent
                            } else {
                                auroraColors.textSecondary.copy(alpha = 0.45f)
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = updateTitle,
                                            color = titleColor,
                                            fontSize = 14.sp,
                                            fontWeight = if (updateEnabled) FontWeight.SemiBold else FontWeight.Normal,
                                            lineHeight = 18.sp,
                                        )
                                        if (updateSubtitle != null) {
                                            Text(
                                                text = updateSubtitle,
                                                color = auroraColors.textSecondary.copy(alpha = 0.78f),
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp,
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        tint = iconTint,
                                    )
                                },
                                enabled = updateEnabled,
                                onClick = {
                                    appHaptics.tap()
                                    overflowMenuOpen = false
                                    onUpdateClicked?.invoke()
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = auroraColors.textPrimary,
                                    disabledTextColor = auroraColors.textSecondary.copy(alpha = 0.55f),
                                ),
                            )
                            HorizontalDivider(
                                color = auroraColors.divider,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            )
                        }
                        if (moveSeriesToOverflow) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(AYMR.strings.series_detail),
                                leadingIcon = Icons.AutoMirrored.Outlined.MenuBook,
                                onClick = {
                                    overflowMenuOpen = false
                                    onSeriesClicked?.invoke()
                                },
                            )
                        }
                        if (onMigrateClicked != null) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(MR.strings.action_migrate),
                                leadingIcon = Icons.Outlined.SwapCalls,
                                onClick = {
                                    overflowMenuOpen = false
                                    onMigrateClicked()
                                },
                            )
                        }
                        val deleteTint = if (auroraColors.isEInk) auroraColors.textPrimary else auroraColors.error
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(MR.strings.action_delete),
                                    color = deleteTint,
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = deleteTint,
                                )
                            },
                            onClick = {
                                appHaptics.tap()
                                overflowMenuOpen = false
                                onDeleteClicked()
                            },
                            colors = MenuDefaults.itemColors(textColor = deleteTint),
                        )
                    }
                } else {
                    DropdownMenu(
                        expanded = overflowMenuOpen,
                        onDismissRequest = { overflowMenuOpen = false },
                    ) {
                        if (showUpdateItem) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(updateTitle)
                                        if (updateSubtitle != null) {
                                            Text(
                                                text = updateSubtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                    )
                                },
                                enabled = updateEnabled,
                                onClick = {
                                    appHaptics.tap()
                                    overflowMenuOpen = false
                                    onUpdateClicked?.invoke()
                                },
                            )
                            HorizontalDivider()
                        }
                        if (moveSeriesToOverflow) {
                            DropdownMenuItem(
                                text = { Text(stringResource(AYMR.strings.series_detail)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    appHaptics.tap()
                                    overflowMenuOpen = false
                                    onSeriesClicked?.invoke()
                                },
                            )
                        }
                        if (onMigrateClicked != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.action_migrate)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.SwapCalls,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    appHaptics.tap()
                                    overflowMenuOpen = false
                                    onMigrateClicked()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.action_delete)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                appHaptics.tap()
                                overflowMenuOpen = false
                                onDeleteClicked()
                            },
                        )
                    }
                }
            }
        }

        if (useAuroraGlass) {
            CompositionLocalProvider(LocalContentColor provides auroraColors.textPrimary) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars
                                .only(WindowInsetsSides.Bottom),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .height(64.dp)
                        .auroraGlassBar(
                            colors = auroraColors,
                            shape = CircleShape,
                            hazeState = hazeState,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = rowContent,
                    )
                }
            }
        } else {
            Surface(
                modifier = modifier,
                shape = MaterialTheme.shapes.large.copy(
                    bottomEnd = ZeroCornerSize,
                    bottomStart = ZeroCornerSize,
                ),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.navigationBars
                                .only(WindowInsetsSides.Bottom),
                        )
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    content = rowContent,
                )
            }
        }
    }
}
