package eu.kanade.presentation.reader.novel

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelReaderScreen(
    state: NovelReaderScreenModel.State.Success,
    onBack: () -> Unit,
    onReadingProgress: (currentIndex: Int, totalItems: Int) -> Unit,
    onOpenPreviousChapter: ((Long) -> Unit)? = null,
    onOpenNextChapter: ((Long) -> Unit)? = null,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showWebView by remember(state.chapter.id) { mutableStateOf(state.textBlocks.isEmpty()) }
    val textListState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.lastSavedIndex
            .coerceIn(0, (state.textBlocks.lastIndex).coerceAtLeast(0)),
    )

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = state.chapter.name.ifBlank { state.novel.title },
                navigateUp = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showWebView = !showWebView }) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.previousChapterId != null || state.nextChapterId != null) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { state.previousChapterId?.let { onOpenPreviousChapter?.invoke(it) } },
                        enabled = state.previousChapterId != null && onOpenPreviousChapter != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(MR.strings.action_previous_chapter))
                    }
                    Button(
                        onClick = { state.nextChapterId?.let { onOpenNextChapter?.invoke(it) } },
                        enabled = state.nextChapterId != null && onOpenNextChapter != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(MR.strings.action_next_chapter))
                    }
                }
            }
        },
    ) { paddingValues ->
        if (showSettings) {
            NovelReaderSettingsDialog(
                sourceId = state.novel.source,
                onDismissRequest = { showSettings = false },
            )
        }

        if (!showWebView && state.textBlocks.isNotEmpty()) {
            LaunchedEffect(textListState.firstVisibleItemIndex, state.textBlocks.size) {
                onReadingProgress(textListState.firstVisibleItemIndex, state.textBlocks.size)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                state = textListState,
            ) {
                itemsIndexed(state.textBlocks) { index, block ->
                    Text(
                        text = block,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = if (index == state.textBlocks.lastIndex) 0.dp else 12.dp),
                    )
                }
            }
        } else {
            val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
            val baseUrl = remember(state.chapter.url) {
                state.chapter.url.takeIf { it.startsWith("http") }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(backgroundColor)
                        settings.javaScriptEnabled = state.enableJs
                        settings.domStorageEnabled = false
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL(baseUrl, state.html, "text/html", "utf-8", null)
                        tag = state.html
                    }
                },
                update = { webView ->
                    if (webView.tag != state.html) {
                        webView.settings.javaScriptEnabled = state.enableJs
                        webView.loadDataWithBaseURL(baseUrl, state.html, "text/html", "utf-8", null)
                        webView.tag = state.html
                    }
                },
            )
        }
    }
}
