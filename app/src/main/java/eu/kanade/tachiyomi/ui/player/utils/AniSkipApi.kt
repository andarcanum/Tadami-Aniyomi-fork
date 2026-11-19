package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AniSkipApi(
    clientProvider: () -> OkHttpClient = { Injekt.get<NetworkHelper>().client },
    jsonProvider: () -> Json = { Injekt.get() },
) {
    private val client: OkHttpClient by lazy(clientProvider)
    private val json: Json by lazy(jsonProvider)

    // credits: https://github.com/saikou-app/saikou/blob/main/app/src/main/java/ani/saikou/others/AniSkip.kt
    fun getResult(malId: Int, episodeNumber: Int, episodeLength: Long): List<TimeStamp>? {
        if (malId <= 0 || episodeNumber <= 0) return null
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed" +
                "&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$episodeLength"
        return try {
            client.newCall(GET(url)).execute().use { response ->
                if (!response.isSuccessful) return null
                parseSkipResponse(response.body.string(), json)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getMalIdFromAL(id: Long): Long {
        if (id <= 0L) return 0L
        val query = """
                query{
                Media(id:$id){idMal}
                }
        """.trimMargin()
        return try {
            client.newCall(
                POST(
                    "https://graphql.anilist.co",
                    body = buildJsonObject { put("query", query) }.toString()
                        .toRequestBody(jsonMime),
                ),
            ).execute().use { response ->
                if (!response.isSuccessful) return 0L
                parseAniListMalId(response.body.string())
            }
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        internal fun parseSkipResponse(body: String, json: Json): List<TimeStamp>? {
            val res = json.decodeFromString<AniSkipResponse>(body)
            return if (res.found) {
                res.results?.map {
                    TimeStamp(
                        start = it.interval.startTime,
                        end = it.interval.endTime,
                        name = it.skipType.getString(),
                        type = it.skipType.toChapterType(),
                    )
                }
            } else {
                null
            }
        }

        internal fun parseAniListMalId(body: String): Long {
            return body.substringAfter("\"idMal\":", missingDelimiterValue = "")
                .substringBefore("}")
                .substringBefore(",")
                .trim()
                .toLongOrNull()
                ?: 0L
        }
    }
}

@Serializable
data class AniSkipResponse(
    val found: Boolean,
    val results: List<Stamp>?,
)

@Serializable
data class Stamp(
    val interval: AniSkipInterval,
    val skipType: SkipType,
)

@Serializable
enum class SkipType {
    @SerialName("op")
    OP,

    @SerialName("ed")
    ED,

    @SerialName("recap")
    RECAP,

    @SerialName("mixed-op")
    MIXED_OP,

    @SerialName("mixed-ed")
    MIXED_ED,
    ;

    fun getString(): String {
        return when (this) {
            OP -> "Opening"
            ED -> "Ending"
            RECAP -> "Recap"
            MIXED_OP -> "Mixed-op"
            MIXED_ED -> "Mixed-ed"
        }
    }

    fun toChapterType(): ChapterType {
        return when (this) {
            OP -> ChapterType.Opening
            ED -> ChapterType.Ending
            RECAP -> ChapterType.Recap
            MIXED_OP -> ChapterType.MixedOp
            MIXED_ED -> ChapterType.Ending
        }
    }
}

@Serializable
data class AniSkipInterval(
    val startTime: Double,
    val endTime: Double,
)
