package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.LocalSettingsUiStyle
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.settingsAccentColor

@Composable
fun PreferenceGroupHeader(title: String) {
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isAurora) 8.dp else 8.dp, top = if (isAurora) 26.dp else 14.dp),
    ) {
        Text(
            text = title,
            color = if (isAurora) settingsAccentColor().copy(alpha = 0.92f) else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(
                horizontal = PrefsHorizontalPadding,
                vertical = 0.dp,
            ),
            style = if (isAurora) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (isAurora) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
