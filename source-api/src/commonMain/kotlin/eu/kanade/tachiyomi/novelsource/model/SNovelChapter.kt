@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.novelsource.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SNovelChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var date_upload_raw: String?

    var chapter_number: Float

    var scanlator: String?

    /**
     * Extra metadata associated with the chapter.
     *
     * The JSON object is not visible to users and is intended for internal or source-specific
     * purposes. Apps may define their own namespaced keys (e.g., `"mihon.*"`) for sources to populate.
     *
     * @since tachiyomix 1.6
     */
    var memo: JsonObject
        get() = JsonObject(emptyMap())
        set(value) {}

    fun copyFrom(other: SNovelChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        date_upload_raw = other.date_upload_raw
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        memo = other.memo
    }

    companion object {
        fun create(): SNovelChapter {
            return SNovelChapterImpl()
        }
    }
}
