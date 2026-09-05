package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun DownloadsBadge(count: Long) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun UnviewedBadge(count: Long) {
    // РЕШ-4 revival: the "unread badge" library preference (display_unread_badge) was collected
    // into ItemPreferences by the manga and anime screen models but never READ - their badges
    // rendered unconditionally in every display mode (only the novel side gated its own badge
    // state). Gate at the shared choke point; idempotent with the novel-side pre-gating.
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val showUnreadBadge by libraryPreferences.unreadBadge().collectAsStateWithLifecycle()
    if (showUnreadBadge && count > 0) {
        Badge(text = "$count")
    }
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
) {
    if (isLocal) {
        Badge(
            imageVector = Icons.Outlined.Folder,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else if (sourceLanguage.isNotEmpty()) {
        Badge(
            text = sourceLanguage.uppercase(),
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun PinnedBadge() {
    Badge(
        imageVector = Icons.Filled.PushPin,
        color = MaterialTheme.colorScheme.primary,
        iconColor = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
internal fun PinnedSectionHeader(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(AYMR.strings.aurora_source_pinned),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@PreviewLightDark
@Composable
private fun BadgePreview() {
    TachiyomiPreviewTheme {
        Column {
            DownloadsBadge(count = 10)
            UnviewedBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "EN")
            LanguageBadge(isLocal = false, sourceLanguage = "EN")
            PinnedBadge()
        }
    }
}
