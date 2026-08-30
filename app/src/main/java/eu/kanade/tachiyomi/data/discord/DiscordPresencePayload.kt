package eu.kanade.tachiyomi.data.discord

import eu.kanade.presentation.util.formatChapterNumber
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@Serializable
internal data class DiscordActivity(
    val name: String,
    val type: Int,
    val details: String? = null,
    val state: String? = null,
    val timestamps: DiscordActivityTimestamps? = null,
)

@Serializable
internal data class DiscordActivityTimestamps(
    val start: Long,
)

@Serializable
internal data class DiscordPresenceData(
    val since: Long = 0,
    val activities: List<DiscordActivity> = emptyList(),
    val status: String = "online",
    val afk: Boolean = false,
)

object DiscordPresencePayload {

    private const val OP_HEARTBEAT = 1
    private const val OP_IDENTIFY = 2
    private const val OP_PRESENCE = 3
    private const val ACTIVITY_WATCHING = 3

    private val json = Json { encodeDefaults = true }

    fun presenceUpdate(info: DiscordPresenceInfo?): String {
        val data = if (info == null) {
            DiscordPresenceData()
        } else {
            DiscordPresenceData(activities = listOf(activity(info)))
        }
        return buildJsonObject {
            put("op", OP_PRESENCE)
            put("d", json.encodeToJsonElement(data))
        }.toString()
    }

    fun identify(token: String): String = buildJsonObject {
        put("op", OP_IDENTIFY)
        put(
            "d",
            buildJsonObject {
                put("token", token)
                put("intents", 0)
                put(
                    "properties",
                    buildJsonObject {
                        put("os", "Android")
                        put("browser", "Tadami")
                        put("device", "Tadami")
                    },
                )
            },
        )
    }.toString()

    fun heartbeat(nonce: Long): String = buildJsonObject {
        put("op", OP_HEARTBEAT)
        put("d", nonce)
    }.toString()

    private fun activity(info: DiscordPresenceInfo): DiscordActivity {
        val label = when (info.mediaKind) {
            DiscordPresenceInfo.MediaKind.ANIME -> "Episode"
            DiscordPresenceInfo.MediaKind.MANGA -> "Chapter"
            DiscordPresenceInfo.MediaKind.NOVEL -> "Chapter"
        }
        val number = info.primaryNumber?.takeIf { it > 0 }
        val details = number?.let { "$label ${formatChapterNumber(it)}" }
        return DiscordActivity(
            name = info.title.take(100),
            type = ACTIVITY_WATCHING,
            details = details,
            state = info.secondaryLine?.takeIf { it.isNotBlank() },
            timestamps = DiscordActivityTimestamps(start = info.startedAt),
        )
    }
}
