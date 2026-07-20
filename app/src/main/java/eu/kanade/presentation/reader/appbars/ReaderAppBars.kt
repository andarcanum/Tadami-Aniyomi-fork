package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

private val animationSpec = tween<IntOffset>(200)
private val expandAnimationSpec = tween<IntSize>(200)
private val panelSlideSpec = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private val panelFadeSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

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
    showWebViewButton: Boolean = false,
    showShareButton: Boolean = false,
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
    onClickChapterList: () -> Unit,
    onClickSettings: () -> Unit,

    bottomBarPosition: ReaderPreferences.BottomBarPosition = ReaderPreferences.BottomBarPosition.BOTTOM,

    // Bottom bar button visibility
    visibleButtons: BottomBarButtonFlags = BottomBarButtonFlags(),
    buttonsOrder: List<String> = emptyList(),

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
    showAutoScrollFloatingButton: Boolean = false,
    onToggleAutoScrollFloatingButton: (Boolean) -> Unit = {},
    isAutoScrollExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
) {
    val isRtl = viewer is R2LPagerViewer
    val appHaptics = LocalAppHaptics.current
    val isDark = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isDark) 0.9f else 0.95f)
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = panelSlideSpec,
                ) + fadeIn(animationSpec = panelFadeSpec),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = panelSlideSpec,
                ) + fadeOut(animationSpec = panelFadeSpec),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = backgroundColor,
                            shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                        )
                        .clickable(onClick = {
                            appHaptics.tap()
                            onClickTopAppBar()
                        }),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                    ) {
                        AppBar(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color.Transparent,
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
                                                if (showWebViewButton) {
                                                    add(
                                                        AppBar.Action(
                                                            title = stringResource(MR.strings.action_open_in_web_view),
                                                            icon = Icons.Outlined.Public,
                                                            onClick = it,
                                                        ),
                                                    )
                                                } else {
                                                    add(
                                                        AppBar.OverflowAction(
                                                            title = stringResource(MR.strings.action_open_in_web_view),
                                                            onClick = it,
                                                        ),
                                                    )
                                                }
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
                                                if (showShareButton) {
                                                    add(
                                                        AppBar.Action(
                                                            title = stringResource(MR.strings.action_share),
                                                            icon = Icons.Outlined.Share,
                                                            onClick = it,
                                                        ),
                                                    )
                                                } else {
                                                    add(
                                                        AppBar.OverflowAction(
                                                            title = stringResource(MR.strings.action_share),
                                                            onClick = it,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                        .build(),
                                )
                            },
                        )

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
                            AutoScrollControlsPanel(
                                autoScrollEnabled = autoScrollEnabled,
                                autoScrollSpeed = autoScrollSpeed,
                                onToggleAutoScroll = onToggleAutoScroll,
                                onSpeedChange = onSpeedChange,
                                showAutoScrollFloatingButton = showAutoScrollFloatingButton,
                                onToggleAutoScrollFloatingButton = onToggleAutoScrollFloatingButton,
                                isPagerViewer = viewer is PagerViewer,
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = {
                                    appHaptics.tap()
                                    onToggleExpand()
                                },
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
                                    tint = scheme.onSurface.copy(alpha = 0.75f),
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
                    animationSpec = panelSlideSpec,
                ) + fadeIn(animationSpec = panelFadeSpec),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = panelSlideSpec,
                ) + fadeOut(animationSpec = panelFadeSpec),
            ) {
                Column(
                    modifier = Modifier.navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    if (showNavigator && bottomBarPosition == ReaderPreferences.BottomBarPosition.BOTTOM) {
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
                            isVertical = false,
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
                        onClickChapterList = onClickChapterList,
                        onClickSettings = onClickSettings,
                        visibleButtons = visibleButtons,
                        buttonsOrder = buttonsOrder,
                    )
                }
            }
        }

        if (showNavigator && bottomBarPosition != ReaderPreferences.BottomBarPosition.BOTTOM) {
            val isLeft = bottomBarPosition == ReaderPreferences.BottomBarPosition.LEFT
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(
                    initialOffsetX = { if (isLeft) -it else it },
                    animationSpec = panelSlideSpec,
                ) + fadeIn(animationSpec = panelFadeSpec),
                exit = slideOutHorizontally(
                    targetOffsetX = { if (isLeft) -it else it },
                    animationSpec = panelSlideSpec,
                ) + fadeOut(animationSpec = panelFadeSpec),
                modifier = Modifier.align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd),
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
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
                        isVertical = true,
                    )
                }
            }
        }
    }
}

/**
 * Auto-scroll controls: plain content under the top bar (no nested card).
 * Appears only when the chevron expands the panel.
 */
@Composable
private fun AutoScrollControlsPanel(
    autoScrollEnabled: Boolean,
    autoScrollSpeed: Int,
    onToggleAutoScroll: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    showAutoScrollFloatingButton: Boolean,
    onToggleAutoScrollFloatingButton: (Boolean) -> Unit,
    isPagerViewer: Boolean,
) {
    val appHaptics = LocalAppHaptics.current
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(AYMR.strings.novel_reader_auto_scroll_speed),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
            )
            Text(
                text = "$autoScrollSpeed",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Slider(
            value = autoScrollSpeed.toFloat(),
            onValueChange = { onSpeedChange(it.toInt()) },
            valueRange = 1f..100f,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isPagerViewer) {
            Text(
                text = stringResource(
                    AYMR.strings.reader_auto_scroll_page_time,
                    autoScrollPageDelayMs(autoScrollSpeed) / 1000,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (autoScrollEnabled) scheme.primary else scheme.primary.copy(alpha = 0.18f))
                .clickable {
                    appHaptics.tap()
                    onToggleAutoScroll()
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (autoScrollEnabled) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = if (autoScrollEnabled) scheme.onPrimary else scheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (autoScrollEnabled) MR.strings.action_pause else MR.strings.action_start,
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (autoScrollEnabled) scheme.onPrimary else scheme.primary,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    appHaptics.tap()
                    onToggleAutoScrollFloatingButton(!showAutoScrollFloatingButton)
                }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(AYMR.strings.reader_auto_scroll_floating_button),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = showAutoScrollFloatingButton,
                onCheckedChange = {
                    appHaptics.tap()
                    onToggleAutoScrollFloatingButton(it)
                },
            )
        }
    }
}

private fun autoScrollPageDelayMs(speed: Int): Long {
    val clamped = speed.coerceIn(1, 100)
    return (10_000 - (clamped - 1) * 80).toLong().coerceIn(2_000L, 10_000L)
}
