package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALImportListResult(
    val data: ALImportListData,
)

@Serializable
data class ALImportListData(
    @SerialName("MediaListCollection")
    val mediaListCollection: ALImportListCollection? = null,
)

@Serializable
data class ALImportListCollection(
    val lists: List<ALImportListGroup> = emptyList(),
)

@Serializable
data class ALImportListGroup(
    val entries: List<ALImportListEntry> = emptyList(),
)

@Serializable
data class ALImportListEntry(
    val id: Long,
    val status: String? = null,
    val scoreRaw: Int = 0,
    val progress: Int = 0,
    val media: ALImportListMedia? = null,
)

@Serializable
data class ALImportListMedia(
    val id: Long,
    val title: ALImportListTitle? = null,
    val episodes: Long? = null,
    val chapters: Long? = null,
    val coverImage: ALImportListCoverImage? = null,
)

@Serializable
data class ALImportListTitle(
    val romaji: String? = null,
    val english: String? = null,
)

@Serializable
data class ALImportListCoverImage(
    val large: String? = null,
)
