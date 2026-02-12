package eu.kanade.tachiyomi.ui.category.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.MangaCategoryScreen
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.category.manga.MangaCategoryScreenState
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.novelCategoryTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { NovelCategoryScreenModel() }

    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_novel,
        searchEnabled = false,
        content = { _, _ ->
            if (state is NovelCategoryScreenState.Loading) {
                LoadingScreen()
            } else {
                val successState = state as NovelCategoryScreenState.Success

                MangaCategoryScreen(
                    state = MangaCategoryScreenState.Success(
                        categories = successState.categories,
                    ),
                    onClickCreate = { screenModel.showDialog(NovelCategoryDialog.Create) },
                    onClickRename = { screenModel.showDialog(NovelCategoryDialog.Rename(it)) },
                    onClickHide = screenModel::hideCategory,
                    onClickDelete = { screenModel.showDialog(NovelCategoryDialog.Delete(it)) },
                    onChangeOrder = screenModel::changeOrder,
                )

                when (val dialog = successState.dialog) {
                    null -> {}
                    NovelCategoryDialog.Create -> {
                        CategoryCreateDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onCreate = screenModel::createCategory,
                            categories = successState.categories.fastMap { it.name }.toImmutableList(),
                        )
                    }
                    is NovelCategoryDialog.Rename -> {
                        CategoryRenameDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onRename = { screenModel.renameCategory(dialog.category, it) },
                            categories = successState.categories.fastMap { it.name }.toImmutableList(),
                            category = dialog.category.name,
                        )
                    }
                    is NovelCategoryDialog.Delete -> {
                        CategoryDeleteDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onDelete = { screenModel.deleteCategory(dialog.category.id) },
                            category = dialog.category.name,
                        )
                    }
                }
            }
        },
        navigateUp = navigator::pop,
    )
}
