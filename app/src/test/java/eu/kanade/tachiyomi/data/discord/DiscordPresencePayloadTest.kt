package eu.kanade.tachiyomi.data.discord

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class DiscordPresencePayloadTest {

    private fun info(
        mediaKind: DiscordPresenceInfo.MediaKind = DiscordPresenceInfo.MediaKind.ANIME,
        title: String = "One Piece",
        primaryNumber: Double? = 5.0,
        secondaryLine: String? = "The Strongest Creature",
    ) = DiscordPresenceInfo(
        mediaKind = mediaKind,
        title = title,
        primaryNumber = primaryNumber,
        secondaryLine = secondaryLine,
        startedAt = 1725000000000,
    )

    @Test
    fun `anime watching frame has episode details and timestamp`() {
        DiscordPresencePayload.presenceUpdate(info()) shouldBe
            """{"op":3,"d":{"since":0,"activities":[{"name":"One Piece","type":3,"details":"Episode 5",""" +
            """"state":"The Strongest Creature","timestamps":{"start":1725000000000}}],"status":"online","afk":false}}"""
    }

    @Test
    fun `manga frame uses chapter label`() {
        val frame = DiscordPresencePayload.presenceUpdate(
            info(
                mediaKind = DiscordPresenceInfo.MediaKind.MANGA,
                title = "Solo Leveling",
                primaryNumber = 12.5,
                secondaryLine = null,
            ),
        )
        frame shouldBe
            """{"op":3,"d":{"since":0,"activities":[{"name":"Solo Leveling","type":3,"details":"Chapter 12.5",""" +
            """"state":null,"timestamps":{"start":1725000000000}}],"status":"online","afk":false}}"""
    }

    @Test
    fun `novel frame uses chapter label`() {
        val frame = DiscordPresencePayload.presenceUpdate(
            info(
                mediaKind = DiscordPresenceInfo.MediaKind.NOVEL,
                title = "Overgeared",
                primaryNumber = 100.0,
                secondaryLine = "Chapter 100",
            ),
        )
        frame shouldContain """{"name":"Overgeared","type":3,"details":"Chapter 100","""
    }

    @Test
    fun `unknown number omits details`() {
        val frame = DiscordPresencePayload.presenceUpdate(info(primaryNumber = 0.0))
        frame shouldContain """"name":"One Piece","type":3,"details":null,"""
    }

    @Test
    fun `cleared update sends empty activities`() {
        DiscordPresencePayload.presenceUpdate(null) shouldBe
            """{"op":3,"d":{"since":0,"activities":[],"status":"online","afk":false}}"""
    }

    @Test
    fun `identify frame carries token and zero intents`() {
        DiscordPresencePayload.identify("secret-token") shouldBe
            """{"op":2,"d":{"token":"secret-token","intents":0,""" +
            """"properties":{"os":"Android","browser":"Tadami","device":"Tadami"}}}"""
    }

    @Test
    fun `heartbeat frame carries nonce`() {
        DiscordPresencePayload.heartbeat(42) shouldBe """{"op":1,"d":42}"""
    }
}
