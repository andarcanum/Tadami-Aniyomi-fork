package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.toArgb
import eu.kanade.tachiyomi.ui.entries.novel.NovelEpubStyleBuilder
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class NovelReaderNullSafetyTest {

    @Test
    fun `resolveNovelReaderBackdropColor falls back when theme and appearance mode are null`() {
        val settings = buildSettings()
        clearField(settings, "theme")
        clearField(settings, "appearanceMode")

        val backdropColor = resolveNovelReaderBackdropColor(
            settings = settings,
            isSystemDark = true,
        )

        backdropColor.toArgb() shouldBe 0xFF121212.toInt()
    }

    @Test
    fun `resolveThemeColors falls back when theme is null`() {
        val settings = buildSettings()
        clearField(settings, "theme")

        val colors = NovelEpubStyleBuilder.resolveThemeColors(settings)

        colors.background shouldBe "#121212"
        colors.text shouldBe "#EAEAEA"
    }

    @Test
    fun `resolveReaderBackgroundSelection falls back when background inputs are null`() {
        val selection = resolveReaderBackgroundSelection(
            backgroundSource = null,
            backgroundPresetId = null,
            customBackgroundPath = null,
            customBackgroundExists = false,
            customBackgroundId = null,
        )

        selection.source shouldBe NovelReaderBackgroundSource.PRESET
    }

    private fun buildSettings() = NovelReaderPreferences(
        preferenceStore = InMemoryPreferenceStore(),
        json = Json { encodeDefaults = true },
    ).resolveSettings(sourceId = 1L)

    private fun clearField(instance: Any, fieldName: String) {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(instance, null)
    }
}
