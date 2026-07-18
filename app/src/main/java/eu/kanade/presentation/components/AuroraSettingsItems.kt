package eu.kanade.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.toggle
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState

/**
 * Aurora-styled settings sheet building blocks.
 *
 * Single source of truth for the visual language of tabbed settings sheets:
 * capsule tab selector, rounded tri-state checkboxes, switches, radio rows,
 * sort rows with a direction chip, display-mode tiles and filter chips.
 * E-ink profiles fall back to opaque, high-contrast styling.
 */

private val AuroraRowShape = RoundedCornerShape(12.dp)

/**
 * Equal-weight capsule tab row with **variant A** asymmetric spring pill:
 * leading edge snaps faster than trailing -> stretch-bounce transfer.
 *
 * Optional [trailing] sits inside the same capsule track (e.g. sheet ⋮ menu),
 * so overflow no longer floats as a separate header icon chip.
 */
@Composable
fun AuroraCapsuleTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val density = LocalDensity.current
    val containerColor = if (colors.isEInk) {
        Color.Transparent
    } else {
        colors.textPrimary.copy(alpha = if (colors.isDark) 0.06f else 0.05f)
    }
    val borderColor = if (colors.isEInk) {
        colors.textPrimary
    } else {
        colors.textPrimary.copy(alpha = 0.10f)
    }
    // Match section tabs (AuroraTabRow): soft moving pill only, no solid fill / white flash.
    val selectedBrush = if (colors.isEInk) {
        null
    } else {
        Brush.verticalGradient(
            colors = listOf(
                if (colors.isDark) {
                    lerp(colors.accent, Color.White, 0.18f).copy(alpha = 0.32f)
                } else {
                    colors.accent.copy(alpha = 0.20f)
                },
                if (colors.isDark) {
                    colors.accent.copy(alpha = 0.18f)
                } else {
                    Color.White.copy(alpha = 0.40f)
                },
            ),
        )
    }
    val selectedSolid = if (colors.isEInk) colors.textPrimary else null
    val selectedBorder = if (colors.isEInk) {
        colors.textPrimary
    } else if (colors.isDark) {
        colors.accent.copy(alpha = 0.25f)
    } else {
        colors.accent.copy(alpha = 0.28f)
    }

    val safeIndex = selectedIndex.coerceIn(0, (titles.size - 1).coerceAtLeast(0))
    val (leftSpring, rightSpring) = rememberAsymmetricTabEdgeSprings(safeIndex)
    val trailingSlot = 34.dp

    Row(
        modifier = modifier
            .background(containerColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .padding(3.dp)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            val count = titles.size.coerceAtLeast(1)
            val segmentPx = with(density) { maxWidth.toPx() / count }
            val targetLeft = segmentPx * safeIndex
            val targetRight = targetLeft + segmentPx

            val animatedLeft by animateFloatAsState(
                targetValue = targetLeft,
                animationSpec = leftSpring,
                label = "capsuleTabLeft",
            )
            val animatedRight by animateFloatAsState(
                targetValue = targetRight,
                animationSpec = rightSpring,
                label = "capsuleTabRight",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val left = animatedLeft
                        val right = animatedRight
                        val pillWidth = (right - left).coerceAtLeast(0f)
                        if (pillWidth <= 0f) return@drawBehind
                        val pillHeight = this.size.height
                        val radius = (pillHeight / 2f) *
                            resolveAsymmetricTabStretchRadiusFactor(pillWidth, segmentPx)
                        val origin = Offset(left, 0f)
                        val pillSize = androidx.compose.ui.geometry.Size(pillWidth, pillHeight)
                        val solid = selectedSolid
                        val brush = selectedBrush
                        if (solid != null) {
                            drawRoundRect(
                                color = solid,
                                topLeft = origin,
                                size = pillSize,
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        } else if (brush != null) {
                            drawRoundRect(
                                brush = brush,
                                topLeft = origin,
                                size = pillSize,
                                cornerRadius = CornerRadius(radius, radius),
                            )
                            drawRoundRect(
                                color = selectedBorder,
                                topLeft = origin,
                                size = pillSize,
                                cornerRadius = CornerRadius(radius, radius),
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                    },
            ) {
                Row(Modifier.fillMaxSize()) {
                    titles.forEachIndexed { index, title ->
                        val selected = index == safeIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(
                                    // No Material ripple — selection is the sliding pill only
                                    // (same language as section AuroraTabRow).
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onSelect(index) }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                color = when {
                                    selected && colors.isEInk -> colors.background
                                    selected -> colors.textPrimary
                                    colors.isDark -> colors.textPrimary.copy(alpha = 0.65f)
                                    else -> colors.textSecondary
                                },
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (trailing != null) {
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .width(1.dp)
                    .height(14.dp)
                    .background(colors.textPrimary.copy(alpha = if (colors.isEInk) 1f else 0.12f)),
            )
            Box(
                modifier = Modifier
                    .width(trailingSlot)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                trailing()
            }
        }
    }
}

@Composable
fun AuroraHeadingItem(labelRes: StringResource) {
    AuroraHeadingItem(stringResource(labelRes))
}

@Composable
fun AuroraHeadingItem(text: String) {
    val colors = AuroraTheme.colors
    Text(
        text = text.uppercase(),
        color = if (colors.isEInk) colors.textPrimary else colors.accent.copy(alpha = 0.75f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
    )
}

@Composable
private fun AuroraCheckIndicator(state: TriState, enabled: Boolean) {
    val colors = AuroraTheme.colors
    val accent = if (colors.isEInk) colors.textPrimary else colors.accent
    val excludeColor = if (colors.isEInk) colors.textPrimary else colors.error
    val shape = RoundedCornerShape(7.dp)
    val alpha = if (enabled) 1f else 0.4f
    when (state) {
        TriState.DISABLED -> Box(
            modifier = Modifier
                .size(22.dp)
                .border(1.6.dp, colors.textSecondary.copy(alpha = 0.6f * alpha), shape),
        )
        TriState.ENABLED_IS -> Box(
            modifier = Modifier
                .size(22.dp)
                .background(accent.copy(alpha = alpha), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (colors.isEInk) colors.background else colors.textOnAccent,
                modifier = Modifier.size(16.dp),
            )
        }
        TriState.ENABLED_NOT -> Box(
            modifier = Modifier
                .size(22.dp)
                .background(excludeColor.copy(alpha = alpha), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Remove,
                contentDescription = null,
                tint = if (colors.isEInk) colors.background else Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun AuroraTriStateItem(
    label: String,
    state: TriState,
    enabled: Boolean = true,
    onClick: ((TriState) -> Unit)?,
) {
    val appHaptics = LocalAppHaptics.current
    val colors = AuroraTheme.colors
    val actuallyEnabled = enabled && onClick != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = actuallyEnabled) {
                appHaptics.tap()
                when (state) {
                    TriState.DISABLED -> onClick?.invoke(TriState.ENABLED_IS)
                    TriState.ENABLED_IS -> onClick?.invoke(TriState.ENABLED_NOT)
                    TriState.ENABLED_NOT -> onClick?.invoke(TriState.DISABLED)
                }
            }
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AuroraCheckIndicator(state = state, enabled = actuallyEnabled)
        Text(
            text = label,
            color = colors.textPrimary.copy(alpha = if (actuallyEnabled) 1f else 0.4f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun AuroraCheckboxItem(
    label: String,
    pref: Preference<Boolean>,
) {
    val checked by pref.collectAsState()
    AuroraCheckboxItem(
        label = label,
        checked = checked,
        onClick = { pref.toggle() },
    )
}

@Composable
fun AuroraCheckboxItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val appHaptics = LocalAppHaptics.current
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                appHaptics.tap()
                onClick()
            }
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AuroraCheckIndicator(
            state = if (checked) TriState.ENABLED_IS else TriState.DISABLED,
            enabled = true,
        )
        Text(
            text = label,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun AuroraSwitchItem(
    label: String,
    pref: Preference<Boolean>,
    enabled: Boolean = true,
) {
    val checked by pref.collectAsState()
    AuroraSwitchItem(
        label = label,
        checked = checked,
        enabled = enabled,
        onClick = { if (enabled) pref.toggle() },
    )
}

@Composable
fun AuroraSwitchItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val appHaptics = LocalAppHaptics.current
    val colors = AuroraTheme.colors
    val accent = if (colors.isEInk) colors.textPrimary else colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                appHaptics.tap()
                onClick()
            }
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            color = colors.textPrimary.copy(alpha = if (enabled) 1f else 0.4f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        val trackColor = if (checked) {
            if (colors.isEInk) colors.textPrimary else accent.copy(alpha = if (enabled) 0.45f else 0.25f)
        } else {
            colors.textPrimary.copy(alpha = if (colors.isEInk) 0.35f else 0.14f)
        }
        val knobColor = when {
            checked && colors.isEInk -> colors.background
            checked -> Color.White
            else -> colors.textSecondary
        }
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(22.dp)
                .background(trackColor, CircleShape),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(16.dp)
                    .background(knobColor.copy(alpha = if (enabled) 1f else 0.5f), CircleShape),
            )
        }
    }
}

@Composable
fun AuroraRadioItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val accent = if (colors.isEInk) colors.textPrimary else colors.accent
    val highlight = if (selected && !colors.isEInk) {
        Modifier
            .background(accent.copy(alpha = 0.10f), AuroraRowShape)
            .border(1.dp, accent.copy(alpha = 0.25f), AuroraRowShape)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .then(highlight)
            .clip(AuroraRowShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    1.8.dp,
                    if (selected) accent else colors.textSecondary.copy(alpha = 0.6f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(accent, CircleShape),
                )
            }
        }
        Text(
            text = label,
            color = if (selected) colors.textPrimary else colors.textPrimary.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun AuroraSortItem(
    label: String,
    sortDescending: Boolean?,
    onClick: () -> Unit,
) {
    val arrowIcon = when (sortDescending) {
        true -> Icons.Default.ArrowDownward
        false -> Icons.Default.ArrowUpward
        null -> null
    }
    AuroraBaseSortItem(
        label = label,
        icon = arrowIcon,
        onClick = onClick,
    )
}

@Composable
fun AuroraBaseSortItem(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    val appHaptics = LocalAppHaptics.current
    val colors = AuroraTheme.colors
    val accent = if (colors.isEInk) colors.textPrimary else colors.accent
    val selected = icon != null
    val highlight = if (selected && !colors.isEInk) {
        Modifier
            .background(accent.copy(alpha = 0.10f), AuroraRowShape)
            .border(1.dp, accent.copy(alpha = 0.25f), AuroraRowShape)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .then(highlight)
            .clip(AuroraRowShape)
            .clickable {
                appHaptics.tap()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (icon != null) {
            Box(
                modifier = Modifier
                    .background(accent, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (colors.isEInk) colors.background else colors.textOnAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
fun AuroraChipRow(
    labelRes: StringResource,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Column {
        AuroraHeadingItem(labelRes)
        FlowRow(
            modifier = Modifier.padding(
                start = SettingsItemsPaddings.Horizontal,
                end = SettingsItemsPaddings.Horizontal,
                bottom = SettingsItemsPaddings.Vertical,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun AuroraFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    val colors = AuroraTheme.colors
    val accent = if (colors.isEInk) colors.textPrimary else colors.accent
    val backgroundColor = when {
        selected && colors.isEInk -> colors.textPrimary
        selected -> accent.copy(alpha = 0.16f)
        colors.isEInk -> Color.Transparent
        else -> colors.textPrimary.copy(alpha = 0.05f)
    }
    val borderColor = if (selected) {
        accent.copy(alpha = if (colors.isEInk) 1f else 0.45f)
    } else {
        colors.textPrimary.copy(alpha = if (colors.isEInk) 1f else 0.14f)
    }
    Box(
        modifier = Modifier
            .background(backgroundColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = when {
                selected && colors.isEInk -> colors.background
                selected -> colors.textPrimary
                else -> colors.textSecondary
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun AuroraDisplayModeTiles(
    options: List<Pair<StringResource, LibraryDisplayMode>>,
    selected: LibraryDisplayMode,
    onSelect: (LibraryDisplayMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SettingsItemsPaddings.Horizontal,
                end = SettingsItemsPaddings.Horizontal,
                bottom = SettingsItemsPaddings.Vertical,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { (labelRes, mode) ->
                    AuroraDisplayModeTile(
                        label = stringResource(labelRes),
                        mode = mode,
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AuroraDisplayModeTile(
    label: String,
    mode: LibraryDisplayMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appHaptics = LocalAppHaptics.current
    val colors = AuroraTheme.colors
    val accent = if (colors.isEInk) colors.textPrimary else colors.accent
    val backgroundColor = when {
        colors.isEInk -> Color.Transparent
        selected -> accent.copy(alpha = 0.12f)
        else -> colors.textPrimary.copy(alpha = 0.05f)
    }
    val borderColor = if (selected) {
        accent.copy(alpha = if (colors.isEInk) 1f else 0.45f)
    } else {
        colors.textPrimary.copy(alpha = if (colors.isEInk) 0.6f else 0.10f)
    }
    val borderWidth = if (selected && colors.isEInk) 2.dp else 1.dp
    Column(
        modifier = modifier
            .background(backgroundColor, AuroraRowShape)
            .border(borderWidth, borderColor, AuroraRowShape)
            .clip(AuroraRowShape)
            .clickable {
                appHaptics.tap()
                onClick()
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AuroraDisplayModePreview(
            mode = mode,
            tint = if (selected) accent else colors.textSecondary.copy(alpha = 0.55f),
        )
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AuroraDisplayModePreview(mode: LibraryDisplayMode, tint: Color) {
    val cellShape = RoundedCornerShape(2.5.dp)
    Box(
        modifier = Modifier.height(24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (mode) {
            LibraryDisplayMode.CompactGrid -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.width(13.dp).height(20.dp).background(tint, cellShape))
                Box(Modifier.width(13.dp).height(20.dp).background(tint, cellShape))
                Box(Modifier.width(13.dp).height(20.dp).background(tint, cellShape))
            }
            LibraryDisplayMode.ComfortableGrid -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.width(18.dp).height(14.dp).background(tint, cellShape))
                    Box(Modifier.width(12.dp).height(3.dp).background(tint, cellShape))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.width(18.dp).height(14.dp).background(tint, cellShape))
                    Box(Modifier.width(12.dp).height(3.dp).background(tint, cellShape))
                }
            }
            LibraryDisplayMode.CoverOnlyGrid -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.width(18.dp).height(20.dp).background(tint, cellShape))
                Box(Modifier.width(18.dp).height(20.dp).background(tint, cellShape))
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(40.dp).height(4.dp).background(tint, cellShape))
                Box(Modifier.width(40.dp).height(4.dp).background(tint, cellShape))
                Box(Modifier.width(28.dp).height(4.dp).background(tint, cellShape))
            }
        }
    }
}
