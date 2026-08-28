package eu.kanade.tachiyomi.animesource.model

import java.io.Serializable

data class FeedPage(
    val videos: List<ShortVideoItem>,
    val hasNextPage: Boolean,
) : Serializable
