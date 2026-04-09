package eu.kanade.presentation.reader.novel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelReaderTtsSettingsPlacementTest {

    @Test
    fun `footer exposes dedicated tts settings entry only when tts is enabled`() {
        assertTrue(resolveNovelReaderTtsSettingsPlacementSnapshot(ttsEnabled = true).showFooterEntry)
        assertFalse(resolveNovelReaderTtsSettingsPlacementSnapshot(ttsEnabled = false).showFooterEntry)
    }

    @Test
    fun `general reader settings keep only the tts enable toggle`() {
        val snapshot = resolveNovelReaderTtsSettingsPlacementSnapshot(ttsEnabled = true)

        assertTrue(snapshot.showGeneralEnableToggle)
        assertFalse(snapshot.showGeneralBehaviorSettings)
    }

    @Test
    fun `footer entry uses voice settings icon and reader settings surface`() {
        val snapshot = resolveNovelReaderTtsSettingsPlacementSnapshot(ttsEnabled = true)

        assertTrue(snapshot.useVoiceSettingsIcon)
        assertTrue(snapshot.useReaderSettingsSurface)
        assertFalse(snapshot.useAlertDialogSurface)
    }
}
