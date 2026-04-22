package eu.kanade.tachiyomi.ui.entries.novel

import android.content.Context
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.presentation.novel.buildNovelCoverImageRequest
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCoverScreenModel(
    private val novelId: Long,
    private val getNovelWithChapters: GetNovelWithChapters = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    val coverCache: NovelCoverCache = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<Novel?>(null) {

    init {
        screenModelScope.launchIO {
            getNovelWithChapters.subscribe(novelId, applyScanlatorFilter = true)
                .collect { (novel, _) -> mutableState.update { novel } }
        }
    }

    fun saveCover(context: Context) {
        screenModelScope.launch {
            try {
                saveCoverInternal(context, temp = false)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.cover_saved),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_saving_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    fun shareCover(context: Context) {
        screenModelScope.launch {
            try {
                val uri = saveCoverInternal(context, temp = true) ?: return@launch
                withUIContext {
                    context.startActivity(uri.toShareIntent(context))
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_sharing_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    fun editCover(context: Context, data: Uri) {
        val novel = state.value ?: return
        screenModelScope.launchIO {
            context.contentResolver.openInputStream(data)?.use { input ->
                try {
                    novel.editCover(input, updateNovel, coverCache)
                    notifyCoverUpdated(context)
                } catch (e: Exception) {
                    notifyFailedCoverUpdate(context, e)
                }
            }
        }
    }

    fun deleteCustomCover(context: Context) {
        val novel = state.value ?: return
        screenModelScope.launchIO {
            try {
                if (coverCache.deleteFromCache(novel) > 0) {
                    updateNovel.awaitUpdateCoverLastModified(novel.id)
                }
                notifyCoverUpdated(context)
            } catch (e: Exception) {
                notifyFailedCoverUpdate(context, e)
            }
        }
    }

    private suspend fun saveCoverInternal(context: Context, temp: Boolean): Uri? {
        val novel = state.value ?: return null
        val req = ImageRequest.Builder(context)
            .data(novel)
            .size(Size.ORIGINAL)
            .build()

        return withIOContext {
            val result = context.imageLoader.execute(req).image?.asDrawable(context.resources)
            val bitmap = result?.getBitmapOrNull() ?: return@withIOContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    name = novel.title,
                    location = if (temp) Location.Cache else Location.Pictures(novel.title),
                ),
            )
        }
    }

    private fun notifyCoverUpdated(context: Context) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.cover_updated),
                withDismissAction = true,
            )
        }
    }

    private fun notifyFailedCoverUpdate(context: Context, e: Throwable) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.notification_cover_update_failed),
                withDismissAction = true,
            )
            logcat(LogPriority.ERROR, e)
        }
    }
}
