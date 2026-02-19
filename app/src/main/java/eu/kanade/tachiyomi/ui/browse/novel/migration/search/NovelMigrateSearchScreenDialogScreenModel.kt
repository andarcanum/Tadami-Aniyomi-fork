package eu.kanade.tachiyomi.ui.browse.novel.migration.search

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelMigrateSearchScreenDialogScreenModel(
    val novelId: Long,
    getNovel: GetNovel = Injekt.get(),
) : StateScreenModel<NovelMigrateSearchScreenDialogScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            val novel = getNovel.await(novelId)!!

            mutableState.update {
                it.copy(novel = novel)
            }
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update {
            it.copy(dialog = dialog)
        }
    }

    @Immutable
    data class State(
        val novel: Novel? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class Migrate(val novel: Novel) : Dialog
    }
}
