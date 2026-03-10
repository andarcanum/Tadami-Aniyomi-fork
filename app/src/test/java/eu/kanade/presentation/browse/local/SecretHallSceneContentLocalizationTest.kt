package eu.kanade.presentation.browse.local

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SecretHallSceneContentLocalizationTest {

    @Test
    fun `uses english localized content for english locale when variants are provided`() {
        val content = contentWithEnglishVariants()
        val localized = content.localizedForLanguage("en")

        assertEquals("SYSTEM UNLOCKED", localized.systemLabel)
        assertEquals("Tadami Hall of Fame", localized.title)
        assertEquals("Only those who endured the signal to the end may enter.", localized.subtitle)
        assertEquals("Return to the ordinary world.", localized.runeDescription)
        assertEquals("All Tadami Participants", localized.rosterTitle)
        assertEquals("A chronicle of those who left a mark.", localized.rosterSubtitle)
        assertEquals("Open the full list of names.", localized.rosterOpenDescription)
        assertEquals("Close the full list of names.", localized.rosterCloseDescription)
    }

    @Test
    fun `falls back to base localized content for non english locales`() {
        val content = contentWithEnglishVariants()
        val localized = content.localizedForLanguage("ru")

        assertEquals("СИСТЕМА РАЗБЛОКИРОВАНА", localized.systemLabel)
        assertEquals("Зал славы Tadami", localized.title)
        assertEquals("К входу допущены только те, кто выдержал сигнал до конца.", localized.subtitle)
        assertEquals("Вернуться в обычный мир.", localized.runeDescription)
        assertEquals("Все участники Tadami", localized.rosterTitle)
        assertEquals("Летопись тех, кто оставил след.", localized.rosterSubtitle)
        assertEquals("Открыть список всех имен.", localized.rosterOpenDescription)
        assertEquals("Закрыть список всех имен.", localized.rosterCloseDescription)
    }

    @Test
    fun `falls back per field when english variants are blank`() {
        val content = contentWithBlankEnglishVariants()
        val localized = content.localizedForLanguage("en")

        assertEquals("СИСТЕМА РАЗБЛОКИРОВАНА", localized.systemLabel)
        assertEquals("Зал славы Tadami", localized.title)
        assertEquals("К входу допущены только те, кто выдержал сигнал до конца.", localized.subtitle)
        assertEquals("Вернуться в обычный мир.", localized.runeDescription)
        assertEquals("Все участники Tadami", localized.rosterTitle)
        assertEquals("Летопись тех, кто оставил след.", localized.rosterSubtitle)
        assertEquals("Открыть список всех имен.", localized.rosterOpenDescription)
        assertEquals("Закрыть список всех имен.", localized.rosterCloseDescription)
    }

    private fun contentWithEnglishVariants(): SecretHallSceneContent {
        return SecretHallSceneContent(
            systemLabel = "СИСТЕМА РАЗБЛОКИРОВАНА",
            title = "Зал славы Tadami",
            subtitle = "К входу допущены только те, кто выдержал сигнал до конца.",
            runeDescription = "Вернуться в обычный мир.",
            rosterTitle = "Все участники Tadami",
            rosterSubtitle = "Летопись тех, кто оставил след.",
            rosterOpenDescription = "Открыть список всех имен.",
            rosterCloseDescription = "Закрыть список всех имен.",
            systemLabelEn = "SYSTEM UNLOCKED",
            titleEn = "Tadami Hall of Fame",
            subtitleEn = "Only those who endured the signal to the end may enter.",
            runeDescriptionEn = "Return to the ordinary world.",
            rosterTitleEn = "All Tadami Participants",
            rosterSubtitleEn = "A chronicle of those who left a mark.",
            rosterOpenDescriptionEn = "Open the full list of names.",
            rosterCloseDescriptionEn = "Close the full list of names.",
        )
    }

    private fun contentWithBlankEnglishVariants(): SecretHallSceneContent {
        return SecretHallSceneContent(
            systemLabel = "СИСТЕМА РАЗБЛОКИРОВАНА",
            title = "Зал славы Tadami",
            subtitle = "К входу допущены только те, кто выдержал сигнал до конца.",
            runeDescription = "Вернуться в обычный мир.",
            rosterTitle = "Все участники Tadami",
            rosterSubtitle = "Летопись тех, кто оставил след.",
            rosterOpenDescription = "Открыть список всех имен.",
            rosterCloseDescription = "Закрыть список всех имен.",
            systemLabelEn = " ",
            titleEn = "   ",
            subtitleEn = "",
            runeDescriptionEn = "  ",
            rosterTitleEn = "",
            rosterSubtitleEn = " ",
            rosterOpenDescriptionEn = "",
            rosterCloseDescriptionEn = " ",
        )
    }
}
