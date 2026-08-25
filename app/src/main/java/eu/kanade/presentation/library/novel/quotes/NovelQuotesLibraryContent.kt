@file:OptIn(ExperimentalFoundationApi::class)

package eu.kanade.presentation.library.novel.quotes

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalIsEInkMode
import eu.kanade.tachiyomi.ui.library.novel.quotes.NovelQuotesLibraryState
import eu.kanade.tachiyomi.ui.library.novel.quotes.NovelQuotesListOps
import eu.kanade.tachiyomi.ui.library.novel.quotes.NovelQuotesSortMode
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

private fun headTextColor(isDark: Boolean): Color = if (isDark) Color(0xFFFAF5ED) else Color(0xFF2B1E0E)
private fun quoteTextColor(isDark: Boolean): Color = if (isDark) Color(0xFFEFE9E0) else Color(0xFF1E293B)

@Composable
fun NovelQuotesLibraryContent(
    state: NovelQuotesLibraryState,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSort: () -> Unit,
    onSelectBook: (String?) -> Unit,
    onEditQuote: (NovelHighlightWithChapter) -> Unit,
    onDeleteQuote: (NovelHighlightWithChapter) -> Unit,
    onCopyQuote: (String) -> Unit,
) {
    val colors = AuroraTheme.colors
    val isDark = colors.isDark
    var searchVisible by remember { mutableStateOf(state.query.isNotEmpty()) }

    val visibleItems = remember(state.quotes) { NovelQuotesListOps.visible(state.quotes) }
    val filteredItems = remember(visibleItems, state.query, state.bookFilter) {
        NovelQuotesListOps.filter(visibleItems, state.query, state.bookFilter)
    }
    val sortedItems = remember(filteredItems, state.sortMode) {
        NovelQuotesListOps.sorted(filteredItems, state.sortMode)
    }
    val sections = remember(sortedItems) { NovelQuotesListOps.sections(sortedItems) }
    val countsByNovel = remember(visibleItems) {
        NovelQuotesListOps.countsByNovel(visibleItems).toSortedMap(compareBy { it.lowercase() })
    }
    val booksCount = remember(filteredItems) { filteredItems.map { it.novelTitle }.distinct().size }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // V6 «Чернила в воде»: живой фон только на тёмной теме (внутри — свои фолбэки).
        if (isDark) {
            InkWaterBackground(modifier = Modifier.matchParentSize())
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Top bar: назад · заголовок · поиск · сортировка — с выверенными аврора-отступами
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuroraTopBarIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(MR.strings.action_bar_up_description),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(AYMR.strings.novel_quotes_library_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AuroraTopBarIconButton(
                        onClick = { searchVisible = !searchVisible },
                        icon = if (searchVisible) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = stringResource(MR.strings.action_search),
                    )
                    AuroraTopBarIconButton(
                        onClick = onToggleSort,
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = stringResource(AYMR.strings.novel_quotes_sort_toggle_cd),
                    )
                }
            }

            // Поиск по цитатам и заметкам
            AnimatedVisibility(visible = searchVisible) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    placeholder = { Text(text = stringResource(AYMR.strings.novel_quotes_search_hint)) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onSearchQueryChange("")
                                searchVisible = false
                            },
                        ) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = null)
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                )
            }

            ExlibrisHeader(
                quotesCount = filteredItems.size,
                booksCount = booksCount,
                bookFilter = state.bookFilter,
            )

            OrnamentDivider()

            BookFilterRow(
                selected = state.bookFilter,
                countsByNovel = countsByNovel,
                totalCount = visibleItems.size,
                onSelect = onSelectBook,
            )

            when {
                visibleItems.isEmpty() -> EmptyContent(isSearch = false, isDark = isDark)
                filteredItems.isEmpty() -> EmptyContent(isSearch = true, isDark = isDark)
                else -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (state.sortMode == NovelQuotesSortMode.TITLE) {
                        sections.forEach { section ->
                            stickyHeader(key = "sec_${section.title}") {
                                SectionHeader(
                                    title = section.title,
                                    count = section.items.size,
                                    isDark = isDark,
                                    backgroundColor = colors.background,
                                )
                            }
                            items(section.items, key = { it.highlight.id }) { item ->
                                QuoteRow(
                                    item = item,
                                    showTitle = false,
                                    isDark = isDark,
                                    onEdit = onEditQuote,
                                    onDelete = onDeleteQuote,
                                    onCopy = onCopyQuote,
                                )
                            }
                        }
                    } else {
                        items(sortedItems, key = { it.highlight.id }) { item ->
                            QuoteRow(
                                item = item,
                                showTitle = true,
                                isDark = isDark,
                                onEdit = onEditQuote,
                                onDelete = onDeleteQuote,
                                onCopy = onCopyQuote,
                            )
                        }
                    }
                    item(key = "fleuron") {
                        Text(
                            text = "❦",
                            color = colors.accent.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Экслибрис «CODEX SACRA» — V4 «Тихое стекло»: типографика + живая aurora-рамка. */
@Composable
private fun ExlibrisHeader(
    quotesCount: Int,
    booksCount: Int,
    bookFilter: String?,
) {
    val colors = AuroraTheme.colors
    val isDark = colors.isDark
    val accent = colors.accent
    val isEInk = LocalIsEInkMode.current

    // Вход заголовка: tracking-in + проявление, один раз при композиции.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(1400, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "exlibrisEntrance",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.accentVariant.copy(alpha = if (isDark) 0.35f else 0.06f),
                            (if (isDark) Color.White else colors.accentVariant).copy(
                                alpha = if (isDark) 0.04f else 0.05f,
                            ),
                        ),
                    ),
                )
                .drawWithCache {
                    val stroke = Stroke(width = 1.dp.toPx())
                    val corner = CornerRadius(20.dp.toPx())
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = 0f,
                                top = 0f,
                                right = size.width,
                                bottom = size.height,
                                radiusX = corner.x,
                                radiusY = corner.y,
                            ),
                        )
                    }
                    // Статичная рамка: мягкий вертикальный градиент из акцентов темы.
                    val borderBrush = if (isEInk) {
                        SolidColor(if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.25f))
                    } else if (isDark) {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.40f),
                                accent.copy(alpha = 0.30f),
                                colors.accentVariant.copy(alpha = 0.22f),
                                Color.Transparent,
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                accent.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.10f),
                            ),
                        )
                    }
                    onDrawBehind {
                        drawPath(path = path, brush = borderBrush, style = stroke)
                    }
                }
                .padding(vertical = 24.dp, horizontal = 18.dp)
                .graphicsLayer { alpha = entrance },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CODEX SACRA",
                    style = MaterialTheme.typography.titleMedium.merge(
                        TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    accent.copy(alpha = 0.70f),
                                    colors.textPrimary,
                                    accent.copy(alpha = 0.70f),
                                ),
                            ),
                        ),
                    ),
                    fontSize = 26.sp,
                    letterSpacing = (6 - 2.5 * entrance).sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (bookFilter == null) {
                    Text(
                        text = stringResource(AYMR.strings.novel_quotes_collection_all),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    val formatted = stringResource(AYMR.strings.novel_quotes_collection_book, bookFilter)
                    val nameStart = formatted.indexOf('„')
                    val nameEnd = formatted.lastIndexOf('”')
                    Text(
                        text = buildAnnotatedString {
                            if (nameStart >= 0 && nameEnd > nameStart) {
                                append(formatted.substring(0, nameStart + 1))
                                withStyle(
                                    SpanStyle(color = accent, fontWeight = FontWeight.SemiBold),
                                ) { append(formatted.substring(nameStart + 1, nameEnd)) }
                                append(formatted.substring(nameEnd))
                            } else {
                                append(formatted)
                            }
                        },
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = pluralStringResource(AYMR.plurals.novel_quotes_count_quotes, quotesCount, quotesCount),
                        color = colors.textPrimary.copy(alpha = 0.90f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 12.dp)
                            .background(colors.divider),
                    )
                    Text(
                        text = pluralStringResource(AYMR.plurals.novel_quotes_count_books, booksCount, booksCount),
                        color = colors.textPrimary.copy(alpha = 0.90f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Орнамент-дивайдер: линия — ❖ — линия. */
@Composable
private fun OrnamentDivider() {
    val accent = AuroraTheme.colors.accent
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
                    Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.28f))),
                ),
        )
        Text(text = "❖", color = accent.copy(alpha = 0.85f), fontSize = 11.sp)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent)),
                ),
        )
    }
}

