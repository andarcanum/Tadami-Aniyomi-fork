package eu.kanade.presentation.components

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import coil3.request.ImageRequest
import com.tadami.aurora.R
import eu.kanade.presentation.theme.LocalIsAuroraTheme
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover

enum class AuroraCoverPlaceholderVariant {
    Portrait,
    Wide,
}

@DrawableRes
fun auroraCoverPlaceholderResId(variant: AuroraCoverPlaceholderVariant): Int {
    return when (variant) {
        AuroraCoverPlaceholderVariant.Portrait -> R.drawable.aurora_cover_placeholder_portrait
        AuroraCoverPlaceholderVariant.Wide -> R.drawable.aurora_cover_placeholder_wide
    }
}

@Composable
fun rememberAuroraCoverPlaceholderPainter(
    variant: AuroraCoverPlaceholderVariant = AuroraCoverPlaceholderVariant.Portrait,
): Painter {
    return rememberResourceBitmapPainter(id = auroraCoverPlaceholderResId(variant))
}

@Composable
fun rememberThemeAwareCoverErrorPainter(
    variant: AuroraCoverPlaceholderVariant = AuroraCoverPlaceholderVariant.Portrait,
): Painter {
    return rememberResourceBitmapPainter(
        id = themeAwareCoverFallbackResId(
            isAuroraTheme = LocalIsAuroraTheme.current,
            variant = variant,
        ),
    )
}

fun resolveAuroraCoverPlaceholderMemoryCacheKey(data: Any?): String? {
    return when (val candidate = resolveAuroraCoverModelCandidate(data)) {
        null -> null
        is String -> candidate
        is AnimeCover -> "anime;${candidate.url};${candidate.lastModified}"
        is MangaCover -> "manga;${candidate.url};${candidate.lastModified}"
        is NovelCover -> "novel;${candidate.url};${candidate.lastModified}"
        else -> candidate.toString()
    }
}

fun buildAuroraCoverImageRequest(
    context: Context,
    data: Any?,
    configure: ImageRequest.Builder.() -> Unit = {},
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(resolveAuroraCoverModel(data))
        .placeholderMemoryCacheKey(resolveAuroraCoverPlaceholderMemoryCacheKey(data))
        .apply(configure)
        .build()
}

@DrawableRes
fun themeAwareCoverFallbackResId(
    isAuroraTheme: Boolean,
    variant: AuroraCoverPlaceholderVariant = AuroraCoverPlaceholderVariant.Portrait,
): Int {
    return if (isAuroraTheme) {
        auroraCoverPlaceholderResId(variant)
    } else {
        R.drawable.cover_empty
    }
}

fun resolveAuroraCoverModel(data: Any?): Any? {
    return resolveAuroraCoverModelCandidate(data)
}

internal data class AuroraPosterModelPair(
    val primary: Any?,
    val fallback: Any?,
)

internal fun resolveAuroraPosterModelPair(
    primary: Any?,
    fallback: Any? = null,
): AuroraPosterModelPair {
    return AuroraPosterModelPair(
        primary = resolveAuroraCoverModelCandidate(primary),
        fallback = resolveAuroraCoverModelCandidate(fallback),
    )
}

internal fun shouldApplyAuroraPosterTrim(url: String?): Boolean {
    if (url.isNullOrBlank()) {
        return false
    }
    val normalized = url.substringBefore('?').substringBefore('#').lowercase()
    val extension = normalized.substringAfterLast('.', missingDelimiterValue = "")
    return extension !in setOf("gif", "webp", "apng", "avif")
}

private fun resolveAuroraCoverModelCandidate(data: Any?): Any? {
    return when (data) {
        null -> null
        is String -> data.takeIf { it.isNotBlank() }
        is AnimeCover -> data.takeIf { !it.url.isNullOrBlank() }
        is MangaCover -> data.takeIf { !it.url.isNullOrBlank() }
        is NovelCover -> data.takeIf { !it.url.isNullOrBlank() }
        else -> data
    }
}
