package eu.kanade.presentation.reader.novel

import android.text.Spanned
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import eu.kanade.tachiyomi.ui.reader.novel.NovelDictionaryUiState
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationUiState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

private enum class TabType {
    DICTIONARY,
    TRANSLATION,
}

@Composable
internal fun SelectedTextTranslationOverlay(
    state: NovelReaderScreenModel.State.Success,
    onTranslate: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onLookupDefinition: () -> Unit = {},
    onRetryDictionary: () -> Unit = {},
    onDismissDictionary: () -> Unit = {},
    onPlayPronunciation: (String) -> Unit = {},
) {
    val showTranslation = state.readerSettings.selectedTextTranslationEnabled
    val showDictionary = state.novelDictionaryEnabled

    if (!showTranslation && !showDictionary) return

    val selection = state.selectedTextTranslationSelection
    val translationState = state.selectedTextTranslationUiState
    val dictionaryState = state.novelDictionaryUiState

    val isVisible = selection != null

    var activeTab by remember(selection) {
        mutableStateOf(
            if (showDictionary) TabType.DICTIONARY else TabType.TRANSLATION,
        )
    }

    // Auto-trigger load when switching tabs if state is Idle
    LaunchedEffect(activeTab, selection) {
        if (selection != null) {
            if (activeTab == TabType.DICTIONARY && dictionaryState is NovelDictionaryUiState.Looking) {
                // Already loading
            } else if (activeTab == TabType.TRANSLATION &&
                translationState is NovelSelectedTextTranslationUiState.Translating
            ) {
                // Already loading
            }
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier,
    ) {
        if (selection != null) {
            // Show full bottom sheet card
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 360.dp)
                    .heightIn(max = 420.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 1. Header Tabs
                    if (showTranslation && showDictionary) {
                        TabRow(
                            selectedTabIndex = activeTab.ordinal,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Tab(
                                selected = activeTab == TabType.DICTIONARY,
                                onClick = {
                                    activeTab = TabType.DICTIONARY
                                    if (dictionaryState is NovelDictionaryUiState.Idle) {
                                        onLookupDefinition()
                                    }
                                },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text(stringResource(AYMR.strings.novel_reader_dictionary_action_lookup))
                                    }
                                },
                            )
                            Tab(
                                selected = activeTab == TabType.TRANSLATION,
                                onClick = {
                                    activeTab = TabType.TRANSLATION
                                    if (translationState is NovelSelectedTextTranslationUiState.Idle) {
                                        onTranslate()
                                    }
                                },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Translate,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text(
                                            stringResource(
                                                AYMR.strings.novel_reader_selected_text_translation_action_translate,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }

                    // 2. Content Body (Scrollable)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        if (activeTab == TabType.DICTIONARY) {
                            DictionaryContent(
                                selection = selection,
                                state = dictionaryState,
                                onRetry = onRetryDictionary,
                                onPlayPronunciation = onPlayPronunciation,
                            )
                        } else {
                            TranslationContent(
                                selection = selection,
                                state = translationState,
                                onRetry = onRetry,
                            )
                        }
                    }

                    // 3. Footer Control Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = {
                            onDismiss()
                            onDismissDictionary()
                        }) {
                            Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryContent(
    selection: NovelSelectedTextSelection?,
    state: NovelDictionaryUiState,
    onRetry: () -> Unit,
    onPlayPronunciation: (String) -> Unit,
) {
    when (state) {
        is NovelDictionaryUiState.Looking -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_dictionary_loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        is NovelDictionaryUiState.Result -> {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Word Headword and TTS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.result.entries.firstOrNull()?.headword ?: selection?.text ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        state.result.entries.firstOrNull()?.pronunciation?.takeIf<String> { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onPlayPronunciation(
                                state.result.entries.firstOrNull()?.headword ?: selection?.text ?: "",
                            )
                        },
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(12.dp),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speak",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                // Definitions List
                state.result.entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            entry.partOfSpeech?.takeIf<String> { it.isNotBlank() }?.let { pos ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(6.dp),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = pos,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            entry.sourceLanguage?.takeIf<String> { it.isNotBlank() }?.let { lang ->
                                Text(
                                    text = lang.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = htmlToAnnotatedString(entry.definitionsHtml),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Attribution Footer
                state.result.attribution?.takeIf<String> { it.isNotBlank() }?.let { attr ->
                    Text(
                        text = attr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        is NovelDictionaryUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = translationErrorMessage(state.reason), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) {
                    Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_retry))
                }
            }
        }
        is NovelDictionaryUiState.Unavailable -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = translationErrorMessage(state.reason), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) {
                    Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_retry))
                }
            }
        }
        NovelDictionaryUiState.Idle -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun TranslationContent(
    selection: NovelSelectedTextSelection?,
    state: NovelSelectedTextTranslationUiState,
    onRetry: () -> Unit,
) {
    when (state) {
        is NovelSelectedTextTranslationUiState.Translating -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_selected_text_translation_loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        is NovelSelectedTextTranslationUiState.Result -> {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = selection?.text ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.translationResult.translation,
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.translationResult.detectedSourceLanguage?.takeIf<String> { it.isNotBlank() }?.let { lang ->
                    Text(
                        text = "Source language: $lang",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is NovelSelectedTextTranslationUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = translationErrorMessage(state.reason), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) {
                    Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_retry))
                }
            }
        }
        is NovelSelectedTextTranslationUiState.Unavailable -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = translationErrorMessage(state.reason), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) {
                    Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_retry))
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

