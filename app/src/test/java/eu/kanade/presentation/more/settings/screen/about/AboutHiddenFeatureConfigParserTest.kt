package eu.kanade.presentation.more.settings.screen.about

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AboutHiddenFeatureConfigParserTest {

    @Test
    fun `parser reads trigger and content from synthetic json`() {
        val config = parseAboutHiddenFeatureConfig(
            """
            {
              "trigger": {
                "requiredPrimarySignals": 4,
                "primedWindowMs": 2500,
                "tapStreakWindowMs": 900
              },
              "content": {
                "systemLabel": "[SYS]",
                "title": "Dummy title",
                "subtitle": "Dummy subtitle",
                "body": "Dummy body",
                "exitLabel": "[Exit]"
              }
            }
            """.trimIndent(),
        )

        assertEquals(4, config.trigger.requiredPrimarySignals)
        assertEquals(2_500L, config.trigger.primedWindowMs)
        assertEquals("Dummy title", config.content.title)
        assertEquals("[Exit]", config.content.exitLabel)
    }
}
