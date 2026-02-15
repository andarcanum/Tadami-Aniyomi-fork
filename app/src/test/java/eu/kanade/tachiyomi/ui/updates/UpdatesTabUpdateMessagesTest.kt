package eu.kanade.tachiyomi.ui.updates

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UpdatesTabUpdateMessagesTest {

    @Test
    fun `current tab updating message is selected by tab id`() {
        val anime = "Обновление аниме"
        val manga = "Обновление манги"
        val novel = "Обновление ранобэ"

        currentTabUpdatingMessage(
            tabId = 0,
            animeMessage = anime,
            mangaMessage = manga,
            novelMessage = novel,
        ) shouldBe anime

        currentTabUpdatingMessage(
            tabId = 1,
            animeMessage = anime,
            mangaMessage = manga,
            novelMessage = novel,
        ) shouldBe manga

        currentTabUpdatingMessage(
            tabId = 2,
            animeMessage = anime,
            mangaMessage = manga,
            novelMessage = novel,
        ) shouldBe novel
    }

    @Test
    fun `update toast message uses started text only when update started`() {
        val startedText = "Обновление манги"
        val runningText = "Обновление уже запущено"

        resolveUpdateToastMessage(
            started = true,
            startedMessage = startedText,
            alreadyRunningMessage = runningText,
        ) shouldBe startedText

        resolveUpdateToastMessage(
            started = false,
            startedMessage = startedText,
            alreadyRunningMessage = runningText,
        ) shouldBe runningText
    }
}

