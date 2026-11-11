package eu.kanade.presentation.more.settings.screen.discovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.tadami.aurora.R
import eu.kanade.presentation.browse.anime.components.AnimeExtensionIcon
import eu.kanade.presentation.browse.manga.components.MangaExtensionIcon
import eu.kanade.presentation.browse.novel.shouldLoadNovelPluginIcon
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.resolveAuroraTabContainerColor
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraTopBarScrimColor
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.util.Screen as ParentScreen

/**
 * Пикер «Источники подборок» (Для тебя): какие плагины участвуют в подборках.
 * Вариант A · Frost Settings, поверхности — единая семья таб-контейнеров Aurora
 * (resolveAuroraTabContainerColor + rim-light): тонкие, округлые, прозрачные,
 * без сероватого frost-налёта. Единица списка — плагин (расширение), не языковой вариант.
 */
class DiscoverySourcesScreen : ParentScreen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val model = rememberScreenModel { DiscoverySourcesScreenModel(context.applicationContext) }
        val state by model.state.collectAsState()
        val colors = AuroraTheme.colors

        AuroraBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(resolveAuroraTopBarScrimColor(colors)),
                    ) {
                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AuroraTopBarIconButton(
                                onClick = navigator::pop,
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(MR.strings.action_bar_up_description),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = stringResource(AYMR.strings.pref_discovery_sources_title),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = stringResource(AYMR.strings.pref_discovery_sources_summary),
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        // Sticky media-табы: отступ сверху от шапки, снизу — минимальный
                        // зазор до контента (просьба пользователя по вертикальному ритму).
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(resolveAuroraTopBarScrimColor(colors))
                                .padding(top = 8.dp, bottom = 6.dp),
                        ) {
                            DiscoveryMediaTabs(
                                selected = state.mediaType,
                                counts = state.counts,
                                onSelect = model::switchMedia,
                            )
                        }
                    }
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    val filtered = remember(state.entries, state.query) {
                        // В ручном режиме выбранные плагины всегда сверху списка.
                        filterSourcePicks(selectedFirst(state.entries), state.query)
                    }
                    // Табы уже дают воздушный зазор — контент стартует ближе к ним.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 24.dp),
                    ) {
                        item(key = "mode") {
                            Box(modifier = Modifier.padding(bottom = 10.dp)) {
                                ModeTabs(mode = state.mode, onMode = model::setMode)
                            }
                        }
                        if (state.mode == "auto") {
                            item(key = "auto_card") {
                                Box(modifier = Modifier.padding(bottom = 10.dp)) {
                                    AutoTopCard(state = state, model = model, colors = colors)
                                }
                            }
                            if (state.entries.isNotEmpty()) {
                                item(key = "auto_preview") {
                                    Box(modifier = Modifier.padding(bottom = 10.dp).alpha(0.38f)) {
                                        SourceSectionCard(
                                            entries = state.entries.take(4),
                                            mediaType = state.mediaType,
                                            model = model,
                                            colors = colors,
                                            allowedCount = state.entries.count { !it.excluded },
                                            clickable = false,
                                            onToggle = {},
                                        )
                                    }
                                }
                            }
                        } else {
                            item(key = "search") {
                                Box(modifier = Modifier.padding(bottom = 10.dp)) {
                                    SearchRow(
                                        state = state,
                                        colors = colors,
                                        onQuery = model::setQuery,
                                        onSelectAll = model::selectAll,
                                        onDeselectAll = model::deselectAll,
                                    )
                                }
                            }
                            if (isOverloadWarn(state.entries)) {
                                item(key = "warn") {
                                    Box(modifier = Modifier.padding(bottom = 10.dp)) {
                                        WarnStrip(colors = colors)
                                    }
                                }
                            }
                            if (filtered.isEmpty()) {
                                item(key = "empty") {
                                    Box(modifier = Modifier.padding(bottom = 10.dp)) {
                                        Text(
                                            text = stringResource(AYMR.strings.discovery_sources_search_empty),
                                            fontSize = 11.5.sp,
                                            color = colors.textSecondary,
                                            fontStyle = FontStyle.Italic,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 18.dp),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = filtered,
                                    key = { _, entry -> "source_" + entry.pluginKey },
                                ) { index, entry ->
                                    val shape = when {
                                        filtered.size == 1 -> RoundedCornerShape(20.dp)
                                        index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                        index == filtered.lastIndex -> RoundedCornerShape(
                                            bottomStart = 20.dp,
                                            bottomEnd = 20.dp,
                                        )
                                        else -> RectangleShape
                                    }
                                    val bottomPad = if (index == filtered.lastIndex) 10.dp else 0.dp
                                    SourcePickRowItem(
                                        entry = entry,
                                        mediaType = state.mediaType,
                                        model = model,
                                        colors = colors,
                                        allowedCount = state.entries.count { !it.excluded },
                                        clickable = true,
                                        shape = shape,
                                        showDivider = index < filtered.lastIndex,
                                        onToggle = { model.toggleSource(entry.pluginKey) },
                                        modifier = Modifier.padding(bottom = bottomPad),
                                    )
                                }
                            }
                            item(key = "counter") {
                                Box(modifier = Modifier.padding(bottom = 10.dp)) {
                                    CounterCard(state = state, colors = colors)
                                }
                            }
                        }
                        item(key = "footer") {
                            Text(
                                text = stringResource(AYMR.strings.discovery_sources_apply_note),
                                fontSize = 10.5.sp,
                                color = colors.textSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 10.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    ToastOverlay(state = state, model = model, colors = colors)
                }
            }
        }
    }
}

