package eu.kanade.tachiyomi.ui.achievement

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.achievement.screenmodel.AchievementScreenModel
import eu.kanade.presentation.achievement.ui.AchievementScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.presentation.core.util.collectAsState

data object AchievementsTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            return TabOptions(
                index = 9u,
                title = "Achievements",
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current

        val screenModel = rememberScreenModel { AchievementScreenModel() }
        val state by screenModel.state.collectAsState()

        AchievementScreen(
            state = state,
            onClickBack = { /* Handled by navigation */ },
            onCategoryChanged = { category -> screenModel.onCategoryChanged(category) },
            onAchievementClick = { achievement ->
                screenModel.onAchievementClick(achievement)
            },
            onDialogDismiss = {
                screenModel.onDialogDismiss()
            },
            modifier = Modifier.fillMaxSize(),
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
