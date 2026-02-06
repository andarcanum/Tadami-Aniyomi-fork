package tachiyomi.domain.category.novel.model

data class NovelCategory(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    val hidden: Boolean,
) {
    companion object {
        fun createDefault(id: Long) = NovelCategory(
            id = id,
            name = "",
            order = 0,
            flags = 0,
            hidden = false,
        )
    }
}