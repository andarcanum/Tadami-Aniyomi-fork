package eu.kanade.tachiyomi.ui.reader.novel.dictionary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.entries.components.aurora.AuroraGlassCtaSurface
import eu.kanade.presentation.entries.components.aurora.AuroraHeroCtaMode
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraTopBarIconSurfaceColor
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryProviderOutcome
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryRequest
import eu.kanade.tachiyomi.ui.reader.novel.translation.OnlineDictionaryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Full-screen dictionary history: personal vocabulary collected while reading novels.
 *
 * Search, favorites, date grouping with sticky headers, swipe actions, per-entry detail
 * sheet with a fresh lookup through the configured offline/online dictionary pipeline,
 * and a lightweight flashcard review mode.
 *
 * Visual language: Aurora glass via `haze` (same stack as the Aurora navigation bar).
 * Content is a haze source; chips / cards / FAB / review panel apply hazeEffect blur + tint.
 */
class NovelDictionaryHistoryScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        NovelDictionaryHistoryScreenContent(onBack = { navigator.pop() })
    }
}

private enum class HistorySort { RECENT, FREQUENT, ALPHABETICAL, STALE }
private enum class HistoryFilter { ALL, TODAY, FREQUENT, FAVORITES }

private sealed interface DetailLookupState {
    data object Loading : DetailLookupState
    data class Loaded(val sections: List<DefinitionSection>, val attribution: String?) : DetailLookupState
    data object Failed : DetailLookupState
}

private data class DefinitionSection(
    val headword: String,
    val pronunciation: String?,
    val partOfSpeech: String?,
    val text: String,
)

private val HistoryCardShape = RoundedCornerShape(16.dp)
private val HistorySheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val HistoryPillShape = RoundedCornerShape(999.dp)
private val HistoryDialogShape = RoundedCornerShape(28.dp)
private val HistoryIconCircleSize = 40.dp

private fun dictionaryChipContentColor(colors: AuroraColors, selected: Boolean): Color {
    return if (selected) {
        when {
            colors.isEInk -> colors.textOnAccent
            colors.isDark -> colors.textPrimary
            else -> colors.accent
        }
    } else {
        colors.textSecondary
    }
}

private fun dictionaryBorderColor(colors: AuroraColors, emphasized: Boolean = false): Color {
    return when {
        colors.isEInk -> if (emphasized) colors.divider else colors.divider.copy(alpha = 0.7f)
        colors.isDark -> Color.White.copy(alpha = if (emphasized) 0.16f else 0.10f)
        else -> Color.Black.copy(alpha = if (emphasized) 0.10f else 0.06f)
    }
}

private fun dictionaryDialogScrim(colors: AuroraColors): Color {
    return if (colors.isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.35f)
}

/** Fallback solid-ish panel when haze is unavailable (e-ink). */
private fun dictionaryFallbackPanel(colors: AuroraColors): Color {
    return if (colors.isDark) {
        Color.White.copy(alpha = 0.10f).compositeOver(colors.background)
    } else {
        Color.White.copy(alpha = 0.92f)
    }
}

private fun dictionaryHazeStyle(
    colors: AuroraColors,
    tint: Color,
    blurRadius: Dp = 22.dp,
    noiseFactor: Float = 0.10f,
): HazeStyle {
    return HazeStyle(
        backgroundColor = colors.background,
        tint = HazeTint(tint),
        blurRadius = blurRadius,
        noiseFactor = noiseFactor,
    )
}

/**
 * Aurora glass via haze blur of [hazeState].
 *
 * @param outline solid uniform border (preferred for chips / cards / review).
 *                When null and [withRim] is true, falls back to gradient rim brush
 *                (nav-bar style). Top-bar icons pass neither.
 */
