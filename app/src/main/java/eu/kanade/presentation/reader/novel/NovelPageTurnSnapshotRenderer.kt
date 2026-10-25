package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * Keep the layer live until page renderers provide a resource/overlay readiness contract.
 * A one-frame bitmap capture can permanently freeze unloaded images or stale interop content.
 * Parameters are retained so existing single-page and spread callers remain source-compatible.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun NovelPageTurnSnapshotRenderer(
    snapshotKey: NovelPageTurnSnapshotKey,
    snapshotCache: NovelPageTurnSnapshotCache<ImageBitmap>,
    preferCachedBitmap: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
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
}
