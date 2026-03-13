package eu.kanade.presentation.more.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SettingsScaffold(
    title: String,
    uiStyle: SettingsUiStyle,
    onBackPressed: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    showTopBar: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
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
                Scaffold(
                    containerColor = Color.Transparent,
                    floatingActionButton = floatingActionButton,
                    topBar = {
                        if (showTopBar) {
                            SettingsAuroraTopBar(
                                title = title,
                                titleContent = titleContent,
                                onBackPressed = onBackPressed,
                                actions = actions,
                            )
                        }
                    },
                ) { contentPadding ->
                    SettingsAuroraBackground(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            content(contentPadding)
                        }
                    }
                }
            }
        }
    }
}

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
private fun SettingsAuroraTopBar(
    title: String,
    titleContent: (@Composable () -> Unit)?,
    onBackPressed: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBackPressed != null) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.glass, CircleShape)
                    .border(
                        width = 1.dp,
                        color = colors.accent.copy(alpha = 0.18f),
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(MR.strings.action_bar_up_description),
                    tint = colors.textPrimary,
                )
            }
        }

        if (titleContent != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBackPressed != null) 12.dp else 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                titleContent()
            }
        } else {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBackPressed != null) 12.dp else 4.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
