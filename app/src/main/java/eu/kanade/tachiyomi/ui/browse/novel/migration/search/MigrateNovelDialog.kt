package eu.kanade.tachiyomi.ui.browse.novel.migration.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.ui.browse.novel.migration.NovelMigrationFlags
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.novelchapter.model.toNovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

@Composable
internal fun MigrateNovelDialog(
    oldNovel: Novel,
    newNovel: Novel,
    screenModel: MigrateNovelDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsState()

    val flags = remember { NovelMigrationFlags.getFlags(oldNovel, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    flags.forEachIndexed { index, flag ->
                        LabeledCheckbox(
                            label = stringResource(flag.titleId),
                            checked = selectedFlags[index],
                            onCheckedChange = { selectedFlags[index] = it },
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onClickTitle()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_show_manga))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateNovel(
                                    oldNovel,
                                    newNovel,
                                    false,
                                    NovelMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                )
                                withUIContext { onPopScreen() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.copy))
                    }
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateNovel(
                                    oldNovel,
                                    newNovel,
                                    true,
                                    NovelMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                )

                                withUIContext { onPopScreen() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.migrate))
                    }
                }
            },
        )
    }
}

internal class MigrateNovelDialogScreenModel(
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val syncNovelChaptersWithSource: SyncNovelChaptersWithSource = Injekt.get(),
    private val categoryRepository: NovelCategoryRepository = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrateNovelDialogScreenModel.State>(State()) {

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    suspend fun migrateNovel(
        oldNovel: Novel,
        newNovel: Novel,
        replace: Boolean,
        flags: Int,
    ) {
        migrateFlags.set(flags)
        val source = sourceManager.get(newNovel.source) ?: return
        val prevSource = sourceManager.get(oldNovel.source)

        mutableState.update { it.copy(isMigrating = true) }

        try {
            val chapters = source.getChapterList(newNovel.toSNovel())

            migrateNovelInternal(
                oldSource = prevSource,
                newSource = source,
                oldNovel = oldNovel,
                newNovel = newNovel,
                sourceChapters = chapters,
                replace = replace,
                flags = flags,
            )
        } catch (_: Throwable) {
            mutableState.update { it.copy(isMigrating = false) }
        }
    }

    private suspend fun migrateNovelInternal(
        oldSource: NovelSource?,
        newSource: NovelSource,
        oldNovel: Novel,
        newNovel: Novel,
        sourceChapters: List<SNovelChapter>,
        replace: Boolean,
        flags: Int,
    ) {
        val migrateChapters = NovelMigrationFlags.hasChapters(flags)
        val migrateCategories = NovelMigrationFlags.hasCategories(flags)
        val deleteDownloaded = NovelMigrationFlags.hasDeleteDownloaded(flags)

        try {
            syncNovelChaptersWithSource.await(sourceChapters, newNovel, newSource)
        } catch (_: Exception) {
            // Worst case, chapters won't be synced.
        }

        if (migrateChapters) {
            val prevNovelChapters = novelChapterRepository.getChapterByNovelId(oldNovel.id)
            val novelChapters = novelChapterRepository.getChapterByNovelId(newNovel.id)

            val maxChapterRead = prevNovelChapters
                .filter { it.read }
                .maxOfOrNull { it.chapterNumber }

            val updatedNovelChapters = novelChapters.map { novelChapter ->
                var updatedChapter = novelChapter
                if (updatedChapter.isRecognizedNumber) {
                    val prevChapter = prevNovelChapters
                        .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                    if (prevChapter != null) {
                        updatedChapter = updatedChapter.copy(
                            dateFetch = prevChapter.dateFetch,
                            bookmark = prevChapter.bookmark,
                            lastPageRead = prevChapter.lastPageRead,
                        )
                    }

                    if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                        updatedChapter = updatedChapter.copy(read = true, lastPageRead = 0L)
                    }
                }

                updatedChapter
            }

            val chapterUpdates = updatedNovelChapters.map { it.toNovelChapterUpdate() }
            novelChapterRepository.updateAllChapters(chapterUpdates)
        }

        if (migrateCategories) {
            val categoryIds = categoryRepository.getCategoriesByNovelId(oldNovel.id).map { it.id }
            categoryRepository.setNovelCategories(newNovel.id, categoryIds)
        }

        if (deleteDownloaded && oldSource != null) {
            downloadManager.deleteNovel(oldNovel)
        }

        if (replace) {
            updateNovel.await(
                NovelUpdate(
                    id = oldNovel.id,
                    favorite = false,
                    dateAdded = 0L,
                ),
            )
        }

        updateNovel.await(
            NovelUpdate(
                id = newNovel.id,
                favorite = true,
                chapterFlags = oldNovel.chapterFlags,
                viewerFlags = oldNovel.viewerFlags,
                dateAdded = if (replace) oldNovel.dateAdded else Instant.now().toEpochMilli(),
            ),
        )
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}
