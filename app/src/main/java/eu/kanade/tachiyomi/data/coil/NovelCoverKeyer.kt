package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.novel.model.Novel as DomainNovel

class NovelKeyer : Keyer<DomainNovel> {
    override fun key(data: DomainNovel, options: Options): String {
        return "novel;${data.id};${data.thumbnailUrl};${data.coverLastModified}"
    }
}

class NovelCoverKeyer : Keyer<NovelCover> {
    override fun key(data: NovelCover, options: Options): String {
        return "novel;${data.novelId};${data.url};${data.lastModified}"
    }
}
