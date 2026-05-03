package eu.kanade.presentation.more.settings.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.novellist.NovelList
import eu.kanade.tachiyomi.data.track.novelupdates.NovelUpdates
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.data.track.simkl.SimklApi
import eu.kanade.tachiyomi.data.track.trakt.TraktApi
import eu.kanade.tachiyomi.ui.webview.TrackerWebViewLoginActivity
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.json.Json
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTrackingScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_tracking

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        val uiStyle = rememberResolvedSettingsUiStyle()
        if (uiStyle == SettingsUiStyle.Aurora) {
            AuroraTopBarIconButton(
                onClick = { uriHandler.openUri("https://aniyomi.org/help/guides/tracking/") },
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        } else {
            IconButton(onClick = { uriHandler.openUri("https://aniyomi.org/help/guides/tracking/") }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(MR.strings.tracking_guide),
                )
            }
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val trackPreferences = remember { Injekt.get<TrackPreferences>() }
        val trackerManager = remember { Injekt.get<TrackerManager>() }
        val mangaSourceManager = remember { Injekt.get<MangaSourceManager>() }
        val animeSourceManager = remember { Injekt.get<AnimeSourceManager>() }

        var dialog by remember { mutableStateOf<Any?>(null) }
        dialog?.run {
            when (this) {
                is LoginDialog -> {
                    TrackingLoginDialog(
                        tracker = tracker,
                        uNameStringRes = uNameStringRes,
                        onDismissRequest = { dialog = null },
                    )
                }
                is LogoutDialog -> {
                    TrackingLogoutDialog(
                        tracker = tracker,
                        onDismissRequest = { dialog = null },
                    )
                }
                NovelUpdatesListMappingDialog -> {
                    NovelUpdatesListMappingDialogContent(
                        trackerManager = trackerManager,
                        trackPreferences = trackPreferences,
                        onDismissRequest = { dialog = null },
                    )
                }
            }
        }

        val enhancedMangaTrackers = trackerManager.trackers
            .filter { it is EnhancedMangaTracker }
            .partition { service ->
                val acceptedMangaSources = (service as EnhancedMangaTracker).getAcceptedSources()
                mangaSourceManager.getCatalogueSources().any { it::class.qualifiedName in acceptedMangaSources }
            }
        val enhancedAnimeTrackers = trackerManager.trackers
            .filter { it is EnhancedAnimeTracker }
            .partition { service ->
                val acceptedAnimeSources = (service as EnhancedAnimeTracker).getAcceptedSources()
                animeSourceManager.getCatalogueSources().any { it::class.qualifiedName in acceptedAnimeSources }
            }

        var enhancedTrackerInfo = stringResource(MR.strings.enhanced_tracking_info)
        if (enhancedMangaTrackers.second.isNotEmpty() || enhancedAnimeTrackers.second.isNotEmpty()) {
            val missingSourcesInfo = stringResource(
                MR.strings.enhanced_services_not_installed,
                (enhancedMangaTrackers.second + enhancedAnimeTrackers.second).joinToString { it.name },
            )
            enhancedTrackerInfo += "\n\n$missingSourcesInfo"
        }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.autoUpdateTrack(),
                title = stringResource(AYMR.strings.pref_auto_update_manga_sync),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.trackOnAddingToLibrary(),
                title = stringResource(AYMR.strings.pref_track_on_add_library),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.showNextEpisodeAiringTime(),
                title = stringResource(AYMR.strings.pref_show_next_episode_airing_time),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = trackPreferences.autoUpdateTrackOnMarkRead(),
                entries = AutoTrackState.entries
                    .associateWith { stringResource(it.titleRes) }
                    .toPersistentMap(),
                title = stringResource(AYMR.strings.pref_auto_update_manga_on_mark_read),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.autoSyncProgressFromTracker(),
                title = stringResource(MR.strings.pref_auto_sync_progress_from_tracker),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.novel_trackers_title),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.novelUpdates,
                        login = {
                            context.startActivity(
                                TrackerWebViewLoginActivity.newIntent(
                                    context = context,
                                    trackerId = trackerManager.novelUpdates.id,
                                    trackerName = trackerManager.novelUpdates.name,
                                    loginUrl = "https://www.novelupdates.com/",
                                ),
                            )
                        },
                        logout = { dialog = LogoutDialog(trackerManager.novelUpdates) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.novelList,
                        login = {
                            context.startActivity(
                                TrackerWebViewLoginActivity.newIntent(
                                    context = context,
                                    trackerId = trackerManager.novelList.id,
                                    trackerName = trackerManager.novelList.name,
                                    loginUrl = NovelList.LOGIN_URL,
                                ),
                            )
                        },
                        logout = { dialog = LogoutDialog(trackerManager.novelList) },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = trackPreferences.novelUpdatesUseCustomListMapping(),
                        title = stringResource(AYMR.strings.novel_updates_use_custom_list_mapping_title),
                        subtitle = stringResource(AYMR.strings.novel_updates_use_custom_list_mapping_summary),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.novel_updates_configure_list_mapping_title),
                        subtitle = stringResource(AYMR.strings.novel_updates_configure_list_mapping_summary),
                        onClick = { dialog = NovelUpdatesListMappingDialog },
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        stringResource(AYMR.strings.novel_trackers_info),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.services),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.myAnimeList,
                        login = {
                            context.openInBrowser(
                                MyAnimeListApi.authUrl(),
                                forceDefaultBrowser = true,
                            )
                        },
                        logout = { dialog = LogoutDialog(trackerManager.myAnimeList) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.aniList,
                        login = {
                            context.openInBrowser(
                                AnilistApi.authUrl(),
                                forceDefaultBrowser = true,
                            )
                        },
                        logout = { dialog = LogoutDialog(trackerManager.aniList) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.kitsu,
                        login = { dialog = LoginDialog(trackerManager.kitsu, MR.strings.email) },
                        logout = { dialog = LogoutDialog(trackerManager.kitsu) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.mangaUpdates,
                        login = { dialog = LoginDialog(trackerManager.mangaUpdates, MR.strings.username) },
                        logout = { dialog = LogoutDialog(trackerManager.mangaUpdates) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.shikimori,
                        login = {
                            context.openInBrowser(
                                ShikimoriApi.authUrl(),
                                forceDefaultBrowser = true,
                            )
                        },
                        logout = { dialog = LogoutDialog(trackerManager.shikimori) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.simkl,
                        login = {
                            context.openInBrowser(
                                SimklApi.authUrl(),
                                forceDefaultBrowser = true,
                            )
                        },
                        logout = { dialog = LogoutDialog(trackerManager.simkl) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.bangumi,
                        login = {
                            context.openInBrowser(
                                BangumiApi.authUrl(),
                                forceDefaultBrowser = true,
                            )
                        },
                        logout = { dialog = LogoutDialog(trackerManager.bangumi) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.trakt,
                        login = {
                            context.openInBrowser(
                                TraktApi.authUrl(),
                                forceDefaultBrowser = true,
                            )
                        },
                        logout = { dialog = LogoutDialog(trackerManager.trakt) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.tmdb,
                        login = {
                            scope.launchIO {
                                try {
                                    val authUrl = trackerManager.tmdb.getAuthUrl()
                                    withUIContext {
                                        context.openInBrowser(authUrl, forceDefaultBrowser = true)
                                    }
                                } catch (e: Exception) {
                                    withUIContext {
                                        context.toast(e.message ?: "TMDB auth failed")
                                    }
                                }
                            }
                        },
                        logout = { dialog = LogoutDialog(trackerManager.tmdb) },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.tracking_info)),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.enhanced_services),
                preferenceItems = (
                    enhancedMangaTrackers.first
                        .map { service ->
                            Preference.PreferenceItem.TrackerPreference(
                                tracker = service,
                                login = { (service as EnhancedMangaTracker).loginNoop() },
                                logout = service::logout,
                            )
                        } +
                        enhancedAnimeTrackers.first
                            .map { service ->
                                Preference.PreferenceItem.TrackerPreference(
                                    tracker = service,
                                    login = { (service as EnhancedAnimeTracker).loginNoop() },
                                    logout = service::logout,
                                )
                            } + listOf(Preference.PreferenceItem.InfoPreference(enhancedTrackerInfo))
                    ).toImmutableList(),
            ),
        )
    }

    @Composable
    private fun TrackingLoginDialog(
        tracker: Tracker,
        uNameStringRes: StringResource,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var username by remember { mutableStateOf(TextFieldValue(tracker.getUsername())) }
        var password by remember { mutableStateOf(TextFieldValue(tracker.getPassword())) }
        var processing by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(MR.strings.login_title, tracker.name),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(text = stringResource(uNameStringRes)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        isError = inputError && !processing,
                    )

                    var hidePassword by remember { mutableStateOf(true) }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(text = stringResource(MR.strings.password)) },
                        trailingIcon = {
                            IconButton(onClick = { hidePassword = !hidePassword }) {
                                Icon(
                                    imageVector = if (hidePassword) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        visualTransformation = if (hidePassword) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        singleLine = true,
                        isError = inputError && !processing,
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !processing && username.text.isNotBlank() && password.text.isNotBlank(),
                    onClick = {
                        scope.launchIO {
                            processing = true
                            val result = checkLogin(
                                context = context,
                                tracker = tracker,
                                username = username.text,
                                password = password.text,
                            )
                            inputError = !result
                            if (result) onDismissRequest()
                            processing = false
                        }
                    },
                ) {
                    val id = if (processing) MR.strings.loading else MR.strings.login
                    Text(text = stringResource(id))
                }
            },
        )
    }

    private suspend fun checkLogin(
        context: Context,
        tracker: Tracker,
        username: String,
        password: String,
    ): Boolean {
        return try {
            tracker.login(username, password)
            withUIContext { context.toast(MR.strings.login_success) }
            true
        } catch (e: Throwable) {
            tracker.logout()
            withUIContext { context.toast(e.message.toString()) }
            false
        }
    }

    @Composable
    private fun TrackingLogoutDialog(
        tracker: Tracker,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(MR.strings.logout_title, tracker.name),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismissRequest,
                    ) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            tracker.logout()
                            onDismissRequest()
                            context.toast(MR.strings.logout_success)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.logout))
                    }
                }
            },
        )
    }

    @Composable
    private fun NovelUpdatesListMappingDialogContent(
        trackerManager: TrackerManager,
        trackPreferences: TrackPreferences,
        onDismissRequest: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val failedRefreshMessage = stringResource(AYMR.strings.novel_updates_list_mapping_failed)
        val defaultLists = listOf(
            "0" to stringResource(MR.strings.reading),
            "1" to stringResource(MR.strings.completed),
            "2" to stringResource(MR.strings.plan_to_read),
            "3" to stringResource(MR.strings.on_hold),
            "4" to stringResource(MR.strings.dropped),
        )

        var availableLists by remember {
            val cached = trackPreferences.novelUpdatesCachedLists().get()
            val lists = runCatching {
                if (cached.isNotBlank() && cached != "[]") {
                    Json.decodeFromString<List<List<String>>>(cached)
                        .mapNotNull { entry ->
                            val listId = entry.getOrNull(0)?.takeIf { it.isNotBlank() }
                            val listName = entry.getOrNull(1)?.takeIf { it.isNotBlank() }
                            if (listId != null && listName != null) {
                                listId to listName
                            } else {
                                null
                            }
                        }
                } else {
                    emptyList()
                }
            }.getOrDefault(emptyList())

            mutableStateOf(if (lists.isEmpty()) defaultLists else lists)
        }

        var mappings by remember {
            val json = trackPreferences.novelUpdatesCustomListMapping().get()
            val map = runCatching {
                if (json.isNotBlank() && json != "{}") {
                    Json.decodeFromString<Map<String, String>>(json)
                } else {
                    defaultNovelUpdatesMapping()
                }
            }.getOrDefault(defaultNovelUpdatesMapping())

            mutableStateOf(map)
        }

        var isLoading by remember { mutableStateOf(false) }

        val statuses = listOf(
            NovelUpdates.READING.toString() to stringResource(MR.strings.reading),
            NovelUpdates.COMPLETED.toString() to stringResource(MR.strings.completed),
            NovelUpdates.ON_HOLD.toString() to stringResource(MR.strings.on_hold),
            NovelUpdates.DROPPED.toString() to stringResource(MR.strings.dropped),
            NovelUpdates.PLAN_TO_READ.toString() to stringResource(MR.strings.plan_to_read),
        )

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(AYMR.strings.novel_updates_list_mapping_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(AYMR.strings.novel_updates_list_mapping_loaded, availableLists.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = {
                                isLoading = true
                                scope.launchIO {
                                    val lists = trackerManager.novelUpdates.getAvailableReadingLists()
                                    if (lists.isNotEmpty()) {
                                        trackPreferences.novelUpdatesCachedLists().set(
                                            Json.encodeToString(
                                                lists.map { listOf(it.first, it.second) },
                                            ),
                                        )
                                        trackPreferences.novelUpdatesLastListRefresh().set(
                                            System.currentTimeMillis(),
                                        )
                                    }
                                    withUIContext {
                                        isLoading = false
                                        if (lists.isNotEmpty()) {
                                            availableLists = lists
                                        }
                                        if (lists.isEmpty()) {
                                            context.toast(failedRefreshMessage)
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Text(stringResource(AYMR.strings.novel_updates_list_mapping_refresh))
                            }
                        }
                    }

                    HorizontalDivider()

                    statuses.forEach { (statusId, statusName) ->
                        var expanded by remember { mutableStateOf(false) }
                        val selectedListId = mappings[statusId] ?: "0"
                        val selectedName = availableLists.find { it.first == selectedListId }?.second
                            ?: stringResource(AYMR.strings.novel_updates_list_mapping_unknown_list, selectedListId)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = statusName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.4f),
                            )
                            Box(modifier = Modifier.weight(0.6f)) {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        selectedName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    availableLists.forEach { (listId, listName) ->
                                        DropdownMenuItem(
                                            text = { Text(listName) },
                                            onClick = {
                                                mappings = mappings.toMutableMap().apply {
                                                    put(statusId, listId)
                                                }
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        trackPreferences.novelUpdatesCustomListMapping().set(
                            Json.encodeToString(mappings),
                        )
                        onDismissRequest()
                    },
                ) {
                    Text(stringResource(MR.strings.action_save))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    private fun defaultNovelUpdatesMapping() = mapOf(
        NovelUpdates.READING.toString() to "0",
        NovelUpdates.COMPLETED.toString() to "1",
        NovelUpdates.ON_HOLD.toString() to "3",
        NovelUpdates.DROPPED.toString() to "4",
        NovelUpdates.PLAN_TO_READ.toString() to "2",
    )
}

private data class LoginDialog(
    val tracker: Tracker,
    val uNameStringRes: StringResource,
)

private data class LogoutDialog(
    val tracker: Tracker,
)

private data object NovelUpdatesListMappingDialog
