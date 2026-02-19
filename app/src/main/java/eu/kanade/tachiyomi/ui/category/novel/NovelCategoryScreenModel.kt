package eu.kanade.tachiyomi.ui.category.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.CreateNovelCategoryWithName
import tachiyomi.domain.category.novel.interactor.DeleteNovelCategory
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.GetVisibleNovelCategories
import tachiyomi.domain.category.novel.interactor.HideNovelCategory
import tachiyomi.domain.category.novel.interactor.RenameNovelCategory
import tachiyomi.domain.category.novel.interactor.ReorderNovelCategory
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCategoryScreenModel(
    private val getAllCategories: GetNovelCategories = Injekt.get(),
    private val getVisibleCategories: GetVisibleNovelCategories = Injekt.get(),
    private val createCategoryWithName: CreateNovelCategoryWithName = Injekt.get(),
    private val hideCategory: HideNovelCategory = Injekt.get(),
    private val deleteCategory: DeleteNovelCategory = Injekt.get(),
    private val reorderCategory: ReorderNovelCategory = Injekt.get(),
    private val renameCategory: RenameNovelCategory = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<NovelCategoryScreenState>(NovelCategoryScreenState.Loading) {

    private val _events: Channel<NovelCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            val allCategories = if (libraryPreferences.hideHiddenCategoriesSettings().get()) {
                getVisibleCategories.subscribe()
            } else {
                getAllCategories.subscribe()
            }

            allCategories.collectLatest { categories ->
                mutableState.update {
                    NovelCategoryScreenState.Success(
                        categories = categories
                            .map(NovelCategory::toCategory)
                            .filterNot(Category::isSystemCategory)
                            .toImmutableList(),
                    )
                }
            }
        }
    }

    fun createCategory(name: String) {
        screenModelScope.launch {
            val order = getAllCategories.await().size.toLong()
            createCategoryWithName.await(name, order = order, flags = 0L)
                ?: _events.send(NovelCategoryEvent.InternalError)
        }
    }

    fun hideCategory(category: Category) {
        screenModelScope.launch {
            runCatching { hideCategory.await(category.id, !category.hidden) }
                .onFailure { _events.send(NovelCategoryEvent.InternalError) }
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteNovelCategory.Result.InternalError -> _events.send(
                    NovelCategoryEvent.InternalError,
                )
                DeleteNovelCategory.Result.Success -> {}
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        screenModelScope.launch {
            when (reorderCategory.await(category.id, newIndex)) {
                is ReorderNovelCategory.Result.InternalError -> _events.send(
                    NovelCategoryEvent.InternalError,
                )
                ReorderNovelCategory.Result.Success,
                ReorderNovelCategory.Result.Unchanged,
                -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        screenModelScope.launch {
            runCatching { renameCategory.await(category.id, name) }
                .onFailure { _events.send(NovelCategoryEvent.InternalError) }
        }
    }

    fun showDialog(dialog: NovelCategoryDialog) {
        mutableState.update {
            when (it) {
                NovelCategoryScreenState.Loading -> it
                is NovelCategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                NovelCategoryScreenState.Loading -> it
                is NovelCategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface NovelCategoryDialog {
    data object Create : NovelCategoryDialog
    data class Rename(val category: Category) : NovelCategoryDialog
    data class Delete(val category: Category) : NovelCategoryDialog
}

sealed interface NovelCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : NovelCategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface NovelCategoryScreenState {

    @Immutable
    data object Loading : NovelCategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<Category>,
        val dialog: NovelCategoryDialog? = null,
    ) : NovelCategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}

private fun NovelCategory.toCategory(): Category {
    return Category(
        id = id,
        name = name,
        order = order,
        flags = flags,
        hidden = hidden,
    )
}
