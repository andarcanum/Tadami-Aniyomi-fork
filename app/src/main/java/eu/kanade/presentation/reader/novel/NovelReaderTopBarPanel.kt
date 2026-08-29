package eu.kanade.presentation.reader.novel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Top reader panel: the app bar and the auto-scroll settings panel.
 *
 * Leaf composable extracted from [NovelReaderContentHost]. Takes narrow data plus grouped
 * callbacks instead of the whole reader state, so unrelated state changes do not recompose it.
 */
@Composable
internal fun NovelReaderTopBarPanel(
    visible: Boolean,
    novelTitle: String,
    chapterName: String,
    chapterBookmarked: Boolean,
    autoScrollExpanded: Boolean,
    usePageReader: Boolean,
    autoScrollIntervalSeconds: Int,
    autoScrollAdaptiveDelay: Boolean,
    autoScrollSpeed: Int,
    chapterEndBehavior: NovelAutoScrollChapterEndBehavior,
    autoScrollEndPauseMs: Long,
    autoScrollEnabled: Boolean,
    showFloatingButton: Boolean,
    adaptiveDelayCharacterCount: () -> Int,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenHighlights: () -> Unit,
    onHapticTap: () -> Unit,
    onIntervalChange: (Int) -> Unit,
    onAdaptiveDelayChange: (Boolean) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onChapterEndBehaviorChange: (NovelAutoScrollChapterEndBehavior) -> Unit,
    onEndPauseMsChange: (Long) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onShowFloatingButtonChange: (Boolean) -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelSlideSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val panelFadeSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val panelBackgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)
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
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    panelBackgroundColor,
                    RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                )
                .statusBarsPadding(),
        ) {
            AppBar(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color.Transparent,
                title = novelTitle,
                subtitle = chapterName,
                navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
                navigateUp = onBack,
                actions = {
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (chapterBookmarked) {
                                Icons.Outlined.Bookmark
                            } else {
                                Icons.Outlined.BookmarkBorder
                            },
                            contentDescription = null,
                        )
                    }
                    var overflowExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = null,
                            )
                        }
                        AuroraEntryDropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(AYMR.strings.novel_highlight_menu_title),
                                leadingIcon = Icons.Outlined.CollectionsBookmark,
                                onClick = {
                                    overflowExpanded = false
                                    onOpenHighlights()
                                },
                            )
                        }
                    }
                },
            )

            NovelReaderAutoScrollPanel(
                expanded = autoScrollExpanded,
                usePageReader = usePageReader,
                autoScrollIntervalSeconds = autoScrollIntervalSeconds,
                autoScrollAdaptiveDelay = autoScrollAdaptiveDelay,
                adaptiveDelayCharacterCount = adaptiveDelayCharacterCount,
                autoScrollSpeed = autoScrollSpeed,
                chapterEndBehavior = chapterEndBehavior,
                autoScrollEndPauseMs = autoScrollEndPauseMs,
                autoScrollEnabled = autoScrollEnabled,
                showFloatingButton = showFloatingButton,
                onHapticTap = onHapticTap,
                onIntervalChange = onIntervalChange,
                onAdaptiveDelayChange = onAdaptiveDelayChange,
                onSpeedChange = onSpeedChange,
                onChapterEndBehaviorChange = onChapterEndBehaviorChange,
                onEndPauseMsChange = onEndPauseMsChange,
                onToggleAutoScroll = onToggleAutoScroll,
                onShowFloatingButtonChange = onShowFloatingButtonChange,
                onToggleExpanded = onToggleExpanded,
            )
        }
    }
}
