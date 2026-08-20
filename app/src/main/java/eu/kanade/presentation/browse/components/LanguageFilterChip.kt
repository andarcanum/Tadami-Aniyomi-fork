package eu.kanade.presentation.browse.components

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AuroraCheckboxItem
import eu.kanade.presentation.components.applyAuroraSheetWindowFx
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageFilterChip(
    languageFilter: ImmutableSet<String>,
    availableLanguages: ImmutableSet<String>,
    onChangeLanguageFilter: (Set<String>) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }

    FilterChip(
        selected = languageFilter.isNotEmpty(),
        onClick = { showSheet = true },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        label = {
            Text(
                text = if (languageFilter.isEmpty()) {
                    stringResource(MR.strings.all_languages)
                } else {
                    languageFilter.joinToString(", ") { LocaleHelper.getLocalizedDisplayName(it) }
                },
            )
        },
    )

    if (showSheet) {
        val colors = AuroraTheme.colors
        val supportsBlurBehind = eu.kanade.presentation.util.rememberSupportsBlurBehind(colors.isEInk)
        var sheetReveal by remember { mutableFloatStateOf(1f) }

        AdaptiveSheet(
            onDismissRequest = { showSheet = false },
            containerColor = when {
                colors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
                !supportsBlurBehind -> colors.surface
                colors.isDark -> Color.Black.copy(alpha = 0.72f)
                else -> Color.White.copy(alpha = 0.90f)
            },
            scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
            onRevealChange = { sheetReveal = it },
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            val revealState = rememberUpdatedState(sheetReveal)

            DisposableEffect(window, supportsBlurBehind) {
                val w = window
                if (w != null && supportsBlurBehind) {
                    w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    w.setDimAmount(0f)
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                }
                onDispose {
                    if (w != null && supportsBlurBehind) {
                        w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                        w.setDimAmount(0f)
                        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
            }

            LaunchedEffect(window, supportsBlurBehind) {
                val w = window ?: return@LaunchedEffect
                if (!supportsBlurBehind) return@LaunchedEffect
                snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                    .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                    .distinctUntilChanged()
                    .collect { step -> applyAuroraSheetWindowFx(w, step / 20f) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                availableLanguages.sorted().forEach { language ->
                    AuroraCheckboxItem(
                        label = LocaleHelper.getLocalizedDisplayName(language),
                        checked = language in languageFilter,
                        onClick = {
                            onChangeLanguageFilter(
                                if (language in languageFilter) {
                                    languageFilter - language
                                } else {
                                    languageFilter + language
                                },
                            )
                        },
                    )
                }
                Button(
                    onClick = { onChangeLanguageFilter(availableLanguages) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text(text = stringResource(MR.strings.action_select_all))
                }
                Button(
                    onClick = { onChangeLanguageFilter(emptySet()) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                ) {
                    Text(text = stringResource(MR.strings.action_clear))
                }
            }
        }
    }
}
