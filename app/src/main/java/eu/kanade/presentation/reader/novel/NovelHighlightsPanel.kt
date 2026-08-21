package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tadami.aurora.R
import eu.kanade.presentation.reader.components.AuroraReaderSheet
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reader highlights sheet styled after the approved "antique paper" prototype:
 * haze-glass container (window blurBehind via [AuroraReaderSheet], solid fallback on e-ink),
 * soft aged-paper overlay, hanging bookmark ribbon and a centered serif header with the
 * source-novel fleuron. Tap opens the editor; the trash icon deletes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelHighlightsPanel(
    visible: Boolean,
    novelTitle: String,
    items: List<NovelHighlightWithChapter>,
    defaultColorArgb: Long,
    onDefaultColorChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onCopy: (String) -> Unit,
) {
    if (!visible) return
    var showDefaultPicker by remember { mutableStateOf(false) }

    AuroraReaderSheet(
        onDismissRequest = onDismiss,
        glassOverlay = {
            PaperVeil(Modifier.matchParentSize())
            BookmarkRibbon(Modifier.align(Alignment.TopEnd))
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            SheetHead(novelTitle = novelTitle)

            Text(
                text = stringResource(AYMR.strings.novel_highlights_default_color),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NOVEL_HIGHLIGHT_PRESET_COLORS.forEach { preset ->
                    ColorSwatchDot(
                        colorArgb = preset,
                        selected = defaultColorArgb == preset,
                        onClick = { onDefaultColorChange(preset) },
                    )
                }
                CustomSwatchDot(
                    colorArgb = defaultColorArgb,
                    selected = NOVEL_HIGHLIGHT_PRESET_COLORS.none { it == defaultColorArgb },
                    onClick = { showDefaultPicker = true },
                )
            }

            OrnamentDivider()

            if (items.isEmpty()) {
                Text(
                    text = stringResource(AYMR.strings.novel_highlight_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp)) {
                    items(items, key = { it.highlight.id }) { item ->
                        HighlightRow(
                            item = item,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onCopy = onCopy,
                        )
                    }
                }
                // Финальный флерон после списка — маленькое украшение-подпись.
                Text(
                    text = "❦",
                    color = AuroraTheme.colors.accent.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }

    if (showDefaultPicker) {
        NovelColorPickerDialog(
            initial = defaultColorArgb,
            onPick = { picked ->
                onDefaultColorChange(picked)
                showDefaultPicker = false
            },
            onDismiss = { showDefaultPicker = false },
        )
    }
}

/** Центрированная шапка: серифный титул + строка-источник «❦ из <новелла> ❦». */
@Composable
private fun SheetHead(novelTitle: String) {
    val accent = AuroraTheme.colors.accent
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(
            text = "Highlights",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = accent.copy(alpha = 0.85f))) { append("❦ из ") }
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(novelTitle) }
                withStyle(SpanStyle(color = accent.copy(alpha = 0.85f))) { append(" ❦") }
            },
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp, start = 20.dp, end = 20.dp),
        )
    }
}

/** Разделитель «линия — ❦ — линия» между секцией цвета и списком. */
@Composable
private fun OrnamentDivider() {
    val hairline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(hairline))
        Text(
            text = "❦",
            color = AuroraTheme.colors.accent.copy(alpha = 0.75f),
            fontSize = 11.sp,
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(hairline))
    }
}

/** Кружок пресетного цвета для дефолта новых выделений. */
@Composable
private fun ColorSwatchDot(colorArgb: Long, selected: Boolean, onClick: () -> Unit) {
    val accent = AuroraTheme.colors.accent
    val ring = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color(colorArgb), CircleShape)
            .border(width = if (selected) 2.dp else 1.dp, color = ring, shape = CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Кружок произвольного цвета: показывает текущий дефолт, открывает пикер. */
@Composable
private fun CustomSwatchDot(colorArgb: Long, selected: Boolean, onClick: () -> Unit) {
    val accent = AuroraTheme.colors.accent
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color(colorArgb), CircleShape)
            .border(width = if (selected) 2.dp else 1.dp, color = accent, shape = CircleShape)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                        center = center,
                        radius = size.minDimension,
                    ),
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Строка одного хайлайта: цветная точка и серифная цитата занимают всю ширину, а источник
 * и компактные действия (копировать / редактировать / удалить) вынесены в нижнюю строку.
 */
