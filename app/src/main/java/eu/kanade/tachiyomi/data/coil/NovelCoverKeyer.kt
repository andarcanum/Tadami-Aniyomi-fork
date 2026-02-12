package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import tachiyomi.domain.entries.novel.model.NovelCover

class NovelCoverKeyer : Keyer<NovelCover> {
    override fun key(data: NovelCover, options: Options): String {
        return "novel;${data.url};${data.lastModified}"
    }
}

