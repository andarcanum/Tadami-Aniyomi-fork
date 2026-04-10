@file:Suppress("ktlint:standard:filename")

package eu.kanade.presentation.reader.novel

internal data class NovelReaderTtsSettingsPlacementSnapshot(
    val showFooterEntry: Boolean,
    val showGeneralEnableToggle: Boolean,
    val showGeneralBehaviorSettings: Boolean,
    val useVoiceSettingsIcon: Boolean,
    val useReaderSettingsSurface: Boolean,
    val useAlertDialogSurface: Boolean,
)

internal fun resolveNovelReaderTtsSettingsPlacementSnapshot(
    ttsEnabled: Boolean,
): NovelReaderTtsSettingsPlacementSnapshot {
    return NovelReaderTtsSettingsPlacementSnapshot(
        showFooterEntry = ttsEnabled,
        showGeneralEnableToggle = true,
        showGeneralBehaviorSettings = false,
        useVoiceSettingsIcon = true,
        useReaderSettingsSurface = true,
        useAlertDialogSurface = false,
    )
}
