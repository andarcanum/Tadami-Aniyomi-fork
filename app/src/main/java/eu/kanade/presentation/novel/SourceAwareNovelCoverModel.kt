package eu.kanade.presentation.novel

import android.content.Context
import coil3.request.ImageRequest
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.novel.model.asNovelCover

internal fun sourceAwareNovelCoverModel(novel: Novel): NovelCover {
    return novel.asNovelCover()
}

internal fun buildNovelCoverImageRequest(
    context: Context,
    novel: Novel,
    configure: ImageRequest.Builder.() -> Unit = {},
): ImageRequest {
    return buildAuroraCoverImageRequest(
        context = context,
        data = sourceAwareNovelCoverModel(novel),
        configure = configure,
    )
}
