package tachiyomi.domain.category.novel.model

data class NovelCategoryUpdate(
    val id: Long,
    val name: String? = null,
    val order: Long? = null,
    val flags: Long? = null,
    val hidden: Boolean? = null,
)
