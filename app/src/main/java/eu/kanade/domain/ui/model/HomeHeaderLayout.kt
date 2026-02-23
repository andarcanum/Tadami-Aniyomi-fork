package eu.kanade.domain.ui.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class HomeHeaderLayoutElement(val key: String) {
    Greeting("greeting"),
    Nickname("nickname"),
    Avatar("avatar"),
    Streak("streak"),
    ;

    companion object {
        fun fromKey(key: String): HomeHeaderLayoutElement? {
            return entries.firstOrNull { it.key == key }
        }
    }
}

@Serializable
data class HomeHeaderLayoutPointSpec(
    val x: Float,
    val y: Float,
)

@Serializable
data class HomeHeaderLayoutCanvasSpec(
    val width: Float = DEFAULT_WIDTH_PX,
    val height: Float = DEFAULT_HEIGHT_PX,
) {
    companion object {
        const val DEFAULT_WIDTH_PX = 360f
        const val DEFAULT_HEIGHT_PX = 72f
    }
}

@Serializable
data class HomeHeaderLayoutSpec(
    val version: Int = CURRENT_VERSION,
    val canvas: HomeHeaderLayoutCanvasSpec = HomeHeaderLayoutCanvasSpec(),
    val elements: Map<String, HomeHeaderLayoutPointSpec> = defaultElementsMap(HomeHeaderLayoutCanvasSpec()),
) {
    fun positionOf(element: HomeHeaderLayoutElement): HomeHeaderLayoutPointSpec {
        return elements[element.key] ?: default(canvas).elements.getValue(element.key)
    }

    fun withPosition(
        element: HomeHeaderLayoutElement,
        x: Float,
        y: Float,
    ): HomeHeaderLayoutSpec {
        return copy(elements = elements + (element.key to HomeHeaderLayoutPointSpec(x = x, y = y)))
    }

    fun toJson(): String = json.encodeToString(this)

    companion object {
        const val CURRENT_VERSION = 2

        private const val DEFAULT_GREETING_X = 0f
        private const val DEFAULT_GREETING_Y = 0f
        private const val DEFAULT_NICKNAME_X = 0f
        private const val DEFAULT_NICKNAME_Y = 32f
        private const val DEFAULT_AVATAR_WIDTH = 48f
        private const val DEFAULT_AVATAR_HEIGHT = 48f
        private const val DEFAULT_STREAK_WIDTH = 68f
        private const val DEFAULT_STREAK_Y = 0f

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun default(): HomeHeaderLayoutSpec = default(HomeHeaderLayoutCanvasSpec())

        fun default(canvas: HomeHeaderLayoutCanvasSpec): HomeHeaderLayoutSpec {
            return HomeHeaderLayoutSpec(
                version = CURRENT_VERSION,
                canvas = canvas,
                elements = defaultElementsMap(canvas),
            )
        }

        fun fromJsonOrNull(raw: String?): HomeHeaderLayoutSpec? {
            if (raw.isNullOrBlank()) return null
            parseV2(raw)?.let { return it }
            parseLegacyV1(raw)?.let { return it }
            return null
        }

        private fun parseV2(raw: String): HomeHeaderLayoutSpec? {
            return runCatching { json.decodeFromString<HomeHeaderLayoutSpec>(raw) }
                .getOrNull()
                ?.takeIf { it.version == CURRENT_VERSION }
        }

        private fun parseLegacyV1(raw: String): HomeHeaderLayoutSpec? {
            val legacy = runCatching { json.decodeFromString<LegacyHomeHeaderLayoutV1Spec>(raw) }
                .getOrNull()
                ?.takeIf { it.version == 1 } ?: return null

            val columns = legacy.grid.columns.coerceAtLeast(1)
            val rows = legacy.grid.rows.coerceAtLeast(1)
            val canvas = HomeHeaderLayoutCanvasSpec()
            val default = default(canvas)

            val converted = HomeHeaderLayoutElement.entries.associate { element ->
                val legacyPoint = legacy.elements[element.key]
                val point = if (legacyPoint == null) {
                    default.positionOf(element)
                } else {
                    HomeHeaderLayoutPointSpec(
                        x = (legacyPoint.x.toFloat() / columns.toFloat()) * canvas.width,
                        y = (legacyPoint.y.toFloat() / rows.toFloat()) * canvas.height,
                    )
                }
                element.key to point
            }

            return HomeHeaderLayoutSpec(
                version = CURRENT_VERSION,
                canvas = canvas,
                elements = converted,
            )
        }

        private fun defaultElementsMap(canvas: HomeHeaderLayoutCanvasSpec): Map<String, HomeHeaderLayoutPointSpec> {
            val rightGroupWidth = maxOf(DEFAULT_AVATAR_WIDTH, DEFAULT_STREAK_WIDTH)
            val rightGroupLeft = (canvas.width - rightGroupWidth).coerceAtLeast(0f)
            val avatarX = (rightGroupLeft + (rightGroupWidth - DEFAULT_AVATAR_WIDTH) / 2f).coerceAtLeast(0f)
            val avatarY = (canvas.height - DEFAULT_AVATAR_HEIGHT).coerceAtLeast(0f)
            val streakX = (rightGroupLeft + (rightGroupWidth - DEFAULT_STREAK_WIDTH) / 2f).coerceAtLeast(0f)

            return mapOf(
                HomeHeaderLayoutElement.Greeting.key to HomeHeaderLayoutPointSpec(
                    x = DEFAULT_GREETING_X,
                    y = DEFAULT_GREETING_Y,
                ),
                HomeHeaderLayoutElement.Nickname.key to HomeHeaderLayoutPointSpec(
                    x = DEFAULT_NICKNAME_X,
                    y = DEFAULT_NICKNAME_Y,
                ),
                HomeHeaderLayoutElement.Avatar.key to HomeHeaderLayoutPointSpec(
                    x = avatarX,
                    y = avatarY,
                ),
                HomeHeaderLayoutElement.Streak.key to HomeHeaderLayoutPointSpec(
                    x = streakX,
                    y = DEFAULT_STREAK_Y,
                ),
            )
        }
    }
}

@Serializable
private data class LegacyHomeHeaderLayoutV1Spec(
    val version: Int = 1,
    val grid: LegacyHomeHeaderGridSpec = LegacyHomeHeaderGridSpec(),
    val elements: Map<String, LegacyHomeHeaderGridPointSpec> = emptyMap(),
)

@Serializable
private data class LegacyHomeHeaderGridSpec(
    val columns: Int = 12,
    val rows: Int = 10,
)

@Serializable
private data class LegacyHomeHeaderGridPointSpec(
    @SerialName("x") val x: Int,
    @SerialName("y") val y: Int,
)