private fun Modifier.dictionaryGlass(
    hazeState: HazeState,
    colors: AuroraColors,
    shape: Shape,
    tint: Color = colors.surface.copy(alpha = if (colors.isDark) 0.55f else 0.62f),
    blurRadius: Dp = 22.dp,
    withRim: Boolean = false,
    outline: Color? = dictionaryBorderColor(colors),
): Modifier {
    if (colors.isEInk) {
        var eInk = this
            .clip(shape)
            .background(dictionaryFallbackPanel(colors), shape)
        val eInkBorder = outline ?: dictionaryBorderColor(colors)
        eInk = eInk.border(BorderStroke(1.dp, eInkBorder), shape)
        return eInk
    }
    var result = this
        .clip(shape)
        .hazeEffect(
            state = hazeState,
            style = dictionaryHazeStyle(colors, tint = tint, blurRadius = blurRadius),
        )
    when {
        outline != null -> {
            result = result.border(BorderStroke(1.dp, outline), shape)
        }
        withRim -> {
            result = result.border(
                BorderStroke(1.dp, auroraMenuRimLightBrush(colors)),
                shape,
            )
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NovelDictionaryHistoryScreenContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AuroraTheme.colors

    var revision by remember { mutableIntStateOf(0) }
    var allEntries by remember { mutableStateOf<List<NovelDictionaryHistoryEntry>>(emptyList()) }
    LaunchedEffect(revision) {
        allEntries = withContext(Dispatchers.IO) {
            runCatching { NovelDictionaryHistory.entries(context) }.getOrDefault(emptyList())
        }
    }

    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(HistorySort.RECENT) }
    var filterMode by remember { mutableStateOf(HistoryFilter.ALL) }
    var languageFilter by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var detailEntry by remember { mutableStateOf<NovelDictionaryHistoryEntry?>(null) }
    var showReview by remember { mutableStateOf(false) }

    val dictionaryProvider = remember {
        val prefs = Injekt.get<NovelReaderPreferences>()
        val networkHelper = Injekt.get<NetworkHelper>()
        val json = Injekt.get<Json>()
        CompositeNovelDictionaryProvider(
            modeProvider = { prefs.novelDictionarySource().get() },
            online = OnlineDictionaryProvider(
                networkHelper.client,
                json,
                prefs.novelDictionaryFallbackLanguage().get(),
            ),
            offline = OfflineStarDictDictionaryProvider(
                context = context.applicationContext,
            ),
        )
    }

    val ttsHolder = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        onDispose {
            ttsHolder[0]?.stop()
            ttsHolder[0]?.shutdown()
            ttsHolder[0] = null
        }
    }
    val pronounce: (String, String?) -> Unit = remember {
        { term, language ->
            val locale = language?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
            val existing = ttsHolder[0]
            if (existing != null) {
                existing.language = locale
                existing.speak(term, TextToSpeech.QUEUE_FLUSH, null, "dictionary_history")
            } else {
                ttsHolder[0] = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        ttsHolder[0]?.language = locale
                        ttsHolder[0]?.speak(term, TextToSpeech.QUEUE_FLUSH, null, "dictionary_history")
                    }
                }
            }
        }
    }

    val toggleFavorite: (NovelDictionaryHistoryEntry) -> Unit = { entry ->
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    NovelDictionaryHistory.setFavorite(context, entry.term, entry.language, !entry.isFavorite)
                }
            }
            revision += 1
        }
    }
    val deleteEntry: (NovelDictionaryHistoryEntry) -> Unit = { entry ->
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { NovelDictionaryHistory.delete(context, entry.term, entry.language) }
            }
            revision += 1
            if (detailEntry?.term == entry.term && detailEntry?.language == entry.language) {
                detailEntry = null
            }
            Toast.makeText(
                context,
                context.contextStringResource(AYMR.strings.novel_reader_dictionary_history_deleted, entry.term),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val copyTerm: (NovelDictionaryHistoryEntry) -> Unit = { entry ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = buildString {
            append(entry.term)
            entry.preview?.let { append(" \u2014 ").append(it) }
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(entry.term, copied))
        Toast.makeText(
            context,
            context.contextStringResource(AYMR.strings.novel_reader_dictionary_history_copied),
            Toast.LENGTH_SHORT,
        ).show()
    }

    val todayStart = remember(allEntries) { startOfDay(System.currentTimeMillis()) }
    val filteredEntries = remember(allEntries, searchQuery, sortMode, filterMode, languageFilter, todayStart) {
        var list = allEntries
        languageFilter?.let { lang ->
            list = list.filter { (it.language ?: "").equals(lang, ignoreCase = true) }
        }
        list = when (filterMode) {
            HistoryFilter.ALL -> list
            HistoryFilter.TODAY -> list.filter { it.lastLookupAt >= todayStart }
            HistoryFilter.FREQUENT -> list.filter { it.lookupCount >= 3 }
            HistoryFilter.FAVORITES -> list.filter { it.isFavorite }
        }
        val query = searchQuery.trim().lowercase()
        if (query.isNotEmpty()) {
            list = list.filter {
                it.term.lowercase().contains(query) ||
                    it.preview?.lowercase()?.contains(query) == true ||
                    it.novelTitle?.lowercase()?.contains(query) == true
            }
        }
        when (sortMode) {
            HistorySort.RECENT -> list.sortedByDescending { it.lastLookupAt }
            HistorySort.FREQUENT -> list.sortedWith(
                compareByDescending<NovelDictionaryHistoryEntry> { it.lookupCount }
                    .thenByDescending { it.lastLookupAt },
            )
            HistorySort.ALPHABETICAL -> list.sortedBy { it.term.lowercase() }
            HistorySort.STALE -> list.sortedBy { maxOf(it.lastLookupAt, it.lastReviewedAt ?: 0L) }
        }
    }
    val reviewQueue = remember(allEntries) { NovelDictionaryHistory.reviewQueue(allEntries) }
    val availableLanguages = remember(allEntries) {
        allEntries.mapNotNull { it.language?.trim()?.takeIf(String::isNotEmpty)?.lowercase() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    }
    val todayCount = remember(allEntries, todayStart) { allEntries.count { it.lastLookupAt >= todayStart } }
    val overlayOpen = showReview || detailEntry != null
    val hazeState = remember { HazeState() }

    AuroraBackground {
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = colors.textPrimary,
                        navigationIconContentColor = colors.textPrimary,
                        actionIconContentColor = colors.textPrimary,
                    ),
                    title = {
                        if (searchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dictionaryGlass(
                                        hazeState = hazeState,
                                        colors = colors,
                                        shape = HistoryPillShape,
                                        tint = colors.surface.copy(alpha = if (colors.isDark) 0.50f else 0.58f),
                                    ),
                                placeholder = {
                                    Text(
                                        text = stringResource(AYMR.strings.novel_reader_dictionary_history_search_hint),
                                        color = colors.textSecondary,
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = colors.accent,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                ),
                            )
                        } else {
                            Column {
                                Text(
                                    text = stringResource(AYMR.strings.novel_reader_dictionary_history),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                )
                                if (allEntries.isNotEmpty()) {
                                    Text(
                                        text = stringResource(
                                            AYMR.strings.novel_reader_dictionary_history_stats,
                                            allEntries.size.toString(),
                                            todayCount.toString(),
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        AuroraIconCircleButton(
                            hazeState = hazeState,
                            onClick = {
                                if (searchActive) {
                                    searchActive = false
                                    searchQuery = ""
                                } else {
                                    onBack()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                                tint = colors.textPrimary,
                            )
                        }
                    },
                    actions = {
                        // Explicit gaps so circular glass backgrounds never overlap.
                        Row(
                            modifier = Modifier.padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AuroraIconCircleButton(
                                hazeState = hazeState,
                                onClick = {
                                    if (searchActive) searchQuery = "" else searchActive = true
                                },
                            ) {
                                Icon(
                                    imageVector = if (searchActive) Icons.Outlined.Close else Icons.Outlined.Search,
                                    contentDescription = stringResource(
                                        AYMR.strings.novel_reader_dictionary_history_search_hint,
                                    ),
                                    tint = colors.textPrimary,
                                )
                            }
                            Box {
                                AuroraIconCircleButton(
                                    hazeState = hazeState,
                                    onClick = { menuExpanded = true },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                    )
                                }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                Text(
                                    text = stringResource(AYMR.strings.novel_reader_dictionary_history_sort),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                HistorySort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = sortLabel(sort),
                                                color = if (sortMode == sort) colors.accent else colors.textPrimary,
                                                fontWeight = if (sortMode == sort) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                        },
                                        onClick = {
                                            sortMode = sort
                                            menuExpanded = false
                                        },
                                    )
                                }
                                HorizontalDivider(color = colors.divider.copy(alpha = 0.6f))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(AYMR.strings.novel_reader_dictionary_history_clear),
                                            color = colors.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = colors.error,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showClearConfirm = true
                                    },
                                )
                            }
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (reviewQueue.isNotEmpty() && !searchActive && !overlayOpen) {
                    DictionaryReviewFab(
                        hazeState = hazeState,
                        label = stringResource(
                            AYMR.strings.novel_reader_dictionary_history_review_fab,
                            reviewQueue.size.toString(),
                        ),
                        onClick = { showReview = true },
                    )
                }
            },
        ) { paddingValues ->
            // Haze source: ambient aurora + list content feed blur for glass surfaces.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .hazeSource(hazeState),
            ) {
                if (allEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp, bottom = 12.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HistoryFilter.entries.forEach { filter ->
                            HistoryPillChip(
                                hazeState = hazeState,
                                label = filterLabel(filter),
                                selected = filterMode == filter,
                                onClick = {
                                    filterMode = if (filterMode == filter) HistoryFilter.ALL else filter
                                },
                            )
                        }
                        availableLanguages.forEach { lang ->
                            HistoryPillChip(
                                hazeState = hazeState,
                                label = lang.uppercase(),
                                selected = languageFilter == lang,
                                onClick = {
                                    languageFilter = if (languageFilter == lang) null else lang
                                },
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                if (filteredEntries.isEmpty()) {
                    HistoryEmptyState(hazeState = hazeState, modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 108.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (sortMode == HistorySort.RECENT) {
                            val groups = filteredEntries.groupBy { startOfDay(it.lastLookupAt) }
                            groups.forEach { (dayStart, groupEntries) ->
                                stickyHeader(key = "header_$dayStart") {
                                    HistoryDayHeader(
                                        hazeState = hazeState,
                                        label = dayLabel(dayStart, todayStart),
                                        count = groupEntries.size,
                                    )
                                }
                                items(
                                    count = groupEntries.size,
                                    key = { index ->
                                        val entry = groupEntries[index]
                                        "entry_${entry.term}_${entry.language.orEmpty()}"
                                    },
                                ) { index ->
                                    HistoryRow(
                                        hazeState = hazeState,
                                        entry = groupEntries[index],
                                        todayStart = todayStart,
                                        onClick = { detailEntry = groupEntries[index] },
                                        onToggleFavorite = { toggleFavorite(groupEntries[index]) },
                                        onDelete = { deleteEntry(groupEntries[index]) },
                                    )
                                }
                            }
                        } else {
                            items(
                                count = filteredEntries.size,
                                key = { index ->
                                    val entry = filteredEntries[index]
                                    "entry_${entry.term}_${entry.language.orEmpty()}"
                                },
                            ) { index ->
                                HistoryRow(
                                    hazeState = hazeState,
                                    entry = filteredEntries[index],
                                    todayStart = todayStart,
                                    onClick = { detailEntry = filteredEntries[index] },
                                    onToggleFavorite = { toggleFavorite(filteredEntries[index]) },
                                    onDelete = { deleteEntry(filteredEntries[index]) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showReview && reviewQueue.isNotEmpty()) {
            FlashcardReviewOverlay(
                hazeState = hazeState,
                queue = reviewQueue,
                onAnswer = { entry, known ->
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            NovelDictionaryHistory.recordReview(context, entry.term, entry.language, known)
                        }
                    }
                },
                onClose = {
                    showReview = false
                    revision += 1
                },
            )
        }
        } // outer Box
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = dictionaryFallbackPanel(colors),
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text(text = stringResource(AYMR.strings.novel_reader_dictionary_history_clear_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        AYMR.strings.novel_reader_dictionary_history_clear_confirm_message,
                        allEntries.size.toString(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) { runCatching { NovelDictionaryHistory.clear(context) } }
                            revision += 1
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
                ) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    detailEntry?.let { entry ->
        HistoryDetailSheet(
            entry = entry,
            provider = dictionaryProvider,
            onDismiss = { detailEntry = null },
            onToggleFavorite = {
                toggleFavorite(entry)
                detailEntry = entry.copy(isFavorite = !entry.isFavorite)
            },
            onDelete = { deleteEntry(entry) },
            onCopy = { copyTerm(entry) },
            onPronounce = { pronounce(entry.term, entry.language) },
        )
    }

}

@Composable
private fun AuroraIconCircleButton(
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = AuroraTheme.colors
    // Same language as Settings / Browse / Library Aurora top bar:
    // soft translucent circle, no rim brush, no haze.
    Box(
        modifier = modifier
            .size(HistoryIconCircleSize)
            .clip(CircleShape)
            .background(resolveAuroraTopBarIconSurfaceColor(colors), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DictionaryReviewFab(
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState,
    label: String,
    onClick: () -> Unit,
) {
    // Same glass CTA language as Aurora title hero action buttons.
    AuroraGlassCtaSurface(
        mode = AuroraHeroCtaMode.Aurora,
        onClick = onClick,
        shape = HistoryPillShape,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) { contentColor ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HistoryPillChip(
    hazeState: HazeState,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val tint = if (selected) {
        colors.accent.copy(alpha = if (colors.isDark) 0.28f else 0.18f)
    } else {
        colors.surface.copy(alpha = if (colors.isDark) 0.40f else 0.48f)
    }
    val outline = if (selected) {
        colors.accent.copy(alpha = if (colors.isDark) 0.55f else 0.40f)
    } else {
        dictionaryBorderColor(colors, emphasized = true)
    }
    Box(
        modifier = Modifier
            .dictionaryGlass(
                hazeState = hazeState,
                colors = colors,
                shape = HistoryPillShape,
                tint = tint,
                blurRadius = 18.dp,
                outline = outline,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = dictionaryChipContentColor(colors, selected),
            maxLines = 1,
        )
    }
}

@Composable
private fun HistoryDayHeader(
    hazeState: HazeState,
    label: String,
    count: Int,
) {
    val colors = AuroraTheme.colors
    // No full-width black bar — only a compact glass count pill next to the label.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent,
        )
        Box(
            modifier = Modifier
                .dictionaryGlass(
                    hazeState = hazeState,
                    colors = colors,
                    shape = HistoryPillShape,
                    tint = colors.surface.copy(alpha = if (colors.isDark) 0.36f else 0.48f),
                    blurRadius = 16.dp,
                    outline = dictionaryBorderColor(colors, emphasized = true),
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HistoryEmptyState(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .dictionaryGlass(
                    hazeState = hazeState,
                    colors = colors,
                    shape = CircleShape,
                    tint = colors.surface.copy(alpha = if (colors.isDark) 0.40f else 0.50f),
                    outline = dictionaryBorderColor(colors, emphasized = true),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = colors.accent,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(AYMR.strings.novel_reader_dictionary_history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(AYMR.strings.novel_reader_dictionary_history_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HistoryRow(
    hazeState: HazeState,
    entry: NovelDictionaryHistoryEntry,
    todayStart: Long,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val mastery = remember(entry) { NovelDictionaryHistory.masteryOf(entry).coerceIn(0f, 1f) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleFavorite()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                else -> false
            }
        },
    )
    // Keep haze translucent so ambient aurora shows through; swipe bg must stay hidden at rest
    // or the favorite star bleeds through the glass (double-star bug).
    val cardTint = colors.surface.copy(alpha = if (colors.isDark) 0.28f else 0.38f)
    val cardOutline = if (entry.isFavorite) {
        colors.accent.copy(alpha = if (colors.isDark) 0.40f else 0.30f)
    } else {
        dictionaryBorderColor(colors, emphasized = true)
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier
            .fillMaxWidth()
            .clip(HistoryCardShape),
        backgroundContent = {
            // Only paint while the user is actively dragging — never under a settled glass card.
            val offsetPx = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
            if (kotlin.math.abs(offsetPx) < 0.5f) {
                return@SwipeToDismissBox
            }
            val isDelete = offsetPx < 0f
            val bg = if (isDelete) {
                colors.error.copy(alpha = 0.35f).compositeOver(colors.background)
            } else {
                colors.accent.copy(alpha = 0.35f).compositeOver(colors.background)
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(horizontal = 22.dp),
                horizontalArrangement = if (isDelete) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isDelete) Icons.Outlined.Delete else Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (isDelete) colors.error else colors.ratingStar,
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .dictionaryGlass(
                    hazeState = hazeState,
                    colors = colors,
                    shape = HistoryCardShape,
                    tint = cardTint,
                    blurRadius = 24.dp,
                    outline = cardOutline,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = entry.term,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        languageBadge(entry)?.let { badge ->
                            // Outline-only chip — no accent fill under text.
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            colors.accent.copy(alpha = if (colors.isDark) 0.45f else 0.35f),
                                        ),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.accent,
                                )
                            }
                        }
                    }
                    entry.preview?.let { preview ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "\u00d7${entry.lookupCount}  \u00b7  ${shortTime(entry.lastLookupAt, todayStart)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary.copy(alpha = 0.85f),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(34.dp),
                ) {
                    CircularProgressIndicator(
                        progress = { mastery.coerceAtLeast(0.04f) },
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = colors.accent,
                        trackColor = Color.White.copy(alpha = if (colors.isDark) 0.14f else 0.16f),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleFavorite,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (entry.isFavorite) {
                            colors.ratingStar
                        } else {
                            colors.textSecondary.copy(alpha = 0.65f)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetailSheet(
    entry: NovelDictionaryHistoryEntry,
    provider: CompositeNovelDictionaryProvider,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onPronounce: () -> Unit,
) {
    val colors = AuroraTheme.colors
    var lookupState by remember(entry.term, entry.language) {
        mutableStateOf<DetailLookupState>(DetailLookupState.Loading)
    }
    LaunchedEffect(entry.term, entry.language) {
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                provider.lookup(
                    NovelDictionaryRequest(
                        term = entry.term,
                        sourceLanguageHint = entry.language,
                        targetLanguageCode = entry.targetLanguage ?: "en",
                    ),
                )
            }.getOrNull()
        }
        lookupState = when (outcome) {
            is NovelDictionaryProviderOutcome.Success -> {
                val sections = outcome.result.entries.map { dictEntry ->
                    DefinitionSection(
                        headword = dictEntry.headword,
                        pronunciation = dictEntry.pronunciation,
                        partOfSpeech = dictEntry.partOfSpeech,
                        text = runCatching { Jsoup.parse(dictEntry.definitionsHtml).text() }
                            .getOrDefault("")
                            .trim(),
                    )
                }.filter { it.text.isNotEmpty() }
                if (sections.isEmpty()) {
                    DetailLookupState.Failed
                } else {
                    DetailLookupState.Loaded(sections, outcome.result.attribution)
                }
            }
            else -> DetailLookupState.Failed
        }
    }

    val sheetColor = dictionaryFallbackPanel(colors)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetColor,
        shape = HistorySheetShape,
        scrimColor = dictionaryDialogScrim(colors),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(HistoryPillShape)
                    .background(Color.White.copy(alpha = if (colors.isDark) 0.22f else 0.28f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.term,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    languageBadge(entry)?.let { badge ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accent,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(dictionaryFallbackPanel(colors)),
                ) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        tint = if (entry.isFavorite) colors.ratingStar else colors.textSecondary,
                    )
                }
            }

            val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    AYMR.strings.novel_reader_dictionary_history_meta,
                    entry.lookupCount.toString(),
                    dateFormat.format(entry.firstLookupAt),
                    dateFormat.format(entry.lastLookupAt),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = lookupState) {
                is DetailLookupState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = dictionaryFallbackPanel(colors),
                        border = BorderStroke(1.dp, dictionaryBorderColor(colors)),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_dictionary_history_loading),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(HistoryPillShape),
                                color = colors.accent,
                                trackColor = dictionaryBorderColor(colors),
                            )
                            entry.preview?.let { preview ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }
                is DetailLookupState.Loaded -> {
                    state.sections.forEach { section ->
                        DefinitionCard(section = section)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    state.attribution?.let { attribution ->
                        Text(
                            text = attribution,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                is DetailLookupState.Failed -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = dictionaryFallbackPanel(colors),
                        border = BorderStroke(1.dp, dictionaryBorderColor(colors)),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            entry.preview?.let { preview ->
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_dictionary_history_refresh_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }

            if (entry.novelTitle != null || entry.quote != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = dictionaryFallbackPanel(colors),
                    border = BorderStroke(1.dp, dictionaryBorderColor(colors)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_dictionary_history_context),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                        )
                        entry.novelTitle?.let { title ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = listOfNotNull(title, entry.chapterName).joinToString(" \u00b7 "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                            )
                        }
                        entry.quote?.let { quote ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "\u201c$quote\u201d",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }

            entry.provider?.let { providerName ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = providerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary.copy(alpha = 0.85f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailActionButton(
                    label = stringResource(AYMR.strings.novel_reader_dictionary_history_action_pronounce),
                    onClick = onPronounce,
                    modifier = Modifier.weight(1f),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.accent,
                        )
                    },
                )
                DetailActionButton(
                    label = stringResource(AYMR.strings.novel_reader_dictionary_history_action_copy),
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            DetailActionButton(
                label = stringResource(AYMR.strings.novel_reader_dictionary_history_action_delete),
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                destructive = true,
            )
        }
    }
}

@Composable
private fun DefinitionCard(section: DefinitionSection) {
    val colors = AuroraTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = dictionaryFallbackPanel(colors),
        border = BorderStroke(1.dp, dictionaryBorderColor(colors)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = section.headword,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                section.pronunciation?.let { pron ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pron,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent,
                    )
                }
                section.partOfSpeech?.let { pos ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pos,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = section.text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun DetailActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val container = if (destructive) {
        colors.error.copy(alpha = 0.16f).compositeOver(dictionaryFallbackPanel(colors))
    } else {
        dictionaryFallbackPanel(colors)
    }
    val content = if (destructive) colors.error else colors.textPrimary
    val border = if (destructive) {
        colors.error.copy(alpha = 0.32f)
    } else {
        dictionaryBorderColor(colors)
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * In-composition review overlay (not a separate Dialog window) so haze can blur the list behind.
 */
@Composable
private fun FlashcardReviewOverlay(
    hazeState: HazeState,
    queue: List<NovelDictionaryHistoryEntry>,
    onAnswer: (NovelDictionaryHistoryEntry, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val colors = AuroraTheme.colors
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var known by remember { mutableIntStateOf(0) }
    val innerCardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(8f)
            .background(dictionaryDialogScrim(colors))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = HistoryDialogShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.25f),
                )
                .dictionaryGlass(
                    hazeState = hazeState,
                    colors = colors,
                    shape = HistoryDialogShape,
                    tint = colors.surface.copy(alpha = if (colors.isDark) 0.48f else 0.58f),
                    blurRadius = 28.dp,
                    outline = dictionaryBorderColor(colors, emphasized = true),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (index >= queue.size) {
                Text(
                    text = stringResource(
                        AYMR.strings.novel_reader_dictionary_history_review_done,
                        known.toString(),
                        queue.size.toString(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.textOnAccent,
                    ),
                    shape = HistoryPillShape,
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            } else {
                val entry = queue[index]
                Text(
                    text = stringResource(
                        AYMR.strings.novel_reader_dictionary_history_review_progress,
                        (index + 1).toString(),
                        queue.size.toString(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = {
                        (index.toFloat() / queue.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(HistoryPillShape),
                    color = colors.accent,
                    trackColor = dictionaryBorderColor(colors),
                )
                Spacer(modifier = Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp)
                        .dictionaryGlass(
                            hazeState = hazeState,
                            colors = colors,
                            shape = innerCardShape,
                            tint = colors.surface.copy(alpha = if (colors.isDark) 0.30f else 0.42f),
                            blurRadius = 18.dp,
                            outline = dictionaryBorderColor(colors, emphasized = true),
                        )
                        .clickable { flipped = !flipped }
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (!flipped) {
                            Text(
                                text = entry.term,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center,
                            )
                            languageBadge(entry)?.let { badge ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.accent,
                                )
                            }
                            Spacer(modifier = Modifier.height(22.dp))
                            Text(
                                text = stringResource(
                                    AYMR.strings.novel_reader_dictionary_history_review_flip,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = colors.textSecondary,
                            )
                        } else {
                            Text(
                                text = entry.term,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = entry.preview.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center,
                            )
                            entry.novelTitle?.let { title ->
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = listOfNotNull(title, entry.chapterName)
                                        .joinToString(" \u00b7 "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onAnswer(entry, false)
                            flipped = false
                            index += 1
                        },
                        modifier = Modifier.weight(1f),
                        shape = HistoryPillShape,
                        border = BorderStroke(1.dp, dictionaryBorderColor(colors, emphasized = true)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary,
                            containerColor = Color.Transparent,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                AYMR.strings.novel_reader_dictionary_history_review_again,
                            ),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Button(
                        onClick = {
                            onAnswer(entry, true)
                            known += 1
                            flipped = false
                            index += 1
                        },
                        modifier = Modifier.weight(1f),
                        shape = HistoryPillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.textOnAccent,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                AYMR.strings.novel_reader_dictionary_history_review_know,
                            ),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun languageBadge(entry: NovelDictionaryHistoryEntry): String? {
    val source = entry.language?.trim()?.takeIf(String::isNotEmpty)?.uppercase()
    val target = entry.targetLanguage?.trim()?.takeIf(String::isNotEmpty)?.uppercase()
    return when {
        source != null && target != null -> "$source\u2192$target"
        source != null -> source
        target != null -> "\u2192$target"
        else -> null
    }
}

private fun startOfDay(time: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = time
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

@Composable
private fun dayLabel(dayStart: Long, todayStart: Long): String {
    return when {
        dayStart >= todayStart -> stringResource(AYMR.strings.novel_reader_dictionary_history_group_today)
        dayStart >= todayStart - 24L * 60L * 60L * 1000L ->
            stringResource(AYMR.strings.novel_reader_dictionary_history_group_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(dayStart)
    }
}

private fun shortTime(time: Long, todayStart: Long): String {
    return if (time >= todayStart) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(time)
    } else {
        DateFormat.getDateInstance(DateFormat.SHORT).format(time)
    }
}

@Composable
private fun sortLabel(sort: HistorySort): String = when (sort) {
    HistorySort.RECENT -> stringResource(AYMR.strings.novel_reader_dictionary_history_sort_recent)
    HistorySort.FREQUENT -> stringResource(AYMR.strings.novel_reader_dictionary_history_sort_frequency)
    HistorySort.ALPHABETICAL -> stringResource(AYMR.strings.novel_reader_dictionary_history_sort_alphabetical)
    HistorySort.STALE -> stringResource(AYMR.strings.novel_reader_dictionary_history_sort_stale)
}

@Composable
private fun filterLabel(filter: HistoryFilter): String = when (filter) {
    HistoryFilter.ALL -> stringResource(AYMR.strings.novel_reader_dictionary_history_filter_all)
    HistoryFilter.TODAY -> stringResource(AYMR.strings.novel_reader_dictionary_history_filter_today)
    HistoryFilter.FREQUENT -> stringResource(AYMR.strings.novel_reader_dictionary_history_filter_frequent)
    HistoryFilter.FAVORITES -> stringResource(AYMR.strings.novel_reader_dictionary_history_filter_favorites)
}
