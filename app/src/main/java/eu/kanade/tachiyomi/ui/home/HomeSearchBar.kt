package eu.kanade.tachiyomi.ui.home

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import com.tadami.aurora.R
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.applyAuroraSheetWindowFx
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.resolveAuroraTabContainerColor
import eu.kanade.presentation.more.settings.auroraCardStyle
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import java.util.TreeMap
import kotlin.math.roundToInt

data class HomeSourceItem(
    val id: Long,
    val name: String,
    val lang: String = "",
    val isLocal: Boolean = false,
    val iconBitmap: ImageBitmap? = null,
    val iconUrl: String? = null,
)

private const val LONG_PRESS_TIMEOUT_MS = 450L

// Short taps never show the long-press ring: wait this long before starting it.
private const val RING_GRACE_MS = 150L

@Composable
internal fun HomeSearchBarWithSourceChip(
    section: HomeHubSection,
    sourceId: Long,
    sourceName: String?,
    availableSources: List<HomeSourceItem>,
    onSearchClick: (String) -> Unit,
    onOpenCatalog: (Long) -> Unit,
    onSelectSource: (Long, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val searchBarShape = CircleShape
    val isLightTheme = !colors.isDark && !colors.isEInk
    val tabContainerColor = resolveAuroraTabContainerColor(colors)

    var showSourcePickerSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val hapticFeedback = LocalHapticFeedback.current

    val quickSourceBorderBrush: Brush = remember(colors) {
        if (colors.isDark && !colors.isEInk) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
            )
        } else {
            auroraMenuRimLightBrush(colors)
        }
    }
    val sourceBorderBrush: Brush = remember(colors) { auroraMenuRimLightBrush(colors) }
    val sourceShowBorder = colors.isDark || colors.isEInk

    val sectionName = when (section) {
        HomeHubSection.Anime -> stringResource(AYMR.strings.home_search_section_anime)
        HomeHubSection.Manga -> stringResource(AYMR.strings.home_search_section_manga)
        HomeHubSection.Novel -> stringResource(AYMR.strings.home_search_section_novel)
    }

    val searchPlaceholder = when {
        sourceId == -1L -> stringResource(AYMR.strings.home_search_placeholder_global)
        else -> stringResource(AYMR.strings.home_search_placeholder_default, sectionName)
    }

    val commitSearch: () -> Unit = {
        val trimmed = query.trim()
        focusManager.clearFocus()
        editing = false
        if (trimmed.isNotEmpty()) {
            appHaptics.tap()
            onSearchClick(trimmed)
        }
    }

    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    Card(
        modifier = modifier
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .padding(horizontal = 16.dp, vertical = if (isLightTheme) 10.dp else 8.dp)
            .then(
                if (isLightTheme) {
                    Modifier
                        .drawBehind {
                            val radius = size.height / 2f
                            val cornerRadius = CornerRadius(radius, radius)
                            val neutralOffsetY = 2.dp.toPx()
                            val neutralInset = 1.dp.toPx()
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.035f),
                                topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                                size = Size(width = size.width - neutralInset * 2, height = size.height),
                                cornerRadius = cornerRadius,
                            )
                        }
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.78f),
                                    Color.White.copy(alpha = 0.68f),
                                    Color.White.copy(alpha = 0.60f),
                                ),
                            ),
                            shape = searchBarShape,
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.75f),
                                    Color.White.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0.12f),
                                ),
                            ),
                            shape = searchBarShape,
                        )
                } else if (colors.isDark && !colors.isEInk) {
                    Modifier
                        .auroraCardStyle(
                            colors = colors,
                            shape = searchBarShape,
                            applyDarkRimLight = false,
                            applyDarkShadow = false,
                        )
                        .border(
                            BorderStroke(1.dp, quickSourceBorderBrush),
                            shape = searchBarShape,
                        )
                } else {
                    Modifier
                },
            ),
        shape = searchBarShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isLightTheme) Color.Transparent else tabContainerColor,
        ),
        border = if (sourceShowBorder && !colors.isDark) {
            BorderStroke(0.75.dp, sourceBorderBrush)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (!editing) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable {
                            appHaptics.tap()
                            editing = true
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = searchPlaceholder,
                        color = colors.textSecondary.copy(alpha = 0.85f),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.5.sp,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(onSearch = { commitSearch() }),
                        singleLine = true,
                        cursorBrush = SolidColor(colors.accent),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(AYMR.strings.home_search_placeholder_search),
                                        color = colors.textSecondary.copy(alpha = 0.7f),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            editing = false
                            query = ""
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(AYMR.strings.home_search_cancel),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // While typing a query the chip hides so the search field takes the full width
            if (!editing || query.isBlank()) {
                Spacer(Modifier.width(6.dp))

                HomeSourceChip(
                    sourceId = sourceId,
                    sourceName = sourceName,
                    onShortClick = {
                        appHaptics.tap()
                        showSourcePickerSheet = true
                    },
                    onLongPress = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (sourceId != -1L) {
                            onOpenCatalog(sourceId)
                        } else {
                            showSourcePickerSheet = true
                        }
                    },
                )
            }
        }
    }

    if (showSourcePickerSheet) {
        HomeSourcePickerSheet(
            currentSourceId = sourceId,
            sources = availableSources,
            onDismiss = { showSourcePickerSheet = false },
            onSelectSource = { selectedId, selectedName ->
                onSelectSource(selectedId, selectedName)
                showSourcePickerSheet = false
            },
            onOpenCatalog = { catalogSourceId ->
                showSourcePickerSheet = false
                onOpenCatalog(catalogSourceId)
            },
        )
    }
}

