package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale

@Composable
internal fun NovelPageTurnSnapshotRenderer(
    snapshotKey: NovelPageTurnSnapshotKey,
    snapshotCache: NovelPageTurnSnapshotCache<ImageBitmap>,
    preferCachedBitmap: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!preferCachedBitmap) {
        val graphicsLayer = rememberGraphicsLayer()
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawWithContent {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                },
        ) {
            content()
        }
        return
    }

    val graphicsLayer = rememberGraphicsLayer()
    var bitmap by remember(snapshotKey) {
        mutableStateOf(snapshotCache[snapshotKey])
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            },
    ) {
        content()
    }

    LaunchedEffect(snapshotKey) {
        withFrameNanos { }
        runCatching { graphicsLayer.toImageBitmap() }
            .onSuccess { captured ->
                bitmap = captured
                snapshotCache.put(snapshotKey, captured)
            }
    }
}
