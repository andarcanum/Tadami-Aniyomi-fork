package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.serialize
import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.toVideoList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

open class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
    val playerId: String? = null,
    val playerLabel: String? = null,
    val dubbingId: String? = null,
    val dubbingLabel: String? = null,
    val sortOrder: Int? = null,
) {
    @Transient
    @Volatile
    var status: State = State.IDLE

    enum class State {
        IDLE,
        LOADING,
        READY,
        ERROR,
    }

    fun copy(
        hosterUrl: String = this.hosterUrl,
        hosterName: String = this.hosterName,
        videoList: List<Video>? = this.videoList,
        internalData: String = this.internalData,
        lazy: Boolean = this.lazy,
        playerId: String? = this.playerId,
        playerLabel: String? = this.playerLabel,
        dubbingId: String? = this.dubbingId,
        dubbingLabel: String? = this.dubbingLabel,
        sortOrder: Int? = this.sortOrder,
    ): Hoster {
        return Hoster(
            hosterUrl = hosterUrl,
            hosterName = hosterName,
            videoList = videoList,
            internalData = internalData,
            lazy = lazy,
            playerId = playerId,
            playerLabel = playerLabel,
            dubbingId = dubbingId,
            dubbingLabel = dubbingLabel,
            sortOrder = sortOrder,
        )
    }

    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"

        private val TRANSLATION_PATTERN = Regex("""\(([^)]+)\)\s*$""")
        private val STRUCTURED_TITLE_DELIMITER = "•"
        private val metadataJson = Json {
            ignoreUnknownKeys = true
        }

        fun List<Video>.toHosterList(): List<Hoster> {
            val metadataGroups = this
                .mapNotNull { video ->
                    PlaybackHosterMetadata.fromVideo(video)?.let { metadata ->
                        metadata.groupKey() to (video to metadata)
                    }
                }
                .groupBy({ it.first }, { it.second })

            if (metadataGroups.isNotEmpty()) {
                return metadataGroups
                    .values
                    .sortedWith(
                        compareBy<List<Pair<Video, PlaybackHosterMetadata>>>(
                            { it.firstOrNull()?.second?.sortOrder ?: Int.MAX_VALUE },
                            { it.firstOrNull()?.second?.playerLabel ?: it.firstOrNull()?.second?.playerId ?: "" },
                            { it.firstOrNull()?.second?.dubbingLabel ?: it.firstOrNull()?.second?.dubbingId ?: "" },
                        ),
                    )
                    .map { items ->
                        val metadata = items.first().second
                        Hoster(
                            hosterUrl = "",
                            hosterName = metadata.dubbingLabel ?: metadata.dubbingId ?: NO_HOSTER_LIST,
                            videoList = items.map { it.first },
                            internalData = metadata.rawJson.orEmpty(),
                            playerId = metadata.playerId,
                            playerLabel = metadata.playerLabel,
                            dubbingId = metadata.dubbingId,
                            dubbingLabel = metadata.dubbingLabel,
                            sortOrder = metadata.sortOrder,
                        )
                    }
            }

            val structuredGroups = this
                .mapNotNull { video ->
                    StructuredPlaybackTitle.parse(video.videoTitle)?.let { parsed ->
                        parsed.groupKey() to (video to parsed)
                    }
                }
                .groupBy({ it.first }, { it.second })

            if (structuredGroups.isNotEmpty()) {
                return structuredGroups
                    .values
                    .sortedWith(
                        compareBy<List<Pair<Video, StructuredPlaybackTitle>>>(
                            { it.firstOrNull()?.second?.sortOrder ?: Int.MAX_VALUE },
                            { it.firstOrNull()?.second?.playerLabel ?: "" },
                            { it.firstOrNull()?.second?.dubbingLabel ?: "" },
                        ),
                    )
                    .map { items ->
                        val parsed = items.first().second
                        Hoster(
                            hosterUrl = "",
                            hosterName = parsed.dubbingLabel,
                            videoList = items.map { (video, title) -> video.copy(videoTitle = title.qualityLabel) },
                            playerId = parsed.playerId,
                            playerLabel = parsed.playerLabel,
                            dubbingId = parsed.dubbingId,
                            dubbingLabel = parsed.dubbingLabel,
                            sortOrder = parsed.sortOrder,
                        )
                    }
            }

            val grouped = this.groupBy { video ->
                TRANSLATION_PATTERN.find(video.videoTitle)?.groupValues?.get(1) ?: NO_HOSTER_LIST
            }

            if (grouped.size <= 1 && grouped.containsKey(NO_HOSTER_LIST)) {
                return listOf(
                    Hoster(
                        hosterUrl = "",
                        hosterName = NO_HOSTER_LIST,
                        videoList = this,
                    ),
                )
            }

            return grouped.map { (translationName, videos) ->
                val cleanedVideos = videos.map { video ->
                    val cleanTitle = video.videoTitle.replace(TRANSLATION_PATTERN, "").trim()
                    video.copy(videoTitle = cleanTitle)
                }
                Hoster(
                    hosterUrl = "",
                    hosterName = translationName,
                    videoList = cleanedVideos,
                )
            }
        }

        internal fun parsePlaybackMetadata(raw: String): PlaybackHosterMetadata? {
            if (raw.isBlank()) return null

            return runCatching {
                val element = metadataJson.parseToJsonElement(raw)
                val obj = when (element) {
                    is JsonObject -> element
                    else -> return null
                }

                val playerId = obj.stringOrNull("playerId") ?: return null
                PlaybackHosterMetadata(
                    playerId = playerId,
                    playerLabel = obj.stringOrNull("playerLabel") ?: playerId,
                    dubbingId = obj.stringOrNull("dubbingId"),
                    dubbingLabel = obj.stringOrNull("dubbingLabel"),
                    sortOrder = obj.intOrNull("sortOrder"),
                    rawJson = raw,
                )
            }.getOrNull()
        }

        private fun JsonObject.stringOrNull(key: String): String? {
            return this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }

        private fun JsonObject.intOrNull(key: String): Int? {
            return this[key]?.jsonPrimitive?.content?.toIntOrNull()
        }

        private data class StructuredPlaybackTitle(
            val playerId: String,
            val playerLabel: String,
            val dubbingId: String,
            val dubbingLabel: String,
            val qualityLabel: String,
            val sortOrder: Int,
        ) {
            fun groupKey(): String = "$playerId|$dubbingId"

            companion object {
                fun parse(rawTitle: String): StructuredPlaybackTitle? {
                    val parts = rawTitle.split(STRUCTURED_TITLE_DELIMITER)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (parts.size < 3) return null

                    val playerLabel = parts[0]
                    val qualityLabel = parts.last()
                    val dubbingLabel = parts.subList(1, parts.lastIndex).joinToString(" $STRUCTURED_TITLE_DELIMITER ")
                    val playerId = when (playerLabel.lowercase()) {
                        "cdn" -> "cdn"
                        "kodik" -> "kodik"
                        "alloha", "aloha" -> "alloha"
                        "parlorate" -> "parlorate"
                        else -> return null
                    }

                    return StructuredPlaybackTitle(
                        playerId = playerId,
                        playerLabel = playerLabel,
                        dubbingId = normalizeId(dubbingLabel),
                        dubbingLabel = dubbingLabel,
                        qualityLabel = qualityLabel,
                        sortOrder = when (playerId) {
                            "cdn" -> 0
                            "kodik" -> 10
                            "parlorate" -> 15
                            "alloha" -> 20
                            else -> Int.MAX_VALUE
                        },
                    )
                }

                private fun normalizeId(value: String): String {
                    return value
                        .lowercase()
                        .replace(Regex("[^\\p{L}\\p{Nd}]+"), "-")
                        .trim('-')
                }
            }
        }
    }
}