@Composable
private fun HighlightRow(
    item: NovelHighlightWithChapter,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onCopy: (String) -> Unit,
) {
    val highlight = item.highlight
    val accent = AuroraTheme.colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(highlight.id) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(12.dp)
                .background(Color(highlight.colorArgb), CircleShape),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = highlight.normalizedText,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
            )
            if (highlight.note.isNotBlank()) {
                Text(
                    text = highlight.note,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .drawBehind {
                            drawRect(
                                brush = SolidColor(accent.copy(alpha = 0.75f)),
                                topLeft = Offset.Zero,
                                size = Size(2.dp.toPx(), size.height),
                            )
                        }
                        .padding(start = 8.dp),
                )
            }
            // Нижняя строка: источник слева, компактные действия справа.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                        ) {
                            append(item.novelTitle)
                        }
                        append(" · ")
                        append(item.chapterName.orEmpty())
                        if (highlight.pageCount > 0) {
                            append(" · ")
                            append(
                                stringResource(
                                    AYMR.strings.novel_highlight_page_label,
                                    highlight.pageIndex,
                                    highlight.pageCount,
                                ),
                            )
                        }
                    },
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MiniAction(
                    icon = Icons.Outlined.ContentCopy,
                    description = stringResource(AYMR.strings.novel_highlight_action_copy),
                    onClick = { onCopy(highlight.normalizedText) },
                )
                MiniAction(
                    icon = Icons.Outlined.Edit,
                    description = stringResource(AYMR.strings.novel_highlight_editor_note_hint),
                    onClick = { onEdit(highlight.id) },
                )
                MiniAction(
                    icon = Icons.Outlined.Delete,
                    description = stringResource(AYMR.strings.novel_highlight_action_delete),
                    onClick = { onDelete(highlight.id) },
                )
            }
        }
    }
}

/** Компактная круглая кнопка-действие без 48dp-минимума стандартного IconButton. */
@Composable
private fun MiniAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Полупрозрачный слой состаренной бумаги поверх стеклянной поверхности шторки:
 * рисуется в режиме Overlay с малой альфой — фактура читается, контент просвечивает.
 */
@Composable
private fun PaperVeil(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val paper = remember(context) {
        context.resources.decodeAssetBitmap(R.drawable.paper_highlights).asImageBitmap()
    }
    Canvas(modifier = modifier) {
        drawImageCovered(paper, alpha = 0.32f)
    }
}

/** Закладка-лента, висящая справа сверху шторки. */
@Composable
private fun BookmarkRibbon(modifier: Modifier = Modifier) {
    val accent = AuroraTheme.colors.accent
    Canvas(modifier = modifier.padding(end = 26.dp).size(width = 22.dp, height = 56.dp)) {
        val w = size.width
        val h = size.height
        val notch = h * 0.16f
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            lineTo(w / 2f, h - notch)
            lineTo(0f, h)
            close()
        }
        drawPath(path = path, brush = Brush.verticalGradient(listOf(Color(0xFFA82020), Color(0xFF6E1010))))
        drawRect(brush = SolidColor(accent), topLeft = Offset.Zero, size = Size(w, 2.dp.toPx()))
    }
}

/** Cover-отрисовка bitmap с Overlay-блендом (аналог CSS mix-blend-mode: overlay). */
private fun DrawScope.drawImageCovered(bitmap: ImageBitmap, alpha: Float) {
    val iw = bitmap.width.toFloat()
    val ih = bitmap.height.toFloat()
    if (iw <= 0f || ih <= 0f) return
    val scale = max(size.width / iw, size.height / ih)
    val dw = (iw * scale).roundToInt()
    val dh = (ih * scale).roundToInt()
    val left = -((dw - size.width) / 2f).roundToInt()
    val top = -((dh - size.height) / 2f).roundToInt()
    drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(dw, dh),
        alpha = alpha,
        blendMode = BlendMode.Overlay,
        filterQuality = FilterQuality.Low,
    )
}

private fun android.content.res.Resources.decodeAssetBitmap(resId: Int): android.graphics.Bitmap =
    android.graphics.BitmapFactory.decodeResource(this, resId)