@Composable
private fun HomeSourceChip(
    sourceId: Long,
    sourceName: String?,
    onShortClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val scope = rememberCoroutineScope()
    val isGlobalSearch = sourceId == -1L
    val isNullState = sourceName.isNullOrBlank() && !isGlobalSearch
    val chipShape = CircleShape

    val progressAnim = remember { Animatable(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var isLongPressTriggered by remember { mutableStateOf(false) }

    val accentColor = colors.accent

    // Gold text is unreadable on the light search bar surface, so use the primary text color there
    val chipContentColor = when {
        isNullState -> colors.textSecondary
        colors.isDark -> colors.accent
        else -> colors.textPrimary
    }

    val chipIcon = if (isGlobalSearch) Icons.Outlined.Public else null
    val chipText = when {
        isGlobalSearch -> stringResource(AYMR.strings.home_all_sources)
        !sourceName.isNullOrBlank() -> sourceName
        else -> stringResource(AYMR.strings.home_select_source)
    }

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(chipShape)
            .pointerInput(sourceId, sourceName) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isLongPressTriggered = false
                    holdJob?.cancel()

                    holdJob = scope.launch {
                        // Grace period: quick taps never draw the ring, so no stray arc appears
                        delay(RING_GRACE_MS)
                        launch {
                            progressAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = (LONG_PRESS_TIMEOUT_MS - RING_GRACE_MS).toInt(),
                                    easing = LinearEasing,
                                ),
                            )
                        }
                        delay(LONG_PRESS_TIMEOUT_MS - RING_GRACE_MS)
                        isLongPressTriggered = true
                        onLongPress()
                    }

                    val upOrCancel = waitForUpOrCancellation()
                    holdJob?.cancel()
                    scope.launch { progressAnim.snapTo(0f) }

                    if (upOrCancel != null && !isLongPressTriggered) {
                        onShortClick()
                    }
                }
            }
            .drawBehind {
                // Long-press progress ring (only after the grace period)
                val progress = progressAnim.value
                if (progress > 0f) {
                    val strokeWidthPx = 2.dp.toPx()
                    val sweepAngle = progress * 360f
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
                        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    )
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (chipIcon != null) {
                Icon(
                    imageVector = chipIcon,
                    contentDescription = null,
                    tint = chipContentColor,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = chipText,
                color = chipContentColor,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = chipContentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun HomeSourcePickerSheet(
    currentSourceId: Long,
    sources: List<HomeSourceItem>,
    onDismiss: () -> Unit,
    onSelectSource: (Long, String?) -> Unit,
    onOpenCatalog: (Long) -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val context = LocalContext.current
    val supportsBlurBehind = eu.kanade.presentation.util.rememberSupportsBlurBehind(colors.isEInk)
    var sheetReveal by remember { mutableFloatStateOf(1f) }
    var sourceQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filteredSources = remember(sourceQuery, sources) {
        val q = sourceQuery.trim().lowercase()
        if (q.isEmpty()) {
            sources
        } else {
            sources.filter {
                it.name.lowercase().contains(q) || it.lang.lowercase().contains(q)
            }
        }
    }

    AdaptiveSheet(
        onDismissRequest = onDismiss,
        containerColor = when {
            colors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
            !supportsBlurBehind -> colors.surface
            colors.isDark -> Color.Black.copy(alpha = 0.72f)
            else -> Color.White.copy(alpha = 0.90f)
        },
        scrimAlpha = if (supportsBlurBehind) 0f else 0.4f,
        onRevealChange = { sheetReveal = it },
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val revealState = rememberUpdatedState(sheetReveal)

        DisposableEffect(window, supportsBlurBehind) {
            val w = window
            if (w != null && supportsBlurBehind) {
                w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                w.setDimAmount(0f)
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = 0 }
            }
            onDispose {
                if (w != null && supportsBlurBehind) {
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                    w.setDimAmount(0f)
                    w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }

        LaunchedEffect(window, supportsBlurBehind) {
            val w = window ?: return@LaunchedEffect
            if (!supportsBlurBehind) return@LaunchedEffect
            snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                .distinctUntilChanged()
                .collect { step -> applyAuroraSheetWindowFx(w, step / 20f) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.textSecondary.copy(alpha = 0.25f)),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AYMR.strings.home_source_picker_title),
                    color = colors.textPrimary,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${filteredSources.size + if (sourceQuery.isBlank()) 1 else 0}",
                        color = colors.textSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(AYMR.strings.home_source_picker_subtitle),
                color = colors.textSecondary,
                fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (colors.isDark) {
                            Color.White.copy(alpha = 0.09f)
                        } else {
                            colors.surface.copy(alpha = 0.85f)
                        },
                    )
                    .border(
                        width = 0.75.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (colors.isDark) 0.18f else 0.45f),
                                Color.White.copy(alpha = if (colors.isDark) 0.04f else 0.10f),
                            ),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = sourceQuery,
                    onValueChange = { sourceQuery = it },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.accent),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (sourceQuery.isEmpty()) {
                                Text(
                                    text = stringResource(AYMR.strings.home_source_picker_search),
                                    color = colors.textSecondary.copy(alpha = 0.75f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))

            val hasResults = sourceQuery.isBlank() || filteredSources.isNotEmpty()
            if (!hasResults) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.home_source_picker_no_results),
                        color = colors.textSecondary.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                }
            } else {
                // Group sources by language like the Browse tab: stable order, "" (system) last
                val groupedByLang = remember(filteredSources) {
                    val map = TreeMap<String, MutableList<HomeSourceItem>> { d1, d2 ->
                        when {
                            d1 == "" && d2 != "" -> 1
                            d2 == "" && d1 != "" -> -1
                            else -> d1.compareTo(d2)
                        }
                    }
                    filteredSources.forEach { s -> map.getOrPut(s.lang) { mutableListOf() }.add(s) }
                    map
                }

                FastScrollLazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    if (sourceQuery.isBlank()) {
                        item(key = "source_all") {
                            val allSourceItem = remember {
                                HomeSourceItem(
                                    id = -1L,
                                    name = "",
                                    lang = "GLOBAL",
                                )
                            }
                            HomeSourceItemRow(
                                item = allSourceItem,
                                title = stringResource(AYMR.strings.home_all_sources),
                                subtitle = stringResource(AYMR.strings.home_all_sources_summary),
                                langBadge = "GLOBAL",
                                isGlobal = true,
                                isSelected = currentSourceId == -1L,
                                showCatalogButton = false,
                                onClick = {
                                    appHaptics.tap()
                                    onSelectSource(-1L, null)
                                },
                                onOpenCatalog = {},
                            )
                        }
                    }

                    groupedByLang.forEach { (lang, langSources) ->
                        item(key = "header_$lang") {
                            HomeSourceLangHeader(
                                lang = lang,
                                count = langSources.size,
                            )
                        }
                        items(langSources, key = { "source_${it.id}" }) { source ->
                            val langDisplayName = remember(source.lang) {
                                LocaleHelper.getSourceDisplayName(source.lang, context)
                                    .takeIf { it.isNotBlank() } ?: source.lang.uppercase()
                            }
                            HomeSourceItemRow(
                                item = source,
                                title = source.name,
                                subtitle = langDisplayName,
                                langBadge = source.lang.uppercase().takeIf { it.isNotBlank() },
                                isGlobal = false,
                                isSelected = currentSourceId == source.id,
                                showCatalogButton = true,
                                onClick = {
                                    appHaptics.tap()
                                    onSelectSource(source.id, source.name)
                                },
                                onOpenCatalog = {
                                    appHaptics.tap()
                                    onOpenCatalog(source.id)
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HomeSourceIcon(
    item: HomeSourceItem,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val iconShape = RoundedCornerShape(8.dp)
    val defaultModifier = modifier
        .size(30.dp)
        .clip(iconShape)

    when {
        item.id == -1L -> {
            Box(
                modifier = defaultModifier.background(
                    Brush.linearGradient(
                        listOf(colors.accent, colors.accent.copy(alpha = 0.8f)),
                    ),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        item.isLocal -> {
            Image(
                painter = painterResource(R.mipmap.ic_local_source),
                contentDescription = null,
                modifier = defaultModifier,
            )
        }
        item.iconBitmap != null -> {
            Image(
                bitmap = item.iconBitmap,
                contentDescription = null,
                modifier = defaultModifier,
            )
        }
        !item.iconUrl.isNullOrBlank() -> {
            AsyncImage(
                model = item.iconUrl,
                contentDescription = null,
                placeholder = ColorPainter(Color(0x1F888888)),
                error = painterResource(R.mipmap.ic_default_source),
                modifier = defaultModifier,
            )
        }
        else -> {
            Image(
                painter = painterResource(R.mipmap.ic_default_source),
                contentDescription = null,
                modifier = defaultModifier,
            )
        }
    }
}

@Composable
private fun HomeSourceLangHeader(
    lang: String,
    count: Int,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 1.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = LocaleHelper.getLocalizedDisplayName(lang).ifBlank { lang.uppercase() },
            color = colors.textPrimary.copy(alpha = 0.85f),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surface.copy(alpha = 0.7f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = "$count",
                color = colors.textSecondary,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HomeSourceItemRow(
    item: HomeSourceItem,
    title: String,
    subtitle: String,
    langBadge: String?,
    isGlobal: Boolean,
    isSelected: Boolean,
    showCatalogButton: Boolean,
    onClick: () -> Unit,
    onOpenCatalog: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val rowShape = RoundedCornerShape(12.dp)
    val rowBgColor = if (isSelected) {
        colors.accent.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeSourceIcon(item = item)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) colors.textPrimary else colors.textPrimary.copy(alpha = 0.92f),
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!langBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isGlobal) {
                                        colors.accent.copy(alpha = 0.15f)
                                    } else {
                                        colors.surface.copy(alpha = 0.8f)
                                    },
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = langBadge,
                                color = if (isGlobal) colors.accent else colors.textSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        color = colors.textSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (showCatalogButton) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surface.copy(alpha = 0.75f))
                    .clickable(onClick = onOpenCatalog)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(AYMR.strings.home_catalog_button),
                    color = colors.textSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(AYMR.strings.home_open_catalog),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(11.dp),
                )
            }
        }

        if (isSelected) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
