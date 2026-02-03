package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.reader.components.ChapterNavigator
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

private val animationSpec = tween<IntOffset>(200)
private val expandAnimationSpec = tween<IntSize>(200)

@Composable
fun ReaderAppBars(
    visible: Boolean,
    fullscreen: Boolean,

    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,

    viewer: Viewer?,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,

    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,

    // Navigator customization options
    showNavigator: Boolean = true,
    navigatorShowPageNumbers: Boolean = true,
    navigatorShowChapterButtons: Boolean = true,
    navigatorSliderColor: Int = 0,
    navigatorBackgroundAlpha: Int = 90,
    navigatorHeight: ReaderPreferences.NavigatorHeight = ReaderPreferences.NavigatorHeight.NORMAL,
    navigatorCornerRadius: Int = 24,
    navigatorShowTickMarks: Boolean = false,

    // Auto-scroll options
    autoScrollEnabled: Boolean = false,
    autoScrollSpeed: Int = 50,
    onToggleAutoScroll: () -> Unit = {},
    onSpeedChange: (Int) -> Unit = {},
    isAutoScrollExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
) {
    val isRtl = viewer is R2LPagerViewer
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)

    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = animationSpec,
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = animationSpec,
            ),
        ) {
            // Box с фоном, который рисуется под статус-баром
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .clickable(onClick = onClickTopAppBar),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) {
                    AppBar(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
                        title = mangaTitle,
                        subtitle = chapterTitle,
                        navigateUp = navigateUp,
                        actions = {
                            AppBarActions(
                                actions = persistentListOf<AppBar.AppBarAction>().builder()
                                    .apply {
                                        add(
                                            AppBar.Action(
                                                title = stringResource(
                                                    if (bookmarked) {
                                                        MR.strings.action_remove_bookmark
                                                    } else {
                                                        MR.strings.action_bookmark
                                                    },
                                                ),
                                                icon = if (bookmarked) {
                                                    Icons.Outlined.Bookmark
                                                } else {
                                                    Icons.Outlined.BookmarkBorder
                                                },
                                                onClick = onToggleBookmarked,
                                            ),
                                        )
                                        onOpenInWebView?.let {
                                            add(
                                                AppBar.OverflowAction(
                                                    title = stringResource(MR.strings.action_open_in_web_view),
                                                    onClick = it,
                                                ),
                                            )
                                        }
                                        onOpenInBrowser?.let {
                                            add(
                                                AppBar.OverflowAction(
                                                    title = stringResource(MR.strings.action_open_in_browser),
                                                    onClick = it,
                                                ),
                                            )
                                        }
                                        onShare?.let {
                                            add(
                                                AppBar.OverflowAction(
                                                    title = stringResource(MR.strings.action_share),
                                                    onClick = it,
                                                ),
                                            )
                                        }
                                    }
                                    .build(),
                            )
                        },
                    )

                    // Expandable auto-scroll controls
                    AnimatedVisibility(
                        visible = isAutoScrollExpanded,
                        enter = expandVertically(
                            animationSpec = expandAnimationSpec,
                        ) + slideInVertically(
                            initialOffsetY = { -it / 2 },
                            animationSpec = animationSpec,
                        ),
                        exit = shrinkVertically(
                            animationSpec = expandAnimationSpec,
                        ) + slideOutVertically(
                            targetOffsetY = { -it / 2 },
                            animationSpec = animationSpec,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MaterialTheme.padding.medium),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                            ) {
                                IconButton(
                                    onClick = onToggleAutoScroll,
                                    modifier = Modifier.padding(top = 16.dp),
                                ) {
                                    Icon(
                                        imageVector = if (autoScrollEnabled) {
                                            Icons.Outlined.Pause
                                        } else {
                                            Icons.Outlined.PlayArrow
                                        },
                                        contentDescription = if (autoScrollEnabled) {
                                            "Pause auto-scroll"
                                        } else {
                                            "Start auto-scroll"
                                        },
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .align(Alignment.CenterVertically),
                                ) {
                                    Text(
                                        text = "Скорость скролла: $autoScrollSpeed",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Slider(
                                        value = autoScrollSpeed.toFloat(),
                                        onValueChange = { onSpeedChange(it.toInt()) },
                                        valueRange = 1f..100f,
                                        steps = 99,
                                    )
                                }
                            }
                        }
                    }

                    // Expand/collapse arrow button
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = onToggleExpand,
                        ) {
                            Icon(
                                imageVector = if (isAutoScrollExpanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = if (isAutoScrollExpanded) {
                                    "Collapse auto-scroll"
                                } else {
                                    "Expand auto-scroll"
                                },
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = animationSpec,
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = animationSpec,
            ),
        ) {
            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                if (showNavigator) {
                    ChapterNavigator(
                        isRtl = isRtl,
                        onNextChapter = onNextChapter,
                        enabledNext = enabledNext,
                        onPreviousChapter = onPreviousChapter,
                        enabledPrevious = enabledPrevious,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageIndexChange = onPageIndexChange,
                        showPageNumbers = navigatorShowPageNumbers,
                        showChapterButtons = navigatorShowChapterButtons,
                        sliderColor = navigatorSliderColor,
                        backgroundAlpha = navigatorBackgroundAlpha,
                        navigatorHeight = navigatorHeight,
                        cornerRadius = navigatorCornerRadius,
                        showTickMarks = navigatorShowTickMarks,
                    )
                }
                BottomReaderBar(
                    backgroundColor = backgroundColor,
                    readingMode = readingMode,
                    onClickReadingMode = onClickReadingMode,
                    orientation = orientation,
                    onClickOrientation = onClickOrientation,
                    cropEnabled = cropEnabled,
                    onClickCropBorder = onClickCropBorder,
                    onClickSettings = onClickSettings,
                )
            }
        }
    }
}
