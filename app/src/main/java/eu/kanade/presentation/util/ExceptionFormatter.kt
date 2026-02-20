package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.util.system.isOnline
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.items.chapter.model.NoChaptersException
import tachiyomi.domain.items.episode.model.NoEpisodesException
import tachiyomi.domain.source.anime.model.AnimeSourceNotInstalledException
import tachiyomi.domain.source.manga.model.SourceNotInstalledException
import tachiyomi.i18n.MR
import java.net.UnknownHostException

fun Throwable.formattedMessage(appContext: Context): String {
    when (this) {
        is HttpException -> return appContext.stringResource(MR.strings.exception_http, code)
        is UnknownHostException -> {
            return if (!appContext.isOnline()) {
                appContext.stringResource(MR.strings.exception_offline)
            } else {
                appContext.stringResource(MR.strings.exception_unknown_host, message ?: "")
            }
        }
        is NoChaptersException, is NoEpisodesException -> return appContext.stringResource(
            MR.strings.no_results_found,
        )
        is SourceNotInstalledException, is AnimeSourceNotInstalledException -> return appContext.stringResource(
            MR.strings.loader_not_implemented_error,
        )
    }
    return when (val className = this::class.simpleName) {
        "Exception", "IOException" -> message ?: className
        else -> "$className: $message"
    }
}
