package eu.kanade.presentation.more.settings.screen.about

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AboutHiddenFeatureContentLocalizationTest {

    @Test
    fun `uses english localized content for english locale when variants are provided`() {
        val content = contentWithEnglishVariants()
        val localized = content.localizedForLanguage("en")

        assertEquals("[System Notification]", localized.systemLabel)
        assertEquals("Hidden Chapter: Confession of the Seeker.", localized.title)
        assertEquals(
            "\"Every time we run from reality into worlds beyond the screen, we forget that one day the illusion may look back at us.\"",
            localized.subtitle,
        )
        assertEquals("[ Exit to 3D ]", localized.exitLabel)
    }

    @Test
    fun `falls back to base content for non english locales`() {
        val content = contentWithEnglishVariants()
        val localized = content.localizedForLanguage("ru")

        assertEquals("[Системное уведомление]", localized.systemLabel)
        assertEquals("Скрытая Глава: Исповедь Искателя.", localized.title)
        assertEquals("[ Выйти в 3D ]", localized.exitLabel)
    }

    @Test
    fun `falls back per field when english variants are blank`() {
        val content = contentWithBlankEnglishVariants()
        val localized = content.localizedForLanguage("en")

        assertEquals("[Системное уведомление]", localized.systemLabel)
        assertEquals("Скрытая Глава: Исповедь Искателя.", localized.title)
        assertEquals("[ Выйти в 3D ]", localized.exitLabel)
    }

    private fun contentWithEnglishVariants(): AboutHiddenFeatureContent {
        return AboutHiddenFeatureContent(
            systemLabel = "[Системное уведомление]",
            title = "Скрытая Глава: Исповедь Искателя.",
            subtitle = "subtitle-ru",
            body = "body-ru",
            exitLabel = "[ Выйти в 3D ]",
            systemLabelEn = "[System Notification]",
            titleEn = "Hidden Chapter: Confession of the Seeker.",
            subtitleEn = "\"Every time we run from reality into worlds beyond the screen, we forget that one day the illusion may look back at us.\"",
            bodyEn = "body-en",
            exitLabelEn = "[ Exit to 3D ]",
        )
    }

    private fun contentWithBlankEnglishVariants(): AboutHiddenFeatureContent {
        return AboutHiddenFeatureContent(
            systemLabel = "[Системное уведомление]",
            title = "Скрытая Глава: Исповедь Искателя.",
            subtitle = "subtitle-ru",
            body = "body-ru",
            exitLabel = "[ Выйти в 3D ]",
            systemLabelEn = " ",
            titleEn = "",
            subtitleEn = "  ",
            bodyEn = "",
            exitLabelEn = "  ",
        )
    }
}
