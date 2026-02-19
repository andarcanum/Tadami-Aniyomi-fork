@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.novelsource.model

class SNovelChapterImpl : SNovelChapter {

    override lateinit var url: String

    override lateinit var name: String

    override var date_upload: Long = 0

    override var chapter_number: Float = 0f

    override var scanlator: String? = null
}
