package eu.kanade.tachiyomi.ui.player.layout

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PlayerLayoutConfig(
    val portrait: Map<PlayerLayoutSlot, PlayerLayoutRegion> = defaultPortraitLayout(),
    val landscape: Map<PlayerLayoutSlot, PlayerLayoutRegion> = defaultLandscapeLayout(),
) {
    fun regionFor(
        orientation: PlayerLayoutOrientation,
        slot: PlayerLayoutSlot,
    ): PlayerLayoutRegion {
        val layout = layoutFor(orientation)
        return layout[slot] ?: defaultRegionFor(orientation, slot)
    }

    fun slotsForRegion(
        orientation: PlayerLayoutOrientation,
        region: PlayerLayoutRegion,
    ): List<PlayerLayoutSlot> {
        val layout = layoutFor(orientation)
        return PlayerLayoutSlot.entries.filter { layout[it] == region }
    }

    fun withRegion(
        orientation: PlayerLayoutOrientation,
        slot: PlayerLayoutSlot,
        region: PlayerLayoutRegion,
    ): PlayerLayoutConfig {
        val updated = layoutFor(orientation).toMutableMap().apply {
            put(slot, region)
        }

        return when (orientation) {
            PlayerLayoutOrientation.Portrait -> copy(portrait = updated)
            PlayerLayoutOrientation.Landscape -> copy(landscape = updated)
        }
    }

    fun toPreferenceValue(): String = PLAYER_LAYOUT_JSON.encodeToString(this)

    fun previewSummary(
        slotLabel: (PlayerLayoutSlot) -> String = { it.previewLabel },
        regionLabel: (PlayerLayoutRegion) -> String = { it.previewLabel },
    ): String {
        return buildString {
            appendLine("Portrait")
            appendLine(regionSummary(PlayerLayoutOrientation.Portrait, slotLabel, regionLabel))
            appendLine("Landscape")
            appendLine(regionSummary(PlayerLayoutOrientation.Landscape, slotLabel, regionLabel))
        }.trim()
    }

    fun summaryFor(
        orientation: PlayerLayoutOrientation,
        slotLabel: (PlayerLayoutSlot) -> String = { it.previewLabel },
        regionLabel: (PlayerLayoutRegion) -> String = { it.previewLabel },
    ): String {
        return regionSummary(
            orientation = orientation,
            slotLabel = slotLabel,
            regionLabel = regionLabel,
        )
    }

    private fun layoutFor(orientation: PlayerLayoutOrientation): Map<PlayerLayoutSlot, PlayerLayoutRegion> {
        return when (orientation) {
            PlayerLayoutOrientation.Portrait -> portrait
            PlayerLayoutOrientation.Landscape -> landscape
        }
    }

    private fun defaultRegionFor(
        orientation: PlayerLayoutOrientation,
        slot: PlayerLayoutSlot,
    ): PlayerLayoutRegion {
        return when (orientation) {
            PlayerLayoutOrientation.Portrait -> defaultPortraitLayout()[slot]
            PlayerLayoutOrientation.Landscape -> defaultLandscapeLayout()[slot]
        } ?: PlayerLayoutRegion.Hidden
    }

    private fun regionSummary(
        orientation: PlayerLayoutOrientation,
        slotLabel: (PlayerLayoutSlot) -> String,
        regionLabel: (PlayerLayoutRegion) -> String,
    ): String {
        val activeLayout = layoutFor(orientation)
        return PlayerLayoutRegion.entries.joinToString(separator = "\n") { region ->
            val slots = PlayerLayoutSlot.entries.filter { activeLayout[it] == region }
            val slotText = if (slots.isEmpty()) {
                "none"
            } else {
                slots.joinToString { slotLabel(it) }
            }
            "${regionLabel(region)}: $slotText"
        }
    }

    companion object {
        private val PLAYER_LAYOUT_JSON = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun defaultPortrait() = PlayerLayoutConfig().portrait

        fun defaultLandscape() = PlayerLayoutConfig().landscape

        fun fromPreferenceValue(value: String): PlayerLayoutConfig {
            return runCatching {
                PLAYER_LAYOUT_JSON.decodeFromString<PlayerLayoutConfig>(value)
            }.getOrElse {
                PlayerLayoutConfig()
            }
        }
    }
}

@Serializable
enum class PlayerLayoutOrientation {
    Portrait,
    Landscape,
}

@Serializable
enum class PlayerLayoutRegion(
    val previewLabel: String,
) {
    BottomLeft("bottom left"),
    BottomRight("bottom right"),
    Hidden("hidden"),
}

@Serializable
enum class PlayerLayoutSlot(
    val previewLabel: String,
    private val canBeHidden: Boolean,
) {
    PlaybackSpeed("playback speed", false),
    LockControls("lock controls", false),
    RotateScreen("rotate screen", false),
    SkipIntro("skip intro", true),
    CustomButton("custom button", true),
    PictureInPicture("picture in picture", true),
    AspectRatio("aspect ratio", true),
    ;

    fun allowedRegions(): List<PlayerLayoutRegion> {
        return if (canBeHidden) {
            listOf(
                PlayerLayoutRegion.BottomLeft,
                PlayerLayoutRegion.BottomRight,
                PlayerLayoutRegion.Hidden,
            )
        } else {
            listOf(
                PlayerLayoutRegion.BottomLeft,
                PlayerLayoutRegion.BottomRight,
            )
        }
    }

    fun nextRegion(current: PlayerLayoutRegion): PlayerLayoutRegion {
        val options = allowedRegions()
        val index = options.indexOf(current).takeIf { it >= 0 } ?: 0
        return options[(index + 1) % options.size]
    }
}

private fun defaultPortraitLayout(): Map<PlayerLayoutSlot, PlayerLayoutRegion> {
    return mapOf(
        PlayerLayoutSlot.PlaybackSpeed to PlayerLayoutRegion.BottomLeft,
        PlayerLayoutSlot.LockControls to PlayerLayoutRegion.BottomLeft,
        PlayerLayoutSlot.RotateScreen to PlayerLayoutRegion.BottomLeft,
        PlayerLayoutSlot.SkipIntro to PlayerLayoutRegion.BottomRight,
        PlayerLayoutSlot.CustomButton to PlayerLayoutRegion.BottomRight,
        PlayerLayoutSlot.PictureInPicture to PlayerLayoutRegion.BottomRight,
        PlayerLayoutSlot.AspectRatio to PlayerLayoutRegion.BottomRight,
    )
}

private fun defaultLandscapeLayout(): Map<PlayerLayoutSlot, PlayerLayoutRegion> {
    return mapOf(
        PlayerLayoutSlot.PlaybackSpeed to PlayerLayoutRegion.BottomLeft,
        PlayerLayoutSlot.LockControls to PlayerLayoutRegion.BottomLeft,
        PlayerLayoutSlot.RotateScreen to PlayerLayoutRegion.BottomRight,
        PlayerLayoutSlot.SkipIntro to PlayerLayoutRegion.BottomRight,
        PlayerLayoutSlot.CustomButton to PlayerLayoutRegion.BottomRight,
        PlayerLayoutSlot.PictureInPicture to PlayerLayoutRegion.BottomRight,
        PlayerLayoutSlot.AspectRatio to PlayerLayoutRegion.BottomRight,
    )
}