// ── единая поверхность семьи таб-контейнеров ───────────────────────────────────

/** Та же рецептура, что у переключателей разделов: прозрачный контейнер + rim-light. */
private fun Modifier.sectionSurface(colors: AuroraColors, shape: Shape): Modifier = this
    .clip(shape)
    .background(resolveAuroraTabContainerColor(colors))
    .border(width = 1.dp, brush = auroraMenuRimLightBrush(colors), shape = shape)

@Composable
private fun DiscoveryMediaTabs(
    selected: DiscoveryMediaType,
    counts: Map<DiscoveryMediaType, Int>,
    onSelect: (DiscoveryMediaType) -> Unit,
) {
    val mediaTypes = remember { DiscoveryMediaType.entries }
    val tabs = remember(counts) {
        persistentListOf(
            TabContent(
                titleRes = AYMR.strings.label_anime,
                badgeNumber = counts[DiscoveryMediaType.ANIME],
                content = { _, _ -> },
            ),
            TabContent(
                titleRes = AYMR.strings.label_manga,
                badgeNumber = counts[DiscoveryMediaType.MANGA],
                content = { _, _ -> },
            ),
            TabContent(
                titleRes = AYMR.strings.label_novel,
                badgeNumber = counts[DiscoveryMediaType.NOVEL],
                content = { _, _ -> },
            ),
        )
    }
    AuroraTabRow(
        tabs = tabs,
        selectedIndex = mediaTypes.indexOf(selected).coerceAtLeast(0),
        onTabSelected = { index -> mediaTypes.getOrNull(index)?.let(onSelect) },
        scrollable = false,
        // Compact: «Ранобэ» + бейдж должны влезать в треть ширины без обрезания.
        compact = true,
    )
}

// ── сегмент режима: тот же тонкий таб-стиль, что и медиатабы ───────────────────

@Composable
private fun ModeTabs(mode: String, onMode: (String) -> Unit) {
    val tabs = remember {
        persistentListOf(
            TabContent(titleRes = AYMR.strings.discovery_sources_mode_auto, content = { _, _ -> }),
            TabContent(titleRes = AYMR.strings.discovery_sources_mode_manual, content = { _, _ -> }),
        )
    }
    AuroraTabRow(
        tabs = tabs,
        selectedIndex = if (mode == "manual") 1 else 0,
        onTabSelected = { index -> onMode(if (index == 1) "manual" else "auto") },
        scrollable = false,
    )
}

// ── авто-карточка ──────────────────────────────────────────────────────────────

