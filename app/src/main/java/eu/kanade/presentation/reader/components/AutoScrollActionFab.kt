package eu.kanade.presentation.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.viewer.autoscroll.AutoScrollControlLogic
import kotlinx.coroutines.delay
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

private const val ACTIVE_DIM_DELAY_MS = 2_500L
private const val ACTIVE_DIM_ALPHA = 0.42f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoScrollActionFab(
    autoScrollEnabled: Boolean,
    showFab: Boolean,
    contentDescription: String? = null,
    longClickLabel: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    speed: Int? = null,
    onSpeedChange: ((Int) -> Unit)? = null,
    isPagerMode: Boolean = false,
    dimWhenActive: Boolean = false,
) {
    val aurora = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    var wakeTick by remember { mutableIntStateOf(0) }
    var isDimmed by remember { mutableStateOf(false) }

    LaunchedEffect(autoScrollEnabled, speed, wakeTick, dimWhenActive, aurora.isEInk) {
        if (!dimWhenActive || !autoScrollEnabled || aurora.isEInk) {
            isDimmed = false
            return@LaunchedEffect
        }
        isDimmed = false
        delay(ACTIVE_DIM_DELAY_MS)
        isDimmed = true
    }

    val containerAlpha by animateFloatAsState(
        targetValue = if (isDimmed) ACTIVE_DIM_ALPHA else 1f,
        animationSpec = tween(durationMillis = if (aurora.isEInk) 0 else 280),
        label = "autoScrollFabAlpha",
    )

    AnimatedVisibility(
        visible = showFab,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        // B-A4: the FAB hardcoded Color.White border/tint over a translucent secondaryContainer;
        // on e-ink (and light aurora surfaces) that was unreadable. Theme-aware colors now:
        // e-ink gets opaque surface + text colors, glass modes keep the white-on-translucent look.
        val iconTint = if (aurora.isEInk) aurora.textPrimary else Color.White
        val containerColor = if (aurora.isEInk) {
            aurora.surface
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        }
        val borderColor = (if (aurora.isEInk) aurora.textSecondary else Color.White).copy(alpha = 0.22f)
        val shape = RoundedCornerShape(16.dp)

        Row(
            modifier = Modifier.alpha(containerAlpha),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = autoScrollEnabled && speed != null && onSpeedChange != null,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
            ) {
                if (speed != null && onSpeedChange != null) {
                    val canDecrease = speed > AutoScrollControlLogic.MIN_SPEED
                    val canIncrease = speed < AutoScrollControlLogic.MAX_SPEED
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .border(width = 0.5.dp, color = borderColor, shape = shape)
                            .clip(shape)
                            .background(containerColor)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = canDecrease) {
                                    wakeTick++
                                    appHaptics.tap()
                                    onSpeedChange(
                                        AutoScrollControlLogic.stepSpeed(
                                            current = speed,
                                            delta = -AutoScrollControlLogic.DEFAULT_STEP,
                                        ),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Remove,
                                contentDescription = stringResource(AYMR.strings.reader_auto_scroll_slower),
                                modifier = Modifier.size(18.dp),
                                tint = iconTint.copy(alpha = if (canDecrease) 1f else 0.35f),
                            )
                        }

                        Text(
                            text = AutoScrollControlLogic.formatCompactBadge(
                                speed = speed,
                                isPagerMode = isPagerMode,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = iconTint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .widthIn(min = 40.dp)
                                .padding(horizontal = 2.dp),
                        )

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = canIncrease) {
                                    wakeTick++
                                    appHaptics.tap()
                                    onSpeedChange(
                                        AutoScrollControlLogic.stepSpeed(
                                            current = speed,
                                            delta = AutoScrollControlLogic.DEFAULT_STEP,
                                        ),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(AYMR.strings.reader_auto_scroll_faster),
                                modifier = Modifier.size(18.dp),
                                tint = iconTint.copy(alpha = if (canIncrease) 1f else 0.35f),
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(
                        width = 0.5.dp,
                        color = borderColor,
                        shape = shape,
                    )
                    .clip(shape)
                    .background(containerColor)
                    .combinedClickable(
                        onClick = {
                            wakeTick++
                            onClick()
                        },
                        onLongClick = {
                            wakeTick++
                            onLongClick()
                        },
                        onLongClickLabel = longClickLabel,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (autoScrollEnabled) {
                        Icons.Outlined.Pause
                    } else {
                        Icons.Outlined.PlayArrow
                    },
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = if (!autoScrollEnabled) 1.dp else 0.dp),
                    tint = iconTint,
                )
            }
        }
    }
}
