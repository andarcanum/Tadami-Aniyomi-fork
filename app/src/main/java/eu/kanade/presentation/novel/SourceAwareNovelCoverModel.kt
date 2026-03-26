package eu.kanade.presentation.novel

import android.content.Context
import coil3.request.ImageRequest
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
    return ImageRequest.Builder(context)
        .data(sourceAwareNovelCoverModel(novel))
        .placeholderMemoryCacheKey(novel.thumbnailUrl)
        .apply(configure)
        .build()
}
