package eu.kanade.presentation.novel

import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.novel.model.asNovelCover

internal fun sourceAwareNovelCoverModel(novel: Novel): NovelCover {
    return novel.asNovelCover()
}
