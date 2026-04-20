package eu.kanade.presentation.more.settings.screen.about

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AboutFooterLinksTest {

    @Test
    fun `footer sections stay split between Aniyomi and Tadami`() {
        val sections = buildAboutFooterSections()

        sections shouldHaveSize 2
        sections[0] shouldBe
            AboutFooterLinkSection(
                title = "Aniyomi",
                links = listOf(
                    AboutFooterLink(
                        label = AboutFooterLinkLabel.Website,
                        icon = AboutFooterLinkIcon.Website,
                        url = "https://aniyomi.org",
                    ),
                    AboutFooterLink(
                        label = AboutFooterLinkLabel.Discord,
                        icon = AboutFooterLinkIcon.Discord,
                        url = "https://discord.gg/F32UjdJZrR",
                    ),
                    AboutFooterLink(
                        label = AboutFooterLinkLabel.GitHub,
                        icon = AboutFooterLinkIcon.Github,
                        url = "https://github.com/aniyomiorg/aniyomi",
                    ),
                ),
            )
        sections[1] shouldBe
            AboutFooterLinkSection(
                title = "Tadami",
                links = listOf(
                    AboutFooterLink(
                        label = AboutFooterLinkLabel.Tadami,
                        icon = AboutFooterLinkIcon.Github,
                        url = "https://github.com/andarcanum/Tadami-Aniyomi-fork",
                    ),
                    AboutFooterLink(
                        label = AboutFooterLinkLabel.TelegramChannel,
                        icon = AboutFooterLinkIcon.TelegramChannel,
                        url = "https://t.me/TadamiApp",
                    ),
                    AboutFooterLink(
                        label = AboutFooterLinkLabel.TelegramGroup,
                        icon = AboutFooterLinkIcon.TelegramGroup,
                        url = "https://t.me/TadamiSupport",
                    ),
                ),
            )
    }
}
