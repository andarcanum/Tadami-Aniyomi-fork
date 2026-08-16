package eu.kanade.presentation.entries.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.description.DescriptionBlock
import eu.kanade.presentation.theme.AuroraColors
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/**
 * Visual style for [DescriptionBlocks]. Aurora cards pass their own palette/typography,
 * classic screens use [defaultDescriptionBlockStyle].
 */
@Stable
data class DescriptionBlockStyle(
    val paragraph: TextStyle,
    val paragraphColor: Color,
    val heading: TextStyle,
    val headingColor: Color,
    val label: TextStyle,
    val labelColor: Color,
    val value: TextStyle,
    val valueColor: Color,
    val markerColor: Color,
    val linkColor: Color,
    val blockSpacing: Dp,
    val headingTopSpacing: Dp,
)

@Composable
fun defaultDescriptionBlockStyle() = DescriptionBlockStyle(
    paragraph = MaterialTheme.typography.bodyMedium,
    paragraphColor = MaterialTheme.colorScheme.onSurface,
    heading = MaterialTheme.typography.titleSmall,
    headingColor = MaterialTheme.colorScheme.primary,
    label = MaterialTheme.typography.labelMedium,
    labelColor = MaterialTheme.colorScheme.primary,
    value = MaterialTheme.typography.bodyMedium,
    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
    markerColor = MaterialTheme.colorScheme.primary,
    linkColor = MaterialTheme.colorScheme.primary,
    blockSpacing = 8.dp,
    headingTopSpacing = 12.dp,
)

/** Aurora info card variant: compact text, accent-colored structure elements. */
@Composable
fun auroraDescriptionBlockStyle(colors: AuroraColors) = DescriptionBlockStyle(
    paragraph = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    paragraphColor = colors.textPrimary.copy(alpha = 0.9f),
    heading = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    headingColor = colors.accent,
    label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelColor = colors.accent,
    value = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    valueColor = colors.textPrimary.copy(alpha = 0.9f),
    markerColor = colors.accent,
    linkColor = colors.accent,
    blockSpacing = 6.dp,
    headingTopSpacing = 10.dp,
)

/**
 * Renders a list of [DescriptionBlock]s as styled blocks. The single renderer used by both
 * Aurora cards and classic summaries, so descriptions look identical everywhere.
 */
@Composable
fun DescriptionBlocks(
    blocks: List<DescriptionBlock>,
    modifier: Modifier = Modifier,
    style: DescriptionBlockStyle = defaultDescriptionBlockStyle(),
) {
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            val last = index == blocks.lastIndex
            when (block) {
                is DescriptionBlock.Paragraph -> Text(
                    text = block.text,
                    style = style.paragraph,
                    color = style.paragraphColor,
                    modifier = Modifier.padding(bottom = if (last) 0.dp else style.blockSpacing),
                )
                is DescriptionBlock.SectionHeading -> Text(
                    text = block.text,
                    style = style.heading,
                    color = style.headingColor,
                    modifier = Modifier.padding(
                        top = style.headingTopSpacing,
                        bottom = if (last) 0.dp else style.blockSpacing,
                    ),
                )
                is DescriptionBlock.LabelRow -> Text(
                    text = buildAnnotatedString {
                        withStyle(style.label.toSpanStyle().copy(color = style.labelColor)) {
                            append("${block.label}: ")
                        }
                        withStyle(style.value.toSpanStyle().copy(color = style.valueColor)) {
                            append(block.value)
                        }
                    },
                    style = style.value,
                    modifier = Modifier.padding(bottom = if (last) 0.dp else style.blockSpacing),
                )
                is DescriptionBlock.ListItem -> Row(
                    modifier = Modifier.padding(bottom = if (last) 0.dp else style.blockSpacing),
                ) {
                    Text(
                        text = "•",
                        style = style.value,
                        color = style.markerColor,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (block.url != null) {
                            buildAnnotatedString {
                                withLink(LinkAnnotation.Url(block.url)) { append(block.text) }
                            }
                        } else {
                            AnnotatedString(block.text)
                        },
                        style = style.value,
                        color = if (block.url != null) style.linkColor else style.valueColor,
                    )
                }
                is DescriptionBlock.LinksRow -> Column(
                    modifier = Modifier.padding(bottom = if (last) 0.dp else style.blockSpacing),
                ) {
                    block.links.forEach { link ->
                        Text(
                            text = buildAnnotatedString {
                                withLink(LinkAnnotation.Url(link.url)) { append(link.text) }
                            },
                            style = style.value.copy(fontWeight = FontWeight.SemiBold),
                            color = style.linkColor,
                        )
                    }
                }
                is DescriptionBlock.Fallback -> Text(
                    text = block.text,
                    style = style.paragraph,
                    color = style.paragraphColor,
                    modifier = Modifier.padding(bottom = if (last) 0.dp else style.blockSpacing),
                )
            }
        }
    }
}

/**
 * Renders [DescriptionBlocks] clipped to [collapsedHeight] (or fully when [expanded]) while
 * measuring the full content height — `heightIn` would constrain the child's measurement,
 * making overflow detection impossible.
 */
@Composable
private fun DescriptionContentBox(
    blocks: List<DescriptionBlock>,
    style: DescriptionBlockStyle,
    expanded: Boolean,
    collapsedHeight: Dp,
    onContentHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val collapsedHeightPx = with(density) { collapsedHeight.toPx() }.roundToInt()
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        content = { DescriptionBlocks(blocks = blocks, style = style) },
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(maxHeight = Constraints.Infinity),
        )
        val height = if (expanded) placeable.height else minOf(placeable.height, collapsedHeightPx)
        onContentHeightChanged(placeable.height)
        layout(constraints.maxWidth, height) {
            placeable.place(0, 0)
        }
    }
}

/**
 * [DescriptionBlocks] clamped to [collapsedLines] when collapsed. When the content overflows the
 * collapsed height a "Show more" / "Collapse" link is shown below the block; tapping the link or
 * the text itself toggles [onToggle]. Used by the Aurora info cards.
 */
@Composable
fun ExpandableDescriptionBlocks(
    blocks: List<DescriptionBlock>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    style: DescriptionBlockStyle = defaultDescriptionBlockStyle(),
    collapsedLines: Int = 5,
    onOverflowChanged: (Boolean) -> Unit = {},
    showMoreText: String = stringResource(AYMR.strings.aurora_show_more),
    collapseText: String = stringResource(AYMR.strings.aurora_collapse),
) {
    val density = LocalDensity.current
    val lineHeightPx = with(density) { style.paragraph.lineHeight.toPx() }
    val collapsedHeightPx = lineHeightPx * collapsedLines
    val collapsedHeight = with(density) { collapsedHeightPx.toDp() }

    var contentHeight by remember { mutableIntStateOf(0) }
    val hasOverflow = contentHeight > collapsedHeightPx
    LaunchedEffect(hasOverflow) { onOverflowChanged(hasOverflow) }

    Column(modifier = modifier) {
        DescriptionContentBox(
            blocks = blocks,
            style = style,
            expanded = expanded,
            collapsedHeight = collapsedHeight,
            onContentHeightChanged = { contentHeight = it },
        )
        if (hasOverflow) {
            Text(
                text = if (expanded) collapseText else showMoreText,
                color = style.linkColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                    ),
            )
        }
    }
}
