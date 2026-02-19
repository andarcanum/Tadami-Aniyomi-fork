package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionFilterState
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun NovelExtensionFilterScreen(
    navigateUp: () -> Unit,
    state: NovelExtensionFilterState.Success,
    onClickToggle: (String) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extensions),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }
        NovelExtensionFilterContent(
            contentPadding = contentPadding,
            state = state,
            onClickLang = onClickToggle,
        )
    }
}

@Composable
private fun NovelExtensionFilterContent(
    contentPadding: PaddingValues,
    state: NovelExtensionFilterState.Success,
    onClickLang: (String) -> Unit,
) {
    val context = LocalContext.current
    val languages = state.languages.mapNotNull { language ->
        val displayName = LocaleHelper.getSourceDisplayName(language, context)
        if (displayName.isBlank()) null else language to displayName
    }
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items(languages) { (language, displayName) ->
            SwitchPreferenceWidget(
                modifier = Modifier.animateItem(),
                title = displayName,
                checked = language in state.enabledLanguages,
                onCheckedChanged = { onClickLang(language) },
            )
        }
    }
}
