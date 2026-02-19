package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.source.novel.NovelPluginImage

class NovelPluginImageKeyer : Keyer<NovelPluginImage> {
    override fun key(data: NovelPluginImage, options: Options): String {
        return data.url
    }
}
