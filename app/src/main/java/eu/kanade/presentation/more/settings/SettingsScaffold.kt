package eu.kanade.presentation.more.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

private val AuroraSettingsBottomContentInset = 16.dp

@Composable
fun SettingsScaffold(
    title: String,
    uiStyle: SettingsUiStyle,
    onBackPressed: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    showTopBar: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    topBarCanScroll: () -> Boolean = { true },
    content: @Composable (PaddingValues) -> Unit,
) {
    CompositionLocalProvider(LocalSettingsUiStyle provides uiStyle) {
        when (uiStyle) {
            SettingsUiStyle.Classic -> {
                Scaffold(
                    topBar = if (showTopBar) {
                        { scrollBehavior ->
                            if (titleContent != null) {
                                AppBar(
                                    titleContent = titleContent,
                                    navigateUp = onBackPressed,
                                    actions = actions,
                                    scrollBehavior = scrollBehavior,
                                )
                            } else {
                                AppBar(
                                    title = title,
                                    navigateUp = onBackPressed,
                                    actions = actions,
                                    scrollBehavior = scrollBehavior,
                                )
                            }
                        }
                    } else {
                        { _ -> }
                    },
                    floatingActionButton = floatingActionButton,
                    content = content,
                )
            }
            SettingsUiStyle.Aurora -> {
                val layoutDirection = LocalLayoutDirection.current
                val topBarState = rememberTopAppBarState()
                val topBarScrollBehavior = if (showTopBar) {
                    TopAppBarDefaults.enterAlwaysScrollBehavior(
                        state = topBarState,
                        canScroll = topBarCanScroll,
                    )
                } else {
                    TopAppBarDefaults.pinnedScrollBehavior(topBarState)
                }
                Scaffold(
                    topBarScrollBehavior = topBarScrollBehavior,
                    containerColor = Color.Transparent,
                    floatingActionButton = floatingActionButton,
                    topBar = { scrollBehavior ->
                        if (showTopBar) {
                            AuroraSettingsTopBarChrome(scrollBehavior) {
                                AuroraTopBarLayout(
                                    title = title,
                                    titleContent = titleContent,
                                    onNavigateUp = onBackPressed,
                                    actions = actions,
                                )
                            }
                        }
                    },
                ) { contentPadding ->
                    SettingsAuroraBackground(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            content(
                                PaddingValues(
                                    start = contentPadding.calculateLeftPadding(layoutDirection),
                                    top = contentPadding.calculateTopPadding(),
                                    end = contentPadding.calculateRightPadding(layoutDirection),
                                    bottom = contentPadding.calculateBottomPadding() + AuroraSettingsBottomContentInset,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AuroraSettingsTopBarChrome(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { size ->
                scrollBehavior.state.heightOffsetLimit = resolveAuroraSettingsTopBarHeightOffsetLimit(
                    size.height,
                )
            }
            .graphicsLayer {
                translationY = scrollBehavior.state.heightOffset
            },
    ) {
        content()
    }
}

internal fun resolveAuroraSettingsTopBarHeightOffsetLimit(heightPx: Int): Float {
    return if (heightPx <= 0) {
        0f
    } else {
        -heightPx.toFloat()
    }
}

internal fun LazyListState.canScroll() = canScrollBackward || canScrollForward

internal fun ScrollState.canScroll() = canScrollBackward || canScrollForward

@Composable
internal fun SettingsAuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    AuroraBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.15f)),
            )
            content()
        }
    }
}

@Composable
internal fun AuroraTopBarLayout(
    title: String,
    titleContent: (@Composable () -> Unit)?,
    onNavigateUp: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNavigateUp != null) {
            AuroraTopBarIconButton(
                onClick = onNavigateUp,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(MR.strings.action_bar_up_description),
            )
        }

        if (titleContent != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onNavigateUp != null) 12.dp else 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                titleContent()
            }
        } else {
            AuroraTopBarTitleText(
                title = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onNavigateUp != null) 12.dp else 4.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
internal fun AuroraTopBarIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = AuroraTheme.colors.textPrimary,
) {
    val colors = AuroraTheme.colors
    val tabContainerColor = if (colors.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.05f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .background(tabContainerColor, CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Composable
internal fun AuroraTopBarTitleText(
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Text(
        text = title,
        modifier = modifier,
        style = if (title.length > 15) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.headlineSmall
        },
        color = colors.textPrimary,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
