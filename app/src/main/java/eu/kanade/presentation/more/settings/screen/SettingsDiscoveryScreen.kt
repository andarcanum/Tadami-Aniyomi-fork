package eu.kanade.presentation.more.settings.screen

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.discovery.DiscoverySourcesScreen
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.parseGenreFilterCsv
import eu.kanade.tachiyomi.data.discovery.resolveUserGenreInput
import eu.kanade.tachiyomi.data.discovery.serializeGenreFilterCsv
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource

/**
 * Настройки ленты «Для тебя» (discovery). Правило адаптивных тумблеров:
 * мастер-выключатель серит все группы; выключенный ряд серит свои подустановки.
 */
object SettingsDiscoveryScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_discovery_title

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val colors = eu.kanade.presentation.theme.AuroraTheme.colors
        val repository = remember { Injekt.get<DiscoveryRepository>() }
        var showResetHiddenDialog by remember { mutableStateOf(false) }
        var showBlacklistDialog by remember { mutableStateOf(false) }
        var showStatusDialog by remember { mutableStateOf(false) }
        var showGenreDialog by remember { mutableStateOf(false) }
        var showResetTasteDialog by remember { mutableStateOf(false) }
        var blacklistTags by remember { mutableStateOf<List<Pair<DiscoveryMediaType, String>>>(emptyList()) }
        val reloadBlacklist: suspend () -> Unit = {
            blacklistTags = DiscoveryMediaType.entries.flatMap { media ->
                repository.getBlacklistedTags(media).sorted().map { media to it }
            }
        }

        // Taste Learning Engine: сводка выученного профиля для настроек.
        var tasteSignalCount by remember { mutableStateOf(0) }
        var tasteTopGenres by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
        val reloadTaste: suspend () -> Unit = {
            val allSignals = DiscoveryMediaType.entries.flatMap { repository.getSignals(it) }
            tasteSignalCount = allSignals.size
            tasteTopGenres = eu.kanade.tachiyomi.data.discovery.foldLearnedTasteProfile(allSignals)
                .genres.filter { it.second > 0.0 }
                .take(5)
        }
        LaunchedEffect(Unit) { reloadTaste() }

        // «Просмотренные» (consumed): список с per-title undo — вернуть тайтл в ленту.
        var showConsumedDialog by remember { mutableStateOf(false) }
        var consumedSignals by remember {
            mutableStateOf<List<tachiyomi.domain.discovery.model.DiscoverySignal>>(emptyList())
        }
        val reloadConsumed: suspend () -> Unit = {
            consumedSignals = DiscoveryMediaType.entries
                .flatMap { repository.getSignals(it) }
                .filter { it.signalType.excludesFromFeed }
                .sortedByDescending { it.createdAt }
        }

        val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
        val sourcePreferences = remember { Injekt.get<eu.kanade.domain.source.service.SourcePreferences>() }

        val enabled by discoveryPreferences.discoveryEnabled().collectAsStateWithLifecycle()
        val rowLike by discoveryPreferences.rowLikeEnabled().collectAsStateWithLifecycle()
        val rowTaste by discoveryPreferences.rowTasteEnabled().collectAsStateWithLifecycle()
        val rowTrend by discoveryPreferences.rowTrendEnabled().collectAsStateWithLifecycle()
        val rowSource by discoveryPreferences.rowSourceEnabled().collectAsStateWithLifecycle()
        val seedCompleted by discoveryPreferences.seedCompleted().collectAsStateWithLifecycle()
        val seedActive14 by discoveryPreferences.seedActive14().collectAsStateWithLifecycle()
        val homeHeroMode by discoveryPreferences.homeHeroMode().collectAsStateWithLifecycle()
        val externalProviders by discoveryPreferences.externalProvidersEnabled().collectAsStateWithLifecycle()
        val isCollageMode = homeHeroMode == "collage"
        // Auto резолвится в Stage при включённом «Для тебя» — stage-поднастройки видны и в авто-режиме.
        val isStageMode = homeHeroMode == "stage" || (homeHeroMode == "auto" && enabled)

        if (showResetHiddenDialog) {
            AuroraFrostDialog(
                onDismiss = { showResetHiddenDialog = false },
                title = stringResource(AYMR.strings.pref_discovery_clear_hidden_dialog_title),
                footer = {
                    AuroraFrostCancel(
                        label = stringResource(MR.strings.action_cancel),
                        onClick = { showResetHiddenDialog = false },
                    )
                    AuroraFrostConfirm(
                        label = stringResource(MR.strings.action_ok),
                        onClick = {
                            showResetHiddenDialog = false
                            scope.launchIO {
                                DiscoveryMediaType.entries.forEach { repository.clearHidden(it) }
                                withUIContext {
                                    context.toast(
                                        context.contextStringResource(AYMR.strings.pref_discovery_clear_hidden_success),
                                    )
                                }
                            }
                        },
                    )
                },
            ) {
                Text(
                    stringResource(AYMR.strings.pref_discovery_clear_hidden_dialog_message),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        if (showResetTasteDialog) {
            AuroraFrostDialog(
                onDismiss = { showResetTasteDialog = false },
                title = stringResource(AYMR.strings.pref_discovery_taste_reset),
                footer = {
                    AuroraFrostCancel(
                        label = stringResource(MR.strings.action_cancel),
                        onClick = { showResetTasteDialog = false },
                    )
                    AuroraFrostConfirm(
                        label = stringResource(MR.strings.action_ok),
                        onClick = {
                            showResetTasteDialog = false
                            scope.launchIO {
                                // Сброс только выученного вкуса: кэш ленты, hidden и
                                // блэклист не трогаются — «вкусы» и «лента» независимы.
                                repository.clearAllSignals()
                                reloadTaste()
                            }
                        },
                    )
                },
            ) {
                Text(
                    stringResource(AYMR.strings.pref_discovery_taste_reset_confirm),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        if (showBlacklistDialog) {
            AuroraFrostDialog(
                onDismiss = { showBlacklistDialog = false },
                title = stringResource(AYMR.strings.pref_discovery_blacklist_tags),
                footer = {
                    AuroraFrostCancel(
                        label = stringResource(MR.strings.action_cancel),
                        onClick = { showBlacklistDialog = false },
                    )
                    AuroraFrostConfirm(
                        label = stringResource(AYMR.strings.pref_discovery_blacklist_reset),
                        onClick = {
                            scope.launchIO {
                                DiscoveryMediaType.entries.forEach { repository.clearBlacklist(it) }
                                showBlacklistDialog = false
                            }
                        },
                    )
                },
            ) {
                if (blacklistTags.isEmpty()) {
                    Text(
                        stringResource(AYMR.strings.pref_discovery_blacklist_empty),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                } else {
                    Column {
                        blacklistTags.forEach { (media, tag) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable {
                                        scope.launchIO {
                                            repository.unblacklistTag(media, tag)
                                            reloadBlacklist()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "$tag · ${media.key}",
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // «Просмотренные»: список consumed-тайтлов с per-title undo и общим возвратом.
        if (showConsumedDialog) {
            AuroraFrostDialog(
                onDismiss = { showConsumedDialog = false },
                title = stringResource(AYMR.strings.pref_discovery_consumed_title),
                footer = {
                    AuroraFrostCancel(
                        label = stringResource(MR.strings.action_cancel),
                        onClick = { showConsumedDialog = false },
                    )
                    AuroraFrostConfirm(
                        label = stringResource(AYMR.strings.pref_discovery_consumed_reset),
                        onClick = {
                            scope.launchIO {
                                consumedSignals.forEach { repository.removeSignal(it.mediaType, it.cleanTitle) }
                                reloadConsumed()
                                reloadTaste()
                            }
                            showConsumedDialog = false
                        },
                        enabled = consumedSignals.isNotEmpty(),
                    )
                },
            ) {
                if (consumedSignals.isEmpty()) {
                    Text(
                        stringResource(AYMR.strings.pref_discovery_consumed_empty),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                } else {
                    Column {
                        consumedSignals.forEach { signal ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable {
                                        scope.launchIO {
                                            // Undo consumed: тайтл снова может попасть в ленту.
                                            repository.removeSignal(signal.mediaType, signal.cleanTitle)
                                            reloadConsumed()
                                            reloadTaste()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${signal.title} · ${signal.mediaType.key}",
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // V1: диалог мультивыбора статусов выпуска (4 свитча, дефолт — все включены).
        if (showStatusDialog) {
            ReleaseStatusFilterDialog(
                currentCsv = discoveryPreferences.releaseStatusFilter().get(),
                onApply = { csv ->
                    discoveryPreferences.releaseStatusFilter().set(csv)
                    showStatusDialog = false
                    // Применить сразу: без триггера лента показывала бы старые статусы
                    // до следующего фонового цикла.
                    DiscoveryUpdateJob.refreshNow(context)
                },
                onDismiss = { showStatusDialog = false },
            )
        }

        // V3: диалог жанров (игнор / приоритет / обязательные).
        if (showGenreDialog) {
            GenreFilterDialog(
                priorityCsv = discoveryPreferences.priorityGenres().get(),
                requiredCsv = discoveryPreferences.requiredGenres().get(),
                ignoredCsv = discoveryPreferences.ignoredGenres().get(),
                onApply = { priCsv, reqCsv, ignCsv ->
                    discoveryPreferences.priorityGenres().set(priCsv)
                    discoveryPreferences.requiredGenres().set(reqCsv)
                    discoveryPreferences.ignoredGenres().set(ignCsv)
                    showGenreDialog = false
                    // Игнор/приоритет/обязательные жанры — применить сразу, не ждать фоновый цикл.
                    DiscoveryUpdateJob.refreshNow(context)
                },
                onDismiss = { showGenreDialog = false },
            )
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_general),
                preferenceItems = buildList {
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = discoveryPreferences.discoveryEnabled(),
                            title = stringResource(AYMR.strings.pref_discovery_enabled),
                            subtitle = stringResource(AYMR.strings.pref_discovery_enabled_summary),
                            onValueChanged = {
                                DiscoveryUpdateJob.setupTask(context)
                                true
                            },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = discoveryPreferences.filterNsfw(),
                            title = stringResource(AYMR.strings.pref_discovery_nsfw_filter),
                            subtitle = stringResource(AYMR.strings.pref_discovery_nsfw_filter_summary),
                            enabled = enabled,
                        ),
                    )
                    // «Только плагины»: выключает внешние провайдеры целиком —
                    // LIKE/TREND (чисто внешние ряды) не строятся, TASTE — из каталогов.
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = discoveryPreferences.externalProvidersEnabled(),
                            title = stringResource(AYMR.strings.pref_discovery_external_providers),
                            subtitle = stringResource(AYMR.strings.pref_discovery_external_providers_summary),
                            enabled = enabled,
                            onValueChanged = {
                                // Переключение состава провайдеров — регенерировать ленту сразу,
                                // иначе старые внешние тайтлы висят до фонового цикла.
                                DiscoveryUpdateJob.refreshNow(context)
                                true
                            },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = discoveryPreferences.homeHeroMode(),
                            entries = persistentMapOf(
                                "auto" to stringResource(AYMR.strings.pref_home_hero_mode_auto),
                                "continue" to stringResource(AYMR.strings.pref_home_hero_mode_continue),
                                "collage" to stringResource(AYMR.strings.pref_home_hero_mode_collage),
                                "hybrid" to stringResource(AYMR.strings.pref_home_hero_mode_hybrid),
                                "stage" to stringResource(AYMR.strings.pref_home_hero_mode_stage),
                            ),
                            title = stringResource(AYMR.strings.pref_home_hero_mode),
                            subtitleProvider = { value, entries -> entries[value] },
                            enabled = enabled,
                        ),
                    )
                    if (isCollageMode || isStageMode) {
                        // Значения (интервалы и скорости) общие с коллажом — переиспользуем строки,
                        // различаются только префы и заголовки.
                        add(
                            Preference.PreferenceItem.ListPreference(
                                preference = if (isStageMode) {
                                    discoveryPreferences.stageRotationIntervalHours()
                                } else {
                                    discoveryPreferences.collageRotationIntervalHours()
                                },
                                entries = persistentMapOf(
                                    0 to stringResource(AYMR.strings.pref_collage_rotation_interval_0),
                                    1 to stringResource(AYMR.strings.pref_collage_rotation_interval_1),
                                    2 to stringResource(AYMR.strings.pref_collage_rotation_interval_2),
                                    4 to stringResource(AYMR.strings.pref_collage_rotation_interval_4),
                                    6 to stringResource(AYMR.strings.pref_collage_rotation_interval_6),
                                    12 to stringResource(AYMR.strings.pref_collage_rotation_interval_12),
                                    24 to stringResource(AYMR.strings.pref_collage_rotation_interval_24),
                                ),
                                title = if (isStageMode) {
                                    stringResource(AYMR.strings.pref_stage_rotation_interval)
                                } else {
                                    stringResource(AYMR.strings.pref_collage_rotation_interval)
                                },
                                subtitleProvider = { value, entries -> entries[value] },
                                enabled = enabled,
                            ),
                        )
                        add(
                            Preference.PreferenceItem.ListPreference(
                                preference = if (isStageMode) {
                                    discoveryPreferences.stageAnimationSpeed()
                                } else {
                                    discoveryPreferences.collageAnimationSpeed()
                                },
                                entries = persistentMapOf(
                                    "fast" to stringResource(AYMR.strings.pref_collage_animation_speed_fast),
                                    "normal" to stringResource(AYMR.strings.pref_collage_animation_speed_normal),
                                    "smooth" to stringResource(AYMR.strings.pref_collage_animation_speed_smooth),
                                ),
                                title = if (isStageMode) {
                                    stringResource(AYMR.strings.pref_stage_animation_speed)
                                } else {
                                    stringResource(AYMR.strings.pref_collage_animation_speed)
                                },
                                subtitleProvider = { value, entries -> entries[value] },
                                enabled = enabled,
                            ),
                        )
                    }
                }.toPersistentList(),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_like),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.rowLikeEnabled(),
                        title = stringResource(AYMR.strings.pref_discovery_row_like),
                        subtitle = stringResource(AYMR.strings.pref_discovery_row_like_summary),
                        // Ряд «Похоже» чисто внешний: без внешних провайдеров он не строится.
                        enabled = enabled && externalProviders,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.seedCount(),
                        entries = persistentMapOf(
                            1 to "1",
                            2 to "2",
                            3 to "3",
                            4 to "4",
                            5 to "5",
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_seed_count),
                        subtitleProvider = { value, _ ->
                            stringResource(AYMR.strings.pref_discovery_seed_count_summary, value)
                        },
                        enabled = enabled && rowLike && externalProviders,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.seedCompleted(),
                        title = stringResource(AYMR.strings.pref_discovery_seed_completed),
                        enabled = enabled && rowLike && externalProviders,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.seedCompletedDays(),
                        entries = persistentMapOf(
                            7 to stringResource(AYMR.strings.pref_discovery_days_7),
                            14 to stringResource(AYMR.strings.pref_discovery_days_14),
                            30 to stringResource(AYMR.strings.pref_discovery_days_30),
                            60 to stringResource(AYMR.strings.pref_discovery_days_60),
                            90 to stringResource(AYMR.strings.pref_discovery_days_90),
                            180 to stringResource(AYMR.strings.pref_discovery_days_180),
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_seed_completed_days),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled && rowLike && externalProviders && seedCompleted,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.seedActive14(),
                        title = stringResource(AYMR.strings.pref_discovery_seed_active14),
                        enabled = enabled && rowLike && externalProviders,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.seedActiveDays(),
                        entries = persistentMapOf(
                            7 to stringResource(AYMR.strings.pref_discovery_days_7),
                            14 to stringResource(AYMR.strings.pref_discovery_days_14),
                            30 to stringResource(AYMR.strings.pref_discovery_days_30),
                            60 to stringResource(AYMR.strings.pref_discovery_days_60),
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_seed_active_days),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled && rowLike && externalProviders && seedActive14,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.seedAdded(),
                        title = stringResource(AYMR.strings.pref_discovery_seed_added),
                        enabled = enabled && rowLike && externalProviders,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.suggestionsUseShikimori(),
                        title = stringResource(AYMR.strings.pref_discovery_provider_shikimori),
                        subtitle = stringResource(AYMR.strings.pref_discovery_provider_shikimori_summary),
                        enabled = enabled && rowLike && externalProviders,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.suggestionsUseMangaUpdatesNovel(),
                        title = stringResource(AYMR.strings.pref_discovery_provider_mangaupdates),
                        subtitle = stringResource(AYMR.strings.pref_discovery_provider_mangaupdates_summary),
                        enabled = enabled && rowLike && externalProviders,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.suggestionsUseNovelUpdates(),
                        title = stringResource(AYMR.strings.pref_discovery_provider_novelupdates),
                        subtitle = stringResource(AYMR.strings.pref_discovery_provider_novelupdates_summary),
                        enabled = enabled && rowLike && externalProviders,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_trend),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.rowTrendEnabled(),
                        title = stringResource(AYMR.strings.pref_discovery_row_trend),
                        // Ряд трендов чисто внешний: без внешних провайдеров он не строится.
                        enabled = enabled && externalProviders,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.trendSeason(),
                        entries = persistentMapOf(
                            "current" to stringResource(AYMR.strings.pref_discovery_trend_season_current),
                            "next" to stringResource(AYMR.strings.pref_discovery_trend_season_next),
                            "both" to stringResource(AYMR.strings.pref_discovery_trend_season_both),
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_trend_season),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled && rowTrend && externalProviders,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.trendSort(),
                        entries = persistentMapOf(
                            "popularity" to stringResource(AYMR.strings.pref_discovery_trend_sort_popularity),
                            "score" to stringResource(AYMR.strings.pref_discovery_trend_sort_score),
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_trend_sort),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled && rowTrend && externalProviders,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_signals),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.rowTasteEnabled(),
                        title = stringResource(AYMR.strings.pref_discovery_row_taste),
                        subtitle = stringResource(AYMR.strings.pref_discovery_row_taste_summary),
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.rowSourceEnabled(),
                        title = stringResource(AYMR.strings.pref_discovery_row_source),
                        subtitle = stringResource(AYMR.strings.pref_discovery_row_source_summary),
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_discovery_sources_title),
                        subtitle = stringResource(AYMR.strings.pref_discovery_sources_summary),
                        icon = Icons.Outlined.Tune,
                        onClick = { navigator.push(DiscoverySourcesScreen()) },
                        enabled = enabled,
                    ),
                ),
            ),
            // V0+V1: новая группа «Фильтры контента» — сразу после «Сигналы и ряды»:
            // статус выпуска + перенесённый сюда блэклист тегов (заметность).
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_filters),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_discovery_release_status),
                        subtitle = run {
                            val ongoingLabel = stringResource(AYMR.strings.pref_discovery_release_status_ongoing)
                            val finishedLabel = stringResource(AYMR.strings.pref_discovery_release_status_finished)
                            val anonsLabel = stringResource(AYMR.strings.pref_discovery_release_status_anons)
                            val pausedLabel = stringResource(AYMR.strings.pref_discovery_release_status_paused)
                            val allLabel = stringResource(AYMR.strings.pref_discovery_release_status_summary_all)
                            val statuses = DiscoveryReleaseStatus.parseCsv(
                                discoveryPreferences.releaseStatusFilter().get(),
                            )
                            if (statuses.isEmpty()) {
                                allLabel
                            } else {
                                statuses.joinToString { status ->
                                    when (status) {
                                        DiscoveryReleaseStatus.ONGOING -> ongoingLabel
                                        DiscoveryReleaseStatus.FINISHED -> finishedLabel
                                        DiscoveryReleaseStatus.ANONS -> anonsLabel
                                        DiscoveryReleaseStatus.PAUSED -> pausedLabel
                                    }
                                }
                            }
                        },
                        onClick = { showStatusDialog = true },
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_discovery_blacklist_tags),
                        subtitle = stringResource(AYMR.strings.pref_discovery_blacklist_tags_summary),
                        onClick = {
                            showBlacklistDialog = true
                            scope.launchIO { reloadBlacklist() }
                        },
                        enabled = enabled,
                    ),
                    // V3: жанры подборки — приоритетные/обязательные.
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_discovery_genre_filters),
                        subtitle = run {
                            val priCsv = discoveryPreferences.priorityGenres().get()
                                .splitToSequence(",").mapNotNull { it.trim().takeIf(String::isNotEmpty) }.count()
                            val reqCsv = discoveryPreferences.requiredGenres().get()
                                .splitToSequence(",").mapNotNull { it.trim().takeIf(String::isNotEmpty) }.count()
                            if (priCsv == 0 && reqCsv == 0) {
                                stringResource(AYMR.strings.pref_discovery_genre_filters_summary)
                            } else {
                                "$priCsv приорит · $reqCsv обязат"
                            }
                        },
                        onClick = { showGenreDialog = true },
                        enabled = enabled,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_updates),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.refreshIntervalHours(),
                        entries = persistentMapOf(
                            2 to stringResource(AYMR.strings.pref_discovery_interval_2),
                            6 to stringResource(AYMR.strings.pref_discovery_interval_6),
                            12 to stringResource(AYMR.strings.pref_discovery_interval_12),
                            24 to stringResource(AYMR.strings.pref_discovery_interval_24),
                            48 to stringResource(AYMR.strings.pref_discovery_interval_48),
                            168 to stringResource(AYMR.strings.pref_discovery_interval_weekly),
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_refresh_interval),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled,
                        onValueChanged = {
                            DiscoveryUpdateJob.setupTask(context)
                            true
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.refreshAfterLibrary(),
                        title = stringResource(AYMR.strings.pref_discovery_refresh_after_library),
                        subtitle = stringResource(AYMR.strings.pref_discovery_refresh_after_library_summary),
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.refreshWifiOnly(),
                        title = stringResource(AYMR.strings.pref_discovery_wifi_only),
                        enabled = enabled,
                        onValueChanged = {
                            DiscoveryUpdateJob.setupTask(context)
                            true
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_display),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.teaserCount(),
                        entries = persistentMapOf(
                            3 to "3",
                            4 to "4",
                            5 to "5",
                            6 to "6",
                            7 to "7",
                            8 to "8",
                            9 to "9",
                            10 to "10",
                            12 to "12",
                            16 to "16",
                            20 to "20",
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_teaser_count),
                        subtitleProvider = { value, _ -> value.toString() },
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.showReasons(),
                        title = stringResource(AYMR.strings.pref_discovery_show_reasons),
                        subtitle = stringResource(AYMR.strings.pref_discovery_show_reasons_summary),
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_discovery_clear_hidden_title),
                        subtitle = stringResource(AYMR.strings.pref_discovery_clear_hidden_summary),
                        onClick = { showResetHiddenDialog = true },
                        enabled = enabled,
                    ),
                    // V0: блэклист тегов переехал в группу «Фильтры контента» выше.
                ),
            ),
            // Taste Learning Engine: выученный профиль вкуса и его сброс.
            // Профиль строится из лайков/добавлений/скрытий в ленте «Для вас».
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_taste_title),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_discovery_taste_title),
                        subtitle = run {
                            val signals = tasteSignalCount
                            if (signals == 0) {
                                stringResource(AYMR.strings.pref_discovery_taste_empty)
                            } else {
                                stringResource(AYMR.strings.pref_discovery_taste_signal_count, signals) +
                                    " · " + tasteTopGenres.joinToString { it.first }
                            }
                        },
                        icon = Icons.Outlined.AutoAwesome,
                        enabled = enabled,
                        onClick = {},
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_discovery_consumed_title),
                        subtitle = stringResource(AYMR.strings.pref_discovery_consumed_summary),
                        icon = Icons.Outlined.TaskAlt,
                        enabled = enabled,
                        onClick = {
                            showConsumedDialog = true
                            scope.launchIO { reloadConsumed() }
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_discovery_taste_reset),
                        subtitle = stringResource(AYMR.strings.pref_discovery_taste_summary),
                        icon = Icons.Outlined.RestartAlt,
                        enabled = enabled,
                        onClick = { showResetTasteDialog = true },
                    ),
                ),
            ),
        )
    }
}

