package eu.kanade.tachiyomi.ui.reader.novel.dictionary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
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
 * and a lightweight flashcard review mode. Uses only MaterialTheme colors, so AMOLED,
 * Aurora and e-ink themes are respected automatically.
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NovelDictionaryHistoryScreenContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // Text-to-speech for pronouncing headwords; created lazily, released on dispose.
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(text = stringResource(AYMR.strings.novel_reader_dictionary_history_search_hint))
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text(text = stringResource(AYMR.strings.novel_reader_dictionary_history))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (searchActive) {
                                searchActive = false
                                searchQuery = ""
                            } else {
                                onBack()
                            }
                        },
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (searchActive) searchQuery = "" else searchActive = true
                        },
                    ) {
                        Icon(
                            imageVector = if (searchActive) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = stringResource(AYMR.strings.novel_reader_dictionary_history_search_hint),
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_dictionary_history_sort),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        HistorySort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = sortLabel(sort),
                                        fontWeight = if (sortMode == sort) FontWeight.Bold else null,
                                    )
                                },
                                onClick = {
                                    sortMode = sort
                                    menuExpanded = false
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(text = stringResource(AYMR.strings.novel_reader_dictionary_history_clear)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                showClearConfirm = true
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (reviewQueue.isNotEmpty() && !searchActive) {
                ExtendedFloatingActionButton(
                    onClick = { showReview = true },
                    icon = {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                    },
                    text = {
                        Text(
                            text = stringResource(
                                AYMR.strings.novel_reader_dictionary_history_review_fab,
                                reviewQueue.size.toString(),
                            ),
                        )
                    },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (allEntries.isNotEmpty()) {
                Text(
                    text = buildString {
                        append(
                            context.contextStringResource(
                                AYMR.strings.novel_reader_dictionary_history_stats,
                                allEntries.size.toString(),
                                todayCount.toString(),
                            ),
                        )
                        availableLanguages.firstOrNull()?.let { top ->
                            append(" \u2022 ")
                            append(
                                context.contextStringResource(
                                    AYMR.strings.novel_reader_dictionary_history_top_language,
                                    top.uppercase(),
                                ),
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = filterMode == filter,
                            onClick = {
                                filterMode = if (filterMode == filter) HistoryFilter.ALL else filter
                            },
                            label = { Text(text = filterLabel(filter)) },
                        )
                    }
                    availableLanguages.forEach { lang ->
                        FilterChip(
                            selected = languageFilter == lang,
                            onClick = {
                                languageFilter = if (languageFilter == lang) null else lang
                            },
                            label = { Text(text = lang.uppercase()) },
                        )
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_dictionary_history_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_dictionary_history_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    if (sortMode == HistorySort.RECENT) {
                        val groups = filteredEntries.groupBy { startOfDay(it.lastLookupAt) }
                        groups.forEach { (dayStart, groupEntries) ->
                            stickyHeader(key = "header_$dayStart") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surface,
                                ) {
                                    Text(
                                        text = dayLabel(dayStart, todayStart),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            items(
                                count = groupEntries.size,
                                key = { index ->
                                    val entry = groupEntries[index]
                                    "entry_${entry.term}_${entry.language.orEmpty()}"
                                },
                            ) { index ->
                                HistoryRow(
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

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
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
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
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

    if (showReview && reviewQueue.isNotEmpty()) {
        FlashcardReviewDialog(
            queue = reviewQueue,
            onAnswer = { entry, known ->
                scope.launch(Dispatchers.IO) {
                    runCatching { NovelDictionaryHistory.recordReview(context, entry.term, entry.language, known) }
                }
            },
            onClose = {
                showReview = false
                revision += 1
            },
        )
    }
}

@Composable
private fun HistoryRow(
    entry: NovelDictionaryHistoryEntry,
    todayStart: Long,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
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
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isDelete = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDelete) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    )
                    .padding(horizontal = 24.dp),
                horizontalArrangement = if (isDelete) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isDelete) Icons.Outlined.Delete else Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (isDelete) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.term,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        languageBadge(entry)?.let { badge ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    entry.preview?.let { preview ->
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "\u00d7${entry.lookupCount} \u00b7 ${shortTime(entry.lastLookupAt, todayStart)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { NovelDictionaryHistory.masteryOf(entry) },
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 3.dp,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        tint = if (entry.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.term,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    languageBadge(entry)?.let { badge ->
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        tint = if (entry.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
            Text(
                text = stringResource(
                    AYMR.strings.novel_reader_dictionary_history_meta,
                    entry.lookupCount.toString(),
                    dateFormat.format(entry.firstLookupAt),
                    dateFormat.format(entry.lastLookupAt),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (val state = lookupState) {
                is DetailLookupState.Loading -> {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_dictionary_history_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    entry.preview?.let { preview ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = preview, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is DetailLookupState.Loaded -> {
                    state.sections.forEach { section ->
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = section.headword,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                section.pronunciation?.let { pron ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = pron,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                section.partOfSpeech?.let { pos ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = pos,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(text = section.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    state.attribution?.let { attribution ->
                        Text(
                            text = attribution,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is DetailLookupState.Failed -> {
                    entry.preview?.let { preview ->
                        Text(text = preview, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_dictionary_history_refresh_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entry.novelTitle != null || entry.quote != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(AYMR.strings.novel_reader_dictionary_history_context),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                entry.novelTitle?.let { title ->
                    Text(
                        text = listOfNotNull(title, entry.chapterName).joinToString(" \u00b7 "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                entry.quote?.let { quote ->
                    Text(
                        text = "\u201c$quote\u201d",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            entry.provider?.let { providerName ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = providerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onPronounce) {
                    Text(text = stringResource(AYMR.strings.novel_reader_dictionary_history_action_pronounce))
                }
                OutlinedButton(onClick = onCopy) {
                    Text(text = stringResource(AYMR.strings.novel_reader_dictionary_history_action_copy))
                }
                OutlinedButton(onClick = onDelete) {
                    Text(text = stringResource(AYMR.strings.novel_reader_dictionary_history_action_delete))
                }
            }
        }
    }
}

@Composable
private fun FlashcardReviewDialog(
    queue: List<NovelDictionaryHistoryEntry>,
    onAnswer: (NovelDictionaryHistoryEntry, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var known by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
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
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onClose) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { index.toFloat() / queue.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { flipped = !flipped },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (!flipped) {
                                Text(
                                    text = entry.term,
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center,
                                )
                                languageBadge(entry)?.let { badge ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = stringResource(AYMR.strings.novel_reader_dictionary_history_review_flip),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = entry.term,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = entry.preview.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                                entry.novelTitle?.let { title ->
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = listOfNotNull(title, entry.chapterName).joinToString(" \u00b7 "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                onAnswer(entry, false)
                                flipped = false
                                index += 1
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(AYMR.strings.novel_reader_dictionary_history_review_again))
                        }
                        Button(
                            onClick = {
                                onAnswer(entry, true)
                                known += 1
                                flipped = false
                                index += 1
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(AYMR.strings.novel_reader_dictionary_history_review_know))
                        }
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
