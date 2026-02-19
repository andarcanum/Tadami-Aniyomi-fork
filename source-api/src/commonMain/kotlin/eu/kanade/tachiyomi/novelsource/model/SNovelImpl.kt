@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.novelsource.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy

class SNovelImpl : SNovel {

    override lateinit var url: String

    override lateinit var title: String

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    override var initialized: Boolean = false
}