@Composable
private fun AutoTopCard(
    state: DiscoverySourcesUiState,
    model: DiscoverySourcesScreenModel,
    colors: AuroraColors,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sectionSurface(colors, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(AYMR.strings.discovery_sources_auto_card_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(AYMR.strings.discovery_sources_auto_card_body),
            fontSize = 12.sp,
            color = colors.textSecondary,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
        val top3 = state.entries.filter { it.isTop3Auto }
        if (top3.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                top3.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.White.copy(alpha = if (colors.isDark) 0.04f else 0.30f))
                            .border(1.dp, colors.divider, RoundedCornerShape(13.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.accent,
                            modifier = Modifier.width(24.dp),
                        )
                        PluginIcon(entry = entry, mediaType = state.mediaType, model = model, size = 34)
                        Text(
                            text = entry.name,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                        )
                        Text(
                            text = pluralStringResource(
                                AYMR.plurals.discovery_sources_weight,
                                entry.weight,
                                entry.weight,
                            ),
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSecondary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

// ── поиск + действия ───────────────────────────────────────────────────────────

@Composable
private fun SearchRow(
    state: DiscoverySourcesUiState,
    colors: AuroraColors,
    onQuery: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
) {
    val allSelected = state.entries.isNotEmpty() && state.entries.none { it.excluded }
    val pillShape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = state.query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = colors.textPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            Text(
                                text = stringResource(AYMR.strings.discovery_sources_search_hint),
                                fontSize = 13.sp,
                                color = colors.textSecondary.copy(alpha = 0.65f),
                            )
                        }
                        inner()
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .sectionSurface(colors, pillShape),
        )
        Text(
            text = stringResource(
                if (allSelected) {
                    AYMR.strings.discovery_sources_deselect_all
                } else {
                    AYMR.strings.discovery_sources_select_all
                },
            ),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.accent,
            modifier = Modifier
                .sectionSurface(colors, pillShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = if (allSelected) onDeselectAll else onSelectAll,
                )
                .padding(horizontal = 15.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun WarnStrip(colors: AuroraColors) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.warning.copy(alpha = 0.09f))
            .border(1.dp, colors.warning.copy(alpha = 0.25f), shape)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "⚠️", fontSize = 12.sp)
        Text(
            text = stringResource(AYMR.strings.discovery_sources_warn_many),
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = colors.warning,
        )
    }
}

// ── секция строк плагинов ──────────────────────────────────────────────────────

/** Отдельная виртуализированная строка плагина в общем списке LazyColumn. */
@Composable
private fun SourcePickRowItem(
    entry: SourcePickUi,
    mediaType: DiscoveryMediaType,
    model: DiscoverySourcesScreenModel,
    colors: AuroraColors,
    allowedCount: Int,
    clickable: Boolean,
    shape: Shape,
    showDivider: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .sectionSurface(colors, shape),
    ) {
        SourcePickRow(
            entry = entry,
            mediaType = mediaType,
            model = model,
            colors = colors,
            allowedCount = allowedCount,
            clickable = clickable,
            onClick = onToggle,
        )
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(1.dp)
                    .background(colors.divider.copy(alpha = 0.5f)),
            )
        }
    }
}

/** Групповая карточка-секция со строками плагинов (паттерн «grouped section cards»). */
@Composable
private fun SourceSectionCard(
    entries: List<SourcePickUi>,
    mediaType: DiscoveryMediaType,
    model: DiscoverySourcesScreenModel,
    colors: AuroraColors,
    allowedCount: Int,
    clickable: Boolean,
    onToggle: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sectionSurface(colors, RoundedCornerShape(20.dp))
            .padding(vertical = 6.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(1.dp)
                        .background(colors.divider.copy(alpha = 0.5f)),
                )
            }
            SourcePickRow(
                entry = entry,
                mediaType = mediaType,
                model = model,
                colors = colors,
                allowedCount = allowedCount,
                clickable = clickable,
                onClick = { onToggle(entry.pluginKey) },
            )
        }
    }
}

@Composable
private fun SourcePickRow(
    entry: SourcePickUi,
    mediaType: DiscoveryMediaType,
    model: DiscoverySourcesScreenModel,
    colors: AuroraColors,
    allowedCount: Int,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (entry.excluded) 0.5f else 1f)
            .then(
                if (clickable) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PluginIcon(entry = entry, mediaType = mediaType, model = model, size = 42)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = entry.name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = pluralStringResource(
                        AYMR.plurals.discovery_sources_weight,
                        entry.weight,
                        entry.weight,
                    ),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary,
                    // Вес уступает место чипам и эллипсизируется: узкий Text иначе
                    // уходил в «вертикальную ленту» и растягивал ряд по высоте.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.isTop3Auto) {
                    SourceBadge(
                        text = stringResource(AYMR.strings.discovery_sources_badge_top3),
                        color = colors.success,
                    )
                }
                if (entry.isLastUsed) {
                    SourceBadge(
                        text = stringResource(AYMR.strings.discovery_sources_badge_last_used),
                        color = colors.textSecondary,
                    )
                }
            }
        }
        FrostCheckbox(
            checked = !entry.excluded,
            locked = !entry.excluded && allowedCount == 1,
        )
    }
}

