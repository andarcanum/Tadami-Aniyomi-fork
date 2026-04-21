package eu.kanade.tachiyomi.data.library

data class LibraryUpdateFailure(
    val title: String,
    val sourceName: String,
    val reason: String?,
)