private fun htmlToAnnotatedString(html: String): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    return spanned.toAnnotatedString()
}

private fun Spanned.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    val spannedText = this@toAnnotatedString
    append(spannedText.toString())

    val styleSpans = spannedText.getSpans(0, spannedText.length, android.text.style.StyleSpan::class.java)
    for (span in styleSpans) {
        val start = spannedText.getSpanStart(span)
        val end = spannedText.getSpanEnd(span)
        if (start in 0..spannedText.length && end in 0..spannedText.length && start < end) {
            when (span.style) {
                android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                android.graphics.Typeface.BOLD_ITALIC -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    start,
                    end,
                )
            }
        }
    }

    val underlineSpans = spannedText.getSpans(0, spannedText.length, android.text.style.UnderlineSpan::class.java)
    for (span in underlineSpans) {
        val start = spannedText.getSpanStart(span)
        val end = spannedText.getSpanEnd(span)
        if (start in 0..spannedText.length && end in 0..spannedText.length && start < end) {
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
        }
    }
}

@Composable
private fun translationErrorMessage(reason: NovelSelectedTextTranslationErrorReason): String {
    return when (reason) {
        NovelSelectedTextTranslationErrorReason.EmptySelection,
        NovelSelectedTextTranslationErrorReason.TooLongSelection,
        NovelSelectedTextTranslationErrorReason.ParserFailure,
        NovelSelectedTextTranslationErrorReason.WebViewUnavailable,
        -> {
            stringResource(AYMR.strings.novel_reader_selected_text_translation_unavailable)
        }
        is NovelSelectedTextTranslationErrorReason.BackendUnavailable -> {
            reason.message?.takeIf<String> { it.isNotBlank() }
                ?: stringResource(AYMR.strings.novel_reader_selected_text_translation_unavailable)
        }
        is NovelSelectedTextTranslationErrorReason.NetworkFailure -> {
            reason.message?.takeIf<String> { it.isNotBlank() }
                ?: stringResource(AYMR.strings.novel_reader_selected_text_translation_unavailable)
        }
        is NovelSelectedTextTranslationErrorReason.Cooldown -> {
            "${stringResource(
                AYMR.strings.novel_reader_selected_text_translation_unavailable,
            )} (${reason.remainingSeconds}s)"
        }
    }
}
