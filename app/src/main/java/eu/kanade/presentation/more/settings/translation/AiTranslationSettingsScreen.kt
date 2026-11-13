package eu.kanade.presentation.more.settings.translation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.translation.profile.LegacyMigrationReport
import eu.kanade.tachiyomi.data.translation.profile.LegacyMigrationStatus
import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.api.get

/** Entry to the approved translator centre. Business operations stay in the lifecycle-owned model. */
class AiTranslationSettingsScreen(
    private val initialTab: AiTranslationTab = AiTranslationTab.JOBS,
    private val initialRecoveryProfileId: String? = null,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val prefs = remember { uy.kohesive.injekt.Injekt.get<NovelReaderPreferences>() }
        val model = rememberScreenModel {
            AiTranslationSettingsScreenModel(
                initialMigrationStatus = migrationStatusOf(TranslationProfileServices.lastMigrationReport),
                startMigration = { migrationStatusOf(TranslationProfileServices.runLegacyMigration()) },
                removeCredentialMetadata = { id ->
                    TranslationProfileServices.repository.deleteCredentialMetadataIfUnused(id)
                },
                ownedRunAction = { run, command ->
                    val queue = uy.kohesive.injekt.Injekt.get<
                        eu.kanade.tachiyomi.data.translation.TranslationQueueManager,
                        >()
                    when (command) {
                        AiTranslationJobCommand.PAUSE -> queue.pauseImplicitOwner(run.chapterId, run.generation)
                        AiTranslationJobCommand.RESUME -> queue.resumeImplicitOwner(run.chapterId, run.generation)
                        AiTranslationJobCommand.CANCEL -> queue.detachImplicitOwner(run.chapterId, run.generation)
                        AiTranslationJobCommand.RETRY_MISSING -> queue.retryOwnedRun(
                            run.chapterId,
                            run.generation,
                            false,
                        )
                        AiTranslationJobCommand.RETRANSLATE -> queue.retryOwnedRun(run.chapterId, run.generation, true)
                        else -> false
                    }
                },
                initialAiTranslationEnabled = prefs.geminiEnabled().get(),
                observeAiTranslationEnabled = prefs.geminiEnabled().changes(),
                setAiTranslationEnabled = { prefs.geminiEnabled().set(it) },
                initialCatalog = TranslationProfileServices.cachedCatalog,
            )
        }
        val state by model.state.collectAsStateWithLifecycle()
        var handledInitialArgs by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(initialTab, initialRecoveryProfileId, state.loadState) {
            if (!handledInitialArgs && state.loadState == AiTranslationLoadState.READY) {
                handledInitialArgs = true
                if (initialTab != AiTranslationTab.JOBS) {
                    model.onAction(AiTranslationAction.SelectTab(initialTab))
                }
                if (initialRecoveryProfileId != null) {
                    model.onAction(AiTranslationAction.EditRecovery(initialRecoveryProfileId))
                }
            }
        }
        LaunchedEffect(model) {
            model.effects.collect { effect ->
                when (effect) {
                    is AiTranslationSettingsEffect.Message -> context.toast(
                        when (effect.kind) {
                            AiTranslationSettingsMessage.VALIDATION -> AYMR.strings.ai_translation_error_field
                            AiTranslationSettingsMessage.STORAGE -> AYMR.strings.ai_translation_protected_save_failed
                            AiTranslationSettingsMessage.UNAVAILABLE -> AYMR.strings.ai_translation_action_unavailable
                            AiTranslationSettingsMessage.REFERENCED -> AYMR.strings.ai_translation_item_in_use
                        },
                    )
                    is AiTranslationSettingsEffect.OpenChapter -> {
                        val chapter = uy.kohesive.injekt.Injekt.get<NovelChapterRepository>().getChapterById(
                            effect.chapterId,
                        )
                        val novel = chapter?.let {
                            uy.kohesive.injekt.Injekt.get<NovelRepository>().getNovelById(it.novelId)
                        }
                        if (chapter != null && novel != null) {
                            navigator.push(
                                eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen(
                                    chapterId = chapter.id,
                                    sourceId = novel.source,
                                    seriesId = novel.id,
                                ),
                            )
                        } else {
                            context.toast(AYMR.strings.ai_translation_action_unavailable)
                        }
                    }
                }
            }
        }
        AiTranslationSettingsContent(
            state,
            model::onAction,
            model::saveCredential,
            navigator::pop,
            rememberResolvedSettingsUiStyle(),
        )
    }
}

private fun migrationStatusOf(report: LegacyMigrationReport?): AiTranslationMigrationStatus = when (report?.status) {
    null -> AiTranslationMigrationStatus.UNKNOWN
    LegacyMigrationStatus.COMPLETE -> AiTranslationMigrationStatus.VERIFIED
    else -> AiTranslationMigrationStatus.FAILED
}