data class PlaybackHosterMetadata(
    val playerId: String,
    val playerLabel: String? = null,
    val dubbingId: String? = null,
    val dubbingLabel: String? = null,
    val sortOrder: Int? = null,
    val rawJson: String? = null,
) {
    fun groupKey(): String {
        return buildString {
            append(playerId)
            append('|')
            append(dubbingId ?: dubbingLabel ?: NO_DUBBING)
        }
    }

    companion object {
        private const val NO_DUBBING = "__no_dubbing__"

        fun fromVideo(video: Video): PlaybackHosterMetadata? {
            return Hoster.parsePlaybackMetadata(video.internalData)
        }
    }
}

@Serializable
data class SerializableHoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: String? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
    val playerId: String? = null,
    val playerLabel: String? = null,
    val dubbingId: String? = null,
    val dubbingLabel: String? = null,
    val sortOrder: Int? = null,
) {
    companion object {
        fun List<Hoster>.serialize(): String =
            Json.encodeToString(
                this.map { host ->
                    SerializableHoster(
                        host.hosterUrl,
                        host.hosterName,
                        host.videoList?.serialize(),
                        host.internalData,
                        host.lazy,
                        host.playerId,
                        host.playerLabel,
                        host.dubbingId,
                        host.dubbingLabel,
                        host.sortOrder,
                    )
                },
            )

        fun String.toHosterList(): List<Hoster> =
            Json.decodeFromString<List<SerializableHoster>>(this)
                .map { sHost ->
                    Hoster(
                        sHost.hosterUrl,
                        sHost.hosterName,
                        sHost.videoList?.toVideoList(),
                        sHost.internalData,
                        sHost.lazy,
                        sHost.playerId,
                        sHost.playerLabel,
                        sHost.dubbingId,
                        sHost.dubbingLabel,
                        sHost.sortOrder,
                    )
                }
    }
}
