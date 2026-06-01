package eu.kanade.presentation.achievement.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.achievement.screenmodel.AchievementScreenModel
import eu.kanade.presentation.achievement.ui.AchievementScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle

/**
 * Voyager Screen wrapper for AchievementScreen.
 * This allows achievements to be opened as a standalone screen with back navigation.
 */
object AchievementScreenVoyager : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { AchievementScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        AchievementScreen(
            state = state,
            onClickBack = { navigator.pop() },
            onCategoryChanged = { category -> screenModel.onCategoryChanged(category) },
            onAchievementClick = { achievement ->
                screenModel.onAchievementClick(achievement)
            },
            onDialogDismiss = {
                screenModel.onDialogDismiss()
            },
            onLocaleChanged = {
                screenModel.refreshAchievements()
            },
            modifier = Modifier,
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
