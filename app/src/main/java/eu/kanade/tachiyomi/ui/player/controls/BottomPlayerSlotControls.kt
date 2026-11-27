package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.controls.components.FilledControlsButton
import eu.kanade.tachiyomi.ui.player.execute
import eu.kanade.tachiyomi.ui.player.executeLongPress
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutSlot
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs

@Immutable
data class BottomPlayerSlotActions(
    val playbackSpeed: Float,
    val customButton: CustomButton?,
    val customButtonTitle: String,
    val skipIntroButton: String?,
    val isPipAvailable: Boolean,
    val onLockControls: () -> Unit,
    val onCycleRotation: () -> Unit,
    val onPlaybackSpeedTap: () -> Unit,
    val onPlaybackSpeedQuickToggle: () -> Unit,
    val onPressSkipIntroButton: () -> Unit,
    val onPipClick: () -> Unit,
    val onAspectClick: () -> Unit,
    val onAspectLongClick: () -> Unit = onAspectClick,
)

internal fun resolveOrderedBottomSlots(layoutSlots: Set<PlayerLayoutSlot>): List<PlayerLayoutSlot> {
    return PlayerLayoutSlot.entries.filter { it in layoutSlots }
}

internal fun resolveQuickTogglePlaybackSpeed(currentSpeed: Float): Float {
    return if (abs(currentSpeed - 1.0f) < 0.01f) 1.5f else 1.0f
}

@Composable
internal fun RenderBottomPlayerSlots(
    layoutSlots: Set<PlayerLayoutSlot>,
    actions: BottomPlayerSlotActions,
) {
    val orderedSlots = resolveOrderedBottomSlots(layoutSlots)
    orderedSlots.forEach { slot ->
        when (slot) {
            PlayerLayoutSlot.LockControls -> {
                ControlsButton(
                    icon = Icons.Default.LockOpen,
                    onClick = actions.onLockControls,
                )
            }
            PlayerLayoutSlot.RotateScreen -> {
                ControlsButton(
                    icon = Icons.Default.ScreenRotation,
                    onClick = actions.onCycleRotation,
                )
            }
            PlayerLayoutSlot.PlaybackSpeed -> {
                ControlsButton(
                    text = stringResource(AYMR.strings.player_speed, actions.playbackSpeed),
                    onClick = actions.onPlaybackSpeedTap,
                    onLongClick = actions.onPlaybackSpeedQuickToggle,
                )
            }
            PlayerLayoutSlot.SkipIntro -> {
                if (actions.skipIntroButton != null) {
                    FilledControlsButton(
                        text = actions.skipIntroButton,
                        onClick = actions.onPressSkipIntroButton,
                        onLongClick = {},
                    )
                }
            }
            PlayerLayoutSlot.CustomButton -> {
                val button = actions.customButton
                if (button != null) {
                    FilledControlsButton(
                        text = actions.customButtonTitle,
                        onClick = button::execute,
                        onLongClick = button::executeLongPress,
                    )
                }
            }
            PlayerLayoutSlot.PictureInPicture -> {
                if (actions.isPipAvailable) {
                    ControlsButton(
                        icon = Icons.Default.PictureInPictureAlt,
                        onClick = actions.onPipClick,
                    )
                }
            }
            PlayerLayoutSlot.AspectRatio -> {
                ControlsButton(
                    icon = Icons.Default.AspectRatio,
                    onClick = actions.onAspectClick,
                    onLongClick = actions.onAspectLongClick,
                )
            }
        }
    }
}