/** Лента чипов фильтра по книгам. */
@Composable
private fun BookFilterRow(
    selected: String?,
    countsByNovel: Map<String, Int>,
    totalCount: Int,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookFilterChip(
            label = stringResource(AYMR.strings.novel_quotes_filter_all),
            count = totalCount,
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        countsByNovel.forEach { (novelTitle, count) ->
            BookFilterChip(
                label = novelTitle,
                count = count,
                selected = selected == novelTitle,
                onClick = { onSelect(novelTitle) },
            )
        }
    }
}

@Composable
private fun BookFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val isDark = colors.isDark

    // Аврора-стекло с динамическим верхним димом темы и спекулярным бликом
    val topBgAlpha = when {
        selected -> if (isDark) 0.28f else 0.18f
        isDark -> 0.08f
        else -> 0.05f
    }
    val bottomBgAlpha = when {
        selected -> if (isDark) 0.08f else 0.05f
        isDark -> 0.02f
        else -> 0.01f
    }
    val topBorderAlpha = when {
        selected -> if (isDark) 0.85f else 0.95f
        isDark -> 0.22f
        else -> 0.18f
    }
    val bottomBorderAlpha = when {
        selected -> if (isDark) 0.25f else 0.18f
        isDark -> 0.06f
        else -> 0.04f
    }

    val bgBrush = if (selected) {
        Brush.verticalGradient(
            listOf(
                colors.accent.copy(alpha = topBgAlpha),
                colors.accent.copy(alpha = bottomBgAlpha),
            ),
        )
    } else {
        val tint = if (isDark) Color.White else Color.Black
        Brush.verticalGradient(
            listOf(
                tint.copy(alpha = topBgAlpha),
                tint.copy(alpha = bottomBgAlpha),
            ),
        )
    }

    val borderBrush = if (selected) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = topBorderAlpha),
                colors.accent.copy(alpha = bottomBorderAlpha),
            ),
        )
    } else {
        val borderTint = if (isDark) Color.White else Color.Black
        Brush.verticalGradient(
            listOf(
                borderTint.copy(alpha = topBorderAlpha),
                borderTint.copy(alpha = bottomBorderAlpha),
            ),
        )
    }

    val textColor = if (selected) {
        if (colors.isEInk) {
            colors.textOnAccent
        } else if (isDark) {
            colors.textPrimary
        } else {
            colors.accent
        }
    } else {
        colors.textSecondary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgBrush, RoundedCornerShape(50))
            .border(1.dp, borderBrush, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        Text(
            text = count.toString(),
            color = if (selected) colors.textOnAccent else textColor.copy(alpha = 0.9f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    brush = if (selected) {
                        Brush.verticalGradient(
                            listOf(colors.accent, colors.accentVariant),
                        )
                    } else {
                        val badgeTint = if (isDark) Color.White else Color.Black
                        Brush.verticalGradient(
                            listOf(badgeTint.copy(alpha = 0.12f), badgeTint.copy(alpha = 0.08f)),
                        )
                    },
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 7.dp, vertical = 1.5.dp),
        )
    }
}