@Composable
private fun SourceBadge(text: String, color: Color) {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text = text,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.4.sp,
        color = color,
        // Чип всегда в одну строку и центрирован: перенос («ТОП-3 / АВТО») ломал ряды.
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.30f), shape)
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
    )
}

/** Чекбокс в стиле таб-селекции: accent-градиент во включённом состоянии, locked — защита последнего. */
@Composable
private fun FrostCheckbox(checked: Boolean, locked: Boolean) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(8.dp)
    val selectedBrush = remember(colors.accent) {
        Brush.verticalGradient(
            colors = listOf(
                if (colors.isDark) {
                    androidx.compose.ui.graphics.lerp(colors.accent, Color.White, 0.18f).copy(alpha = 0.32f)
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
    Box(
        modifier = Modifier
            .size(23.dp)
            .alpha(if (locked) 0.55f else 1f)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(selectedBrush).border(
                        1.dp,
                        colors.accent.copy(alpha = 0.45f),
                        shape,
                    )
                } else {
                    Modifier.border(1.6.dp, Color.White.copy(alpha = 0.24f), shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                text = "✓",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun CounterCard(state: DiscoverySourcesUiState, colors: AuroraColors) {
    val selected = state.entries.count { !it.excluded }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sectionSurface(colors, RoundedCornerShape(20.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                AYMR.strings.discovery_sources_counter,
                selected,
                state.entries.size,
            ),
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = selected.toString(),
            fontSize = 22.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.accent,
        )
    }
}

// ── тост ───────────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.ToastOverlay(
    state: DiscoverySourcesUiState,
    model: DiscoverySourcesScreenModel,
    colors: AuroraColors,
) {
    val toast = state.toast ?: return
    LaunchedEffect(toast) {
        delay(2600)
        model.clearToast()
    }
    val message = when (toast) {
        is DiscoverySourcesToast.Excluded ->
            stringResource(AYMR.strings.discovery_sources_toast_excluded, toast.name)
        DiscoverySourcesToast.LastGuard ->
            stringResource(AYMR.strings.discovery_sources_toast_last)
        is DiscoverySourcesToast.DeselectAllKeep ->
            stringResource(AYMR.strings.discovery_sources_toast_deselect_kept, toast.keptName)
        DiscoverySourcesToast.SelectAll ->
            stringResource(AYMR.strings.discovery_sources_toast_select_all)
    }
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .sectionSurface(colors, RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = if (colors.isDark) 0.45f else 0.10f))
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = message, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            if (toast is DiscoverySourcesToast.Excluded) {
                Text(
                    text = stringResource(AYMR.strings.for_you_undo),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.accent,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = model::undoLast,
                    ),
                )
            }
        }
    }
}

// ── иконки плагинов (расширений) ───────────────────────────────────────────────

@Composable
private fun PluginIcon(
    entry: SourcePickUi,
    mediaType: DiscoveryMediaType,
    model: DiscoverySourcesScreenModel,
    size: Int,
) {
    // Фиксированный клип-бокс: ни одна ветка иконки (bitmap произвольного размера,
    // AsyncImage, loading-Box) не может диктовать ряду свою высоту/ширину.
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        when (mediaType) {
            DiscoveryMediaType.ANIME ->
                model.animePlugin(entry.pluginKey)?.let {
                    AnimeExtensionIcon(extension = it, modifier = Modifier.matchParentSize())
                }
            DiscoveryMediaType.MANGA ->
                model.mangaPlugin(entry.pluginKey)?.let {
                    MangaExtensionIcon(extension = it, modifier = Modifier.matchParentSize())
                }
            DiscoveryMediaType.NOVEL -> {
                val plugin = model.novelPlugin(entry.pluginKey)
                if (plugin != null && shouldLoadNovelPluginIcon(plugin.iconUrl)) {
                    AsyncImage(
                        model = plugin.iconUrl,
                        contentDescription = null,
                        placeholder = ColorPainter(Color(0x1F888888)),
                        error = painterResource(R.mipmap.ic_default_source),
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Image(
                        painter = painterResource(R.mipmap.ic_default_source),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}
