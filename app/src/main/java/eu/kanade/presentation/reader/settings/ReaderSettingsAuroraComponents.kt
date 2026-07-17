package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.toggle
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.roundToInt

/**
 * Aurora building blocks for the manga reader quick settings sheet:
 * glass section cards, flat toggle rows, segmented controls, visual mode
 * cards and an Aurora tab row. Same glass language as import/nickname
 * surfaces; used only by the manga ReaderSettingsDialog pages.
 */

/** Glass section card: translucent frost (lets window blur show through), 1dp rim. */
@Composable
internal fun AuroraGlassSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(22.dp)
    // Section lift over the sheet — enough contrast, still translucent.
    val frostBase = when {
        colors.isEInk -> colors.surface
        colors.isDark -> Color.White.copy(alpha = 0.06f)
        else -> Color.Black.copy(alpha = 0.04f)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clip(shape)
            .background(frostBase)
            .border(width = 1.dp, color = auroraRimColor(), shape = shape)
            .padding(vertical = 8.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                color = auroraRimColor(),
            )
        }
        content()
    }
}

/** 1dp rim color shared by glass surfaces. */
@Composable
internal fun auroraRimColor(): Color {
    val colors = AuroraTheme.colors
    return if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
}

/** Small secondary label for a field group inside a glass section. */
@Composable
internal fun AuroraFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = AuroraTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** Wrapping chip container inside a glass section. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AuroraChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** Pill chip: solid accent fill + contrast text when selected. */
@Composable
internal fun AuroraChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector? = null,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    selected -> colors.accent
                    colors.isDark -> Color.White.copy(alpha = 0.07f)
                    else -> Color.Black.copy(alpha = 0.05f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.background else colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.background else colors.textPrimary,
            maxLines = 1,
        )
    }
}

/** Flat toggle row: label left, switch right. No full-width M3 list chrome. */
@Composable
internal fun AuroraToggleRow(
    label: String,
    pref: Preference<Boolean>,
) {
    val checked by pref.collectAsState()
    AuroraToggleRow(
        label = label,
        checked = checked,
        onClick = { pref.toggle() },
    )
}

@Composable
internal fun AuroraToggleRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.scale(0.85f),
        )
    }
}

/** Slider row with label + value pill, sized for glass sections. */
@Composable
internal fun AuroraSliderRow(
    label: String,
    value: Int,
    valueRange: IntRange,
    onChange: (Int) -> Unit,
    valueText: String = value.toString(),
) {
    val colors = AuroraTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        )
    }
}

/** Single-choice segmented control with strong accent fill for the selected cell. */
@Composable
internal fun AuroraSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (colors.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.05f),
            )
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.background else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Visual mode card (icon + label) for the reading mode grid — selected = solid accent. */
@Composable
internal fun AuroraModeCard(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    painter: Painter,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(16.dp)
    // Fixed height so 1-line and 2-line labels produce identical tiles in the grid.
    Column(
        modifier = modifier
            .height(ModeCardHeight)
            .clip(shape)
            .background(
                when {
                    selected -> colors.accent
                    colors.isDark -> Color.White.copy(alpha = 0.07f)
                    else -> Color.Black.copy(alpha = 0.05f)
                },
            )
            .border(
                width = 1.dp,
                color = when {
                    selected -> colors.accent
                    colors.isDark -> Color.White.copy(alpha = 0.10f)
                    else -> Color.Black.copy(alpha = 0.06f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = if (selected) colors.background else colors.textSecondary,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.background else colors.textPrimary,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
        )
    }
}

/** Shared tile height for the reading-mode grid (icon + 2-line label). */
private val ModeCardHeight = 96.dp

/** Compact option tile for orientation grid — 2-line labels, no ellipsis mid-word. */
@Composable
internal fun AuroraMiniOption(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(
                when {
                    selected -> colors.accent
                    colors.isDark -> Color.White.copy(alpha = 0.07f)
                    else -> Color.Black.copy(alpha = 0.05f)
                },
            )
            .border(
                width = 1.dp,
                color = when {
                    selected -> colors.accent
                    colors.isDark -> Color.White.copy(alpha = 0.10f)
                    else -> Color.Black.copy(alpha = 0.06f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.background else colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.background else colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Aurora tab row: accent label + short underline pill instead of M3 primary tabs. */
@Composable
internal fun AuroraTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.accent else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) colors.accent else Color.Transparent),
                )
            }
        }
    }
}

/** Navigation row with trailing chevron for sub-dialogs. */
@Composable
internal fun AuroraNavRow(
    label: String,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}
