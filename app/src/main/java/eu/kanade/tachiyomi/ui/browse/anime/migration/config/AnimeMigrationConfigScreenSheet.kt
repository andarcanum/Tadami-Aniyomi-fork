package eu.kanade.tachiyomi.ui.browse.anime.migration.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.collectAsState

// Same bit values as AnimeMigrationFlags
private const val EPISODES = 0b00001
private const val CATEGORIES = 0b00010
private const val CUSTOM_BACKGROUND = 0b00100
private const val CUSTOM_COVER = 0b01000
private const val DELETE_DOWNLOADED = 0b10000

@Composable
fun AnimeMigrationConfigScreenSheet(
    preferences: SourcePreferences,
    onDismissRequest: () -> Unit,
    onStartMigration: (extraSearchQuery: String?) -> Unit,
) {
    var extraSearchQuery by rememberSaveable { mutableStateOf("") }
    val migrationFlags by preferences.migrationFlagsAnime().collectAsState()

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = MaterialTheme.padding.medium),
            ) {
                Text(
                    text = stringResource(MR.strings.action_migrate),
                    style = MaterialTheme.typography.header,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium),
                )
                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    MigrationFlagChip(
                        selected = migrationFlags and EPISODES != 0,
                        label = stringResource(AYMR.strings.episodes),
                        onClick = {
                            preferences.migrationFlagsAnime().set(migrationFlags xor EPISODES)
                        },
                    )
                    MigrationFlagChip(
                        selected = migrationFlags and CATEGORIES != 0,
                        label = stringResource(MR.strings.categories),
                        onClick = {
                            preferences.migrationFlagsAnime().set(migrationFlags xor CATEGORIES)
                        },
                    )
                    MigrationFlagChip(
                        selected = migrationFlags and CUSTOM_COVER != 0,
                        label = stringResource(MR.strings.custom_cover),
                        onClick = {
                            preferences.migrationFlagsAnime().set(migrationFlags xor CUSTOM_COVER)
                        },
                    )
                    MigrationFlagChip(
                        selected = migrationFlags and CUSTOM_BACKGROUND != 0,
                        label = stringResource(AYMR.strings.custom_background),
                        onClick = {
                            preferences.migrationFlagsAnime().set(migrationFlags xor CUSTOM_BACKGROUND)
                        },
                    )
                    MigrationFlagChip(
                        selected = migrationFlags and DELETE_DOWNLOADED != 0,
                        label = stringResource(MR.strings.delete_downloaded),
                        onClick = {
                            preferences.migrationFlagsAnime().set(migrationFlags xor DELETE_DOWNLOADED)
                        },
                    )
                }

                OutlinedTextField(
                    value = extraSearchQuery,
                    onValueChange = { extraSearchQuery = it },
                    label = { Text(text = stringResource(MR.strings.migration_extra_search_query)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                )

                MigrationSwitchItem(
                    title = stringResource(MR.strings.migration_hide_not_found),
                    subtitle = null,
                    preference = preferences.migrationHideNotFound(),
                )
                MigrationSwitchItem(
                    title = stringResource(MR.strings.migration_only_new_chapters),
                    subtitle = null,
                    preference = preferences.migrationOnlyNewChapters(),
                )
                MigrationSwitchItem(
                    title = stringResource(MR.strings.migration_skip_next_time),
                    subtitle = null,
                    preference = preferences.migrationSkipNextTime(),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall))
                MigrationWarningItem(text = stringResource(MR.strings.migration_advanced_options_warning))
                MigrationSwitchItem(
                    title = stringResource(MR.strings.migration_deep_search_mode),
                    subtitle = null,
                    preference = preferences.migrationDeepSearchMode(),
                )
                MigrationSwitchItem(
                    title = stringResource(MR.strings.migration_extra_search_param),
                    subtitle = null,
                    preference = preferences.migrationExtraSearchParam(),
                )

                Text(
                    text = stringResource(MR.strings.migration_strategy),
                    style = MaterialTheme.typography.header,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                )
                val prioritizeByChapters by preferences.migrationPrioritizeByChapters().collectAsState()
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    MigrationFlagChip(
                        selected = !prioritizeByChapters,
                        label = stringResource(MR.strings.migration_strategy_first_source),
                        onClick = {
                            preferences.migrationPrioritizeByChapters().set(false)
                        },
                    )
                    MigrationFlagChip(
                        selected = prioritizeByChapters,
                        label = stringResource(MR.strings.migration_strategy_most_chapters),
                        onClick = {
                            preferences.migrationPrioritizeByChapters().set(true)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
            }
            HorizontalDivider()
            Button(
                onClick = {
                    val cleanedExtraSearchQuery = extraSearchQuery.trim().ifBlank { null }
                    onStartMigration(cleanedExtraSearchQuery)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
            ) {
                Text(text = stringResource(AYMR.strings.action_continue))
            }
        }
    }
}

@Composable
private fun MigrationFlagChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            if (selected) {
                Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
            }
        },
    )
}

@Composable
private fun MigrationSwitchItem(
    title: String,
    subtitle: String?,
    preference: Preference<Boolean>,
) {
    val checked by preference.collectAsState()
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = subtitle?.let { { Text(text = subtitle) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { preference.set(it) },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(horizontal = MaterialTheme.padding.small)
            .clickable { preference.set(!checked) },
    )
}

@Composable
private fun MigrationWarningItem(text: String) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.active,
            )
        },
        headlineContent = {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.error,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
