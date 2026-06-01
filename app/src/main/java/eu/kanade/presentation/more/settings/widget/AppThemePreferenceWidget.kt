package eu.kanade.presentation.more.settings.widget

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.more.settings.LocalSettingsUiStyle
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.screen.isThemePreviewUnlocked
import eu.kanade.presentation.more.settings.screen.visibleUnlockablesForTreasuryPreview
import eu.kanade.presentation.more.settings.settingsTitleColor
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get

@Composable
internal fun AppThemePreferenceWidget(
    value: AppTheme,
    amoled: Boolean,
    onItemClick: (AppTheme) -> Unit,
) {
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora

    if (isAurora) {
        AppThemesList(
            currentTheme = value,
            amoled = amoled,
            onItemClick = onItemClick,
        )
    } else {
        BasePreferenceWidget(
            subcomponent = {
                AppThemesList(
                    currentTheme = value,
                    amoled = amoled,
                    onItemClick = onItemClick,
                )
            },
        )
    }
}

@Composable
private fun AppThemesList(
    currentTheme: AppTheme,
    amoled: Boolean,
    onItemClick: (AppTheme) -> Unit,
) {
    val context = LocalContext.current
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora

    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val debugBypassLocks by uiPreferences.debugBypassTreasuryLocks().collectAsStateWithLifecycle()

    val userProfileManager = remember { Injekt.get<tachiyomi.data.achievement.UserProfileManager>() }
    val userProfile by userProfileManager.profile.collectAsStateWithLifecycle(initialValue = null)

    val unlockableManager = remember { Injekt.get<tachiyomi.data.achievement.UnlockableManager>() }

    val appThemes = remember(userProfile, debugBypassLocks) {
        val unlockedThemes = userProfile?.unlockedThemes?.toSet() ?: emptySet()
        val unlockedUnlockables = visibleUnlockablesForTreasuryPreview(
            debugBypassLocks = debugBypassLocks,
            unlockedUnlockables = unlockableManager.getUnlockedUnlockables(),
        )

        AppTheme.entries
            .filterNot {
                it.titleRes == null ||
                    it == AppTheme.AURORA ||
                    (it == AppTheme.MONET && !DeviceUtil.isDynamicColorAvailable) ||
                    (
                        it.isHidden &&
                            !unlockedThemes.contains(it.name) &&
                            !isThemePreviewUnlocked(it, unlockedUnlockables)
                        )
            }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = appThemes,
            key = { it.name },
        ) { appTheme ->
            Column(
                modifier = Modifier
                    .width(114.dp)
                    .padding(top = 8.dp),
            ) {
                TachiyomiTheme(
                    appTheme = appTheme,
                    amoled = amoled,
                ) {
                    AppThemePreviewItem(
                        selected = currentTheme == appTheme,
                        onClick = {
                            onItemClick(appTheme)
                            (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(appTheme.titleRes!!),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isAurora) {
                                Modifier
                            } else {
                                Modifier.secondaryItemAlpha()
                            },
                        ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                    color = settingsTitleColor(),
                )
            }
        }
    }
}

@Composable
fun AppThemePreviewItem(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val appHaptics = LocalAppHaptics.current
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora

    // Interactive spring-scale states
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "theme_preview_item_scale",
    )

    val borderShape = RoundedCornerShape(16.dp)
    val innerShape = RoundedCornerShape(12.dp)

    val borderWidth = if (selected) {
        if (isAurora) 3.dp else 4.dp
    } else {
        1.dp
    }

    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        if (isAurora) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        } else {
            DividerDefaults.color
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = borderWidth,
                color = borderColor,
                shape = borderShape,
            )
            .padding(if (isAurora) 3.dp else 4.dp)
            .clip(innerShape)
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = {
                    appHaptics.tap()
                    onClick()
                },
            ),
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .weight(0.7f)
                    .padding(end = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.small,
                    ),
            )

            Box(
                modifier = Modifier.weight(0.3f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(MR.strings.selected),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Cover
        Box(
            modifier = Modifier
                .padding(start = 8.dp, top = 2.dp)
                .background(
                    color = if (isAurora) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    } else {
                        DividerDefaults.color
                    },
                    shape = MaterialTheme.shapes.small,
                )
                .fillMaxWidth(0.5f)
                .aspectRatio(ItemCover.Book.ratio),
        ) {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .size(width = 24.dp, height = 16.dp)
                    .clip(RoundedCornerShape(5.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
        }

        // Bottom bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .alpha(0.6f)
                            .height(17.dp)
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AppThemesListPreview() {
    var appTheme by remember { mutableStateOf(AppTheme.DEFAULT) }
    Injekt.addSingleton(fullType<UiPreferences>(), UiPreferences(InMemoryPreferenceStore()))
    TachiyomiTheme(appTheme = appTheme) {
        Surface {
            AppThemesList(
                currentTheme = appTheme,
                amoled = false,
                onItemClick = { appTheme = it },
            )
        }
    }
}
