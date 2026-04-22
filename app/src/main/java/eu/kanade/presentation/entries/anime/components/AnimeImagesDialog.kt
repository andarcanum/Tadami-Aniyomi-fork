package eu.kanade.presentation.entries.anime.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.updatePadding
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.components.aurora.AuroraPosterActionPanel
import eu.kanade.presentation.entries.components.aurora.AuroraPosterDialog
import eu.kanade.presentation.entries.components.aurora.AuroraZoomablePoster
import eu.kanade.tachiyomi.data.coil.useBackground
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication

@Composable
fun AnimeImagesDialog(
    anime: Anime,
    isCustomCover: Boolean,
    isCustomBackground: Boolean,
    snackbarHostState: SnackbarHostState,
    pagerState: PagerState,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onEditClick: ((EditCoverAction) -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isCover = pagerState.currentPage != 1

    val arrowIcon = if (isCover) {
        Icons.AutoMirrored.Outlined.KeyboardArrowRight
    } else {
        Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    }

    val (editImageStringResource, alternateImageStringResource) = if (isCover) {
        MR.strings.action_edit_cover to AYMR.strings.action_edit_background
    } else {
        AYMR.strings.action_edit_background to MR.strings.action_edit_cover
    }

    val onImageSwitchClicked: () -> Unit = {
        scope.launchUI {
            pagerState.animateScrollToPage(1 - pagerState.currentPage)
        }
    }

    AuroraPosterDialog(
        snackbarHostState = snackbarHostState,
        onDismissRequest = onDismissRequest,
        bottomBar = {
            AuroraPosterActionPanel(
                onDismissRequest = onDismissRequest,
            ) { contentColor ->
                IconButton(onClick = onImageSwitchClicked) {
                    Icon(
                        imageVector = arrowIcon,
                        tint = contentColor,
                        contentDescription = stringResource(alternateImageStringResource),
                    )
                }
                IconButton(onClick = onSaveClick) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        tint = contentColor,
                        contentDescription = stringResource(MR.strings.action_download),
                    )
                }
                if (onEditClick != null) {
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                if ((isCover && isCustomCover) || (!isCover && isCustomBackground)) {
                                    expanded = true
                                } else {
                                    onEditClick(EditCoverAction.EDIT)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                tint = contentColor,
                                contentDescription = stringResource(editImageStringResource),
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            offset = DpOffset(8.dp, 0.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(MR.strings.action_edit)) },
                                onClick = {
                                    onEditClick(EditCoverAction.EDIT)
                                    expanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(MR.strings.action_delete)) },
                                onClick = {
                                    onEditClick(EditCoverAction.DELETE)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = onShareClick) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        tint = contentColor,
                        contentDescription = stringResource(MR.strings.action_share),
                    )
                }
            }
        },
    ) { contentPadding ->
            val statusBarPaddingPx = with(LocalDensity.current) { contentPadding.calculateTopPadding().roundToPx() }
            val bottomPaddingPx = with(LocalDensity.current) { contentPadding.calculateBottomPadding().roundToPx() }

            AuroraZoomablePoster(onDismissRequest = onDismissRequest) {
                HorizontalPager(
                    state = pagerState,
                ) { page ->
                    AndroidView(
                        factory = {
                            ReaderPageImageView(it).apply {
                                onViewClicked = onDismissRequest
                                clipToPadding = false
                                clipChildren = false
                            }
                        },
                        update = { view ->
                            val context = view.context
                            val request = ImageRequest.Builder(context)
                                .data(anime)
                                .useBackground(page == 1)
                                .size(Size.ORIGINAL)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .target { image ->
                                    val drawable = image.asDrawable(context.resources)
                                    // Copy bitmap in case it came from memory cache
                                    // Because SSIV needs to thoroughly read the image
                                    val copy = (drawable as? BitmapDrawable)?.let {
                                        BitmapDrawable(
                                            context.resources,
                                            it.bitmap.copy(Bitmap.Config.HARDWARE, false),
                                        )
                                    } ?: drawable
                                    view.setImage(copy, ReaderPageImageView.Config(zoomDuration = 500))
                                }
                                .build()
                            context.imageLoader.enqueue(request)
                            view.updatePadding(top = statusBarPaddingPx, bottom = bottomPaddingPx)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
}
