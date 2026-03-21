package eu.kanade.presentation.more.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.StringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun PreferenceScaffold(
    titleRes: StringResource,
    uiStyle: SettingsUiStyle = SettingsUiStyle.Classic,
    actions: @Composable RowScope.() -> Unit = {},
    onBackPressed: (() -> Unit)? = null,
    itemsProvider: @Composable () -> List<Preference>,
) {
    val state = rememberLazyListState()
    SettingsScaffold(
        title = stringResource(titleRes),
        uiStyle = uiStyle,
        onBackPressed = onBackPressed,
        actions = actions,
        topBarCanScroll = { state.canScroll() },
    ) { contentPadding ->
        PreferenceScreen(
            items = itemsProvider(),
            state = state,
            contentPadding = contentPadding,
        )
    }
}
