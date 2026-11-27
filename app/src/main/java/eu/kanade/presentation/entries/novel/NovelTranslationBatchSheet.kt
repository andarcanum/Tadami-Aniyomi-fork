package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.more.settings.translation.AiTranslationOutlinedTextField
import eu.kanade.presentation.more.settings.translation.AiTranslationPrimaryButton
import eu.kanade.presentation.more.settings.translation.AiTranslationToggleRow
import eu.kanade.presentation.more.settings.translation.AiTranslatorChoiceChip
import eu.kanade.presentation.reader.components.AuroraReaderSheet
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.novel.TranslationBatchScope
import eu.kanade.tachiyomi.ui.entries.novel.resolveTranslationBatchChapterIds
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun NovelTranslationBatchSheet(
    onDismissRequest: () -> Unit,
    chapterTitle: String,
    anchorChapterId: Long,
    chapters: List<NovelChapter>,
    selectedChapterIds: Set<Long>,
    downloadedChapterIds: Set<Long>,
    onStartBatch: (TranslationBatchScope, Int, Int, Int, Boolean) -> Unit,
) {
    var selectedScope by remember { mutableStateOf(TranslationBatchScope.SELECTED) }
    var customCount by remember { mutableStateOf("10") }
    var rangeStart by remember { mutableStateOf("1") }
    var rangeEnd by remember { mutableStateOf("10") }
    var forceRetranslate by remember { mutableStateOf(false) }

    val effectiveSelectedChapterIds = remember(selectedChapterIds, anchorChapterId) {
        selectedChapterIds.ifEmpty { setOf(anchorChapterId) }
    }
    val resolvedLimit = customCount.toIntOrNull()?.coerceAtLeast(1) ?: 10
    val resolvedRangeStart = rangeStart.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val resolvedRangeEnd = rangeEnd.toIntOrNull()?.coerceAtLeast(1) ?: 10
    val previewChapterIds = remember(
        selectedScope,
        resolvedLimit,
        resolvedRangeStart,
        resolvedRangeEnd,
        chapters,
        effectiveSelectedChapterIds,
        downloadedChapterIds,
    ) {
        resolveTranslationBatchChapterIds(
            scope = selectedScope,
            limit = resolvedLimit,
            chapters = chapters,
            selectedChapterIds = effectiveSelectedChapterIds,
            downloadedChapterIds = downloadedChapterIds,
            rangeStart = resolvedRangeStart,
            rangeEnd = resolvedRangeEnd,
        )
    }

    AuroraReaderSheet(onDismissRequest = onDismissRequest) {
        val aurora = AuroraTheme.colors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.novel_batch_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = aurora.textPrimary,
                )
                if (chapterTitle.isNotBlank()) {
                    Text(
                        text = stringResource(MR.strings.novel_batch_current_chapter, chapterTitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = aurora.textSecondary,
                    )
                }
                Text(
                    text = stringResource(MR.strings.novel_batch_select_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = aurora.textSecondary,
                )
            }

            AuroraGlassSection(title = stringResource(MR.strings.novel_batch_mode)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ScopeChipRow(
                        selected = selectedScope,
                        onSelected = { selectedScope = it },
                    )

                    if (selectedScope == TranslationBatchScope.FIRST_N_VISIBLE) {
                        AiTranslationOutlinedTextField(
                            value = customCount,
                            onValueChange = { customCount = it.filter(Char::isDigit) },
                            label = { Text(stringResource(MR.strings.novel_batch_first_n_chapters)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (selectedScope == TranslationBatchScope.RANGE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AiTranslationOutlinedTextField(
                                value = rangeStart,
                                onValueChange = { rangeStart = it.filter(Char::isDigit) },
                                label = { Text(stringResource(MR.strings.novel_batch_from_chapter)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            AiTranslationOutlinedTextField(
                                value = rangeEnd,
                                onValueChange = { rangeEnd = it.filter(Char::isDigit) },
                                label = { Text(stringResource(MR.strings.novel_batch_to_chapter)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            AuroraGlassSection {
                AiTranslationToggleRow(
                    label = stringResource(MR.strings.novel_batch_force_retranslate),
                    subtitle = stringResource(MR.strings.novel_batch_force_retranslate_desc),
                    checked = forceRetranslate,
                    onClick = { forceRetranslate = !forceRetranslate },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = auroraRimColor(),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(MR.strings.novel_batch_queue_count, previewChapterIds.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = aurora.textPrimary,
                    )
                    if (!forceRetranslate) {
                        Text(
                            text = stringResource(MR.strings.novel_batch_skip_translated),
                            style = MaterialTheme.typography.bodySmall,
                            color = aurora.textSecondary,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuroraFrostCancel(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = onDismissRequest,
                )
                AiTranslationPrimaryButton(
                    text = stringResource(MR.strings.novel_batch_start),
                    onClick = {
                        onStartBatch(
                            selectedScope,
                            resolvedLimit,
                            resolvedRangeStart,
                            resolvedRangeEnd,
                            forceRetranslate,
                        )
                    },
                    enabled = previewChapterIds.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ScopeChipRow(
    selected: TranslationBatchScope,
    onSelected: (TranslationBatchScope) -> Unit,
) {
    val scopes = listOf(
        TranslationBatchScope.SELECTED to stringResource(MR.strings.novel_batch_selected),
        TranslationBatchScope.DOWNLOADED to stringResource(MR.strings.label_downloaded),
        TranslationBatchScope.UNREAD to stringResource(MR.strings.unread),
        TranslationBatchScope.FIRST_N_VISIBLE to stringResource(MR.strings.novel_batch_first_n),
        TranslationBatchScope.RANGE to stringResource(MR.strings.novel_batch_range),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        scopes.chunked(2).forEach { rowScopes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowScopes.forEach { (scope, label) ->
                    AiTranslatorChoiceChip(
                        text = label,
                        selected = selected == scope,
                        onClick = { onSelected(scope) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowScopes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