/**
 * V1: диалог мультивыбора статусов выпуска. Дефолт — все включены (пустой CSV = без фильтра).
 * Пустой выбор запрещён: хотя бы один статус должен остаться включённым.
 */
@Composable
private fun ReleaseStatusFilterDialog(
    currentCsv: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = eu.kanade.presentation.theme.AuroraTheme.colors
    val initial = DiscoveryReleaseStatus.parseCsv(currentCsv)
    var selected by remember {
        mutableStateOf(if (initial.isEmpty()) DiscoveryReleaseStatus.entries.toSet() else initial)
    }

    AuroraFrostDialog(
        onDismiss = onDismiss,
        title = stringResource(AYMR.strings.pref_discovery_release_status),
        footer = {
            AuroraFrostCancel(
                label = stringResource(MR.strings.action_cancel),
                onClick = onDismiss,
            )
            AuroraFrostConfirm(
                label = stringResource(MR.strings.action_ok),
                onClick = {
                    // Пустой выбор = без фильтра (все статусы); но не даём выключить ВСЁ.
                    val csv = if (selected.size == DiscoveryReleaseStatus.entries.size) {
                        ""
                    } else {
                        selected.joinToString(",") { it.key }
                    }
                    onApply(csv)
                },
                enabled = selected.isNotEmpty(),
            )
        },
    ) {
        DiscoveryReleaseStatus.entries.forEach { status ->
            val label = when (status) {
                DiscoveryReleaseStatus.ONGOING -> stringResource(
                    AYMR.strings.pref_discovery_release_status_ongoing,
                ) to
                    stringResource(AYMR.strings.pref_discovery_release_status_ongoing_desc)
                DiscoveryReleaseStatus.FINISHED -> stringResource(
                    AYMR.strings.pref_discovery_release_status_finished,
                ) to
                    stringResource(AYMR.strings.pref_discovery_release_status_finished_desc)
                DiscoveryReleaseStatus.ANONS -> stringResource(
                    AYMR.strings.pref_discovery_release_status_anons,
                ) to
                    stringResource(AYMR.strings.pref_discovery_release_status_anons_desc)
                DiscoveryReleaseStatus.PAUSED -> stringResource(
                    AYMR.strings.pref_discovery_release_status_paused,
                ) to
                    stringResource(AYMR.strings.pref_discovery_release_status_paused_desc)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .clickable { selected = if (status in selected) selected - status else selected + status }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label.first,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        label.second,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }
                androidx.compose.material3.Checkbox(
                    checked = status in selected,
                    onCheckedChange = { checked ->
                        selected = if (checked) selected + status else selected - status
                    },
                    colors = androidx.compose.material3.CheckboxDefaults.colors(
                        checkedColor = colors.accent,
                        checkmarkColor = colors.textOnAccent,
                        uncheckedColor = colors.textSecondary,
                    ),
                )
            }
        }
        Text(
            stringResource(AYMR.strings.pref_discovery_release_status_hint),
            color = colors.textSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * V3: «Жанры подборки» — диалог на тёмном полупрозрачном Aurora-стекле: rim-light рамка,
 * сдержанная frost-подложка, табы режимов капсулой на всю ширину (эталон AuroraTabRow),
 * Haze Frost чипы в FlowRow, свободный ввод с резолвом через онтологию.
 * Все три режима (Игнор/Приоритет/Обязательные) равноправны: accent-окраска, чипы, ручной ввод.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreFilterDialog(
    priorityCsv: String,
    requiredCsv: String,
    ignoredCsv: String,
    onApply: (priorityCsv: String, requiredCsv: String, ignoredCsv: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = eu.kanade.presentation.theme.AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val shape = RoundedCornerShape(28.dp)

    val allGenres = remember {
        eu.kanade.tachiyomi.data.discovery.GenreOntology.displayNames.entries
            .sortedBy { (it.value.second ?: it.value.first).lowercase() }
            .toList()
    }
    // «Популярные» — курированный порядок реально частых жанров, НЕ take(N) по алфавиту.
    val popular = remember {
        listOf(
            "action", "adventure", "comedy", "drama", "fantasy",
            "romance", "sci-fi", "slice of life", "mystery", "thriller",
        ).mapNotNull { key -> allGenres.firstOrNull { it.key == key } }
    }

    var mode by remember { mutableStateOf(1) } // 0=ignored, 1=priority, 2=required
    var search by remember { mutableStateOf("") }

    val (priCanonInit, priRawInit) = remember { parseGenreFilterCsv(priorityCsv) }
    val (reqCanonInit, reqRawInit) = remember { parseGenreFilterCsv(requiredCsv) }
    val (ignCanonInit, ignRawInit) = remember { parseGenreFilterCsv(ignoredCsv) }
    // Snapshot-коллекции: add/remove триггерит рекомпозицию.
    val priorityCan = remember { mutableStateSetOf<String>().apply { addAll(priCanonInit) } }
    val priorityRaw = remember { priRawInit.toMutableStateList() }
    val requiredCan = remember { mutableStateSetOf<String>().apply { addAll(reqCanonInit) } }
    val requiredRaw = remember { reqRawInit.toMutableStateList() }
    val ignoredCan = remember { mutableStateSetOf<String>().apply { addAll(ignCanonInit) } }
    val ignoredRaw = remember { ignRawInit.toMutableStateList() }

    val activeCanon: MutableSet<String> = when (mode) {
        0 -> ignoredCan
        1 -> priorityCan
        else -> requiredCan
    }
    val activeRaw: MutableList<String> = when (mode) {
        0 -> ignoredRaw
        1 -> priorityRaw
        else -> requiredRaw
    }
    // «Занят другим режимом»: жанр состоит в любом из двух прочих наборов.
    val othersCanon: Set<String> = when (mode) {
        0 -> priorityCan + requiredCan
        1 -> ignoredCan + requiredCan
        else -> ignoredCan + priorityCan
    }
    val selectedColor = colors.accent

    val toggleCanon: (String) -> Unit = { key ->
        if (activeCanon.contains(key)) {
            activeCanon.remove(key)
        } else {
            // Сначала взаимоисключение: жанр вычищается из всех наборов (включая активный),
            // и только затем добавляется в активный. Иначе remove из активного набора
            // гасил только что сделанный add — чипы не подсвечивались.
            priorityCan.remove(key)
            requiredCan.remove(key)
            ignoredCan.remove(key)
            activeCanon.add(key)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Системный blur (API 31+): backgroundBlur — всё под окном диалога размывается
        // ВНУТРИ его границ и читается сквозь полупрозрачное стекло (настоящий frost);
        // blurBehind — размывает окружение вокруг диалога. Haze так не умеет (он блюрит
        // только внутри одной композиции). Ниже API 31 / без поддержки вендором радиусы
        // игнорируются — тёмное стекло + дим остаются фолбэком.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (!colors.isEInk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow?.let { w ->
                    runCatching { w.setBackgroundBlurRadius(48) }
                    val lp = w.attributes
                    lp.blurBehindRadius = 32
                    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    w.attributes = lp
                }
            }
            @Suppress("DEPRECATION")
            dialogWindow?.setDimAmount(if (colors.isDark) 0.35f else 0.45f)
        }
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .heightIn(max = 700.dp)
                .clip(shape)
                .border(1.dp, auroraMenuRimLightBrush(colors), shape)
                .background(
                    if (colors.isEInk) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else if (colors.isDark) {
                        if (colors.isAmoled) {
                            Color(0xFF08070C).copy(alpha = 0.80f)
                        } else {
                            Color(0xFF0E0C13).copy(alpha = 0.74f)
                        }
                    } else {
                        Color.White.copy(alpha = 0.78f)
                    },
                    shape,
                ),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (colors.isEInk) {
                            Modifier
                        } else {
                            // Frost-подложка: сдержанный молочный градиент — поверхность
                            // остаётся тёмной и полупрозрачной, без пятен и серости.
                            Modifier.background(
                                if (colors.isDark) {
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.015f)),
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.18f)),
                                    )
                                },
                                shape,
                            )
                        },
                    )
                    .padding(20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(AYMR.strings.pref_discovery_genre_filters),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Color.White.copy(alpha = if (colors.isDark) 0.08f else 0.40f),
                                CircleShape,
                            )
                            .clickable {
                                appHaptics.tap()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // Табы режимов: эталон AuroraTabRow (scrollable=false) — капсула-желоб на
                // всю ширину, табы weight(1f) по центру; активный — accent темы во всех режимах.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(
                            if (colors.isEInk) {
                                Color(0xFFEBEBEB)
                            } else if (colors.isDark) {
                                Color.White.copy(alpha = 0.045f)
                            } else {
                                Color.White.copy(alpha = 0.70f)
                            },
                            CircleShape,
                        )
                        .border(
                            1.dp,
                            if (colors.isEInk) {
                                colors.divider
                            } else if (colors.isDark) {
                                Color.White.copy(alpha = 0.08f)
                            } else {
                                Color.White.copy(alpha = 0.80f)
                            },
                            CircleShape,
                        )
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        Triple(
                            0,
                            stringResource(AYMR.strings.pref_discovery_genre_mode_ignored),
                            ignoredCan.size + ignoredRaw.size,
                        ),
                        Triple(
                            1,
                            stringResource(AYMR.strings.pref_discovery_genre_tab_priority),
                            priorityCan.size + priorityRaw.size,
                        ),
                        Triple(
                            2,
                            stringResource(AYMR.strings.pref_discovery_genre_tab_required),
                            requiredCan.size + requiredRaw.size,
                        ),
                    ).forEach { (m, label, count) ->
                        val selected = mode == m
                        val tabBg = when {
                            colors.isEInk -> {
                                val c = if (selected) colors.textPrimary else Color.Transparent
                                Brush.verticalGradient(listOf(c, c))
                            }
                            selected -> Brush.verticalGradient(
                                listOf(
                                    selectedColor.copy(alpha = if (colors.isDark) 0.28f else 0.22f),
                                    selectedColor.copy(alpha = if (colors.isDark) 0.10f else 0.08f),
                                ),
                            )
                            else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                        val tabBorder = when {
                            colors.isEInk -> {
                                val c = if (selected) colors.textPrimary else Color.Transparent
                                Brush.verticalGradient(listOf(c, c))
                            }
                            selected -> Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (colors.isDark) 0.50f else 0.85f),
                                    selectedColor.copy(alpha = if (colors.isDark) 0.25f else 0.35f),
                                ),
                            )
                            else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(tabBg, CircleShape)
                                .border(1.dp, tabBorder, CircleShape)
                                .clickable {
                                    appHaptics.tap()
                                    mode = m
                                }
                                // Горизонталь чуть плотнее: иначе лейбл+точка+бейдж
                                // счётчика не влезают в треть ширины диалога.
                                .padding(horizontal = 6.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        ) {
                            if (selected && !colors.isEInk) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(selectedColor),
                                )
                            }
                            Text(
                                label,
                                // weight(1f, fill=false): Row сначала меряет не-взвешенных
                                // (точку и бейдж счётчика), лейбл берёт остаток — бейдж
                                // больше не схлопывается в ноль на узких табах.
                                modifier = Modifier.weight(1f, fill = false),
                                color = if (selected) {
                                    if (colors.isEInk) {
                                        colors.background
                                    } else if (colors.isDark) {
                                        Color.White
                                    } else {
                                        selectedColor
                                    }
                                } else {
                                    colors.textSecondary
                                },
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (count > 0) {
                                Box(
                                    Modifier
                                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (colors.isEInk) {
                                                if (selected) colors.background else colors.textPrimary
                                            } else if (selected) {
                                                selectedColor
                                            } else {
                                                Color.White.copy(alpha = if (colors.isDark) 0.12f else 0.25f)
                                            },
                                            CircleShape,
                                        )
                                        .padding(horizontal = 5.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        count.toString(),
                                        color = if (colors.isEInk) {
                                            if (selected) colors.textPrimary else colors.background
                                        } else if (selected) {
                                            colors.textOnAccent
                                        } else {
                                            colors.textSecondary
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (colors.isEInk) {
                                Color(0xFFF7F7F7)
                            } else {
                                selectedColor.copy(alpha = if (colors.isDark) 0.10f else 0.08f)
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = if (colors.isEInk) colors.textPrimary else selectedColor,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = when (mode) {
                            0 -> stringResource(AYMR.strings.pref_discovery_genre_hint_ignored)
                            1 -> stringResource(AYMR.strings.pref_discovery_genre_hint_priority)
                            else -> stringResource(AYMR.strings.pref_discovery_genre_hint_required)
                        },
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = colors.textSecondary,
                    )
                }

                // Скроллируемая область: поиск, чипы, ручной ввод, raw-чипы.
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(12.dp))
                    // Единое компактное поле: поиск по онтологии; если совпадений нет —
                    // под списком появляется аффорданс добавления запроса в активный режим.
                    // Своя frost-пилла с BasicTextField: M3 OutlinedTextField верстается под
                    // минимальные 56dp и при жёсткой высоте сдвигает/обрезает строку.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(
                                if (colors.isEInk) {
                                    Brush.verticalGradient(listOf(Color.White, Color.White))
                                } else if (colors.isDark) {
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.03f)),
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.50f)),
                                    )
                                },
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                when {
                                    colors.isEInk -> colors.divider
                                    search.isNotBlank() -> selectedColor.copy(alpha = 0.60f)
                                    colors.isDark -> Color.White.copy(alpha = 0.10f)
                                    else -> Color.Black.copy(alpha = 0.08f)
                                },
                                CircleShape,
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = if (search.isNotBlank()) selectedColor else colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Box(Modifier.weight(1f)) {
                            if (search.isEmpty()) {
                                Text(
                                    stringResource(AYMR.strings.pref_discovery_genre_search),
                                    color = colors.textSecondary.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = search,
                                onValueChange = { search = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                cursorBrush = SolidColor(selectedColor),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (search.isNotBlank()) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { search = "" },
                            )
                        }
                    }

                    val chipGenres = if (search.isBlank()) {
                        popular
                    } else {
                        val q = search.trim().lowercase()
                        allGenres.filter { (key, names) ->
                            key.contains(q) ||
                                names.first.lowercase().contains(q) ||
                                (names.second?.lowercase()?.contains(q) == true)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (search.isBlank()) {
                        Text(
                            stringResource(AYMR.strings.pref_discovery_genre_popular),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (chipGenres.isEmpty()) {
                        Text(
                            stringResource(AYMR.strings.pref_discovery_genre_empty),
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                        // Совпадений нет — предлагаем добавить запрос как есть в активный
                        // режим: каноническим ключом, если матчер резолвит, иначе raw-строкой.
                        if (search.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                selectedColor.copy(alpha = if (colors.isDark) 0.16f else 0.10f),
                                                selectedColor.copy(alpha = if (colors.isDark) 0.06f else 0.04f),
                                            ),
                                        ),
                                        CircleShape,
                                    )
                                    .border(1.dp, selectedColor.copy(alpha = 0.40f), CircleShape)
                                    .clickable {
                                        appHaptics.tap()
                                        val (resolved, isCanonical) = resolveUserGenreInput(search)
                                        if (resolved.isNotBlank()) {
                                            if (isCanonical) {
                                                toggleCanon(resolved)
                                            } else {
                                                activeRaw.add(resolved)
                                            }
                                        }
                                        search = ""
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = selectedColor,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    stringResource(AYMR.strings.pref_discovery_genre_add_custom, search.trim()),
                                    color = selectedColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            chipGenres.forEach { (key, names) ->
                                GenreFrostChip(
                                    label = genreDisplayLabel(names),
                                    selected = key in activeCanon,
                                    occupied = key in othersCanon,
                                    selectedColor = selectedColor,
                                    enabled = true,
                                    onClick = { toggleCanon(key) },
                                )
                            }
                        }
                    }

                    if (activeRaw.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            activeRaw.forEach { rawGenre ->
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    selectedColor.copy(alpha = if (colors.isDark) 0.12f else 0.08f),
                                                    selectedColor.copy(alpha = if (colors.isDark) 0.04f else 0.03f),
                                                ),
                                            ),
                                            CircleShape,
                                        )
                                        .border(1.dp, selectedColor.copy(alpha = 0.30f), CircleShape)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text("«»", fontSize = 9.sp, color = selectedColor.copy(alpha = 0.7f))
                                    Text(
                                        rawGenre,
                                        color = selectedColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                appHaptics.tap()
                                                activeRaw.remove(rawGenre)
                                            },
                                    )
                                }
                            }
                        }
                    }
                }

                // Кнопки действий закреплены внутри окна (вне скролла).
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colors.textPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        modifier = Modifier
                            .background(
                                if (colors.isEInk) {
                                    Brush.verticalGradient(listOf(Color.White, Color.White))
                                } else {
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (colors.isDark) 0.09f else 0.72f),
                                            Color.White.copy(alpha = if (colors.isDark) 0.04f else 0.50f),
                                        ),
                                    )
                                },
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                if (colors.isEInk) {
                                    colors.divider
                                } else {
                                    Color.White.copy(alpha = if (colors.isDark) 0.12f else 0.40f)
                                },
                                CircleShape,
                            ),
                    ) {
                        Text(
                            stringResource(MR.strings.action_cancel),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                    Button(
                        onClick = {
                            onApply(
                                serializeGenreFilterCsv(priorityCan.toSet(), priorityRaw.toList()),
                                serializeGenreFilterCsv(requiredCan.toSet(), requiredRaw.toList()),
                                serializeGenreFilterCsv(ignoredCan.toSet(), ignoredRaw.toList()),
                            )
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (colors.isEInk) colors.textPrimary else colors.accent,
                            contentColor = colors.textOnAccent,
                        ),
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
                    ) {
                        Text(
                            stringResource(MR.strings.action_ok),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Лейбл жанра для чипа: RU-название, если оно короткое и человекоподобное;
 * иначе EN; иначе ключ. Часть RU-значений онтологии — wikidata-описания
 * («аниме или манга в жанре ужасы») — такие на чипах не показываем.
 */
private fun genreDisplayLabel(names: Pair<String, String?>): String {
    val ru = names.second
    if (ru != null && ru.length <= 20 && ru.split(' ').size <= 2) return ru
    val en = names.first
    if (en.length <= 24 && en.split(' ').size <= 3) return en
    return ru ?: en
}

/**
 * Haze Frost чип жанра (рецепт FeedTabs): стеклянный градиент, градиентная рамка,
 * точка-индикатор у выбранных; «занят другим режимом» — приглушён, без клика.
 */
@Composable
private fun GenreFrostChip(
    label: String,
    selected: Boolean,
    occupied: Boolean,
    selectedColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = eu.kanade.presentation.theme.AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val shape = CircleShape

    val bgBrush = when {
        colors.isEInk -> {
            val c = when {
                selected -> colors.textPrimary
                occupied -> Color(0xFFE5E5E5)
                else -> Color.White
            }
            Brush.verticalGradient(listOf(c, c))
        }
        selected -> Brush.verticalGradient(
            listOf(
                selectedColor.copy(alpha = if (colors.isDark) 0.26f else 0.20f),
                selectedColor.copy(alpha = if (colors.isDark) 0.10f else 0.06f),
            ),
        )
        occupied -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (colors.isDark) 0.035f else 0.40f),
                Color.White.copy(alpha = if (colors.isDark) 0.015f else 0.25f),
            ),
        )
        else -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (colors.isDark) 0.09f else 0.72f),
                Color.White.copy(alpha = if (colors.isDark) 0.03f else 0.48f),
            ),
        )
    }

    val borderBrush = when {
        colors.isEInk -> {
            val c = if (selected) colors.textPrimary else colors.divider
            Brush.verticalGradient(listOf(c, c))
        }
        selected -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (colors.isDark) 0.60f else 0.85f),
                selectedColor.copy(alpha = if (colors.isDark) 0.35f else 0.40f),
            ),
        )
        occupied -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (colors.isDark) 0.06f else 0.20f),
                Color.White.copy(alpha = if (colors.isDark) 0.02f else 0.10f),
            ),
        )
        else -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (colors.isDark) 0.20f else 0.85f),
                Color.White.copy(alpha = if (colors.isDark) 0.05f else 0.25f),
            ),
        )
    }

    val textColor = when {
        colors.isEInk -> {
            if (selected) {
                colors.background
            } else if (occupied) {
                colors.textSecondary.copy(alpha = 0.5f)
            } else {
                colors.textPrimary
            }
        }
        selected -> {
            if (colors.isDark) Color.White else selectedColor
        }
        occupied -> colors.textSecondary.copy(alpha = 0.45f)
        else -> colors.textSecondary
    }

    Row(
        Modifier
            .clip(shape)
            .background(bgBrush, shape)
            .border(1.dp, borderBrush, shape)
            .then(
                if (occupied || !enabled) {
                    Modifier
                } else {
                    Modifier.clickable {
                        appHaptics.tap()
                        onClick()
                    }
                },
            )
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected || occupied) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (colors.isEInk) {
                            if (selected) colors.background else colors.textSecondary
                        } else if (selected) {
                            selectedColor
                        } else {
                            selectedColor.copy(alpha = 0.40f)
                        },
                    ),
            )
        }
        Text(
            label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
