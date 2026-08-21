package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dedicated antique parchment & amber gold palette:
 * Warm, noble tones tuned specifically for the vellum/parchment texture in both dark & light themes.
 */
private val ParchmentGold = Color(0xFFE5A855) // Warm luminous antique gold (Cloudflare amber tone)
private val ParchmentGoldMuted = Color(0xFFB88A4A) // Gentle antique bronze

/**
 * Reader highlights & quotes sheet: Compact Reader Flow (Variant 8).
 * Airy, high-density reader layout with delicate dividers, authentic parchment texture,
 * hanging silk ribbon, and clean centered typography.
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
    val isDark = isSystemInDarkTheme()

    AuroraReaderSheet(
        onDismissRequest = onDismiss,
        glassOverlay = {
            PaperVeil(Modifier.matchParentSize())
            BookmarkRibbon(Modifier.align(Alignment.TopEnd))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetHead(novelTitle = novelTitle, isDark = isDark)

            PaletteSection(
                defaultColorArgb = defaultColorArgb,
                onDefaultColorChange = onDefaultColorChange,
                onOpenCustomPicker = { showDefaultPicker = true },
            )

            OrnamentDivider()

            if (items.isEmpty()) {
                Text(
                    text = stringResource(AYMR.strings.novel_highlight_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFFA39682) else Color(0xFF7A6953),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                ) {
                    items(items, key = { it.highlight.id }) { item ->
                        CompactQuoteRow(
                            item = item,
                            isDark = isDark,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onCopy = onCopy,
                        )
                    }
                }
                // Завершающий флерон после списка
                Text(
                    text = "❦",
                    color = ParchmentGold.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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

/** Центрированная шапка: современный заголовок + подпись источника «❦ из <новелла> ❦». */
@Composable
private fun SheetHead(novelTitle: String, isDark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(AYMR.strings.novel_highlight_menu_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = (-0.2).sp,
            color = if (isDark) Color(0xFFFAF5ED) else Color(0xFF2B1E0E),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = ParchmentGold.copy(alpha = 0.85f))) { append("❦ из ") }
                withStyle(SpanStyle(color = ParchmentGold, fontWeight = FontWeight.Bold)) { append(novelTitle) }
                withStyle(SpanStyle(color = ParchmentGold.copy(alpha = 0.85f))) { append(" ❦") }
            },
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp, start = 20.dp, end = 20.dp),
        )
    }
}

/** Гармоничная палитра маркеров. */
@Composable
private fun PaletteSection(
    defaultColorArgb: Long,
    onDefaultColorChange: (Long) -> Unit,
    onOpenCustomPicker: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                onClick = onOpenCustomPicker,
            )
        }
    }
}

/** Тонкий градиентный разделитель «линия — ❦ — линия». */
@Composable
private fun OrnamentDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, ParchmentGold.copy(alpha = 0.28f)),
                    ),
                ),
        )
        Text(
            text = "❦",
            color = ParchmentGold.copy(alpha = 0.85f),
            fontSize = 11.sp,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(ParchmentGold.copy(alpha = 0.28f), Color.Transparent),
                    ),
                ),
        )
    }
}

/** Кружок цвета маркера. */
@Composable
private fun ColorSwatchDot(colorArgb: Long, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) ParchmentGold else ParchmentGoldMuted.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(Color(colorArgb), CircleShape)
            .border(width = if (selected) 2.dp else 1.dp, color = ring, shape = CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Кружок произвольного цвета маркера. */
@Composable
private fun CustomSwatchDot(colorArgb: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(Color(colorArgb), CircleShape)
            .border(width = if (selected) 2.dp else 1.dp, color = ParchmentGold, shape = CircleShape)
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
        Text(text = "+", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Вариант 8: Компактная эстетичная строка цитаты без тяжелых рамок.
 * Текст цитаты дышит, заметка вынесена изящным отступом, а снизу идет тонкая золотая нить.
 */
@Composable
private fun CompactQuoteRow(
    item: NovelHighlightWithChapter,
    isDark: Boolean,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onCopy: (String) -> Unit,
) {
    val highlight = item.highlight
    val textColor = if (isDark) Color(0xFFEFE9E0) else Color(0xFF342616)
    val metaColor = if (isDark) Color(0xFFA39682) else Color(0xFF7A6953)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(highlight.id) }
            .padding(horizontal = 20.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // Маркер цвета слева
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(Color(highlight.colorArgb), CircleShape),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                // Текст цитаты
                Text(
                    text = "«${highlight.normalizedText}»",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    color = textColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                // Заметка
                if (highlight.note.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "↳",
                            color = ParchmentGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = highlight.note,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            style = MaterialTheme.typography.bodySmall,
                            color = ParchmentGold.copy(alpha = 0.92f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Нижняя строка: источник слева, компактные действия справа
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color(0xFFFAF5ED) else Color(0xFF2B1E0E),
                                ),
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
                        letterSpacing = 0.2.sp,
                        color = metaColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    MiniAction(
                        icon = Icons.Outlined.ContentCopy,
                        description = stringResource(AYMR.strings.novel_highlight_action_copy),
                        tint = metaColor,
                        onClick = { onCopy(highlight.normalizedText) },
                    )
                    MiniAction(
                        icon = Icons.Outlined.Edit,
                        description = stringResource(AYMR.strings.novel_highlight_editor_note_hint),
                        tint = metaColor,
                        onClick = { onEdit(highlight.id) },
                    )
                    MiniAction(
                        icon = Icons.Outlined.Delete,
                        description = stringResource(AYMR.strings.novel_highlight_action_delete),
                        tint = metaColor,
                        onClick = { onDelete(highlight.id) },
                    )
                }
            }
        }
        // Тончайшая золотистая градиентная нить-разделитель между цитатами
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            ParchmentGold.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

/** Компактная круглая кнопка-действие. */
@Composable
private fun MiniAction(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(15.dp),
            tint = tint,
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
        drawRect(brush = SolidColor(ParchmentGold), topLeft = Offset.Zero, size = Size(w, 2.dp.toPx()))
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