/** Липкий заголовок секции книги (режим сортировки «по тайтлу»). */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    isDark: Boolean,
    backgroundColor: Color,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = headTextColor(isDark),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "· $count",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent.copy(alpha = 0.85f),
        )
    }
}

/** Строка цитаты: маркер, цитата, заметка, провенанс, действия, динамическая нить. */
@Composable
private fun QuoteRow(
    item: NovelHighlightWithChapter,
    showTitle: Boolean,
    isDark: Boolean,
    onEdit: (NovelHighlightWithChapter) -> Unit,
    onDelete: (NovelHighlightWithChapter) -> Unit,
    onCopy: (String) -> Unit,
) {
    val colors = AuroraTheme.colors
    val highlight = item.highlight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(item) }
            .padding(horizontal = 20.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
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
                Text(
                    text = "«${highlight.normalizedText}»",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    color = quoteTextColor(isDark),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
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
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = highlight.note,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accent.copy(alpha = 0.92f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            if (showTitle) {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = headTextColor(isDark))) {
                                    append(item.novelTitle)
                                }
                                append(" · ")
                            }
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
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    MiniAction(
                        icon = Icons.Outlined.ContentCopy,
                        description = stringResource(AYMR.strings.novel_highlight_action_copy),
                        tint = colors.textSecondary,
                        onClick = { onCopy(highlight.normalizedText) },
                    )
                    MiniAction(
                        icon = Icons.Outlined.Edit,
                        description = stringResource(AYMR.strings.novel_highlight_editor_note_hint),
                        tint = colors.textSecondary,
                        onClick = { onEdit(item) },
                    )
                    MiniAction(
                        icon = Icons.Outlined.Delete,
                        description = stringResource(AYMR.strings.novel_highlight_action_delete),
                        tint = Color(0xFFEF4444),
                        onClick = { onDelete(item) },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, colors.accent.copy(alpha = 0.22f), Color.Transparent),
                    ),
                ),
        )
    }
}

/** Компактная круглая кнопка-действие строки. */
@Composable
private fun MiniAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(15.dp),
            tint = tint,
        )
    }
}

/** Пустые состояния: нет цитат вообще / нет результатов поиска. */
@Composable
private fun EmptyContent(isSearch: Boolean, isDark: Boolean) {
    val colors = AuroraTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isSearch) "🔍" else "❦",
            color = colors.textSecondary,
            fontSize = 34.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isSearch) {
                stringResource(AYMR.strings.novel_quotes_empty_search)
            } else {
                stringResource(AYMR.strings.novel_quotes_empty)
            },
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
