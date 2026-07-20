@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.reader.settings.AuroraChip
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

/** Horizontal inset shared by glass-section body content. */
internal val NovelGlassContentPadding = 16.dp

/**
 * Live type preview card for the Reading tab — sample paragraph reacts to
 * size / line height / align / weight / italic / indent / shadow / font.
 */
@Composable
internal fun NovelLiveTypePreview(
    sampleText: String,
    badgeLabel: String,
    fontSizeSp: Int,
    lineHeightEm: Float,
    textAlign: TextAlign?,
    forceBold: Boolean,
    forceItalic: Boolean,
    forceIndent: Boolean,
    textShadow: Boolean,
    fontFamily: FontFamily?,
    textColor: Color,
    paperColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clip(shape)
            .background(paperColor)
            .border(1.dp, auroraRimColor(), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = badgeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Text(
            text = sampleText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = textColor,
            fontSize = fontSizeSp.sp,
            lineHeight = lineHeightEm.em,
            fontFamily = fontFamily,
            fontWeight = if (forceBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (forceItalic) FontStyle.Italic else FontStyle.Normal,
            textAlign = textAlign ?: TextAlign.Start,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                textIndent = if (forceIndent) {
                    androidx.compose.ui.text.style.TextIndent(firstLine = 2.em)
                } else {
                    androidx.compose.ui.text.style.TextIndent.None
                },
                shadow = if (textShadow) {
                    androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.35f),
                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                        blurRadius = 3f,
                    )
                } else {
                    null
                },
            ),
        )
    }
}

/**
 * Alignment control: icon + short caption, no nested track —
 * items sit flat in the glass section; only the selected cell is filled.
 */
@Composable
internal fun NovelCaptionedAlignRow(
    selected: ReaderTextAlign,
    options: List<Triple<ReaderTextAlign, ImageVector, String>>,
    onSelect: (ReaderTextAlign) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NovelGlassContentPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, icon, caption) ->
            val isSelected = value == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) colors.accent else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = caption,
                    tint = if (isSelected) colors.background else colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) colors.background else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Selectable choice card for appearance mode / similar 2-up choices.
 * Icon + title + optional subtitle; solid accent when selected.
 */
@Composable
internal fun NovelChoiceCard(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
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
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.background else colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.background else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    colors.background.copy(alpha = 0.82f)
                } else {
                    colors.textSecondary
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Expandable folder row for local / imported fonts — aurora colors + glass inset. */
@Composable
internal fun NovelFontFolderRow(
    title: String,
    count: Int,
    expanded: Boolean,
    selectedInSection: Boolean,
    selectedLabel: String,
    onToggle: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NovelGlassContentPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .background(
                    if (colors.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f),
                )
                .border(1.dp, auroraRimColor(), shape)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Text(
                text = buildString {
                    append(title)
                    append(" (")
                    append(count)
                    append(')')
                    if (selectedInSection) {
                        append(" · ")
                        append(selectedLabel)
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = "+ $actionLabel",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
                modifier = Modifier
                    .clip(shape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                maxLines = 1,
            )
        }
    }
}

/** Compact 2-col style toggle chips (concept B). */
@Composable
internal fun NovelStyleChipGrid(
    items: List<NovelStyleChipItem>,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NovelGlassContentPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(shape)
                            .background(
                                when {
                                    item.selected -> colors.accent
                                    colors.isDark -> Color.White.copy(alpha = 0.07f)
                                    else -> Color.Black.copy(alpha = 0.05f)
                                },
                            )
                            .border(
                                width = 1.dp,
                                color = when {
                                    item.selected -> colors.accent
                                    colors.isDark -> Color.White.copy(alpha = 0.10f)
                                    else -> Color.Black.copy(alpha = 0.06f)
                                },
                                shape = shape,
                            )
                            .clickable(onClick = item.onClick)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (item.leading != null) {
                            Text(
                                text = item.leading,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (item.selected) colors.background else colors.textSecondary,
                            )
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (item.selected) colors.background else colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

internal data class NovelStyleChipItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val leading: String? = null,
)

/** Font option cards with typeface sample (concept D). */
@Composable
internal fun NovelFontCardRow(
    selectedId: String,
    fonts: List<NovelReaderFontOption>,
    resolveFamily: (NovelReaderFontOption) -> FontFamily?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NovelGlassContentPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(fonts, key = { it.id }) { option ->
            val selected = option.id == selectedId
            val shape = RoundedCornerShape(14.dp)
            val family = resolveFamily(option)
            Column(
                modifier = Modifier
                    .width(118.dp)
                    .height(84.dp)
                    .clip(shape)
                    .background(
                        when {
                            selected -> colors.accent.copy(alpha = 0.16f)
                            colors.isDark -> Color.White.copy(alpha = 0.07f)
                            else -> Color.Black.copy(alpha = 0.04f)
                        },
                    )
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) colors.accent else auroraRimColor(),
                        shape = shape,
                    )
                    .clickable { onSelect(option.id) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = family,
                    color = if (selected) colors.accent else colors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = option.label.ifBlank { option.id },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = family,
                    color = if (selected) colors.accent else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Horizontal chip strip for typography presets / theme / texture choices. */
@Composable
internal fun NovelChipStrip(
    options: List<Pair<String, Boolean>>,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NovelGlassContentPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.size) { index ->
            val (label, selected) = options[index]
            AuroraChip(
                selected = selected,
                onClick = { onSelectIndex(index) },
                label = label,
            )
        }
    }
}

/** Subtle secondary helper text inside a glass section. */
@Composable
internal fun NovelGlassHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = AuroraTheme.colors.textSecondary,
        modifier = modifier.padding(horizontal = NovelGlassContentPadding, vertical = 4.dp),
    )
}
